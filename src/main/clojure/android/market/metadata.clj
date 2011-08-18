(ns android.market.metadata
  (:use
    [android.tools.util :only (find-files map-values mapmap seq-counter pmapcat)]
    [android.tools.serialization :only (deserialize-all)]
    [clojure.contrib.string :only (split)]
    [clojure.java.io :only (file make-parents)]
    [clojure.contrib.duck-streams :only (append-spit)]
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
  (let [files (find-files "e:/android/metadata/incoming")
        maps-per-file (pmap (comp flatten deserialize-all) files)]
    (doseq [m maps-per-file]
      (mass-insert! :metadata (map #(select-keys % relevant-keys) m))))
  
  (map (fn [f] (android.tools.archive/process-entries! 
          f
          (fn [_ bytes] (mass-insert! :metadata 
                                      (map #(select-keys % relevant-keys)
                                           (flatten (deserialize-all bytes)))))))
       (find-files "e:/android/metadata/incoming"))
  
  
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
  
  
  (map-reduce :metadata
              "function(){
                   emit({packageName: this.packageName, versionCode: this.versionCode}, {count: 1});
               }"
              "function(p, versions){
                  var c = 0;
                  for ( v in versions )
                      c += v['count'];
                  return {count: c}; 

               }"
              "clustered")
  )