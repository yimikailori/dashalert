(ns dashalert.callapp
  (:use [clojure.xml :only [parse]])
  (:use [clojure.tools.logging])
  (:require [clojure.string :as str]
            [dashalert.dbconf :as db]
            [dashalert.config :as conf]
            [org.httpkit.client :as http :exclude get])
  (:import (java.io ByteArrayInputStream)
           (java.net ConnectException SocketTimeoutException)))



(defn callapp
  "Calls PSA and return failed or success with corresponding
  status code"
  [fullUrl psa-sid request amt as_connect_timeout msisdn]
  (info "callPSA | Debit Request |" fullUrl "|" (str/trim (str/join "\n" (str/split-lines request))))
  (let [debitstart (System/currentTimeMillis)
        options  {:timeout (Integer. as_connect_timeout)
                  :basic-auth conf/auth
                  :body request
                  :headers {"Content-Type" "application/xml"}}
        {:keys [status  body error] :as resp}  @(http/post fullUrl options)]
    (if error
      (do
        (errorf "Failed, exception %s" error)
        "failed")
      (do
        (infof "Http Status %s|%s|%s" status msisdn (str/trim (str/join "\n" (str/split-lines body))))
        (let [body-parser (parse
                            (ByteArrayInputStream. (.getBytes (str/trim body))))
              {soap-header :tag} body-parser]
          (debug "Parsed response" body-parser)
          (debug "Response header" soap-header)

          (cond (= (str soap-header) ":ucap:ChargeResponse")
                (let [{[{[{txnID :content} {retry :content} {responseCode :content}] :content}] :content} body-parser
                  debit-status (Integer. (responseCode 0))
                  txnID (conf/biggerint (txnID 0))]
                  (do
                    (infof "callProf|debitMA|%s|[%s %s %s]" (- (System/currentTimeMillis) debitstart) txnID msisdn fullUrl)
                    (if (= (str debit-status) "0")
                    ;; insert into DB here and run proc
                    (when (and (db/update-charge-requests txnID amt (Integer. debit-status)) true)
                      (infof "Success [%s|%s|%s|%s]" txnID debit-status msisdn amt)
                      (str "200:success"))
                    (when (and (db/update-charge-requests txnID amt debit-status) true)
                      (infof "Failed [%s|%s|%s|%s]" txnID debit-status msisdn amt)
                      (str "200:failed")))))

                (= (str soap-header) ":ErrorType") (let [update-rec (db/update-charge-requests psa-sid amt 99)
                                                         {[{ErrorCode :content} {errorDesc :content}] :content}body-parser]
                                                     (errorf "!callapp|ErrorCode:%s|Desc %s" (ErrorCode 0) (errorDesc 0))
                                                     (str "200:failed:"))
                :else (when (and (db/update-charge-requests psa-sid amt 99) true)
                        (errorf "!callapp|Soap-header not correct | %s" soap-header)
                        (str "200:failed:"))))))))