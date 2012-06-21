(ns pingtown.pinger
  (:use compojure.core)  
  (:use ring.middleware.resource)  
  (:use overtone.at-at)
  (:use pingtown.pagerduty)
  (:require 
      [compojure.route           :as route]
      [compojure.handler         :as handler]
    [http.async.client         :as http]
    [http.async.client.request :as request]
    [ring.util.codec :as codec]))

;; pool for at-at timer tasks
(def at-pool (mk-pool))

;; store the tasks here, protected by an agent - maybe a defrecord or type?
(def task-list (agent {}))

(defn print-tasks [] (str (deref task-list)))

;;(defn print-tasks []  (map (fn [e] (e 0)) (deref task-list)))

(defn check-existing? [url] (contains? (deref task-list) url))

;; functions to do the evil mutation of tasks via agent: 
(defn get-task-value [url task-key] (task-key ((deref task-list) url)))
(defn update-task-value [url task-key value]
  (defn update [all-tasks]
    (let [task-entry (all-tasks url)]
        (merge all-tasks {url (merge task-entry {task-key value})})))
  (send task-list update))
(defn remove-task-value [url task-key]
  (defn update [all-tasks]
    (let [task-entry (all-tasks url)]
        (merge all-tasks {url (dissoc task-entry task-key)})))
  (send task-list update))


(def http-client (http/create-client))

(defn site-is-down? [url] (contains? ((deref task-list) url) :outage-start))

(defn- dependency-ok? 
  "Check we don't have a failed depdendency, if we do, shut up."
  [conf]
  (if (= nil (:depends-on conf))
    true
    (not (site-is-down? (:depends-on conf)))))

(defn notify-down [conf fail-reason]    
  (println (str "DOWN " (:url conf)))
  (if (dependency-ok? conf) 
    (pd-down conf fail-reason)
    true))

(defn notify-up [conf]
  (let [url (:url conf)
        downtime (- (System/currentTimeMillis)  
                (get-task-value url :outage-start))] 
      (println (str "UP " url " was down for " downtime "ms"))
      (pd-up conf downtime)))



(defn site-down 
  "Maybe send a notification that the site is down."
  [conf fail-reason]  
  (if (site-is-down? (:url conf))
    (println (str "... (already noted as down) " (:url conf)))    
    (if (notify-down conf fail-reason)
        (update-task-value (:url conf) :outage-start (System/currentTimeMillis)) 
        (println "ERR: Unable to contact endpoint for alert"))))

(defn maybe-failure 
  "record a failure, site possibly really down"
  [client conf fail-reason];;url count-to-failure webhook fail-reason]  
  (let [ fail-tally (+ 1 (get-task-value (:url conf) :failures)) ]      
      (update-task-value (:url conf) :failures fail-tally)
      (if (>= fail-tally (:failures conf))
          (site-down conf fail-reason)
          (println (str "... a failure noted for " 
                    (:url conf) " failure reason " fail-reason)))))
  

(defn site-available  
  "Maybe notify that site is back up, or not." 
  [conf]
  (if (site-is-down? (:url conf))
    (do      
      (notify-up conf)      
      (remove-task-value (:url conf) :outage-start)
      (update-task-value (:url conf) :failures 0))    
    (println (str "... " (:url conf) " is still OK, no action taken."))))

(defn quick-check []
  (codec/base64-encode (.getBytes "yeah man !")))

(defn check-response 
  "check the response against the expected. If 
  expected is not set, then it has to be < 400 to be success
  returns {:up :reason}"
  [resp conf]

  (let [done (http/done? resp)
        expected (:expected-code conf)
        upper-status (:expected-upper conf)] 
    (cond 
      (not done) {:up false :reason "Timeout"}
      (http/failed? resp) {:up false :reason (str (http/error resp))}
      (= nil (:code (http/status resp))) {:up false :reason "No response"}                 
      expected (if (= expected (:code (http/status resp))) 
                        {:up true :reason "Got what we wanted"}
                        {:up false :reason 
                          (str "Got response " (:code (http/status resp))
                           " expected " expected)})
      (< (:code (http/status resp)) upper-status)  
          {:up true :reason "Is lower than upper-status"}
      :else {:up false :reason (str "Response" resp )})))

(defn check-body [resp conf]
  (if (.contains (http/string resp) (:body-pattern conf))    
    {:up true :reason "Body matches pattern"}
    {:up false :reason "Body did not match pattern"}))
  

(defn check-it [resp conf]
  (let [status-check (check-response resp conf)]
    (println status-check)
    (if (and (:up status-check) (not (= nil (:body-pattern conf))))
        (check-body resp conf)
        status-check)))


(defn test-follow-up
  [client resp conf];;url count-to-failure webhook expected-code]
  (println "test follow up called")
  (let [result (check-it resp conf)] 
    (println (str "Result was " result))
    (if (:up result)
      (site-available conf)  
      (maybe-failure client conf (:reason result)))))

(defn http-get [client conf]
  (if (= nil (:user conf))
      (http/GET client (:url conf) :timeout (conf :timeout))
      (http/GET client (:url conf) :timeout (conf :timeout) 
        :auth { :type :basic :user (:user conf) :password (:password conf) :preemptive true})))

  
(defn perform-test
  [client conf];;url wait-time count-to-failure webhook expected-code]
  (println (str "... now checking: " (:url conf)))
  (let [resp (http-get client conf)]
    (after (:timeout conf)
          #(test-follow-up client resp conf) at-pool)))

(defn store-config [config] (println "IMPLEMENT ME !"))
(defn remove-from-storage [url] (println (str "IMPLEMENT ME REMOVE!" url)))


(defn remove-check-for [url]
    (remove-from-storage url)    
    (let [task-entry ((deref task-list) url)]
        (println (str "STOPPING " task-entry " for " url))
        (stop (:task task-entry))
        (send task-list (fn [all-tasks] (dissoc all-tasks url)))))


(defn maybe-expire [conf]
    (if (:expires-after conf)
      (after (:expires-after conf) 
          #(remove-check-for (:url conf)) at-pool)))

(defn register-check    
    "create a new check"
    [conf]
    (store-config conf)
    (println (str "Registering " conf))
    (let [task (every (conf :interval) 
                      #(perform-test http-client conf) 
                        at-pool
                        :initial-delay 
                        (if (:initial-delay conf) 
                          (:initial-delay conf) 0))]
      (maybe-expire conf)
      (defn append-task [ls new-task] (merge ls new-task))
      (send task-list append-task {(conf :url) {:task task :failures 0}})))


