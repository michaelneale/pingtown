(ns sample-clojure-cloudbees.core
  (:use compojure.core)  
  (:use ring.middleware.resource)  
  (:require 
	  [compojure.route           :as route]
	  [compojure.handler         :as handler]
    [http.async.client         :as http]
    [http.async.client.request :as request]))

(defn check-response [resp]
  (= 200 (:code (http/status resp))))


(defn ping-url 
  "Open an async url connection - check if done"
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


