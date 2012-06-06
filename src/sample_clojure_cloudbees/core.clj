(ns sample-clojure-cloudbees.core
  (:use compojure.core)  
  (:use ring.middleware.resource)  
  (:require 
	  [compojure.route           :as route]
	  [compojure.handler         :as handler]
    [http.async.client         :as http]
    [http.async.client.request :as request]))

(defn open-url 
  "Open an async url connection - check if done"
  [url]  
  (with-open [client (http/create-client)] ; Create client
    (let [resp (http/GET client url)
          status (http/status resp)
          headers (http/headers resp)]
      (println (:code status))
      (http/await resp)
      (http/string resp))))

;; should really use agents with http/await in function passed


(defn with-callback 
  "lets try this with a call back - a lot of WTF"
  []
  (with-open [client (http/create-client)] ; create client
  (let [request (request/prepare-request :get "http://www.smh.com.au/") ; create request
        status (promise)                ; status promise that will be delivered by callback
        response (request/execute-request
                  client request        ; execute *request*
                  :status               ; status callback
                  (fn [res st]          ; *res* is response map, same as one returned by *execute-request*
                    (println "called back !")                    ; *st* is status map, as described above
                    (deliver status st) ; deliver status promise
                    [st :abort]))]      ; return status to be delivered to response map and abort further processing of response.                  
        (println @status); await status to be delivered and print it.
        ))

     (println "callback was dispatched")

  )

(defn get-urls [urls]
  (let [agents (doall (map #(agent %) urls))]
    (doseq [agent agents] (send-off agent open-url))
    (apply await-for 5000 agents)
    (doall (map #(deref %) agents))))


(defn root-page []
    (prn (get-urls '("http://lethain.com" "http://willarson.com")))


    (with-callback)
    (println (open-url "http://github.com/neotyk/http.async.client/"))
  "OK")


  




(defroutes main-routes    
  (GET "/" [] (root-page))
  (GET "/another-page" [] "This is another page")  
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))

(def app  
  (handler/site main-routes))


