(ns pingtown.persist
  (:use compojure.core)  
  (:use cheshire.core)  
  (:require 
    [http.async.client :as http]
    [ring.util.codec :as c]))


(defn- get-env [var-name] (System/getProperty var-name (System/getenv var-name)))

(def db-url (get-env "pingtown_db"))
(def db-key (get-env "pingtown_db_key"))
(def db-secret (get-env "pingtown_db_secret"))
(def client (http/create-client))


(defn store-check [check]
    (let [resp (http/PUT client (str db-url (c/url-encode (:url check)))
        :headers { :content-type "application/json"}
        :body (generate-string check)
        :timeout 10000
     :auth { :user db-key :password db-secret})]                
        (http/await resp)  
        (println (http/string resp))
        (:ok (parse-string (http/string resp) true))))

(defn delete-check [url]
    (let [current-check (load-check url)
          resp (http/DELETE client 
                   (str db-url (c/url-encode url) "?rev=" (:_rev current-check))
                :timeout 10000
                :auth { :user db-key :password db-secret})]
            (http/await resp)
            (println (http/string resp))
            (:ok (parse-string (http/string resp) true))))

(defn load-check [url]
    (let [resp (http/GET client (str db-url (c/url-encode url))
                :auth {:user db-key :password db-secret})]
                (http/await resp)
            (parse-string (http/string resp) true)))        



(defn list-checks 
    "return a list of all checks (ids - which are urls, really)"
    [] 
    (let [resp (http/GET client (str db-url "_all_docs")
     :auth { :user db-key :password db-secret})]                
        (http/await resp)  
        (map (fn [row] (c/url-decode (:id row))) 
            (:rows (parse-string (http/string resp) true)))))





