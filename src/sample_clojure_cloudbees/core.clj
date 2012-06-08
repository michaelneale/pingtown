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

(defn test-follow-up
  [resp url count-to-failure webhook]
  (if (check-response resp)
    (println (str "... " url " appears to be OK at this time."))
    (println (str "ATTENTION: we have a check fail on " url))))

(defn perform-test
  [client url wait-time count-to-failure webhook]
  (println (str "... now checking: " url))
  (let [resp (http/GET client url :timeout wait-time)]
    (after wait-time
          #(test-follow-up resp url count-to-failure webhook) at-pool)))

(defn register-check
    "create a new check"
    [client check-config]
    (let [task (every (check-config :interval) #(perform-test client
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
            :interval 20000 
            :url "http://www.smh.com.au"
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


