(ns android.scripts.process
  (:use
    android.market.process
    [android.tools.util :only (find-files date-string)]
    [clojure.java.io :only (file)])
  (:require 
    [android.tools.archive :as archive]
    [android.analysis.core :as c]
    [android.analysis.manifest :as mf]
    [android.analysis.intent :as intents]))

(def *path* "z:/")

(defn -main []
  (let [*apps*    (str *path* "original/")
        *jars*    (str *path* "jars/")
        *intents* (str *path* "intents/")
        *mf*      (str *path* "manifests/")
        *hash*    (str *path* "classes-md5/")
        *stats*   (str *path* "reduced/")]
    
    (println "extracting jars from" *apps* "into" *jars*)
    (extract-jars *apps* (skip-files-in-dir *jars*) *jars*)
    
    (println "extracting manifests from" *jars* "into" *mf*)
    (let [now           (date-string)
          mf-dir        *mf*
          output-dir    (str mf-dir now)
          skip?         (skip-files-in-archives (find-files mf-dir #".*\.zip"))
          num-extracted (extract-android-manifests *apps* skip? output-dir)] 
      (archive/copy-to-zip (file mf-dir (str now ".zip")) output-dir true)
      num-extracted)
  
    (println "extracting intents from" *jars* "into" *intents*)
    (let [output-dir (extract-intents *jars* *intents*)]
      (archive/copy-to-zip (file *intents* (str output-dir ".zip")) (str *intents* output-dir) true))
    
    
    (println "calculating hashes from" *jars* "into" *hash*)
    (let [output-dir (hash-zip-contents *jars* *hash*)]
      (archive/copy-to-zip (file *hash* (str output-dir ".zip")) (str *hash* output-dir) true))
    
    
    (println "writing output csv... into" *stats*) 
    (let [class-lookup (c/build-name-classes-fn *jars*)
          apps (apply c/join-intents 
                      (pvalues 
                        (-> *mf* (find-files #".*\d{4}\d*") mf/load-apps-from-disk mf/unique-apps)
                        (intents/load-intents-from-disk *intents*)))]
      (do
        (c/save-sizes-csv (str *stats* "sizes-" (date-string) ".csv") apps *apps*)
        (c/save-to-csv (str *stats* "deps-" (date-string) ".csv") apps class-lookup)))))