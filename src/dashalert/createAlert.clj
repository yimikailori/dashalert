(ns dashalert.createAlert
  (:use [feedparser-clj.core]
        [clojure.tools.logging]
        [dashalert.dbconf :as db]
        [dashalert.getAlert :as getal])
  (:import (org.nnh.service GAService)
           (org.nnh.bean Alert HowMany HowOften DeliveryTo Region)))


(defn create [service query sid sub]
  (let [alert (Alert.)
        hm (.setHowMany alert (HowMany/ALL_RESULTS))
        ho (.setHowOften alert (HowOften/AS_IT_HAPPENS))
        rg (.setRegion alert (Region/United_States))
        sq (.setSearchQuery alert query)
        d (.setDeliveryTo alert (DeliveryTo/FEED))
        id (.createAlert service alert)
        alert (.getAlertById service id)
        path (.getDeliveryTo alert)]

    (if (empty? path)
      (do
        (errorf "Alert path is empty -> [%s|%s]"sub query)
        (str "false;"sub ";"query ))
      (do
      (infof "Alert created [path=%s,sub=%s,query=%s]"path sub query)
       (let [call-alert (getal/getAlertId sid path sub)]
         (when (and (db/update_alert_id path sid sub) true)
           (infof "Alert info -> [%s]" (str call-alert ":"path))
           (str  call-alert ";"path)))))))


#_ (defn alert [query]
    (let [service (GAService. "ffitalert@gmail.com" "ffitalert007")
          login (.doLogin service)
          alert (Alert.)
          hm (.setHowMany alert (HowMany/ALL_RESULTS))
          ho (.setHowOften alert (HowOften/AS_IT_HAPPENS))
          rg (.setRegion alert (Region/Nigeria))
          sq (.setSearchQuery alert query)
          d (.setDeliveryTo alert (DeliveryTo/FEED))
          id (.createAlert service alert)
          alert (.getAlertById service id)
          path (.getDeliveryTo alert)]
      (println path, id)))