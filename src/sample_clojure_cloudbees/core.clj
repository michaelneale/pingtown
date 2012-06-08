(ns sample-clojure-cloudbees.core
  (:use compojure.core)  
  (:use ring.middleware.resource)  
  (:use overtone.at-at)
  (:require 
	  [compojure.route           :as route]
	  [compojure.handler         :as handler]
    [http.async.client         :as http]
    [http.async.client.request :as request]))

(def at-pool (mk-pool))

;; store the tasks here, protected by an agent
(def task-list (agent {}))

(def http-client (http/create-client))

(defn check-response [resp]
  (= 200 (:code (http/status resp))))

(defn get-task-value [url task-key] ((deref task-list) url) task-key)
(defn update-task-value [url task-key value]
  (defn update [all-tasks]
    (let [task-entry (all-tasks url)]
        (merge all-tasks {url (merge task-entry {task-key value})})))
  (send task-list update))


(defn site-down 
  "handle the site down event - should invoke webhook, as is really down"
  [url webhook]
  (println (str "SITE DOWN " url " should call webhook " webhook))
  ;;TODO PUT ACTUAL WEBHOOK CALL HERE
  (update-task-value url :failures 0) )

(defn site-unavailable
  "register that site is unavailable"
  [url new-fail-tally]
  (update-task-value url :failures new-fail-tally))

(defn maybe-failure 
  "record a failure, possibly taking action on webhook"
  [client url count-to-failure webhook]
  (println (str "... a failure noted for " url))
  (println ((deref task-list) url)) 
  (println (((deref task-list) url)) :failures) ;;WTF
  (let [ fail-tally (+ 1 (get-task-value url :failures)) ]
      (println (str "failure tally is " fail-tally))
      (if (>= fail-tally count-to-failure)
          (site-down url webhook)
          (site-unavailable url fail-tally))))
  

(defn test-follow-up
  [client resp url count-to-failure webhook]
  (if (check-response resp)
    (println (str "... " url " appears to be OK at this time."))
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
            :url "http://www.smh.com.aux"
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


