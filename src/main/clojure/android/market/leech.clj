(ns android.market.leech
  (:use 
    [clojure.contrib.java-utils :only (read-properties)]
    [clojure.contrib.io :only (as-file)]
    [clojure.stacktrace :only (root-cause)]
    [clojure.string :only (join)]
    android-manifest.serialization
    android-manifest.util)
  (:import 
    [com.gc.android.market.api AndroidMarketApi]
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

(defn create-market-api [credentials]
  (let [api (AndroidMarketApi. (get credentials "username") (get credentials "password") false)]
    (do
      (.setAndroidId api (get credentials "androidid"))
      api)))


(defn fetch-app-infos [m market-api]
  (map extract-app-infos 
    (.executeRequest market-api (create-apps-request m))))


(defn fetch-all-apps [query-template-map credentials]
  (let [api (atom (create-market-api credentials))
        queries (map #(assoc query-template-map :start-idx (* % 10) :entries-count 10) (range 0 80))]
    (filter map?
      (flatten
        (for [query queries]
          (try
            (sleep-random 100 1000) 
            (fetch-app-infos query @api)
            (catch Exception e
              ;(println (root-cause e))
              (reset! api (create-market-api credentials)))))))))
  
(defn fetch-all-newest-apps [ & cred-files]
  (let [date (date-string)
        dir (str "results/market-apps/" date)]
    (.mkdirs (java.io.File. dir))
    (dorun 
      (map 
        (fn [cat cred]
          (serialize (str dir "/apps-" cat) (fetch-all-apps {:category cat :app-type nil} cred)))
        all-known-categories
        (cycle (map read-properties cred-files))))))
  
(defn load-authors [input-dir]
  (let [authors (into #{} (flatten (map #(map :creatorId (deserialize (str input-dir "/apps-" %))) all-known-categories)))]
      authors))

(defn leech-apps-per-author [session downloaded-apps-dir]
  (let [authors (load-authors downloaded-apps-dir)
        downloaded-apps (into #{} (map #(.getName %) (file-seq (as-file downloaded-apps-dir))))]
    (for [author authors]
      (remove #(contains? downloaded-apps %) (fetch-app-infos session {:query (str "pub:" author)})))))

#_(comment
  
  (def credentials (read-properties "marketcredentials4.properties"))
  

  
  (def request (create-apps-request {:query "NihongoUp" :view-type Market$AppsRequest$ViewType/PAID}))
  (def api (create-market-api credentials))
  (fetch-app-infos {:query "Open"} api)
  (def authtoken (.getAuthSubToken session))

  (fetch-all-newest-apps "marketcredentials4.properties")
  (fetch-all-newest-apps "marketcredentials.properties" "marketcredentials2.properties" "marketcredentials3.properties" "marketcredentials4.properties")
  
)
