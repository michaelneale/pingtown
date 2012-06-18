(ns pingtown.pagerduty
  (:use compojure.core)  
  (:use overtone.at-at)
  (:use pingtown.secrets)
  (:require 
    [http.async.client         :as http]))

;; For actually doing the notifications

(defn- down-message    
    "create a pagerduty alert message"
    [site fail-reason] 
    (str "{
        \"service_key\": \"" pagerduty-key  "\",
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
    [site downtime] 
    (str "{
        \"service_key\": \"" pagerduty-key  "\",
        \"incident_key\": \"" site "\",
        \"event_type\": \"resolve\",
        \"description\": \"UP " site  " was down for " downtime "ms\",
        \"details\": {
        \"reported by\": \"PingTown (tm)\",
        \"downtime\": \""  downtime "\"                
        }
    }"))


(defn- send-message [msg]   
    (def pd-endpoint "https://events.pagerduty.com/generic/2010-04-15/create_event.json") 
    (println "calling pager duty ..")
    (with-open [client (http/create-client)] ; Create client
        (let [resp (http/POST client pd-endpoint :body msg)
              status (http/status resp)]                
            (http/await resp)  ;; this will make it wait              
            (println (http/string resp))
            (println status)
            (= 200 (:code status)))))

            
(defn pd-down [conf fail-reason]
    (send-message (down-message (:url conf) fail-reason)))

(defn pd-up [conf downtime]
    (send-message (up-message (:url conf) downtime)))





