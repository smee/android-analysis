(ns android.market.leech
  (:use 
    [clojure.contrib.java-utils :only (read-properties)]
    [clojure.string :only (join)]
    [clojure.contrib.duck-streams :only (copy append-spit)]
    android-manifest.serialization)
  (:import 
    java.net.URLEncoder
    [com.gc.android.market.api MarketSession MarketSession$Callback]
    [com.gc.android.market.api.model 
     Market$App 
     Market$AppType 
     Market$AppsRequest 
     Market$AppsResponse 
     Market$CategoriesRequest
     Market$CategoriesResponse 
     Market$Category 
     Market$ResponseContext 
     Market$AppsRequest$ViewType
     Market$AppsRequest$OrderType
     Market$RequestContext$Builder]))


(def all-known-categories ["ARCADE"
                           "BRAIN"
                           "CARDS"
                           "CASUAL"
                           "COMICS"
                           "COMMUNICATION"
                           "DEMO"
                           "ENTERTAINMENT"
                           "FINANCE"
                           "HEALTH"
                           "LIBRARIES"
                           "LIFESTYLE"
                           "MULTIMEDIA"
                           "NEWS"
                           "PRODUCTIVITY"
                           "REFERENCE"
                           "SHOPPING"
                           "SOCIAL"
                           "SPORTS"
                           "THEMES"
                           "TOOLS"
                           "TRAVEL"])


(defn- apps-request [query app-type view-type order-type cat-id start-idx entries-count]
  "Private function that sets all properties of a market request."
  (.build 
    (doto (Market$AppsRequest/newBuilder)
      ;(.setQuery query)
      (.setAppType app-type)
      (.setViewType view-type)
      (.setOrderType order-type)
      ;(.setCategoryId cat-id)
      (.setStartIndex start-idx)
      (.setEntriesCount entries-count)
      ;(.setWithExtendedInfo true)
      )))


(defn create-apps-request [{:keys [query app-type view-type order-type category start-idx entries-count extended-info], 
                           :or {query nil,app-type Market$AppType/APPLICATION, view-type Market$AppsRequest$ViewType/FREE, 
                                order-type Market$AppsRequest$OrderType/NEWEST, category "COMMUNICATION", start-idx 0, entries-count 10, extended-info true}}]
  "Use a map to specify an arbitrary subset of market request parameters."
  (apps-request query app-type view-type order-type category start-idx entries-count))

(defn- call-market [session req res-fn]
  "Call android market api, returns (res-fn response) wrapped in a promise."
  (let [result   (promise)
        callback (proxy [MarketSession$Callback] []
                   (onResult [ctx, resp]
                     (deliver result (res-fn resp))))]
    (do
      (doto session
        (.append req callback)
        (.flush))
      result)))

(defn fetch-categories [session]
  "Return reference to list of Category. Dereference with @."
  (call-market 
    session
    (.build (Market$CategoriesRequest/newBuilder))
    (fn [resp] (.getCategoriesList resp))))      


(defn fetch-app-infos [session m]
  "Return reference to sequence of application details. Dereference with @."
  (call-market 
    session
    (create-apps-request m)
    (fn [resp] (map 
                 #(update-in ;;use enum value name for apptype 
                    (select-keys (bean %) [:creator :versionCode :version :title :id :price :appType])
                    [:appType]
                    (memfn name))
                 (.getAppList resp)))))


(defn url-encode
  "Wrapper around java.net.URLEncoder returning a (UTF-8) URL encoded
   representation of text."
  [text]
  (URLEncoder/encode text "UTF-8"))

(defn leech-category [cat session]
  )

(defn download-app [assetid authtoken credentials filename]
  "Download app from the official google market."
  (let [cookie   (str "ANDROID=" authtoken)
        userid   (get credentials "userid")
        deviceid (get credentials "deviceid")
        request  (str 
                   "?assetId=" (url-encode assetid)
                   "&userId="   (url-encode userid)
                   "&deviceId=" (url-encode deviceid))
        url      (java.net.URL. (str "http://android.clients.google.com/market/download/Download" request))
        conn     (doto (.openConnection url)
                   (.setRequestMethod "GET")
                   (.setRequestProperty "User-Agent" "Android-Market/2 (dream DRC85)")
                   (.setRequestProperty "cookie" cookie))]
    (copy (.getInputStream conn) (java.io.File. filename))))

(defn sleep-random [min max]
  (Thread/sleep (+ min (.nextInt (java.util.Random.) (- max min)))))

(comment
  
  (def credentials (read-properties "marketcredentials.properties"))
  (def session (doto (new MarketSession)
                 (.login (get credentials "username") (get credentials "password"))))
  (def authtoken (.getAuthSubToken session))
  
  
  
  (def leech-them 
    (map #(do (time (sleep-random 2000 3000)) (fetch-app-infos session {:start-idx (* % 10)})) [0 1 2 3 4 5 6 7 8 9 10]))
  
  ;(doall (map #(append-spit "apps" %) leech-them))
  
  
  
  (def request (create-apps-request {:query "open"}))
  (fetch-app-infos session {:query "open" :category "COMMUNICATION"})
  (download-app "-1515770811183552303" authtoken credentials (str (java.lang.System/nanoTime) ".apk"))
  (def cats (fetch-categories session))  
  )