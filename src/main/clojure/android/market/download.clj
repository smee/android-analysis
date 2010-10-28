(ns android.market.download
  (:import 
    java.io.File
    [java.net URL URLEncoder]
    com.gc.android.market.api.MarketSession)
  (:use 
    android-manifest.serialization
    [android.market.leech :as ml]
    [clojure.contrib.duck-streams :only (copy)]
    [clojure.contrib.java-utils :only (read-properties)]))

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
                   (.setRequestProperty "User-Agent" "Android-Market/2 (dream DRC85)")
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
      (download-app app-id authtoken userid deviceid output-file)
      :ok)))
                  

(defn load-apps-metadata [category]
  (flatten (deserialize (str "results/market-apps/apps-" category))))


(defn download-all-apps [& credentials-files]
  (let [properties        (map #(into {} (read-properties %)) credentials-files)
        avail-credentials (map #(assoc % "authtoken" (get-auth-token %)) properties)
        workers           (agent 0)]
    (doseq [category ml/all-known-categories]
      (let [apps (load-apps-metadata category)
            output-dir (str "results/market-apps/" category)]
        (printf "got %d apps to download into %s \n" (count apps) output-dir)
        (doall
          (pmap #(leech-apps %1 %2 output-dir) apps (cycle avail-credentials)))))))
  
(comment
  #_(printf "%s %s %s\n" %1 %2 output-dir)
  #_(leech-apps %1 %2 output-dir)
  (set! *print-length* 10)
  (doall (download-all-apps "marketcredentials.properties" "marketcredentials2.properties" "marketcredentials3.properties"))
)