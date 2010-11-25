(ns android.market.download
  (:import 
    java.io.File
    [java.net URL URLEncoder]
    com.gc.android.market.api.MarketSession)
  (:use 
    android-manifest.serialization
    [android-manifest.util :only (ignore-exceptions sleep-random)]
    [android.market.leech :as ml]
    [clojure.contrib.duck-streams :only (copy)]
    [clojure.contrib.java-utils :only (read-properties)]
    ))

(def *path* "d:/android/apps/original")

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
        conn     (doto (.openConnection url)
                   (.setRequestMethod "GET")
                   (.setRequestProperty "User-Agent" "AndroidDownloadManager")
                   (.setRequestProperty "cookie" cookie))]
    (copy (.getInputStream conn) (File. filename))))


(defn get-auth-token [credentials]
  "Login and aquire authtoken."
  (let [username (get credentials "username")
        password (get credentials "password")
        session  (doto (new MarketSession)
                   (.login username password))]
    (.getAuthSubToken session)))

(defn leech-apps [app credentials output-dir]
  "Download all android applications described in apps into output-dir
   using the credentials defined in the map credentials."
  (let [userid      (get credentials "userid")
        deviceid    (get credentials "deviceid")
        authtoken   (get credentials "authtoken")
        app-id      (:id app)
        output-file (str output-dir \/ app-id)]
    
    ;(println app-id \tab output-file)
    (when-not (.exists (File. output-file))
      (println "downloading into " output-dir " " app-id)
      (ignore-exceptions
        ;(sleep-random 5000 20000)
        (download-app app-id authtoken userid deviceid output-file)))))
                  

(defn load-apps-metadata [category]
  (flatten (deserialize (str *path* "/apps-" category))))


(defn download-all-apps [& credentials-files]
  (let [properties        (map #(into {} (read-properties %)) credentials-files)
        avail-credentials (map #(assoc % "authtoken" (get-auth-token %)) properties)]
    (doseq [category ml/all-known-categories]
      (let [apps (load-apps-metadata category)
            output-dir (str *path* "/" category)]
        (printf "got %d apps to download into %s \n" (count apps) output-dir)
        (doall
          (pmap #(leech-apps %1 %2 output-dir) apps (cycle avail-credentials)))))))

(comment
  #_(printf "%s %s %s\n" %1 %2 output-dir)
  #_(leech-apps %1 %2 output-dir)
  (set! *print-length* 10)
 (download-all-apps "marketcredentials.properties" "marketcredentials2.properties" "marketcredentials3.properties" "marketcredentials4.properties")
 
 (let [c (read-properties "marketcredentials3.properties")
       userid      (get c "userid")
       deviceid    (get c "deviceid")x`
       authtoken   (get-auth-token c)
       app-id      "-6230861955167956295"]
   (download-app app-id authtoken userid deviceid (str app-id ".apk")))
 (count (deserialize "d:/android/apps/original/apps-BRAIN"))
)