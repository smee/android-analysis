(ns android.analysis.core
  (:use 
    android-manifest.serialization
    android-manifest.util
    [clojure.contrib.io :only (with-out-writer)]
    [clojure.contrib.seq :only (indexed)])
  (:require 
    clojure.set
    [android.analysis.intent :as intents]
    [android.analysis.manifest :as mf]
    [android.market.archive :as archive]))

(defn clean-app-names [manifests]
  (for [mf manifests]
    (assoc mf :name (mf/extract-app-name mf))))

(defn join-intents 
  "Add intents to each app."
  [apps intents]
  (for [{n :name :as a} apps]
    (assoc a :intents (intents n))))

(defn normalize-classname [cn]
  (clojure.string/replace cn \/ \.))

(defn explicit-called-intent-classes [app]
  (->> app :intents :called vals (apply concat) (filter :explicit?) (map :class) (remove nil?) (map normalize-classname) set))

(defn exported-intent-classes [app]
  (->> app mf/explicit-components (map :class) set))

(defn defined-intent-classes [app]
  (->> app mf/components (map :class) set))

(defn- remove-dot-class [s]
  (subs s 0 (- (count s) 6)))

(defn build-name-classes-fn 
  "Build function that looks up the set of classes defined in an android app. Requires a directory
with classes.dex files that are zip archives with class files in it"
  [jars-dir]
  (let [fs (find-file jars-dir #".*classes.dex")
        name-file (into {} (pmap (fn [f] (vector (intents/extract-app-name (.getAbsolutePath f) \\) f)) fs))]
    (fn [n] (when-let [f (name-file n)]
              (set (map (comp remove-dot-class normalize-classname) (archive/get-entries f #".*class")))))))

(defn external-explicit-intent-calls 
  ([app] (external-explicit-intent-calls app {}))
  ([app name-classes]
  (let [diff (clojure.set/difference (explicit-called-intent-classes app) (defined-intent-classes app))]
    (if (empty? diff)
      diff
      (clojure.set/difference diff (name-classes (:name app)))))))


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
  (apply (partial merge-with conj) (map if-to-name-map apps)))

(defn aggregate 
  "Create histogram data by counting elements in the data seq and calculate their frequency"
  [data]
  (->> data
    (map count)
    frequencies
    (into (sorted-map))))

(defn- p [app type f flag]
  (println (str (:name app) \, (count (f app)) \, type \, flag)))

(defn save-to-csv [file apps]
  (with-out-writer file
    (println "id,count,type,capability")
    (doseq [app apps]
      (do
        ;; explicit intent call with classes that are not in the app's manifest
        (p app "unit-dependent_units" #(external-explicit-intent-calls % (memoize (build-name-classes-fn "d:/android/apps/jars"))) false)
        ;; explicitly exported android components per app
        ;(p app "reverse unit depends" mf/explicit-components false)
        ;; number of intent filters per app
        (p app "unit-dependent_units" mf/implicit-components true)
        ;; implicit intent calls per app
        (p app "unit-dependent_capabilities" intents/called-implicit-intents true)))
        ))
;; apps per unique intent filter
    (doseq [[idx names] (indexed (vals (group-intent-filters apps)))]
      (println (str "cap" idx \, (count names) ",capability-providing_unit,true") ))))


(comment
  ;; use parallel function invocations
    (def apps (join-intents 
                (-> "d:/android/reduced/android-20101127.zip" mf/load-apps-from-zip mf/unique-apps clean-app-names)
                (intents/load-intents-zip "d:/android/apps/intents2.zip") 
              #_(deserialize "d:/android/allintents2.clj")))
  
  (aggregate (dep-provides apps))
  (aggregate (dep-unit-depends apps))
  
  (save-to-csv "d:/android/android-results.csv" apps)
  
  (def class-lookup (memoize (build-name-classes-fn "d:/android/apps/jars")))
  
  (aggregate (dep-unit-dependent apps class-lookup))
  
  (external-explicit-intent-calls (nth apps 7049) class-lookup)
  
  )
