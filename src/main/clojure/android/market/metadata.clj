(ns android.market.metadata
  (:use
    [org.clojars.smee
     [map :only (map-values mapmap pmapcat)]
     [file :only (find-files)]
     [seq :only (seq-counter)]
     [serialization :only (deserialize-all)]]
    [archive :only (process-entries)]
    [clojure.string :only (split)]
    [clojure.java.io :only (file make-parents)]
    somnium.congomongo))


(def relevant-keys [:appType
                    :category
                    :creator
                    :creatorId
                    :id
                    :installSize
                    :packageName
                    :permissionIdCount
                    :permissionIdList
                    :rating
                    :ratingsCount
                    :timestamp
                    :title
                    :version
                    :versionCode])

(defn default-connection []
  (make-connection "android" {:host "localhost" :port 27017}))

(defn fetch-metadata-per-package 
  "Lazy sequence of apps, each containing a sorted collection of metadata (sorted by :timestamp)."
  ([] (fetch-metadata-per-package (default-connection)))
  ([conn]
    (with-mongo conn 
      (let [; fetch all versions per package name
            all-meta (fetch :metadata 
                            :sort {:packageName 1})
            ; split into versions per package name
            per-package (->> all-meta
                          (partition-by :packageName)
                          (map (partial sort-by :timestamp)))]
        per-package))))

(defn fetch-newest-ids 
  ([] (fetch-newest-ids (default-connection)))
  ([conn] 
    (with-mongo conn
      (let [v-p-p (fetch-metadata-per-package conn)
            newest (map #(apply max-key :timestamp %) v-p-p)]
        (map :id newest)))))

(comment
  
  (def db (mongo! :db :android))
  ;; load flat files
  (let [files (find-files "e:/android/metadata/incoming")
        maps-per-file (pmap (comp flatten deserialize-all) files)]
    (doseq [m maps-per-file]
      (mass-insert! :metadata (map #(select-keys % relevant-keys) m))))
  ;;
  ;; OR
  ;; load zip archives
  (dorun
    (map (fn [f] (process-entries 
                   f
                   (fn [_ bytes] (mass-insert! :metadata 
                                               (map #(select-keys % relevant-keys)
                                                    (flatten (deserialize-all bytes))))
                     nil)))
         (find-files "e:/android/metadata/incoming")))
  
  
  (add-index! :metadata [:packageName])
  (add-index! :metadata [:timestamp])
  
  (fetch-count :metadata :where {:timestamp {:$lt 1301531006761}})
  
  ; how many metadata entries do not have a timestamp?
  (fetch-count :metadata :where {:timestamp {:$exists false}})
  
  (def version-depth
    ;; map of packagename to map of versions to number of metadata documents
    (let [raw (fetch :metadata :only [:packageName :versionCode])
          clustered (group-by :packageName raw)
          fine-clustered (map-values (partial group-by :versionCode) clustered)]
      (map-values #(into (sorted-map) (map-values count %)) fine-clustered)))
  
  ;; number of metadata entries per packageName and version
  (map-reduce :metadata
              "function(){
                   emit({packageName: this.packageName, versionCode: this.versionCode}, {count: 1});
               }"
              "function(p, versions){
                  var c = 0;
                  versions.forEach(function(v){
                      c += v['count'];
                   });

                  return {count: c}; 
               }"
              "clustered"
              :output :collection)
  (def metadata-counts
    (let [x (fetch :clustered)
          y (map (juxt #(get-in % [:_id :packageName]) #(get-in % [:_id :versionCode]) #(get-in % [:value :count])) x)]
      (->> y
        (group-by first)
        (map-values #(->> % 
                       (map next)
                       (map vec)
                       (into (sorted-map)))))))
  ;; print number of different versions per package name
   (clojure.pprint/pprint (into (sorted-map) (frequencies (map count (vals metadata-counts)))))
  )