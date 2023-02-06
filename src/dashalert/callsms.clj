(ns dashalert.callsms
  (:use [clojure.xml :only [parse]])
  (:use [clojure.tools.logging])
  (:require [clojure.string :as str]
            [dashalert.dbconf :as db]
            [dashalert.config :as conf]
            [org.httpkit.client :as http :exclude get])
  (:import (java.io ByteArrayInputStream)
           (java.net ConnectException SocketTimeoutException)))



(defn callsms
  "Calls SMS URL and return failed or success with corresponding
  status code"
  [msisdn msg]
  (info "callSMS | Debit Request |" msg)
  (let [options  {:socket-timeout (Integer. conf/read-timeout )
                  :conn-timeout (Integer. conf/connect-timeout)
                  :timeout (Integer. conf/connect-timeout)             ; ms
                  :query-params {:to msisdn :text msg :header conf/sms-header}}
        {:keys [status  body error] :as resp}  @(http/get conf/sms-url options)]
    (if error
      (do
        (errorf "!sendSMS failed [%s|%s]" error resp)
          "sms-failed")
      (do
        (infof "sendSMS success [%s|%s|%s]" status msisdn body)
        "sms-success" ))))


(defn callmention
  "Calls SMS URL and return failed or success with corresponding
  status code"
  []
  (println "callSMS | Debit Request")
  (let [options {:timeout 5000
                 :oauth-token ; ms
                 :header  {"Content-Type" "application/json"
                           "Accept-Language" "en"
                           "Accept-Version" "1.8"
                           "Authorization" "Bearer YTBmYTQ4MWU2NDAyOWY1NmYzMTVmZDQzMjI3MmZmMTUzYTQwNjA5N2I4MjgyZWNkZjBjYWYxYjYxNjk3OGM5OA"}}
        {:keys [status  body error] :as resp}  @(http/get "https://api.mention.net/api/accounts/772921_qxho63ya1zk8sk0840sg8cwok84gkogoskw808k4ssosks844/alerts/1445760" options)]
    (if error
      (do
        (println "!sendSMS failed " resp)
        "sms-failed")
      (do
        (println "sendSMS success " resp)
        "sms-success" ))))


(callmention)