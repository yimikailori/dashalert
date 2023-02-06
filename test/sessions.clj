;; (require 'com.nts.appserver.sessions :reload)
;; (in-ns 'com.nts.appserver.sessions)

(ns dashalert.sessions
  (:require [dashalert.dbconf :as db])
  (:use [clojure.tools.logging]))

;;; --------------------------------------------------------------------

(declare session-exists? get-session-data state->int state<-int)

(def *session-data-field-map*
  [[:request-id   "request_id"]
   [:subscriber   "subscriber_fk"]
   [:state        "session_state"]
   [:max-loanable "max_loanable"]
   [:new-pin      "new_pin"]
   [:pin-retries  "pin_retries"]])

;; ---

(defn start-new-session
  ([session-data reuse-sessions?]
     "Start a new session. Return the result of the session start
operation."
     (db/with-transaction []
       (let [%insert
             (fn [initial-state]
               (let [{:keys [session-id request-id subscriber max-loanable pin-retries]} session-data]
                 ;; Ensure that this is actually a new session.
                 (when (db/session-exists? session-id)
                   (throw (RuntimeException. (format "dupSessionID(%s,msisdn=%s)" session-id subscriber))))
                 ;; ---
                 (let [rows-affected
                       (db/execute-sql (str "insert into tbl_sessions"
                                            " (session_id, request_id, subscriber_fk, session_state, max_loanable, pin_retries)"
                                            " values (?, ?, ?, ?, ?, ?)")
                                       session-id request-id subscriber (state->int initial-state) max-loanable pin-retries)]
                   (if (= rows-affected 1)
                     true
                     (throwf RuntimeException "unexpectedInsertRC(%s)" rows-affected)))))]
         (if reuse-sessions?
           ;; Yes, we can reuse sessions. Do we have an unexpired
           ;; session for this subscriber?  If yes, borrow its state
           ;; and use it as the new session entry's state. Otherwise,
           ;; use the state passed at invocation.
           (let [[prev-session prev-state]
                 (db/get-tuple :vector
                               (str "select session_id, session_state from tbl_sessions"
                                    " where subscriber_fk = ?"
                                    " and time_expiry > now()")
                               (long (session-data :subscriber)))]
             (if prev-session
               (%insert prev-state)
               (%insert (session-data :state))))
           ;; ---
           (%insert (session-data :state))))))
  ([session-data]
     (start-new-session session-data false)))

(defn end-session [session-id subscriber]
  (let [rows-affected
        (db/execute-sql (str "delete from tbl_sessions"
                             " where session_id = ? and subscriber_fk = ?")
                        (str session-id) (long subscriber))]
    (cond (= rows-affected 1) true
          (= rows-affected 0) (throwf RuntimeException "sessionNotFound(%s,sub=%s)" session-id subscriber)
          :else (throwf RuntimeException "internalError() -> tooManySessions(%s,sub=%s)" session-id subscriber))))

;; ---

(defn session-exists?
  "verifies if session exists"
  ([session-id]
     (and (db/get-column-value (str "select subscriber_fk"
                                    " from tbl_sessions where session_id = ?"
                                    " and time_expiry > now()")
                               (str session-id))
          true))
  ([session-id subscriber]
     (let [%subscriber (db/get-column-value (str "select subscriber_fk"
                                                 " from tbl_sessions where session_id = ?"
                                                 " and time_expiry > now()")
                                            (str session-id))]
       (when %subscriber
         (if (= subscriber %subscriber)
           true
           (throwf RuntimeException "sessionMismatch(%s,expected=%s,found=%s)"
                   session-id subscriber %subscriber))))))

(defn get-session-data [session-id subscriber]
  (let [[request-id %subscriber state max-loanable new-pin pin-retries]
        (db/get-tuple :vector
                      (str "select request_id, subscriber_fk, session_state, max_loanable, new_pin, pin_retries"
                           " from tbl_sessions where session_id = ?"
                           " and time_expiry > now()")
                      (str session-id))]
    ;; Ensure that the session actually exists.
    (when-not %subscriber
      (throwf RuntimeException "sessionNotFound(%s,sub=%s)" session-id subscriber))
    ;; Validate session data.
    (when-not (= subscriber %subscriber)
      (throwf RuntimeException "sessionMismatch(%s,expected=%s,found=%s)"
              session-id subscriber %subscriber))
    ;; ---
    {:session-id session-id :subscriber subscriber :request-id request-id
     :state (state<-int state) :max-loanable max-loanable
     :new-pin new-pin :pin-retries pin-retries}))

(defn set-session-data [session-id subscriber session-data]
  (when-not (empty? session-data)
    (let [fields (filter (fn [[k]] (session-data k)) *session-data-field-map*)
          values (into [] (map (fn [[k]]
                                 (let [v (session-data k)]
                                   (if (= k :state) (state->int v) v)))
                               fields))
          sql (str "update tbl_sessions set "
                   (str (apply str (interpose " = ?, " (map second fields))) " = ?")
                   " where session_id = ? and subscriber_fk = ?"
                   " and time_expiry > now()")]
      (let [rows-affected (apply db/execute-sql sql (conj values (str session-id) subscriber))]
        (cond (= rows-affected 1) true
              (= rows-affected 0) (throwf RuntimeException "sessionNotFound(%s,sub=%s)" session-id subscriber)
              :else (throwf RuntimeException "internalError() -> tooManySessions(%s,sub=%s)" session-id  subscriber))))))
       


;;; --------------------------------------------------------------------
;;;  Utilities.
;;; --------------------------------------------------------------------

(let [states     {:initial 0 :new-pin 1 :confirm-pin 2 :menu-borrow-now 3 :subscription-menu-L1 4 :subscription-menu-L2 5 :top-menu 6 :subscription-help 7 :borrow-now-help  8 :subscription-menu-L1-subscribed 9}
      keys       (reduce conj #{} (map (fn [[k v]] k) states))
      values     (reduce conj #{} (map (fn [[k v]] v) states))
      rev-states (reduce (fn [res [k v]] (assoc res v k)) {} states)]
  (defn state->int [state]
    (if (integer? state)
      (values state)
      (states state)))
  (defn state<-int [state]
    (when (values state)
      (rev-states state))))

;;; ####################################################################
;;;  eof
