(ns dashalert.getAlert
  (:use [clojure.tools.logging]
        [feedparser-clj.core]
        [dashalert.callGoogl])
  (:require [clj-time.format :as f]
            [clj-time.local :as l]
            [clj-time.core :as t]
            [clojure.string :as str])
  (:import (org.nnh.service GAService)
           (org.nnh.bean DeliveryTo)
           (java.text SimpleDateFormat)
           (java.util Date)
           (java.sql Timestamp)
           (org.jsoup Jsoup)))

(defn strip-html-tags
  "Function strips HTML tags from string."
  [s]
  (.text (Jsoup/parse s)))


(def multi-parser (f/formatter (t/default-time-zone) "yyyy-MM-dd HH:mm:ss" "EEE MMM DD HH:mm:ss z yyyy"))


(defn getAlertId [sid path msisdn]
  (try

    (let [parse (parse-feed path)
          update_time (apply str (map :updated-date (take 1 (:entries parse))))
          parsedDate (.parse (SimpleDateFormat. "EEE MMM DD HH:mm:ss zzz yyyy") update_time)
          outputText (.format (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss") parsedDate)
          current-time-minus-5 (l/format-local-time (t/minus (l/local-now) (t/minutes 5)) :mysql)]

      (info "Path to Alert:" path)
      (infof "Feed time|Compared time [outputText=%s,current-time-minus-5=%s]" outputText current-time-minus-5)
      ; (if (>= (compare outputText current-time-minus-5) 0)
      (if (>= (compare outputText outputText) 0)
        (let [link (apply str (map :link (take 1 (:entries parse))))
              reg-link (str/split link #"url=")
              url-link (get reg-link 1)
              link (get (str/split url-link #"&") 0)
              value (strip-html-tags (:value (into {} (:contents
                                                        (into {} (take 1 (:entries parse)))))))
              short-link (getshortner 5000 5000 link)]
          ;(info "Parse" (:entries parse))
          (infof "Recieved Alert [shortlink=%s,msg=%s]" (count (apply str short-link "\n" value)) (apply str short-link "\n" value))
          ;(sms/callsms msisdn (apply str short-link "\n"value))
          (str "true;" sid ";" msisdn ";" (apply str short-link "\n" value)))
        (do
          (info "Update_time in RSS feed is stale. no update")
          (str "false;" sid ";" msisdn ";Update_time in RSS feed is stale. no update"))))
    (catch Exception e
      (do
        (error "Cannot create feed alert: " (.getMessage e))
        (str "false;" sid ";" msisdn ";no current feed with that topic")))))


(defn getAlertByDelivery
  "Get alert by delivery."
  [service]
  (let [lstAlert (.getAlertByDelivery service (DeliveryTo/FEED))]
        (info "Alert by delivery:" lstAlert)))

(defn getAllAlerts
  "Get all alerts."
  [service]
  (let [lstAlert (.getAlerts service)]
        (info "All Alerts:" lstAlert)))


