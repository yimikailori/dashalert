(ns dashalert.core
  (:gen-class :initialize true)
  (:use [clojure.tools.logging]
        [org.httpkit.server]
        [compojure.core]
        [dashalert.rpc :as rpc]
        [dashalert.config]
    ;[dashalert.sessions :as session]
        [dashalert.dbconf :as db])
  (:import (org.nnh.service GAService)
           (java.util.concurrent Executors TimeUnit)))



(defn- start-sweeper
  "Sweep initiates subscribers who have opted in for the service"
  []
  (infof "startSweeper(interval-delay=%s)" sweep-interval-delay)

  (let [service (GAService. "ymkmayowa@gmail.com" "L0v3ayinke007")
        login (.doLogin service)
        timer (Executors/newScheduledThreadPool 1)] ; Do not run as a daemon thread.
    (do
        ;(swap! *timer-list* conj timer)
        (.scheduleWithFixedDelay timer
                             (proxy [Runnable] []
                               (run []

                                 (if (true? login)

                                   (do

                                     (info "Running Sweep....")
                                   ;;(sweep/run-sweep-alerts service)
                                   ;;(db/run-sweep-alerts service)
                                   ;(recovery/run-sweep-recovery @*sweeper-thread-pool* batch-size)
                                   )
                                   (do
                                     (infof "startSweeperFailed:"login))
                                   )))
                             (long 1000)
                             (long (* sweep-interval-delay 1000))
                             TimeUnit/MILLISECONDS))

    ))


(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@server :timeout 100)
    (reset! server nil)))

(defroutes all-routes
           (GET "/alert/subscribe" [req] rpc/process-request)
           (GET "/alert/unsubscribe" [req] rpc/unsubscribe-req)
           (GET "/test" [req] {:status   200
                               :headers {"Content-Type" "text/plain"}
                               :body    "Author:yilori\n"})
           (ANY "*" [req] {:status   404
                           :headers {"Content-Type" "text/plain"}
                           :body    "Page not Found, contact @yilori\n"}))

(defn -main [& args]
  ;; The #' is useful when you want to hot-reload code
  ;; You may want to take a look: https://github.com/clojure/tools.namespace
  ;; and http://http-kit.org/migration.html#reload


  (info "Service Starting...")
  (db/call-get-denoms "start")

  (dosync
    (let [port 8082]
      (reset! server (run-server all-routes {:port port}))
      (infof "Server now accepting requests on port [%d]" port)
      (rpc/call-google)
      (Thread/sleep 2000)
      ;; (start-sweeper)
      ))

  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (fn [] (info "APPServer shutting down...")
                               (stop-server)))))

;;(info "We are starting...")
;(-start "create" "" "Donald Trump")


;;(-start "" "get" "8c71b7583486ea74:c5f4725cd9525a6c:com:en:US:R")


;(-main)