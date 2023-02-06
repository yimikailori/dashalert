(ns dashalert.rpc
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [dashalert.config :refer :all]
            [org.httpkit.client :as http :exclude get]
            [dashalert.createAlert :as ca]
            [dashalert.getAlert :as getAlert]
            [dashalert.dbconf :as db]
            [dashalert.callapp :as charge]
            [dashalert.getBal :as getBal]
            [dashalert.callapp :as charge]
            [dashalert.callsms :as sms]
            [dashalert.deleteAlert :as del])
  (:use [clojure.tools.logging]
        [dashalert.config])
  (:import (org.nnh.service GAService)
           (java.text SimpleDateFormat)
           (java.util Date)
           (java.sql Timestamp)))


(defn make-response [action body]
  {:status 200
   :headers {"Content-Type" "text/plain"
             "CONT"  (cond (= action :terminate) "FB"
                           (= action :continue)  "FC"
                           :else "?")}
   :body body})




(declare process-charge)

(def google-service (atom nil))
(def google-login (atom nil))


(defn call-google []
  (do
    (let [serv (GAService. "ffitalert@gmail.com" "ffitalert007")
          login (.doLogin serv)]
      (reset! google-service serv)
      (reset! google-login login))))

(defn process-url-encoded
  "Receives incoming HTTP requests and
  returns the appropriate HTTP response"
  [req]
  (let [headers (:content-type req)
        input (or (:query-string req) (:input-string req))                           ;data comes in the input-string of request
        body (:body req)]
    (if (= nil headers)
      (do
        (let [input-vec (apply hash-map (str/split input #"[&=]"))]
          (walk/keywordize-keys input-vec))))))



(defn request-id
  "Makes a 19 digit session identifer"
  []
  (let [component-id "33"
        timestamp (System/currentTimeMillis)
        rand (format "%04d" (rand-int 9999))
        request-id (str component-id timestamp rand)]
    (biggerint request-id)))


(defn unsubscribe-req
  "Unsubscribes a sub"
  [req]
  (let [{sub    :sub
         sid    :sid} (process-url-encoded req)]
    (if-not (and sid sub)
      (do
          (error "Content is empty or incorrect [sub:%s|sid:%s]" sub sid)
        (make-response :terminate "Incorrect request" ))
      (let [msisdn (submsisdn sub)
            opt-out (db/opt-out sid msisdn)]
        (infof "OPT-OUT[%s]" opt-out)
        (make-response :terminate "You have opted out of the service")))))

#_(defn in?
  "true if coll contains val"
  [val]
  (some #{val} '("7" "15" "25")))

(defn check [s]
  (when (seq s)
    (if (str/blank? (first s))
      "yes"
      (do
        (recur (rest s))))))

(defn rpl [strg] (str/replace strg #"[\{\(\)\}]" ""))

(defn process-request
  "Receives all status check requests, processes them and provides appropriate
  response."
  [req]
  (when req
    (debugf "Status request received [%s]" req)
    (let [{msisdn  :sub
           sidd    :sid
           type    :type
           inputt  :input} (process-url-encoded req)]

      (if (= "yes" (check (list inputt msisdn sidd type)))
       (do
         (errorf "Content is empty or incorrect [msisdn=%s,sid=%s,type=%s,input=%s]" msisdn sidd type inputt)
         (make-response :terminate (str "Kindly follow the process below:\nff <space> <topic>\ndel <space> <topic>\nstatus <space> <topic>\nThanks.")))

       (try
         (infof "Verifying request -> [msisdn=%s,sid=%s,type=%s,input=%s]" msisdn sidd type inputt)

         ;; (some #{101} '(100 101 102))
         (let [msisdn (submsisdn msisdn)
               sid (biggerint sidd)
               input (str/replace inputt #"\+" " ") ]
           (cond (= type "ff") (let [check-profile (db/check-profile sid msisdn input)]
                                 (cond (= "sub-not-profiled" check-profile)
                                       (do
                                         (warnf "Subscriber is not profiled [%s]" msisdn)
                                         (make-response :terminate (str msisdn " - You are not eligible to create Topics")))

                                       (= "wasin" check-profile)
                                       (do
                                         (warn "Subscriber already has a plan|" msisdn)
                                         (make-response :terminate (str msisdn " - You are have exhausted your plan for the day. Thank you")))
                                       :else
                                       (if (true? @google-login)
                                         (do
                                           (info "Login is" @google-login)
                                           (let [alert-outcome (ca/create @google-service input sid msisdn)
                                                 res (str/split (str alert-outcome) #";")
                                                 id (last res)
                                                 alert_count (count res)
                                                 value (get res (- alert_count 2))]

                                             (infof "Alert outcome -> [%s]" alert-outcome)
                                             (if (str/starts-with? alert-outcome "true")

                                               ;;with a successful response
                                               (let [psa-id (request-id)
                                                     pro-charge (str "success:"7000)
                                                     ;(process-charge msisdn psa-id sid)
                                                     ;(str/split out #":")
                                                     out (str/split pro-charge #":")]

                                                 (if (str/starts-with? pro-charge "success")
                                                   (do
                                                     (future (sms/callsms msisdn value))
                                                     (infof "Successful alert created [msisdn=%s,input=%s,alert-id=%s,outcome=%s]" msisdn input id alert-outcome)
                                                     (make-response :terminate (str "Your Topic [" input "] is created")))

                                                   (do
                                                     (infof "Alert failed -> [%s]" (out 2))
                                                     (del/deleteAlert @google-service id)
                                                     (infof "Failed alert [msisdn=%s,input=%s,alert-id=%s,outcome=%s]" msisdn input id alert-outcome)
                                                     (make-response :terminate (str "Your Topic [" input "] cannot be created due to " (out 2))))))

                                               ;;failed, because outcome is false
                                               (when (and (db/opt-out sid msisdn) true)
                                                 (infof "Failed alert [msisdn=%s,input=%s,outcome=%s]" msisdn input alert-outcome)
                                                 (make-response :terminate (str "We failed while attempting to create your Topic [" input "] due to [" value "]. Please try again later"))))))
                                         (when (and (db/opt-out sid msisdn) true)
                                           (error "Login is" @google-login)
                                           (make-response :terminate "We could not process your request. Please try again later")))))
                 ;user chooses delete
                 (= type "del")
                 (let [output (db/proc_clean_sub_query msisdn input)]
                   (if (= output "out")
                     (do
                       (infof "Subscriber[%s] -> input [%s] is cleared" msisdn input)
                       (make-response :terminate (str msisdn " - Your Topic [" input "] has been removed. You can now create another Topic.\nThank you")))
                     (do
                       (infof "Subscriber[%s] -> input [%s] is not found" msisdn input)
                       (make-response :terminate (str msisdn " - Your Topic [" input "] cannot be found.\nTo check topics you have created, send [status] to the shortcode.\nThank you")))))

                 ;;user chooses status
                 (= type "status")
                 (let [sta (db/proc_check_sub_status msisdn)]
                   (if (empty? sta)
                     (do
                       (infof "No status for subscriber -> [%s|%s]" msisdn input)
                       (make-response :terminate (str msisdn " - You have no Topic. You can create 3 Topics by sending to the shortcode below commands\nff <space> <topic>.\nThank you")))

                     (let [stats (apply str sta)]
                       (infof "Present Topics -> %s" stats)
                       (let [b (str/split (apply str sta) #",")
                             val (count b)]
                         (cond (= val 3) (do
                                           (infof (str "Your Topic(s) include the following ->"(str "1." (b 0) " 2."  (b 1) " 3." (b 2))) )
                                           (make-response :terminate (str "Your Topic(s) include the following\n"(str "1." (b 0) "\n2." (b 1) "\n3." (b 2)))))
                               (= val 2) (do
                                           (infof (str "Your Topic(s) include the following ->"(str "1." (b 0) " 2." (b 1))))
                                           (make-response :terminate (str "Your Topic(s) include the following\n"(str "1." (b 0) "\n2." (b 1)))))
                               (= val 1) (do
                                           (infof (str "Your Topic(s) include the following ->"(str "1."(rpl (b 0)))))
                                           (make-response :terminate (str "Your Topic(s) include the following\n"(str "1."(b 0)))))
                               :else (do
                                       (info "We cannot get your stats at the moment!")
                                       (make-response :terminate "We cannot get your stats at the moment!")))
                         ))))
                 :else
                 (do
                   (infof "Improper type received -> [%s]" type)
                   (make-response :terminate (str "Kindly follow the process below:\nff <space> <topic>\ndel <space> <topic>\nstatus <space> <topic>\nThanks.")))))
         (catch Exception e
           (do
             (db/opt-out (biggerint sidd) (submsisdn msisdn))
             (error "Something bad just happened: " (.getMessage e) "|" e)
             (make-response :terminate "We observed an internal error"))))))))






(defn- process-charge
  [msisdn sid subscription_fk]
  (let [getstart (System/currentTimeMillis)
        get-bal-time (quot (System/currentTimeMillis) 1000)
        get-bal-req (str/trim (format query-balance-xml sid sms-header get-bal-time msisdn))
        get-call-bal-resp (getBal/call-getbal-app as-url get-bal-req connect-timeout sid msisdn)]
    ;;return the response
    (infof "callProf|getBalance|%s|%s" (- (System/currentTimeMillis) getstart) get-call-bal-resp)

    (cond (str/starts-with? (str get-call-bal-resp) "approved")
          (let [;;insert into tbl_log_recovery_new
                ;; convert amount to ratecode
                ma_bal (str/split (str get-call-bal-resp) #":")
                type (ma_bal 1)
                amount (Integer. (ma_bal 2))
                denom (db/get-denoms amount)]

            (infof "GetDenoms [msisdn=%s,amount=%s,denom=%s,type=%s]" msisdn amount denom type)
            (let [psa-sid (request-id)
                  current-time (Timestamp/valueOf (.format (SimpleDateFormat. "yyyy-MM-dd hh:mm:ss.ms") (Date.)))
                  ;proc (db/insert-requests psa-sid msisdn amount input)
                  proc (db/insert-requests psa-sid current-time msisdn amount subscription_fk)

                  debit-time (quot (System/currentTimeMillis) 1000)
                  debit-bal-request (str/trim (format adjust-balance-xml psa-sid sms-header denom debit-time msisdn))
                  resp (charge/callapp as-url psa-sid debit-bal-request amount connect-timeout msisdn)
                  line (str/split resp #":")]
              (if (= (line 1) "failed")
                (do
                  (infof "No Successful debit [msisdn=%s,id=%s]" msisdn psa-sid)
                  (str "failed:"amount":no successful debit"))
                (do
                  (infof "Successful debit [msisdn=%s,id=%s,amount=%s]" msisdn psa-sid amount)
                  (str "success:"amount)))))
          (str/starts-with? (str get-call-bal-resp) "lowbalance") (do
                                                     (error "Low balance" get-call-bal-resp)
                                                     (str "lowbalance:"msisdn ":low balance"))

          (str/starts-with? (str get-call-bal-resp) "hybrid") (do
                                                                    (error "hybrid" get-call-bal-resp)
                                                                    (str "hybrid:"msisdn ":msisdn is hybrid/postpaid"))
          (str/starts-with? (str get-call-bal-resp) "duplicateSessionId") (do
                                                       (error "duplicateSessionId" get-call-bal-resp)
                                                       (str "duplicateSessionId" msisdn ":duplicate session identifier"))
          :else (do
                  (error "Error getting balance" get-call-bal-resp)
                  (str "failed:"msisdn ":other error getting balance")))))