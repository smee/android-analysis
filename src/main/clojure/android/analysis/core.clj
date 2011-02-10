(ns android.analysis.core
  (:use 
    android.analysis.manifest
    android.analysis.intent
    android-manifest.serialization
    [clojure.contrib.io :only (with-out-writer)]
    [clojure.contrib.seq :only (indexed)])
  (:require 
    clojure.set))

(defn clean-app-names [manifests]
  (for [mf manifests]
    (assoc mf :name (extract-app-name mf))))

(defn join-intents 
  "Add intents to each app."
  [apps intents]
  (for [{n :name :as a} apps]
    (assoc a :intents (intents n))))

(defn normalize-classname [cn]
  (clojure.string/replace cn #"/" "."))

(defn explicit-called-intent-classes [app]
  (->> app :intents :called vals (apply concat) (filter :explicit?) (map :class) (remove nil?) (map normalize-classname) set))

(defn exported-intent-classes [app]
  (->> app explicit-components (map :class) set))

(defn defined-intent-classes [app]
  (->> app components (map :class) set))

(defn external-explicit-intent-calls [app]
  (clojure.set/difference (explicit-called-intent-classes app) (defined-intent-classes app)))


(defn dep-unit-dependent 
  "TODO: there are some apps that explicitly call activities that are not declared in the manifest. Need to
match them with existing classes...."
  [apps]
  (pmap external-explicit-intent-calls apps))

(defn dep-reverse-unit-depends [apps]
  (map explicit-components apps))

(defn dep-provides [apps]
  (map implicit-components apps))

(defn if-to-name-map 
  "Create a map of unique intent filters of an "
  [app]
  (let [i-f (unique-intent-filters app)
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
    #_(doseq [app apps]
      (do
        ;; explicit intent call with classes that are not in the app's manifest
        (p app "unit depends" external-explicit-intent-calls false)
        ;; explicitly exported android components per app
        (p app "reverse unit depends" explicit-components false)
        ;; number of intent filters per app
        (p app "provides" implicit-components true)
        ;; implicit intent calls per app
        (p app "capability depends" called-implicit-intents true)
        ;; apps per unique intent filter
        ))
    (doseq [[idx names] (indexed (vals (group-intent-filters apps)))]
      (println (str "cap" idx \, (count names) ",capability-providing_unit,true") ))))


(comment
  (def apps (join-intents 
              (-> "d:/android/reduced/android-20101127.zip" load-apps-from-zip unique-apps clean-app-names)
              #_(deserialize "d:/android/allintents2.clj")
              (load-intents-zip "d:/android/apps/intents2.zip")) )
  
  (aggregate (dep-provides apps))
  (aggregate (dep-unit-depends apps))
  (aggregate (dep-reverse-unit-dependent apps))
  
  (save-to-csv "d:/android/android-results.csv" apps)
  
  )
