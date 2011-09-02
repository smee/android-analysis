(ns android.scripts.process
  (:use
    android.market.process
    [org.clojars.smee  
     [file :only (find-files)]
     [time :only (date-string)]
     [map :only (pmapcat)]]
    [clojure.java.io :only (file)]
    [android.experiments.sdk :only (android-specific?)])
  (:require 
    [archive :as archive]
    [android.analysis.core :as c]
    [android.analysis.manifest :as mf]
    [android.analysis.intent :as intents]))

(declare load-apps)

(def *path* "z:/")

(defn -main []
  (let [*apps*    (str *path* "original/")
        *jars*    (str *path* "jars/")
        *intents* (str *path* "intents/")
        *mf*      (str *path* "manifests/")
        *hash*    (str *path* "classes-md5/")
        *stats*   (str *path* "reduced/")
        zips      #".*.(?i)zip"]
    
    (println "extracting jars from" *apps* "into" *jars*)
    #_(time (extract-jars *apps* (skip-files-in-dir *jars*) *jars*))
    
    (println "extracting manifests from" *jars* "into" *mf*)
    #_(let [now           (date-string)
          mf-dir        *mf*
          output-dir    (str mf-dir now)
          skip?         (skip-files-in-archives (find-files mf-dir zips))
          num-extracted (time (extract-android-manifests *apps* skip? output-dir))] 
      (archive/copy-to-zip (file mf-dir (str now ".zip")) output-dir true)
      num-extracted)
  
    (println "extracting intents from" *jars* "into" *intents*)
    (let [output-dir (time (extract-intents *jars* "d:/intents/" #_*intents*))]
      (archive/copy-to-zip (file *intents* (str output-dir ".zip")) (str *intents* output-dir) true))
    
    
    (println "calculating hashes from" *jars* "into" *hash*)
    (let [output-dir (time (hash-zip-contents *jars* *hash*))]
      (archive/copy-to-zip (file *hash* (str output-dir ".zip")) (str *hash* output-dir) true))
    
    
    #_(println "writing output csv into" *stats* "...") 
    #_(let [class-lookup (c/build-name-classes-fn *jars*)
          apps (load-apps *mf* *intents*)]
      (do
        (c/save-sizes-csv (str *stats* "sizes-" (date-string) ".csv") apps *apps*)
        (c/save-to-csv (str *stats* "deps-" (date-string) ".csv") apps class-lookup)))
    
    (println "Done.")))


(defn load-apps [manifests-dir intents-dir]
  (c/join-intents 
    (pmapcat mf/load-apps-from-zip (find-files manifests-dir #".*.(?i)zip"))
    (reduce merge (map intents/load-intents-from-zip (find-files intents-dir #".*.(?i)zip")))))

(defn extract-real-inter-apps-actions 
  "For every app find the names of all actions that are called via implicit intents 
- that are not defined within the apps' manifest and
- that are not defined in the android SDK documentation and
- that are not defined in any intent filter of any google core app."
  [apps]
  (let [inter-apps-actions (map (juxt :name :package c/external-implicit-intent-actions) apps)
        remove-android-actions (fn [[_ _ actions :as row]] (assoc row 2 (remove android-specific? actions)))
        inter-wo-sdk (map remove-android-actions inter-apps-actions)]
    inter-wo-sdk))


(comment
  
  (binding [*path* "e:/android/"]
    (-main)))