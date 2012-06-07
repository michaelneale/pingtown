(ns sample-clojure-cloudbees.core
  (:use compojure.core)  
  (:use ring.middleware.resource)  
  (:require 
	  [compojure.route           :as route]
	  [compojure.handler         :as handler]
    [http.async.client         :as http]
    [http.async.client.request :as request]))

(defn check-url 
  "Open an async url connection - check if done"
  [url]  
  (print (str "checking:" url "\n"))  
  (print (str "Thread:" (Thread/currentThread)))
  (with-open [client (http/create-client)] ; Create client
    (let [resp (http/GET client url)
          status (http/status resp)
          headers (http/headers resp)]      
      (http/await resp)
      (= 200 (:code status)))))


(defn check-urls [urls]
  (let [agents (doall (map #(agent %) urls))]
    (doseq [agent agents] (send-off agent check-url))
    (apply await-for 5000 agents)
    (doall (map #(deref %) agents))))


(defn root-page []
    (prn (check-urls 
      '("http://lethain.com" "http://willarson.com" "http://www.smh.com.aux")
      ))  
    "OK")


  




(defroutes main-routes    
  (GET "/" [] (root-page))
  (GET "/another-page" [] "This is another page")  
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))

(def app  
  (handler/site main-routes))


