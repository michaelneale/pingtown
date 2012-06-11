(ns pingtown.core
  (:use compojure.core)  
  (:use ring.middleware.resource)  
  (:use overtone.at-at)
  (:use pingtown.pinger)
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
  (POST "/tasks" {form-params :form-params} (str (form-params "foo") ))
  (GET "/task" [] (new-task-action))  
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))

(def app    
  (handler/site main-routes))


