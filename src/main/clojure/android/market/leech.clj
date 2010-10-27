(ns android.market.leech
  (:use 
    [clojure.contrib.java-utils :only (read-properties)]
    [clojure.string :only (join)]
    [clojure.contrib.duck-streams :only (copy append-spit)]
    android-manifest.serialization
    [android-manifest.util :only (try-times)])
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


(defn- apps-request [query app-type view-type order-type cat-id start-idx entries-count extended?]
  "Private function that sets all properties of a market request."
  (.build 
    (doto (Market$AppsRequest/newBuilder)
      ;(.setQuery query)
      ;(.setAppType app-type)
      (.setViewType view-type)
      (.setOrderType order-type) 
      (.setCategoryId cat-id)
      (.setStartIndex start-idx)
      (.setEntriesCount entries-count)
      (.setWithExtendedInfo extended?)
      )))


(defn create-apps-request [{:keys [query app-type view-type order-type category start-idx entries-count extended?], 
                           :or {query nil,app-type Market$AppType/APPLICATION, view-type Market$AppsRequest$ViewType/FREE, 
                                order-type Market$AppsRequest$OrderType/NEWEST, category "COMMUNICATION", start-idx 0, entries-count 10, extended? true}}]
  "Use a map to specify an arbitrary subset of market request parameters."
  (apps-request query app-type view-type order-type category start-idx entries-count extended?))

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
      @result)))

(defn fetch-categories [session]
  "Return reference to list of Category. Dereference with @."
  (call-market 
    session
    (.build (Market$CategoriesRequest/newBuilder))
    (fn [resp] (.getCategoriesList resp))))      


(defn- extract-app-infos [app]
  (let [m           (bean app)
        app-type    (.name (.getAppType app))
        ex          (bean (.getExtendedInfo app))
        permissions (into [] (:permissionIdList ex))]
    (dissoc    
      (assoc (merge ex m)
        :permissionIdList permissions
        :appType app-type)
      :extendedInfo :allFields :descriptorForType :defaultInstanceForType :unknownFields :serializedSize :promoText :class)))

(defn fetch-app-infos [session m]
  "Return reference to sequence of application details. Dereference with @."
  (call-market 
    session
    (create-apps-request m)
    (fn [resp] (map 
                 extract-app-infos
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

(defn- init-session [credentials]
  (doto (new MarketSession)
    (.login (get credentials "username") (get credentials "password"))))

(defn fetch-all-apps [category credentials]
  (let [session (atom (init-session credentials))
        results (map #(delay 
                        (sleep-random 1000 3000) 
                        (fetch-app-infos @session {:start-idx (* % 10) :entries-count 10 :category category}))
                    (range 0 79))]
        (doseq [result results]
          (try
            (serialize (str "results/market-apps/apps-" category) (deref result) true)
            (catch Exception e
              (println "Caught exception in " category)
              (reset! session (init-session credentials)))))))

(defn not-exists? [category id]
  (not (.exists (java.io.File. (str "results/market-apps/" category \/ id)))))

#_(comment
  
  (def credentials (read-properties "marketcredentials.properties"))
  ;(map #(fetch-all-apps % credentials) all-known-categories)

  (def session (doto (new MarketSession)
                 (.login (get credentials "username") (get credentials "password"))))
  
  
  (def cat (second all-known-categories))
  (def leech-them 
    (map #(delay 
            (sleep-random 1000 3000) 
            (fetch-app-infos session {:start-idx (* % 10) :category cat}))
      (range 0 79)))
  
  
  (doall (map #(serialize (str "apps-" cat) (deref %) true) leech-them))
  
  (def authtoken (.getAuthSubToken session))
  
  (doall (for [category all-known-categories]  
           (do 
             (println "starting " category)
             (doall 
               (for [id (map :id (flatten (deserialize (str "results/market-apps/apps-" category))))
                     :when (not-exists? category id)]
                 (do
                   (println "downloading " category \space id)
                   (download-app id authtoken credentials (str "results/market-apps/" category "/" id))))))))
  
  (def request (create-apps-request {:query "open"}))
  (fetch-app-infos session {:query "open" :category "COMMUNICATION"})
  (download-app "-1515770811183552303" authtoken credentials (str (java.lang.System/nanoTime) ".apk"))
  (def cats (fetch-categories session))
)
