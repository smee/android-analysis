(ns android.analysis.core
  (:use 
    android-manifest.serialization
    android-manifest.util
    [android.market.download :as download]
    [clojure.contrib.io :only (with-out-writer file)]
    [clojure.contrib.seq :only (indexed)])
  (:require 
    clojure.set
    [android.analysis.intent :as intents]
    [android.analysis.manifest :as mf]
    [android.market.archive :as archive]))

(defn clean-app-names [manifests]
  (for [mf manifests]
    (assoc mf :name ((comp mf/extract-app-name :name) mf))))

(defn join-intents 
  "Add intents to each app."
  [apps intents]
  (for [{n :name :as a} apps]
    (assoc a :intents (intents n))))



(defn explicit-called-intent-classes [app]
  (->> app :intents :called vals (apply concat) (filter :explicit?) (map :class) (remove nil?) (map intents/normalize-classname) set))

(defn exported-intent-classes [app]
  (->> app mf/explicit-components (map :class) set))

(defn defined-intent-classes [app]
  (->> app mf/components (map :class) set))

(defn- remove-dot-class [s]
  (subs s 0 (- (count s) 6)))

(defn app-file 
  "Construct file object of android app that resides in a subdirectory of style download/construct-path-parts"
  [dir id]
  (let [[p1 p2] (download/construct-path-parts id)]
    (file dir p1 p2 id)))

(defn build-name-classes-fn 
  "Build function that looks up the set of classes defined in an android app. 
Operates on the result directory of android.market.process/dex2jar"
  [jars-dir]
  (fn [n] (when-let [f (app-file jars-dir n)]
            (set (map (comp remove-dot-class intents/normalize-classname) 
                      (archive/get-entries f #".*class"))))))

(defn external-explicit-intent-calls 
  ([app] (external-explicit-intent-calls app {}))
  ([app class-lookup-fn]
  (let [diff (clojure.set/difference (explicit-called-intent-classes app) (defined-intent-classes app))]
    (if (empty? diff)
      diff
      (clojure.set/difference diff (class-lookup-fn (:name app)))))))


(defn dep-unit-dependent 
  "TODO: there are some apps that explicitly call activities that are not declared in the manifest. Need to
match them with existing classes...."
  ([apps] (pmap external-explicit-intent-calls apps))
  ([apps class-lookup] (pmap #(external-explicit-intent-calls % class-lookup) apps)))

(defn dep-reverse-unit-depends [apps]
  (map mf/explicit-components apps))

(defn dep-provides [apps]
  (map mf/implicit-components apps))

(defn if-to-name-map 
  "Create a map of unique intent filters of an "
  [app]
  (let [i-f (mf/unique-intent-filters app)
        app-name (:name app)]
    (apply hash-map (interleave i-f (repeat (set (list app-name)))))))

(defn group-intent-filters 
  "Group apps by unique intent filters"
  [apps]
  (apply (partial merge-with clojure.set/union) (map if-to-name-map apps)))

(defn aggregate 
  "Create histogram data by counting elements in the data seq and calculate their frequency"
  [data]
  (->> data
    (map count)
    frequencies
    (into (sorted-map))))

(defn- p [app type f flag]
  (println (str (:name app) \, (count (f app)) \, type \, flag)))

(defn save-to-csv [file apps lookup]
    (with-out-writer file
      (println "id,count,type,capability")
      (doseq [app apps]
        (do
          ;; explicit intent call with classes that are not in the app's manifest
          (p app "unit-dependent_units" #(external-explicit-intent-calls % lookup) false)
          ;; explicitly exported android components per app
          (p app "reverse unit depends" mf/explicit-components false)
          ;; number of intent filters per app
          (p app "unit-provided_capabilities" mf/unique-intent-filters false)
          ;; implicit intent calls per app
          (p app "unit-dependent_capabilities" (comp distinct intents/called-intents-app :intents) false)))
      ;; apps per unique intent filter
      (doseq [[idx names] (indexed (vals (group-intent-filters apps)))]
        (println (str "cap" idx \, (count names) ",capability-providing_units,true") ))
      ))

(defn save-sizes-csv 
  "id, size in bytes, #all unique called intents, 0, # unique intent filters defined"
  [file apps apps-dir]
  (with-out-writer file
      (println "id,size,count,revcount,provides")
      (doseq [{id :name :as app} apps]
        (do
          ;; explicit intent call with classes that are not in the app's manifest
          (println id 
                   \, 
                   (if-let [f (app-file apps-dir id)] (.length f) -1)
                   \,
                   (-> app :intents intents/called-intents-app distinct count)
                   \,
                   0
                   \,
                   (count (mf/unique-intent-filters app)))))))

;;;;;;;;;;;;;;;;;  find explicit intent dependencies

(defn resolve-explicit-dependencies [apps class-lookup]
  (let [packages (apply sorted-set (map :package apps))
        lookup (into {} (map (juxt :package :name) apps))
        n2p (remove-empty-values 
              (zipmap 
                (map (juxt :name :package) apps) 
                (dep-unit-dependent apps class-lookup)))]
    (remove-empty-values
      (map-values 
        #(remove nil?
           (distinct 
             (for [cls %]
               (when-let [match (starts-with-any packages cls)]
                 (lookup match))))) 
        n2p))))


(defn lcp 
  "Longest common string prefix"
  [^String s1 ^String s2]
  (let [n (min (count s1))]
    (loop [i 0]
      (if (or (not= (.charAt s1 i) (.charAt s2 i)) (= i n))
        (subs s1 0 i)
        (recur (unchecked-inc i)))))) 


(comment
  ;; use parallel function invocations
    (def apps (apply join-intents 
                (pvalues 
                  (-> "z:/reduced/manifests-20110330.zip" mf/load-apps-from-zip mf/unique-apps)
                  (intents/load-intents-zip "z:/reduced/intents-20110330.zip"))))
    (def apps (deserialize "d:/android/apps-58k"))
    (def class-lookup (build-name-classes-fn "z:/jars"))
    (def x (resolve-explicit-dependencies apps class-lookup))
  (spit "d:/android/explicit-deps.dot" (graphviz-test x))
  (aggregate (dep-provides apps))
  (aggregate (dep-unit-depends apps))
  
  
  (save-to-csv (str "z:/reduced/results-" (date-string) ".csv") apps class-lookup)
  
  (aggregate (dep-unit-dependent apps class-lookup))
  
  (external-explicit-intent-calls (nth apps 7049) class-lookup)
  
  (def gif (group-intent-filters apps))
  ;; find intent filter with biggest number of apps defining it
  (apply (partial max-key second) (map-values count gif))
  (filter #(< 800 (second %)) (map-values count gif))
  
  (let [apps (apply join-intents 
                    (pvalues 
                      (-> "z:/manifests" (find-file #".*\d{4}\d*") mf/load-apps-from-disk mf/unique-apps)
                      (intents/load-intents-from-disk "z:/intents")))]
    (save-sizes-csv (str "z:/reduced/" (date-string) ".csv") apps "z:/original"))
  )
