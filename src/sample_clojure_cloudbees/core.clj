(ns sample-clojure-cloudbees.core
  (:use compojure.core)  
  (:use ring.middleware.resource)  
  (:require 
	  [compojure.route :as route]
	  [compojure.handler :as handler]
    [http.async.client :as http]))

(defn open-url 
  "Open an async url connection"
  [url]  
  (with-open [client (http/create-client)] ; Create client
    (let [resp (http/GET client url)]    
        (print "done: ")
        (println (.toString (http/done? resp)))
        (print "failed: ")
        (println (.toString (http/failed? resp)))
      )
  ))


(defn root-page []
    (open-url "http://github.com/neotyk/http.async.client/")
  "OK")


  




(defroutes main-routes    
  (GET "/" [] (root-page))
  (GET "/another-page" [] "This is another page")  
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))

(def app  
  (handler/site main-routes))


