(ns sample-clojure-cloudbees.core
  (:use compojure.core)  
  (:use ring.middleware.resource)  
  (:use overtone.at-at)
  (:require 
	  [compojure.route           :as route]
	  [compojure.handler         :as handler]
    [http.async.client         :as http]
    [http.async.client.request :as request]))

(def my-pool (mk-pool))

;; sample data
(def targets [
  {:url "http://www.smh.com.au" 
   :webhook "http://localhost:8000/smhdown"
   :failures 2 }
   {:url "http://www.smh.com.aux" 
   :webhook "http://localhost:8000/smhdown"
   :failures 2 }])

;; store the failures here, keyed against url string
(def failures (agent {"http://www.example.com" 1}))


(defn process-new-failure 
  "We have a failure, accumulate it, and take the action if needed"
  [url]
  ;;(send failures )
  )


(defn check-response [resp]
  (= 200 (:code (http/status resp))))

(defn ping-url 
  "Open an async url connection - return the map of promises"
  [client url]  
    (http/GET client url :timeout 5000)) 

(defn ping-urls [urls]
  (with-open [client (http/create-client)] ; Create client
    (println "starting")
    (let [requests (doall (map #(ping-url client %) urls))]
      (println "dispatched.. now resting")
      (Thread/sleep 5000)
      (doall (map #(check-response %) requests)))))



(defn root-page []
  (at (+ 10000 (now)) #(println "hello from the past!") my-pool)
    (prn (ping-urls 
      '("http://lethain.com" "http://willarson.com" "http://www.smh.com.aux")))  
    "OK")


  


;;
;; Rest application: 
;;
(defroutes main-routes    
  (GET "/" [] (root-page))
  (GET "/another-page" [] "This is another page")  
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))

(def app    
  (handler/site main-routes))


