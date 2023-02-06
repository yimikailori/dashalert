(ns dashalert.getBal
  (:use [clojure.xml :only [parse]])
  (:use [clojure.tools.logging])
  (:require [clojure.string :as str]
            [dashalert.dbconf :as db]
            [org.httpkit.client :as http :exclude get]
            [dashalert.config :as conf])
  (:import (java.io ByteArrayInputStream)
           (java.net ConnectException SocketTimeoutException)))


(defn call-getbal-app [fullUrl request as_connect_timeout sid msisdn]
  (info (str "getBal | Request |"msisdn "|" fullUrl "|" (str/trim (str/join "\\n" (str/split-lines request)))))
  (let [ options  {:timeout    (Integer. as_connect_timeout)
                   :basic-auth conf/auth
                   :body       request
                   :headers    {"Content-Type" "application/xml"}}
        {:keys [status  body error] :as resp}  @(http/post fullUrl options)]
    (if error
      (do
        (errorf "Failed, exception |%s" error )
        "failed")
      (do
        (infof "Http Status [%s|%s|%s|%s]" status sid msisdn (str/trim (str/join "\n" (str/split-lines body))))
        (let [body-parser (parse
                            (ByteArrayInputStream. (.getBytes (str/trim body))))
              {soap-header :tag} body-parser
              {[{[{txnID :content}  {retry :content}
                  {resptag :tag responsecode :content}] :content}] :content} body-parser]

          (cond (= (str soap-header) ":ucap:ChargeResponse")

                ;;get balance xml format

                (cond
                  ;; prepaid msisdn normal code is Zero
                  (= (responsecode 0) "0") (let [{[{[{txnID :content}] :content}] :content} body-parser
                                                 {[{[{c1 :content} {t1 :tag, c2 :content} {t2 :tag, c3 :content}] :content}
                                                   {[{c2-1 :content} {t1t :tag, c2-2 :content} {t2t :tag, c2-3 :content}
                                                     {t3t :tag, c3-3 :content}] :content}] :content} body-parser]
                                             (do
                                               ;;confirm if the tag is at the position is correct
                                               (if (= (str t3t) ":ucap:AmountValue")

                                                 ;;confirm if msisdn's balance is greater than
                                                 ;; or equals to the actual amount to be debited
                                                 (let [val (db/get-bal-in-rate-code (Integer. (c3-3 0)))]
                                                   (if (nil? val)
                                                     ;;if msisdn's balance is nil compared to available denoms
                                                     ;;then throw low balance
                                                     (when (and (db/opt-out sid msisdn) true)
                                                       (infof "Low Balance [MABal=%s,id=%s,msisdn=%s]"(c3-3 0) sid msisdn)
                                                       "lowbalance")

                                                     (do    ;when (and (db/update-msisdn-subscription sid msisdn val) true)
                                                       (infof "MA Balance [MABal=%s,deductable=%s,id=%s,msisdn=%s]"(c3-3 0) val sid msisdn)
                                                       ;(str "approved:prepaid:"val)
                                                       ;just for testing, dont forget to remove this
                                                       (str "approved:prepaid:"val))))

                                                 (when (and (db/opt-out sid msisdn) true)
                                                   (error "Incorrect XML tag |" t3t "|" sid "|" msisdn)
                                                   "failed"))))
                  ;; postpaid msisdn response code is 99

                  (= (responsecode 0) "99") (when (and (db/opt-out sid msisdn) true)
                                              (infof "getBal|HYBRID [responseCode=%s,sid=%s,msisdn=%s]" (responsecode 0)  sid msisdn)
                                              "hybrid")
                  (= (responsecode 0) "24") (when (and (db/opt-out sid msisdn) true)
                                              (errorf "!getBal[DuplicateSessionID=%s,responseCode=%s]" sid (responsecode 0) )
                                              "duplicateSessionId")
                  :else (when (and (db/opt-out sid msisdn) true)
                          (infof "getBal|Unknown responsecode=%s,sid=%s,msisdn=%s"(responsecode 0) sid msisdn)
                          "failed"))

                (= (str soap-header) ":ErrorType") (when (and (db/opt-out sid msisdn) true)
                                                     (let [{[{stat :content}
                                                             {desc :content}]:content}body-parser]
                                                       (errorf "!getBal|ErrorCode:%s|Desc:%s]" (stat 0) (desc 0))
                                                       "failed"))


                :else (when (and (db/opt-out sid msisdn) true)
                        (error "!getBal|Incorrect XML Header [%s|%s|%s|%s]" soap-header sid msisdn)
                        "failed")))))))




#_(defn !call-getbal-app [fullUrl request as_connect_timeout sid msisdn]
  (info (str "getBalPSA | Request |"msisdn "|" fullUrl "|" (str/trim (str/join "\\n" (str/split-lines request)))))
  (let [ options  {:timeout    (Integer. as_connect_timeout)
                   :basic-auth conf/auth
                   :body       request
                   :headers    {"Content-Type" "application/xml"}}
        {:keys [status  body error] :as resp}  @(http/post fullUrl options)]
    (if error
      (when (and (db/opt-out sid msisdn) true)
        (errorf "Failed, exception |%s" error )
        "failed")
      (do
        (infof "Http Status [%s|%s|%s|%s]" status sid msisdn (str/trim (str/join "\n" (str/split-lines body))))
        (let [body-parser (parse
                            (ByteArrayInputStream. (.getBytes (str/trim body))))
              {soap-header :tag} body-parser
              {[{[{txnID :content}  {retry :content}
                  {resptag :tag responsecode :content}] :content}] :content} body-parser]

          (cond (= (str soap-header) ":ucap:ChargeResponse")

                ;;get balance xml format

                (cond
                  ;; prepaid msisdn normal code is Zero
                  (= (responsecode 0) "0") (let [{[{[{txnID :content}] :content}] :content} body-parser
                                                 {[{[{c1 :content} {t1 :tag, c2 :content} {t2 :tag, c3 :content}] :content}
                                                   {[{c2-1 :content} {t1t :tag, c2-2 :content} {t2t :tag, c2-3 :content}
                                                     {t3t :tag, c3-3 :content}] :content}] :content} body-parser]
                                             (do
                                               ;;confirm if the tag is at the position is correct
                                               (if (= (str t3t) ":ucap:AmountValue")

                                                 ;;confirm if msisdn's balance is greater than
                                                 ;; or equals to the actual amount to be debited
                                                 (if (>= (Integer. (c3-3 0)) (Integer. amt))
                                                   (do
                                                     (infof "MA Balance [%s|%s|%s|%s]"(c3-3 0) amt sid msisdn)
                                                     (str "approved:prepaid:"amt))


                                                   ;;if msisdn's balance is lower than actual amount requested
                                                   ;;then throw low balance
                                                   (when (and (db/opt-out sid msisdn) true)
                                                     (info "Low Balance"(c3-3 0) "| amount requested |" amt "|" sid "|" msisdn)
                                                     "lowbalance"))

                                                 (when (and (db/opt-out sid msisdn) true)
                                                   (error "Incorrect XML tag |" t3t "|" sid "|" msisdn)
                                                   "failed"))))
                  ;; postpaid msisdn response code is 99

                  (= (responsecode 0) "99") (when (and (db/opt-out sid msisdn) true)
                                              (infof "getBal|HYBRID [amt=%s,sid=%s,msisdn=%s]" amt sid msisdn)

                                              (str "approved:hybrid:"amt))
                  (= (responsecode 0) "24") (when (and (db/opt-out sid msisdn) true)

                                              (errorf "!getBal|Duplicate session ID[%s]" sid)
                                              "duplicateSessionId")
                  :else (when (and (db/opt-out sid msisdn) true)
                          (errorf "getBal|Unknown responsecode=%s,amt=%s,sid=%s,msisdn=%s"(responsecode 0) amt sid msisdn)
                          "failed"))

                (= (str soap-header) ":ErrorType") (when (and (db/opt-out sid msisdn) true)
                                                     (let [{[{stat :content}
                                                             {desc :content}]:content}body-parser]
                                                       (errorf "!getBal|ErrorCode:%s|Desc:%s]" (stat 0) (desc 0))
                                                       "failed"))


                :else (when (and (db/opt-out sid msisdn) true)
                        (error "!getBal|Incorrect XML Header [%s|%s|%s|%s]" soap-header sid msisdn)
                        "failed")))))))





