(ns android.market.download
  (:import 
    java.io.File
    [java.net URL URLEncoder]
    com.gc.android.market.api.MarketSession)
  (:require 
    [android.market.category :as cat])
  (:use 
    android-manifest.serialization
    [android-manifest.util :only (ignore-exceptions sleep-random date-string)]
    [android.market.leech :as ml]
    [clojure.contrib.io :only (file make-parents)]
    [clojure.contrib.duck-streams :only (copy)]
    [clojure.contrib.java-utils :only (read-properties)]
    ))


(defn url-encode
  "Wrapper around java.net.URLEncoder returning a (UTF-8) URL encoded
   representation of text."
  [text]
  (URLEncoder/encode text "UTF-8"))


(defn download-app [assetid authtoken userid deviceid filename]
  "Download app from the official google market."
  (let [cookie   (str "ANDROID=" authtoken)
        request  (str 
                   "?assetId=" (url-encode assetid)
                   "&userId="   (url-encode userid)
                   "&deviceId=" (url-encode deviceid))
        url      (URL. (str "http://android.clients.google.com/market/download/Download" request))
        out-file (file filename)]
    (try
      (make-parents out-file)
      (with-open [instream (.getInputStream (doto (.openConnection url)
                               (.setRequestMethod "GET")
                               (.setRequestProperty "User-Agent" "Android-Market/2")
                               (.setRequestProperty "Cookie" cookie)))] 
        (copy instream out-file))
      (catch java.io.IOException e
        (.printStackTrace e System/err)
        (.createNewFile (file (str filename ".403")))))))

(defn download-app-secure [assetid authtoken userid deviceid filename]
  "Download app from the official google market."
  (let [cookie   (str "MarketDA=" authtoken)
        request  (str 
                   "?assetId=" (url-encode assetid)
                   "&userId="   (url-encode userid)
                   "&deviceId=" (url-encode deviceid)
                   "&sig=AOGrW-wAAAAATPghJDx4KBmYHEqdsxeApQ8ql7AzpEzn")
        url      (URL. (str "https://android.clients.google.com/market/download/Download" request))]
      
    ;; disable ssl certificate check
     (javax.net.ssl.HttpsURLConnection/setDefaultHostnameVerifier 
       (proxy [javax.net.ssl.HostnameVerifier] [] 
         (verify [hostname session] true)))
    (try
      (copy (.getInputStream (doto (.openConnection url)
                               (.setRequestMethod "GET")
                               (.setRequestProperty "User-Agent" "Android-Market/2")
                               (.setRequestProperty "cookie" cookie))) 
        (file filename))
      (catch java.io.IOException e
        (.createNewFile (file (str filename ".403")))))))


(defn get-auth-token [credentials]
  "Login and aquire authtoken."
  (let [username (get credentials "username")
        password (get credentials "password")
        session  (doto (new MarketSession)
                   (.login username password #_MarketSession/SERVICE_SECURE))]
    (.getAuthSubToken session)))

(defn- downloaded? [f]
  (let [f (file f)]
    (or (.exists f) (.exists (file (str (.toString f) ".403"))))))

(defn download-if-nonexistant [app credentials output-dir]
  "Download all android applications described in apps into output-dir
   using the credentials defined in the map credentials."
  (let [userid      (get credentials "userid")
        deviceid    (get credentials "deviceid")
        authtoken   (get credentials "authtoken")
        app-id      (:id app)
        output-file (file output-dir app-id)]
    
    ;(println app-id \tab output-file)
    (when-not (downloaded? output-file)
      (println "downloading into" output-dir "app with id" app-id)
      (ignore-exceptions
        (download-app app-id authtoken userid deviceid output-file)))))
                  

(defn load-apps-metadata [file]
  (flatten (deserialize file)))


(defn download-all-apps [input-dir output-dir & credentials-files]
  (let [properties        (map #(into {} (read-properties %)) credentials-files)
        avail-credentials (map #(assoc % "authtoken" (get-auth-token %)) properties)]
    (doseq [in-file (file-seq (file input-dir)) :when (.isFile in-file)]
      (let [apps (load-apps-metadata in-file)]
        (println (count apps))
        (doall
          (pmap #(download-if-nonexistant %1 %2 (file output-dir (cat/get-category %1))) apps (cycle avail-credentials)))))))

(comment

  (set! *print-length* 10)
 (download-all-apps 
   (file "results/market-apps/" (date-string)) 
   "d:/android/apps/original" 
   "marketcredentials.properties" 
   "marketcredentials2.properties" 
   "marketcredentials3.properties" 
   "marketcredentials4.properties" 
   "marketcredentials5.properties")
 
 
 (let [c (read-properties "marketcredentials4.properties")
       userid      (get c "userid")
       deviceid    (get c "deviceid")
       authtoken   (get-auth-token c)
       app-id      "4332943286439977254"]
   (download-app-secure app-id authtoken userid deviceid (str app-id ".apk")))
 
 (count (deserialize "d:/android/apps/original/apps-BRAIN"))
 
 ;; disable https certificate verification
 (javax.net.ssl.HttpsURLConnection/setDefaultHostnameVerifier 
   (proxy [javax.net.ssl.HostnameVerifier] [] 
     (verify [hostname session] true)))
)