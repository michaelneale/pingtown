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


(defn check-url 
  "Open an async url connection - check if done"
  [url]  
  (print (str "checking:" url "\n"))  
  (print (str "Thread:" (Thread/currentThread)))
  (with-open [client (http/create-client)] ; Create client
    (let [resp (http/GET client url)]      
      (http/await resp) ;; will block
      (check-response resp))))



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


