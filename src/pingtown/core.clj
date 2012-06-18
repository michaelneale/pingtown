(ns pingtown.core
  (:use compojure.core)  
  (:use ring.middleware.resource)  
  (:use overtone.at-at)
  (:use pingtown.pinger)
  (:use pingtown.pagerduty)
  (:require 
	  [compojure.route           :as route]
	  [compojure.handler         :as handler]
    [ring.util.response :as resp]))



(defn nbr [s] (Integer/parseInt s))

(defn maybe-millis [params field otherwise]
  (if (contains? params field)
    (* 1000 (nbr (params field)))
    otherwise))

(defn maybe-nbr [params field otherwise]
  (if (contains? params field)
    (nbr (params field))
    otherwise))


(defn create-check [p]
      (register-check {
            :interval (maybe-millis p "interval" 60000) 
            :timeout (maybe-millis p "timeout" 8000)                           
            :failures (maybe-nbr p "failures" 2)
            :url (p "url")
            :webhook (p "endpoint")
            :expected-code (maybe-nbr p "expected_code" nil)
            :expected-upper (maybe-nbr p "expected_code_below" 400)
            :initial-delay (maybe-millis p "initial_delay" nil)
            :expires-after (maybe-millis p "expires_after" nil)})
        {:status 200 :body "-- Registered check OK --\n"})



(defn validate-and-create 
  "validate and create the new task if possible or else explain why not"
  [params]
   (cond 
      (not (contains? params "url")) 
        {:status 400 :body "Please provide a [url] to check"}      
      (check-existing? (params "url"))
        {:status 202 :body "A check for that site already exists"}
      (not (.startsWith (params "url") "http"))
        {:status 400 :body "Url must start with http"}  
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
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))

(def app    
  (handler/site main-routes))


