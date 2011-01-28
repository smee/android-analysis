(ns android.market.leech
  (:use 
    [clojure.contrib.java-utils :only (read-properties)]
    [clojure.contrib.io :only (file)]
    [clojure.stacktrace :only (root-cause)]
    [clojure.string :only (join)]
    android-manifest.serialization
    android-manifest.util)
  (:require
    [android.market.category :as cat])
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

(def api-cache (ref {}))

(defn- create-market-api 
  "Login to google android market. Caches all instances."
  [cred]
  (dosync
    (if-let [api (get @api-cache cred)]
      api
      (let [user (get cred "username")
            pw   (get cred "password")
            api  (doto (AndroidMarketApi. user pw true)
                   (.setAndroidId (get cred "androidid"))
                   (.fakeGermanCarrier))]
        (alter api-cache assoc cred api)
        api))))


(defn- fetch-app-infos [m market-api]
  (map extract-app-infos
    (filter identity
      (try
        (.executeRequest market-api (create-apps-request m))
        (catch Exception e
          (.printStackTrace e System/err))))))
  

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
  [query-template-map cred]
  (let [api     (create-market-api cred)
        queries (create-queries query-template-map)]
    (filter #(empty? (:price %))
      (flatten (create-metadata-fetcher api queries)))))
  
(defn fetch-all-newest-apps-category 
  "Download metadata of all the newest android apps for a category."
  [category credentials]
  (fetch-all-apps  credentials))

(defn fetch-all-apps-author
  "Download metadata of all android apps of one author."
  [author cred]
  (fetch-all-apps {:app-type nil :query (str "pub:" author) :order-type nil} cred))


(defn batch-download 
  "Fetch metadata about apps from the google market. Tries to fetch all metadata for 
   each query template (read: as many as available).

   outdir: directory to write the results to
   query-templates: sequence of maps that resemble the queries
   out-file-gen-fn: function that converts query into a string that is used as filename for the results of this query
   cred-files : sequence of credential files"
  [outdir query-templates out-file-gen-fn cred-files]
  (let [dir (file outdir)]
    (.mkdirs dir)
    (dorun 
      (pmap 
        (fn [template cred] 
          (let [out (file dir (out-file-gen-fn template))
                new-file? (not (.exists out))]
          (when new-file? (serialize out (fetch-all-apps template cred)))))
        query-templates
        (cycle (shuffle (map read-properties cred-files))))))) 
    

(defn batch-download-newest 
  "Download the newest free apps per category."
  [cred-files]
  (let [dir          (file (str "results/market-apps/" (date-string)))
        out-files-fn :category
        query-tmpl   (map #(hash-map :category % :app-type nil :order-type Market$AppsRequest$OrderType/NEWEST) cat/all-known-categories)]
        (batch-download dir query-tmpl out-files-fn cred-files)))

(defn batch-download-query
  [q cred-files]
  (let  [dir (str "results/market-apps/")
         o-f (constantly (str (date-string) "_" q))
         queries [{:app-type nil :query q}]]
    (batch-download dir queries o-f cred-files)))
            

(defn load-authors [input-dir]
  (let [authors (->> input-dir file file-seq (filter #(.isFile %)) (mapcat deserialize) (map :creatorId) distinct)]
      authors))

(defn leech-apps-per-author [apps-metadata-dir credentials]
  (let [authors (load-authors apps-metadata-dir)]  
    (pmap #(fetch-all-apps-author %1 %2) authors (cycle credentials))))




(comment
  (def cred-files ["marketcredentials.properties" "marketcredentials2.properties" "marketcredentials3.properties" "marketcredentials4.properties" "marketcredentials5.properties"])
  (batch-download-newest cred-files)
  (batch-download-query " " cred-files)
  
  (def credentials (map read-properties cred-files))
  
  (let [input-dir (str "results/market-apps/" #_(date-string))
        all-from-authors (leech-apps-per-author input-dir credentials)
        outfile (str "results/author-" (date-string))]
    (serialize outfile (remove empty? all-from-authors)))
  
  (take 3 all-from-authors)
  (def request (create-apps-request {:query "NihongoUp" :view-type Market$AppsRequest$ViewType/FREE}))
  (def api (create-market-api (first credentials)))
  (count (fetch-app-infos {:query "a" :start-idx 0} api))

  
  
  (doseq [f (filter #(.isFile %) (file-seq (file "results/market-apps")))] (do (println f) (load-authors f)))
  (def authors (load-authors "results/market-apps"))
  (count authors)
  
  (with-open [rdr (clojure.java.io/reader "t:/downloads/apps.txt")]
      (serialize "results/foo"
        (remove empty?
          (pmap
            #(fetch-app-infos {:query (str "pname:" %1) :start-idx 0 :entries-count 1 :view-type nil} %2)
            (line-seq rdr)
            (cycle (map create-market-api credentials))))))

)
