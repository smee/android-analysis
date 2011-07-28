(ns android.analysis.core
  (:use 
    android.tools.serialization
    android.tools.util
    [android.market.download :as download]
    [clojure.contrib.io :only (with-out-writer file)]
    [clojure.contrib.seq :only (indexed)]
    [clojure.contrib.datalog.util  :only (reverse-map)])
  (:require 
    clojure.set
    [android.analysis.intent :as intents]
    [android.analysis.manifest :as mf]
    [android.tools.archive :as archive]
    [android.analysis.hash :as hash]))

(defn clean-app-names [manifests]
  (for [mf manifests]
    (assoc mf :name ((comp mf/extract-app-name :name) mf))))

(defn join-intents 
  "Add intents to each app."
  [apps intents]
  (for [{n :name :as a} apps]
    (assoc a :intents (intents n))))

(defn implicit-called-intent-actions [app]
  (->> app :intents :called vals (apply concat) (remove :explicit?) (map :action) (remove nil?) set))


(defn explicit-called-intent-classes [app]
  (->> app :intents :called vals (apply concat) (filter :explicit?) (map :class) (remove nil?) (map intents/normalize-classname) set))

(defn exported-intent-classes [app]
  (->> app mf/explicit-components (map :class) set))

(defn defined-intent-classes [app]
  (->> app mf/components (map :class) set))

(defn- remove-dot-class [s]
  (subs s 0 (- (count s) 6)))

(defn external-implicit-intent-actions 
  "Construct sets of action strings that get used within implicit intents that can't 
be targeted at a component within the same app (because no intent filter uses that action)."
  [app]
  (clojure.set/difference 
    (implicit-called-intent-actions app) 
    (mf/intent-filter-actions app)))

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
          ;; implicit intent calls per app, only if it looks like the call goes to another app
          (p app "unit-dependent_capabilities" external-implicit-intent-actions false)))
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

(defn package-of-class 
  "extract package of class file name , e.g. (package-of-class \"a/b/c/d.class\") returns
\"a/b/c\""
  [clz]
  (let [idx (.lastIndexOf clz "/")]
    (if (not= -1 idx) (subs clz 0 idx) "")))

(defn hashes-per-package 
  "Takes map of class file names to md5 hashes, returns map of packages to set of md5
hashes of the files in that package."
  [hashes]
  (let [hpp (->> hashes
              reverse-map
              (group-by (comp package-of-class second))
              (map-values (comp (partial into (sorted-set)) (partial map first))))]
    hpp))

(defn not-distinct
  "Returns a lazy sequence of the elements of coll that have duplicates"
  {:added "1.0"}
  [coll]
    (let [step (fn step [xs seen]
                   (lazy-seq
                    ((fn [[f :as xs] seen]
                      (when-let [s (seq xs)]
                        (if ((complement contains?) seen f) 
                          (recur (rest s) (conj seen f))
                          (cons f (step (rest s) seen)))))
                     xs seen)))]
      (distinct (step coll #{}))))

(defn android-libraries [hashes]
  (let [hpp (pmap #(->> % second hashes-per-package (map-values hash/md5-of)) hashes)
        duplicates (not-distinct (apply concat hpp))]
    duplicates))

(defn discard-path-start [file n]
  (let [name (-> file str (clojure.string/replace \\ \/))
        parts (clojure.string/split name #"/")]
    (clojure.string/join "/" (drop n parts))))
;;;;;;;;;;;;;;;;;; misc

(defn record2maps [apps]
  (->> apps
    ;; records in :components
    (map #(update-in % [:components] (partial map (partial into {}))))
    ;; app records
    (map (partial into {}))))

(comment
  ;; use parallel function invocations
    (def apps (apply join-intents 
                (pvalues 
                  (-> "z:/reduced/manifests-20110330.zip" mf/load-apps-from-zip mf/unique-apps)
                  (intents/load-intents-zip "z:/reduced/intents-20110330.zip"))))
    (def apps (deserialize "d:/android/apps-58k"))
    (def class-lookup (build-name-classes-fn "e:/android/jars"))
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
                      (mf/unique-apps (pmapcat mf/load-apps-from-zip (find-files "g:/android/manifests" #".*\.zip")))
                      (apply merge (pmap (comp (partial into {}) intents/load-intents-zip) (find-files "g:/android/intents" #".*\.zip")))))]
    (save-sizes-csv (str "e:/android/reduced/" (date-string) ".csv") apps "e:/android/original"))

  (def apps (apply join-intents 
                    (pvalues 
                      
                      (intents/load-intents-zip "d:/android/reduced/intents-20110504.zip"))))
  
  
  )

(comment
  (let [hashes (pmap #(list (.getName %) (deserialize %)) (find-files "z:/classes-md5/" #".*\d{4}\d*"))
        libs (android-libraries hashes)
        ]
    (serialize "z:/reduced/identified-libs" libs)
    )
  (def src (find-files "d:/android/sample/src" #".*java"))
  (def todelete (filter #(starts-with-any libs (discard-path-start % 5)) src))
  (dorun (map #(let [newfile (java.io.File. (str "d:/android/sample/src-libs/" (discard-path-start (.getPath %) 4)))]
                 (clojure.java.io/make-parents newfile)
                 (clojure.java.io/copy % newfile)) todelete))
  )

