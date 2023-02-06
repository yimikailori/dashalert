(ns dashalert.dbconf
  (:use [korma.db]
        [korma.core :exclude [update]]
        [dashalert.config :refer [dbname user host password port biggerint]]
        [clojure.tools.logging])
  (:require [dashalert.getAlert :as getAlert])
  (:import (java.util Properties)))

(System/setProperties
  (doto (Properties. (System/getProperties))
    (.put "com.mchange.v2.log.MLog" "com.mchange.v2.log.FallbackMLog")
    (.put "com.mchange.v2.log.FallbackMLog.DEFAULT_CUTOFF_LEVEL" "OFF")))


(def call-denoms (atom nil))

(defdb db (postgres {:db         dbname
                     :user       user
                     :password   password
                     :host       host
                     :port       port
                     :delimiters ""}))

(defentity tbl_subscriptions_new
           (table :tbl_subscriptions_new))

(defentity tbl_charge_denoms
           (table :tbl_charge_denoms))


(defentity tbl_charge_requests_new
           (table :tbl_charge_requests_new))



(defn check-profile
  "Proc: proc_optin confirms if a sub is in
  and inserts if not in"
  [sid msisdn input]
  (let [query (format "select proc_check_prof (%d,%d,'%s');" sid msisdn input)]
    ;proc_optin (%d,%d,%d,'%s');"sid msisdn duration input
    (info query)
    (try
      (let [execute (exec-raw [query]:results)
            val (:proc_check_prof (into {} execute))]
        (infof "Check-profile[%s]" val)
        val)
      (catch Exception e
        (throw (Exception. (format "!check-profile [%s]." (.getMessage e))))))))


(defn insert-requests
  "Inserts into tbl_charge_requests_new, returns done when completed
 or returns failed"
  [id time sub amount_requested subscription_fk]
  (debug "Inserting |" id time sub amount_requested subscription_fk)
  (try
    (let [insert (insert tbl_charge_requests_new
                                (values {:request_id       id
                                                :event_time       time
                                                :subscriber_no    sub
                                                :charge_status    -1
                                                :kobo_charged    amount_requested
                                                :error_message    ""
                                                :flag_done        (boolean false)
                                                :flag_Reconciled  (boolean false)
                                                :subscription_fk subscription_fk}))]
      (debug "Inserted |" insert)
      "done")
    (catch Exception e
      (throw (Exception. (format "!inserting fails -> [%s]." (.getMessage e)))))))

(defn proc_clean_sub_query
  [msisdn input]
  (let [query (format "select proc_clean_sub_query (%d,'%s');" msisdn input)]
    (try
      (let [execute (exec-raw [query]:results)
            val (:proc_clean_sub_query (into {} execute))]
        (infof "Clean subscriber query [%s]" val)
        val)
      (catch Exception e
        (throw (Exception. (format "!clean subscriber query [%s]." (.getMessage e))))))))

(defn proc_check_sub_status
  [msisdn]
  (let [query (format "select proc_check_sub_status (%d);" msisdn)]
    (try
      (let [execute (exec-raw [query]:results)
            val (:proc_check_sub_status (into {} execute))]
        (infof "Sub status -> [%s]" val)
        val)
      (catch Exception e
        (throw (Exception. (format "!Sub status -> [%s]." (.getMessage e))))))))

(defn opt-out
  "opt out of the service"
  [sid msisdn]
  (let [query (format "select proc_optout (%d,%d)" sid msisdn)]
    (try
      (when (and (exec-raw [query]:results) true)
        (infof "ProcOptOut [%s|%s]" sid msisdn)
        true)
      (catch Exception e
        (do
          (error "!ProcOptOut" (.getMessage e))
          (throw (Exception. (format "Error opting out [%s]." (.getMessage e)))))))))

(defn call-get-denoms
  "get denoms"
  [& args]
  (try
    (let [call-denoms (atom nil)
          get-d (select tbl_charge_denoms
                        (order :kobo_value :desc))]
      (doseq [v get-d]
        (swap! call-denoms conj v))
      (def denoms @call-denoms)
      (info "Initializing Denoms "denoms))
    (catch Exception e
      (do
        (error (.getMessage e))
        (throw (Exception. (format "Error Initializing Database Denoms [%s]." (.getMessage e))))))))


(def get-bal-in-rate-code
  "Gets corresponding value deductable by PSA"
  (fn [val]
    (let [den (reduce conj () (map :kobo_value denoms))]
      (first (filter #(>= val %) den)))))


(defn get-denoms [code]
  (doseq [n denoms]
    (if (= (n :kobo_value) code)
      (def denom (n :rate_code))))denom)


(defn update-charge-requests
  "Proc: update-charge-requests takes
  recovery_id, amount and charge status"
  [id amt charge-stat]
  (let [query (format "select proc_update_charge_requests (%d,%d,%d)" id amt charge-stat)]
    (try
      (when (and (exec-raw [query]:results) true)
        (infof "ProcUpdateChargeRequest [%s|%s|%s]" amt id charge-stat)
        (str "Recovered amount |" amt "| Recovery ID |" id "| Charge Status |" charge-stat))
      (catch Exception e
        (throw (Exception. (format "!ProcUpdateChargeRequest [%s]." (.getMessage e))))))))




(defn update_alert_id
  "update_alert_id it
  update tbl_subscriptions_new alert id column"
  [id sid sub]
  (info "details"sub sid id)
  (let [query (str "update tbl_subscriptions_new set alert_id='"id"' where subscription_id="sid" and subscriber_fk="sub)]
    (info "format-"query)
    (try
      (when (and (exec-raw [query]) true)
        (infof "UpdateAlertId [%s|%s|%s]" sid sub id)
        true)
      (catch Exception e
        (throw (Exception. (format "!UpdateAlertId [%s]." (.getMessage e))))))))

#_(defn update_create_alert
  [sid sub query]
  (let [query (format "select proc_update_get_query_id (%d,%d,'%s')"sid sub query)]
    (try
      (let [execute (exec-raw [query]:results)
            val (:proc_update_get_query_id (into {} execute))]
        (infof "update_create_alert [%s]" val)
        val)
      (catch Exception e
        (throw (Exception. (format "!update_create_alert [%s]." (.getMessage e))))))))