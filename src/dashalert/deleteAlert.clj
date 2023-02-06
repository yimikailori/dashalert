(ns dashalert.deleteAlert
  (:use [clojure.tools.logging])
  (:import (org.nnh.service GAService)
           (java.util ArrayList)))

(defn deleteAlert
  "Delete an alert"
  [service id]
  (let [alert (.deleteAlert service id)]
    (infof "Alert deleted [%s|%s]"id alert)
    alert))

(defn deleteAllAlert
  "Deletes all alerts"
  [service id]
  (let [lstAlertId (ArrayList.)
        arr (.add lstAlertId id)
        alert (.deleteAlert service id)]
    (infof "Alerts deleted [%s|%s]"id alert)
    alert))
