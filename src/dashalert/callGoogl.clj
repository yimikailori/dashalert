(ns dashalert.callGoogl
  (:use [clojure.tools.logging])
  (:require [org.httpkit.client :as http])
  (:import (java.net ConnectException SocketTimeoutException)
           (org.json.simple.parser JSONParser)))




(defn getshortner [read_timeout connect_timeout address]
  (let [url "https://www.googleapis.com/urlshortener/v1/url?key=AIzaSyBjPocYJdNviesUSrmtvue8y7rxQ55203U"
        options  {:socket-timeout (Integer. read_timeout)
                  :conn-timeout (Integer. read_timeout)
                  :timeout (Integer. connect_timeout) ; ms
                  :body (str "{\"longUrl\":\""address "\"}")
                  ;:body "{\"json\": \"input\"}"
                  :query {:longUrl address}
                  :accept :json
                  :content-type :json
                  :headers {"Content-Type" "application/json"}}
        {:keys [status body error] :as trace}  @(http/post url options)]
    (try
      (if error
        (do
          (let [error-msg (condp instance? error
                            ConnectException ":timeout-on-connect"
                            SocketTimeoutException ":timeout-on-read")]
            (errorf "Connection exception [%s|%s]" error-msg trace)
            (throw (Exception. "Error: "trace))))

        (if (not-empty body)
          (let [parser (JSONParser.)
                jsonObj (.parse parser body)
                id (.get jsonObj "id" )]
            (infof "Parsed message [%s]" body)
            id)
          (do
            (errorf "Error: Empty body [%s]" trace)
            (throw (Exception. "Error: Empty body")))))
      (catch Exception e
        (errorf "Exception error [%s]" (.getMessage e))
        (throw (Exception. (.getMessage e) ))))))