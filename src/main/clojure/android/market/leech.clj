(ns android.market.leech
  (:use 
    [clojure.contrib.core :only (.?.)]
    [clojure.contrib.io :only (file)]
    [clojure.contrib.java-utils :only (read-properties)]
    [clojure.stacktrace :only (root-cause)]
    [clojure.string :only (join)]
    [org.clojars.smee serialization util time])
  (:require
    [android.market.category :as cat])
  (:import 
    [com.gc.android.market.api MarketSession MarketSession$Callback]
    [com.gc.android.market.api.model
     Market$App 
     Market$AppType 
     Market$AppsRequest 
     Market$AppsResponse 
     Market$CategoriesRequest
     Market$CategoriesResponse
     Market$CommentsRequest
     Market$CommentsResponse
     Market$ResponseContext 
     Market$AppsRequest$ViewType
     Market$AppsRequest$OrderType
     Market$RequestContext$Builder]))




(defn- apps-request [query app-type view-type order-type cat-id start-idx entries-count extended?]
"Private function that sets all properties of a market request.
 use setters only if parameter is given (There is a difference between not setting a value
 and setting it to null.)"
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

(defn- comments-request [app-id start-idx entries-count]
  (let [b (Market$CommentsRequest/newBuilder)]
    (do
      (when app-id
        (.setAppId b app-id))
      (when start-idx
        (.setStartIndex b start-idx))
      (when entries-count
        (.setEntriesCount b entries-count))
      (.build b))))

(defn- categories-request []
  (.build (Market$CategoriesRequest/newBuilder)))

(defn- create-apps-request 
    "Use a map to specify an arbitrary subset of market request parameters."
  [{:keys [query app-type view-type order-type category start-idx entries-count extended?], 
    :or {query nil,app-type nil #_Market$AppType/APPLICATION, view-type Market$AppsRequest$ViewType/FREE, 
         order-type Market$AppsRequest$OrderType/NEWEST, category nil, start-idx 0, entries-count 10, extended? true}}]
  (apps-request query app-type view-type order-type category start-idx entries-count extended?))

(defn- create-comments-request
  [{:keys [app-id start-idx entries-count]
    :or {app-id "", start-idx 0, entries-count 10}}]
  (comments-request app-id start-idx entries-count))

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

(defn- extract-comment [comment]
  (let [m (bean comment)
        now (System/currentTimeMillis)]
    (dissoc (assoc m :timestamp now)
            :serializedSize :descriptorForType :commentslist :initialized :allFields :defaultInstanceForType :unknownFields :class)))

(defn- init-session [credentials]
  (let [session  (new MarketSession)
        username (get credentials "username")
        password (get credentials "password")
        ]
  (do
    ; we are using a gingerbread device....
    (doto (.getContext session)
      (.setDeviceAndSdkVersion "crespo:10"))
    
    ; login
    (doto session
      (.login username password)
      (.setLocale java.util.Locale/US))
     
    session)))

(def api-cache (ref {}))
(defn- create-market-api 
  "Login to google android market. Caches all instances."
  [cred]
  (if-let [api (get @api-cache cred)]
      api
      (dosync
        (let [api (init-session cred)]
          (alter api-cache assoc cred api)
          api))))


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


(defn get-all-categories
  "Retrieve sequence of all currently active categories from android market."
  [cred]
  (let [resp-fn #(sort (remove empty? (map (memfn getCategoryId) (mapcat (memfn getSubCategoriesList) (.?. % getCategoriesList)))))
        api (create-market-api cred)]
    (call-market api (categories-request) resp-fn)))

(defn fetch-app-infos [req session resp-fn]
  "Return reference to sequence of application details. Dereference with @."
  (ignore-exceptions
    (call-market 
      session
      req
      resp-fn)))
  

(defn create-metadata-fetcher 
"Create a lazy sequence of android app metadata by calling to the remote api
for as long as there are more than 0 results per request.
api: instance of MarketSession
queries: sequence of query maps
req-maker: functions that creates instance of a valid request that MarketSessions accepts via append
resp-fn: function that handles the response"
[api queries req-maker resp-fn] 
(lazy-seq
  (when-let [q (first queries)]
    (let [_ (sleep-random 2000 5000)
          apps (filter map? (fetch-app-infos (req-maker q) api resp-fn))]
      (when (not-empty apps)
        (lazy-cat apps (create-metadata-fetcher api (rest queries) req-maker resp-fn)))))))

(defn- create-queries 
  "Sequence of api queries 0..infinity"
  [template]
  (map #(assoc template 
          :start-idx (* % 10) 
          :entries-count 10) 
    (iterate inc 0)))

(defn fetch-all-apps 
  "Download metadata of all android apps matching the query."
  ([query-template-map cred] 
    (fetch-all-apps query-template-map cred create-apps-request #(map extract-app-infos (.?. % getAppList))))
  ([query-template-map cred req-maker resp-fn]
    (let [api     (create-market-api cred)
          queries (create-queries query-template-map)]
      (flatten (create-metadata-fetcher api queries req-maker resp-fn)))))
  
(defn fetch-all-apps-author
  "Download metadata of all android apps of one author."
  [author cred]
  (fetch-all-apps {:app-type nil :query (str "pub:" author) :order-type nil} cred))

(defn fetch-all-comments [app-id cred]
  (fetch-all-apps {:app-id app-id} cred create-comments-request #(map extract-comment (.?. % getCommentsList))))

(defn batch-download 
  "Fetch metadata about apps from the google market. Tries to fetch all metadata for 
   each query template (read: as many as available).

   outdir: directory to write the results to
   query-templates: sequence of maps that resemble the queries
   out-file-gen-fn: function that converts query into a string that is used as filename for the results of this query
   cred-files : sequence of credential files"
  ;;TODO rewrite using agents to avoid thread safety issues in Market.AppRequest.Builder when two threads use the same credentials/api instance
  [outdir query-templates out-file-gen-fn cred-files]
  (let [dir (file outdir)]
    (.mkdirs dir)
    (dorun 
      (map 
        (fn [template cred] 
          (let [out (file dir (out-file-gen-fn template))
                new-file? (not (.exists out))]
          (when new-file? (serialize out (fetch-all-apps template cred)))))
        query-templates
        (cycle (map read-properties cred-files)))))) 
    

(defn batch-download-newest 
  "Download the newest free apps per category."
  [cred-files]
  (let [dir          (file (str "results/market-apps/" (str (date-string) "-" (java.util.UUID/randomUUID))))
        out-files-fn :category
        categories (get-all-categories (read-properties (first cred-files)))
        query-tmpl   (map #(hash-map :category % :app-type nil :order-type Market$AppsRequest$OrderType/NEWEST) categories)]
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
  (def cred-files  (find-files "." #"marketcredentials.*.properties"))
  (batch-download-newest cred-files)
  (batch-download-query " " cred-files)
  
  (def credentials (map read-properties cred-files))
  
  (let [input-dir (str "results/market-apps/" (date-string))
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

(comment
  (def api (create-market-api (read-properties (first cred-files))))
  (def cr (.build 
            (doto (Market$CommentsRequest/newBuilder)
              (.setAppId "4657776670211489294")
              (.setStartIndex 70)
              (.setEntriesCount 10))))
  (call-market api cr identity)
  (def cred (read-properties (first cred-files)))
  (def x (fetch-all-comments "-6318794405226192550" cred))
  )

