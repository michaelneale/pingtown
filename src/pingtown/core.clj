(ns pingtown.core
  (:use compojure.core)  
  (:use ring.middleware.resource)  
  (:use overtone.at-at)
  (:require 
	  [compojure.route           :as route]
	  [compojure.handler         :as handler]
    [http.async.client         :as http]
    [http.async.client.request :as request]))

;; TODO invoke webhook
;; TODO REST api
;; TODO index.html with example
;; TODO store tasks and load from s3 on startup
;; TODO show list and removal of tasks by url

(def at-pool (mk-pool))

;; store the tasks here, protected by an agent - maybe a defrecord or type?
(def task-list (agent {}))

(def http-client (http/create-client))

(defn check-response [resp]
  (= 200 (:code (http/status resp))))

(defn get-task-value [url task-key] (task-key ((deref task-list) url)))
(defn url-is-down [url] (contains? ((deref task-list) url) :outage-start))
(defn update-task-value [url task-key value]
  (defn update [all-tasks]
    (let [task-entry (all-tasks url)]
        (merge all-tasks {url (merge task-entry {task-key value})})))
  (send task-list update))

(defn remove-task-value [url task-key]
  (defn update [all-tasks]
    (let [task-entry (all-tasks url)]
        (merge all-tasks {url (dissoc task-entry task-key)})))
  (send task-list update))


(defn notify-down [url webhook]  
  ;;TODO: invoke webhook here
  (println (str "DOWN " url)))

(defn notify-up [url]
  (println (str "UP " url " was down for " 
    (- (System/currentTimeMillis)  (get-task-value url :outage-start)))))


(defn site-down 
  [url webhook]  
  (if (get-task-value url :outage-start)
    (println (str " (already down) " url))
    (do
      (update-task-value url :outage-start (System/currentTimeMillis)) 
      (notify-down url webhook))))


(defn maybe-failure 
  "record a failure, site possibly down"
  [client url count-to-failure webhook]
  (println (str "... a failure noted for " url))
  (let [ fail-tally (+ 1 (get-task-value url :failures)) ]
      (println (str "failure tally is " fail-tally))
      (if (>= fail-tally count-to-failure)
          (site-down url webhook)
          (update-task-value url :failures fail-tally))))
  

(defn site-available   
  [url webhook]
  (println (str "... " url " has OK status"))
  (println (url-is-down url))
  (if (url-is-down url)
    (do      
      (notify-up url)      
      (remove-task-value url :outage-start)
      (update-task-value url :failures 0))    
    (println (str "... " url " is still OK, no action taken."))))

(defn test-follow-up
  [client resp url count-to-failure webhook]
  (if (check-response resp)  
    (site-available url webhook)  
    (maybe-failure client url count-to-failure webhook)))

(defn perform-test
  [client url wait-time count-to-failure webhook]
  (println (str "... now checking: " url))
  (let [resp (http/GET client url :timeout wait-time)]
    (after wait-time
          #(test-follow-up client resp url count-to-failure webhook) at-pool)))

(defn register-check
    "create a new check"
    [client check-config]
    (let [task (every (check-config :interval) 
                      #(perform-test client
                        (check-config :url) 
                        (check-config :timeout) 
                        (check-config :failures) 
                        (check-config :webhook)) 
                      at-pool)]
      (defn append-task [ls new-task] (merge ls new-task))
      (send task-list append-task {(check-config :url) {:task task :failures 0}})))


(defn root-page []
    ;;(prn (ping-urls '("http://lethain.com" "http://willarson.com" "http://www.smh.com.aux")))  
    "OK")


(defn new-task-action []
  (register-check http-client {
            :interval 10000
            :url "http://localhost:8000"
            :timeout 3000
            :failures 2
            :webhook "http://localhost:8000/smhfail"
            }))
  



;;
;; Rest application: 
;;
(defroutes main-routes    
  (GET "/" [] (root-page))
  (GET "/task" [] (new-task-action))  
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))

(def app    
  (handler/site main-routes))


