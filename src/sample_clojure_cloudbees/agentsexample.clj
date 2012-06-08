(ns sample-clojure-cloudbees.agentsexample
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
  "EXAMPLE Open an async url connection - check if done - using an agent"
  [url]  
  (print (str "checking:" url "\n"))  
  (print (str "Thread:" (Thread/currentThread)))
  (with-open [client (http/create-client)] ; Create client
    (let [resp (http/GET client url)]      
      (http/await resp) ;; will block
      (check-response resp))))


(defn check-urls 
  "EXAMPLE shows how to use it with agents"
  [urls]
  (let [agents (doall (map #(agent %) urls))]
    (doseq [agent agents] (send-off agent check-url))
    (apply await-for 5000 agents)
    (doall (map #(deref %) agents))))
