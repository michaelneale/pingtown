(ns pingtown.pinger
  (:use compojure.core)  
  (:use ring.middleware.resource)  
  (:use overtone.at-at)
  (:require 
      [compojure.route           :as route]
      [compojure.handler         :as handler]
    [http.async.client         :as http]
    [http.async.client.request :as request]))

;; pool for at-at timer tasks
(def at-pool (mk-pool))

;; store the tasks here, protected by an agent - maybe a defrecord or type?
(def task-list (agent {}))

(defn print-tasks [] (str (deref task-list)))

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

(defn check-response [resp]
  (= 200 (:code (http/status resp))))


(defn notify-down [url webhook]  
  ;;TODO: invoke webhook here
  (println (str "DOWN " url)))

(defn notify-up [url webhook]
  (println (str "UP " url " was down for " 
    (- (System/currentTimeMillis)  (get-task-value url :outage-start)))))


(defn site-is-down? [url] (contains? ((deref task-list) url) :outage-start))

(defn site-down 
  [url webhook]  
  (if (site-is-down? url)
    (println (str "... (already noted as down) " url))
    (do
      (update-task-value url :outage-start (System/currentTimeMillis)) 
      (notify-down url webhook))))


(defn maybe-failure 
  "record a failure, site possibly down"
  [client url count-to-failure webhook]  
  (let [ fail-tally (+ 1 (get-task-value url :failures)) ]      
      (update-task-value url :failures fail-tally)
      (if (>= fail-tally count-to-failure)
          (site-down url webhook)
          (println (str "... a failure noted for " url)))))
  

(defn site-available   
  [url webhook]
  (if (site-is-down? url)
    (do      
      (notify-up url webhook)      
      (remove-task-value url :outage-start)
      (update-task-value url :failures 0))    
    (println (str "... " url " is still OK, no action taken."))))

(defn test-follow-up
  [client resp url count-to-failure webhook]
  (if (check-response resp)  
    (site-available url webhook)  
    (maybe-failure client url count-to-failure webhook)))

(defn perform-test
  [client url wait-time count-to-failure webhook]
  (println (str "... now checking: " url))
  (let [resp (http/GET client url :timeout wait-time)]
    (after wait-time
          #(test-follow-up client resp url count-to-failure webhook) at-pool)))

(defn store-config [config] (println "IMPLEMENT ME !"))
(defn remove-from-storage [url] (println "IMPLEMENT ME !"))

(defn register-check    
    "create a new check"
    [check-config]
    (store-config check-config)
    (println (str "Registering " check-config))
    (let [task (every (check-config :interval) 
                      #(perform-test http-client
                        (check-config :url) 
                        (check-config :timeout) 
                        (check-config :failures) 
                        (check-config :webhook)) 
                      at-pool)]
      (defn append-task [ls new-task] (merge ls new-task))
      (send task-list append-task {(check-config :url) {:task task :failures 0}})))

(defn remove-check-for [url]
    (remove-from-storage url)
    ;; TODO: remove from s3 here
    (let [task-entry ((deref task-list) url)]
        (stop (:task task-entry))
        (send task-list (fn [all-tasks] (dissoc all-tasks url)))))

