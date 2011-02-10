(ns android.analysis.core
  (:use 
    android.analysis.manifest
    android.analysis.intent
    android-manifest.serialization
    [clojure.contrib.io :only (with-out-writer)])
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
        (p app "unit depends" external-explicit-intent-calls false)
        ;; explicitly exported android components per app
        (p app "reverse unit depends" explicit-components false)
        ;; number of intent filters per app
        (p app "provides" implicit-components true)
        ;; implicit intent calls per app
        (p app "capability depends" called-implicit-intents true)))))


(comment
  (def apps (join-intents 
              (-> "d:/android/reduced/android-20101127.zip" load-apps-from-zip unique-apps clean-app-names)
              (deserialize "d:/android/allintents2.clj")) )
  
  (aggregate (dep-provides apps))
  (aggregate (dep-unit-depends apps))
  (aggregate (dep-reverse-unit-dependent apps))
  
  (save-to-csv "d:/android/android-results.csv" apps)
  
  )
