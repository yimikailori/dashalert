(ns dashalert.config
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.data.codec.base64 :as base64])
  (:use [clojure.tools.logging])
  (:import (java.io File PushbackReader)))

;;; commense processing the config file

(defn- config-file
  []
  (let [
        ;result (System/getProperty "com.yim.app.config.file")
        result "J:\\config-alert.md"
        ]
    (if (and result (-> result File. .isFile))
      result
      (do (fatal (format "serverConfig(%s) = nil" result))
          (throw (Exception. (format "Server configuration file (%s) not found." result)))))))

(defn load-config [filename]
  (with-open [r (io/reader filename)]
    (read (PushbackReader. r))))

;; read config file contents

(def config-map (load-config (config-file)))

;;; path to the URL to charge or get balance
(def as-url (let [res (:as-url config-map)]
              (if (empty? res)
                (do (fatal (format "Charging Address (%s) = nil" res))
                    (throw (Exception. (format "Charging Address (%s) not found." res))))

                (str/trim res))))

;;Database Port
(def port (let [res (:port config-map)]
            (if (empty? res)
              (do (fatalf "Database Port (%s) = nil" res)
                  (throw (Exception. (format "Database Port (%s) not found." res))))

              (str/trim res))))

;;Host IP
(def host (let [res (:host config-map)]
            (if (empty? res)
              (do (fatalf "Database Host (%s) = nil" res)
                  (throw (Exception. (format "Database Host (%s) not found." res))))

              (str/trim res))))

;;database password
(def password (let [res (:password config-map)]
                (if (empty? res)
                  (do (fatalf "Database Password (%s) = nil" res)
                      (throw (Exception. (format "Database Password (%s) not found." res))))

                  (str/trim res))))

;;user
(def user (let [res (:user config-map)]
            (if (empty? res)
              (do (fatalf "Database User (%s) = nil" res)
                  (throw (Exception. (format "Database User (%s) not found." res))))

              (str/trim res))))


;; datebase name
(def dbname (let [res (:dbname config-map)]
              (if (empty? res)
                (do (fatalf "Database Name (%s) = nil" res)
                    (throw (Exception. (format "Database Name (%s) not found." res))))

                (str/trim res))))



;;; section for socket pool configuration
(def read-timeout (or (:read-timeout config-map) 5000))
(def connect-timeout (or (:connect-timeout config-map) 5000))

(def sweep-interval-delay (or (Integer. (:sweep-interval-delay config-map)) 3600))

;;; path to the URL to charge or get balance
(def sms-url (let [res (:sms-url config-map)]
               (if (empty? res)
                 (do (fatal (format "SMS Address (%s) = nil" res))
                     (throw (Exception. (format "SMS Address (%s) not found." res))))

                 (str/trim res))))

;;message header or product name
(def sms-header (let [res (:sms-header config-map)]
                  (if (empty? res)
                    (do (fatalf "SMS header Name (%s) = nil" res)
                        (throw (Exception. (format "SMS header Name (%s) not found." res))))

                    (str/trim res))))

(defn- byte-transform
  "Used to encode and decode strings.  Returns nil when an exception
  was raised."
  [direction-fn string]
  (try
    (apply str (map char (direction-fn (.getBytes string))))
    (catch Exception _)))

(defn- decode-base64
  "Will do a base64 decoding of a string and return a string."
  [^String string]
  (byte-transform base64/decode string))

(def auth (let [res (:charge-auth config-map)]
            (if (empty? res)
              (do (fatalf "Charge Basic authorization (%s) = nil" res)
                  (throw (Exception. (format "Charge Basic authorization (%s) not found." res))))

              (decode-base64 (str/trim res)))))


;;; section for XML requests
(def query-balance-xml (let [res (:query-balance-xml config-map)]
                         (if (and res (-> ^String res File. .isFile))
                           (slurp res)
                           (do (fatal (format "noQueryBalanceXML(%s) = nil" res))
                               (throw (Exception. (format "XML query balance file response (%s) not found." res)))))))
(def adjust-balance-xml (let [res (:adjust-balance-xml config-map)]
                          (if (and res (-> ^String res File. .isFile))
                            (slurp res)
                            (do (fatal (format "noAdjustBalanceXML(%s) = nil" res))
                                (throw (Exception. (format "XML AjustBalance file response (%s) not found." res)))))))




(defn
  biggerint [str]
  (let [n (read-string str)]
    (if (integer? n) n)))


(defn submsisdn [sub]
  (if (= (count sub) 13)
    (biggerint (subs sub 3))
    (do
      (if (= (count sub) 11)
        (biggerint (subs sub 1))
        (do
          (if (= (count sub) 10)
            (biggerint sub)))))))





