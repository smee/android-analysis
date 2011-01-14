(ns android.market.leech
  (:use 
    [clojure.contrib.java-utils :only (read-properties)]
    [clojure.contrib.io :only (file)]
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
     Market$ResponseContext 
     Market$AppsRequest$ViewType
     Market$AppsRequest$OrderType
     Market$RequestContext$Builder]))


(def all-known-categories 
  ["APP_WALLPAPER"
   "APP_WIDGETS"
   "ARCADE"
   "BOOKS_AND_REFERENCE"
   "BRAIN"
   "BUSINESS"
   "CARDS"
   "CASUAL"
   "COMICS"
   "COMMUNICATION"
   "DEMO"
   "EDUCATION"
   "ENTERTAINMENT"
   "FINANCE"
   "GAME_WALLPAPER"
   "GAME_WIDGETS"
   "HEALTH"
   "HEALTH_AND_FITNESS"
   "LIBRARIES"
   "LIBRARIES_AND_DEMO"
   "LIFESTYLE"
   "MEDIA_AND_VIDEO"
   "MEDICAL"
   "MULTIMEDIA"
   "MUSIC_AND_AUDIO"
   "NEWS"
   "NEWS_AND_MAGAZINES"
   "PERSONALIZATION"
   "PHOTOGRAPHY"
   "PRODUCTIVITY"
   "REFERENCE"
   "SHOPPING"
   "SOCIAL"
   "SPORTS"
   "SPORTS_GAMES"
   "THEMES"
   "TOOLS"
   "TRANSPORTATION"
   "TRAVEL"
   "TRAVEL_AND_LOCAL"
   "WEATHER"])


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


(defn- create-apps-request 
    "Use a map to specify an arbitrary subset of market request parameters."
  [{:keys [query app-type view-type order-type category start-idx entries-count extended?], 
    :or {query nil,app-type nil #_Market$AppType/APPLICATION, view-type Market$AppsRequest$ViewType/FREE, 
         order-type Market$AppsRequest$OrderType/NEWEST, category nil, start-idx 0, entries-count 10, extended? true}}]
  (apps-request query app-type view-type order-type category start-idx entries-count extended?))


(defn- extract-app-infos 
  "Create map of relevant bean properties of one android app."
  [app]
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

(defn- create-market-api [credentials]
  (let [user (get credentials "username")
        pw   (get credentials "password")]
    (doto (AndroidMarketApi. user pw true)
      (.setAndroidId (get credentials "androidid"))
      (.fakeGermanCarrier))))


(defn- fetch-app-infos [m market-api]
  (map extract-app-infos 
    (.executeRequest market-api (create-apps-request m))))
  

(defn create-metadata-fetcher 
"Create a lazy sequence of android app metadata by calling to the remote api
for as long as there are more than 0 results per request."
  [api queries] 
  (lazy-seq
    (if-let [q (first queries)]
      (let [apps (->> api (fetch-app-infos q) (filter map?))]
        (when (not-empty apps)
          (cons apps (create-metadata-fetcher api (rest queries))))))))

(defn- create-queries 
  "Sequence of api queries 0..800"
  [template]
  (map #(assoc template 
          :start-idx (* % 10) 
          :entries-count 10) 
    (range 0 80)))

(defn fetch-all-apps 
  "Download metadata of all android apps matching the query."
  [query-template-map credentials]
  (let [api     (create-market-api credentials)
        queries (create-queries query-template-map)]
    (create-metadata-fetcher api queries)))
  
(defn fetch-all-newest-apps-category 
  "Download metadata of all the newest android apps for a category."
  [category credentials]
  (fetch-all-apps {:category category :app-type nil :order-type Market$AppsRequest$OrderType/NEWEST} credentials))

(defn fetch-all-apps-author
  "Download metadata of all android apps of one author."
  [author credentials]
  (fetch-all-apps {:app-type nil :query (str "pub:" author) :order-type nil} credentials))

(defn batch-download-newest [& cred-files]
  (let [date (date-string)
        dir  (file (str "results/market-apps/" date))]
    (.mkdirs dir)
    (dorun 
      (pmap 
        (fn [cat cred]
          (serialize (str dir "/apps-" cat) 
            (fetch-all-newest-apps-category cat cred)))
        all-known-categories
        (cycle (map read-properties cred-files)))))) 

(defn load-authors [input-dir]
  (let [authors (->> input-dir file file-seq (filter #(.isFile %)) (mapcat deserialize) (map :creatorId) distinct)]
      authors))

(defn leech-apps-per-author [apps-metadata-dir & credentials]
  (let [authors (load-authors apps-metadata-dir)]  
    (mapcat #(fetch-all-apps-author %1 %2) authors (cycle credentials))))

(comment
  (def cred-files ["marketcredentials.properties" "marketcredentials2.properties" "marketcredentials3.properties" "marketcredentials4.properties"])
  (def credentials (read-properties (first cred-files)))
  (def api (create-market-api credentials))
  
  (def input-dir "results/market-apps/20110113")
  (def all-from-authors (leech-apps-per-author input-dir credentials))
  (take 3 all-from-authors)
    
  (def request (create-apps-request {:query "NihongoUp" :view-type Market$AppsRequest$ViewType/FREE}))
  (count (fetch-app-infos {:query "a" :start-idx 600} api))
  (def authtoken (.getAuthSubToken session))

  (apply batch-download-newest cred-files)
  
)
