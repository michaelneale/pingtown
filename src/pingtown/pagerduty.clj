(ns pingtown.pagerduty
  (:use compojure.core)  
  (:use overtone.at-at)  
  (:require 
    [http.async.client         :as http]))

;; For actually doing the notifications

(defn- down-message    
    "create a pagerduty alert message"
    [site fail-reason service-key] 
    (str "{
        \"service_key\": \"" service-key  "\",
        \"incident_key\": \"" site "\",
        \"event_type\": \"trigger\",
        \"description\": \"DOWN " site "\",
        \"details\": {
        \"reported by\": \"PingTown (tm)\",        
        \"failure-reason\": \"" fail-reason "\"
        }
    }"))

(defn- up-message    
    "create a pagerduty up message"
    [site downtime service-key] 
    (str "{
        \"service_key\": \"" service-key "\",
        \"incident_key\": \"" site "\",
        \"event_type\": \"resolve\",
        \"description\": \"UP " site  " was down for " downtime "ms\",
        \"details\": {
        \"reported by\": \"PingTown (tm)\",
        \"downtime\": \""  downtime "\"                
        }
    }"))

(defn- get-env [var-name] (System/getProperty var-name (System/getenv var-name)))

;; "https://events.pagerduty.com/generic/2010-04-15/create_event.json"

(defn- get-endpoint [conf]
    (if (:webhook conf)
        (:webhook conf)
        (get-env "endpoint") ))

(defn- get-service-key [conf]
    (if (:service-key conf) (:service-key conf) 
        (get-env "endpoint_service_key")))

(defn- send-message [msg endpoint]      
    (println (str "calling pager duty .." endpoint))

    (with-open [client (http/create-client)] ; Create client
        (let [resp (http/POST client endpoint :body msg)
              status (http/status resp)]                
            (http/await resp)  ;; this will make it wait              
            (println (http/string resp))
            (println status)
            (= 200 (:code status)))))

            
(defn pd-down [conf fail-reason]
    (send-message (down-message (:url conf) fail-reason (get-service-key conf)) 
        (get-endpoint conf)))

(defn pd-up [conf downtime]
    (send-message (up-message (:url conf) downtime (get-service-key conf)) 
        (get-endpoint conf))) 





