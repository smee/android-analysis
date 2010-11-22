(ns android.market.leech
  (:use 
    [clojure.contrib.java-utils :only (read-properties)]
    [clojure.contrib.io :only (as-file)]
    [clojure.stacktrace :only (root-cause)]
    [clojure.string :only (join)]
    android-manifest.serialization
    android-manifest.util)
  (:import 
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
;use setters only if parameter is given (There is a difference between not setting a value
; and setting it to null.)
(let [b (Market$AppsRequest/newBuilder)]
  (do
    (when query
      (.setQuery b query))
    (when app-type
      (.setAppType b app-type))
    (when view-type
      (.setViewType b view-type))
    (when order-type
      (.setOrderType b order-type))
    (when cat-id
      (.setCategoryId b cat-id))
    (when start-idx
      (.setStartIndex b start-idx))
    (when entries-count
      (.setEntriesCount b entries-count))
    (when extended?
      (.setWithExtendedInfo b extended?))
      
    (.build b))))


(defn create-apps-request [{:keys [query app-type view-type order-type category start-idx entries-count extended?], 
                           :or {query nil,app-type Market$AppType/APPLICATION, view-type Market$AppsRequest$ViewType/FREE, 
                                order-type Market$AppsRequest$OrderType/NEWEST, category nil, start-idx 0, entries-count 10, extended? true}}]
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
        :appType app-type
        :timestamp (System/currentTimeMillis))
      :extendedInfo :allFields :descriptorForType :defaultInstanceForType :unknownFields :serializedSize :promoText :class)))

(defn fetch-app-infos [session m]
  "Return reference to sequence of application details. Dereference with @."
  (call-market 
    session
    (create-apps-request m)
    (fn [resp] (map 
                 extract-app-infos
                 (.getAppList resp)))))


(defn sleep-random [min max]
  (Thread/sleep (+ min (.nextInt (java.util.Random.) (- max min)))))

(defn init-session [credentials]
  (let [session  (new MarketSession)
        username (get credentials "username")
        password (get credentials "password")]
  (do
    ; login
    (doto session
      (.login username password)
      (.setLocale java.util.Locale/US))
      
    ; we are on an US carrier using a froyo device....
    (doto (.getContext session)
      (.setAuthSubToken (.getAuthSubToken session))
      (.setUnknown1 0)
      (.setVersion 1002)
      ;(.setAndroidId "0000000000000000")
      (.setDeviceAndSdkVersion "sapphire:8")
      (.setUserLanguage "de");"en")
      (.setUserCountry "de");"us")
      (.setOperatorAlpha "Vodafone");"T-Mobile USA")
      (.setOperatorNumeric "26202")
      (.setSimOperatorAlpha "Vodafone");"T-Mobile USA")
      (.setSimOperatorNumeric "26202"))
    ; return
    session)))

(defn fetch-all-apps [category credentials directory]
  (println "fetching category " category)
  (let [session (atom (init-session credentials))
        results (map #(delay 
                        (sleep-random 100 1000) 
                        (fetch-app-infos @session {:start-idx (* % 10) :entries-count 10 :category category :app-type nil}))
                    (range 0 79))]
    (serialize (str directory "/apps-" category)
      (filter map?  
        (flatten (doall 
                   (for [result results]
                     (try
                       (deref result)
                       (catch Exception e
                         (println "Caught exception in " category)
                         (println (root-cause e))
                         (reset! session (init-session credentials)))))))))))

(defn load-authors [input-dir]
  (let [authors (into #{} (flatten (map #(map :creatorId (deserialize (str input-dir "/apps-" %))) all-known-categories)))]
      authors))

(defn leech-apps-per-author [session downloaded-apps-dir]
  (let [authors (load-authors downloaded-apps-dir)
        downloaded-apps (into #{} (map #(.getName %) (file-seq (as-file downloaded-apps-dir))))]
    (for [author authors]
      (remove #(contains? downloaded-apps %) (fetch-app-infos session {:query (str "pub:" author)})))))

#_(comment
  
  (def credentials (read-properties "marketcredentials.properties"))
  (def session (init-session credentials))
  
  
  (def cat (second all-known-categories))
  (def leech-them 
    (map #(delay 
            (sleep-random 1000 3000) 
            (fetch-app-infos session {:start-idx (* % 10) :category cat}))
      (range 0 79)))
  
  
  
  
  (def request (create-apps-request {:query "NihongoUp" :view-type Market$AppsRequest$ViewType/PAID}))
  (fetch-app-infos session {:query "pub:\"Games HUB\"" })
  (def authtoken (.getAuthSubToken session))
  (download-app "3543009218990312084" authtoken (get credentials "userid") (get credentials "deviceid") (str (java.lang.System/nanoTime) ".apk"))
  (def cats (fetch-categories session))
  
  (let [date (date-string)
        dir (str "results/market-apps/" date)]
    (.mkdirs (java.io.File. dir))
    (doall 
      (map 
        #(fetch-all-apps %1 %2 dir) 
        all-known-categories 
        (cycle (map read-properties ["marketcredentials.properties" "marketcredentials2.properties" "marketcredentials3.properties"])))))
)
