(ns pingtown.core
  (:use compojure.core)  
  (:use ring.middleware.resource)    
  (:use pingtown.pinger)  
  (:use ring.middleware.http-basic-auth)
  (:use cheshire.core)
  (:use pingtown.persist)
  (import [java.security MessageDigest])  
  (:require 
	  [compojure.route           :as route]
	  [compojure.handler         :as handler]
    [ring.util.response :as resp]))



(defn nbr [s] (Integer/parseInt s))

(defn maybe-millis [params field otherwise]
  (if (contains? params field)
    (* 1000 (nbr (params field)))
    otherwise))

(defn maybe-nbr [params field otherwise]
  (if (contains? params field)
    (nbr (params field))
    otherwise))

(defn hash-fn [input]
  (let [md (MessageDigest/getInstance "SHA-256")]
    (. md update (.getBytes input))
    (let [digest (.digest md)]
      (apply str (map #(format "%02x" (bit-and % 0xff)) digest)))))


(defn create-check [p]
      (if (register-check {
            :interval (maybe-millis p "interval" 60000) 
            :timeout (maybe-millis p "timeout" 8000)                           
            :failures (maybe-nbr p "failures" 2)
            :url (p "url")
            :webhook (p "endpoint")
            :expected-code (maybe-nbr p "expected_code" nil)
            :expected-upper (maybe-nbr p "expected_code_below" 400)
            :initial-delay (maybe-millis p "initial_delay" nil)
            :expires-after (maybe-millis p "expires_after" nil)
            :service-key (p "service_key")
            :body-pattern (p "body_pattern")
            :user (p "check_user")
            :password (p "check_password")
            :depends-on (p "depends_on")
            })      
        {:status 200 :body "Registered check OK\n"}
        {:status 500 :body "ERROR: Unable to create check\n"}))



(defn validate-and-create 
  "validate and create the new task if possible or else explain why not"
  [params]
   (cond 
      (not (contains? params "url")) 
        {:status 400 :body "Please provide a [url] to check"}      
      (check-existing? (params "url"))
        {:status 202 :body "A check for that site already exists"}
      (not (.startsWith (params "url") "http"))
        {:status 400 :body "Url must start with http"}  
      (and (contains? params "interval") (> 30 (nbr (params "interval"))))         
        {:status 400 :body "Interval should be at least 30 (seconds)"}
      (and (contains? params "timeout") (> 5 (nbr (params "timeout")) )) 
        {:status 400 :body "Timeout should be at least 5 (seconds)"}
      (and (contains? params "failures") (> 1 (nbr (params "failures")))) 
        {:status 400 :body "Failures should be set to be at least 1"}
      :else (create-check params)))




;;
;; Rest application: 
;;
(defroutes api-routes      
  (POST "/tasks" {form-params :form-params} (validate-and-create form-params))
  (DELETE "/tasks" {form-params :form-params} (remove-check-for (form-params "url")))
  (GET "/failing-checks.json" [] (str (generate-string (failing-checks))))
  (GET "/all-checks.json" [] (str (generate-string (all-checks))))
  (GET "/" [] (resp/redirect "/index.html"))  
  (GET "/quick" [] (quick-check))
  (route/resources "/")
  (route/not-found "<h1>Dave's not here man</h1>"))

(def user (System/getProperty "pingtown_api_key" (System/getenv "pingtown_api_key")))
(def password (System/getProperty "pingtown_api_secret" (System/getenv "pingtown_api_secret")))

(defn same? [s1 s2] 
  ;;(let [rnd (* (rand-int 200) (rand-int 2000))]
  ;;    (= (str s2 rnd) (str s1 rnd))))
  (= (hash-fn (str "XX" s1)) (hash-fn (str "XX" s2))))

(defn authenticate [api-key api-secret]
  (cond 
    (= nil user) {:username "anon"}
    (and (same? api-key user)
           (same? api-secret password))
          {:username api-key}
    :else nil ))

(defroutes main-routes      
  (wrap-require-auth api-routes authenticate
    "The Secret Area" {:body "You're not allowed in The Secret Area!"}))


(defn on-start [] 
  (start-db-sync))


;;following
;;https://github.com/adeel/ring-http-basic-auth

(def app     
  (do (on-start)  (handler/site main-routes)))


