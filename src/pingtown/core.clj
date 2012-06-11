(ns pingtown.core
  (:use compojure.core)  
  (:use ring.middleware.resource)  
  (:use overtone.at-at)
  (:use pingtown.pinger)
  (:require 
	  [compojure.route           :as route]
	  [compojure.handler         :as handler]
    [ring.util.response :as resp]))

;; TODO invoke webhook
;; TODO REST api
;; TODO index.html with example
;; TODO store tasks and load from s3 on startup
;; TODO show list and removal of tasks by url


(defn test-task-action []
  (register-check {
            :interval 10000
            :url "http://localhost:8000"
            :timeout 3000
            :failures 2
            :webhook "http://localhost:8000/smhfail"
            }))
 


(defn nbr [s] (Integer/parseInt s))

(defn create-check [p]
      (register-check {
            :interval (if (contains? p "interval") 
                          (* 1000 (nbr (p "interval"))) 60000)
            :timeout (if (contains? p "timeout") 
                          (* 1000 (nbr (p "timeout"))) 8000)
            :failures (if (contains? p "failures") 
                          (nbr (p "failures")) 2)
            :url (p "url")
            :webhook (p "webhook")})
        {:status 200 :body "Registered check OK"})



(defn validate-and-create 
  "validate and create the new task if possible or else explain why not"
  [params]
   (cond 
      (not (contains? params "url")) 
        {:status 400 :body "Please provide a [url] to check"}
      (check-existing? (params "url"))
        {:status 409 :body "A check for that site already exists"}
      (not (contains? params "webhook")) 
        {:status 400 :body "Please provide a [webhook] to call"}
      (and (contains? params "interval") (> 30 (nbr (params "interval"))))         
        {:status 400 :body "Interval should be at least 30 (seconds)"}
      (and (contains? params "timeout") (> 5 (nbr (params "timeout")) )) 
        {:status 400 :body "Timeout should be at least 5 (seconds)"}
      (and (contains? params "failures") (> 1 (nbr (params "failures")))) 
        {:status 400 :body "Failures should be set to be at least 1"}
      :else (create-check params)))




;;
;; Rest application: 
;;
(defroutes main-routes  
  (GET "/" [] (resp/redirect "/index.html"))
  (POST "/tasks" {form-params :form-params} (validate-and-create form-params))
  (DELETE "/tasks" {form-params :form-params} (remove-check-for (form-params "url")))
  (GET "/tasks" [] (print-tasks))
  (GET "/test" [] (test-task-action))    
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))

(def app    
  (handler/site main-routes))


