(defproject dashalert "0.1.2-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :omit-source true
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojars.scsibug/feedparser-clj "0.4.0"]
                 ;[feedparser-clj/feedparser-clj "0.2"]
                 [org.jsoup/jsoup "1.7.2"]
                 [http-kit "2.2.0"]
                 [compojure "1.1.5"]
                 [ring-cors "0.1.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [medley/medley "0.8.4"]
                 [log4j/log4j "1.2.16" :exclusions [javax.mail/mail
                                                     javax.jms/jms
                                                     com.sun.jdmk/jmxtools
                                                     com.sun.jmx/jmxri]]
                 [korma "0.4.3"]
                 [clj-time "0.11.0"]
                 [org.postgresql/postgresql "9.2-1002-jdbc4"]
                 [com.mchange/mchange-commons-java "0.2.12"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.clojure/data.json "0.2.6"]
                 [com.googlecode.json-simple/json-simple "1.1"]

                 [ring/ring-json "0.2.0"]
                 [ring/ring-devel "1.1.8"]]


  ;[ring/ring-json "0.2.0"]
  ;[http-kit "2.2.0"]
  ;[ring/ring-devel "1.1.8"]
  ;[compojure "1.1.5"]
  ;[ring-cors "0.1.0"]
  ;[com.novemberain/langohr "3.5.0"]


  :profiles  {:uberjar
              {:aot :all}}

  :jvm-opts ["-Dcom.yim.app.config.file=J:/config-alert.md"]

  :main dashalert.core)
