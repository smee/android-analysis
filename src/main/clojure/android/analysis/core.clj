(ns android.analysis.core
  (:use 
    [clojure.contrib.zip-filter.xml :only (xml-> xml1-> attr)]
    [clojure.java.io :only (input-stream)]
    android.market.archive
    android-manifest.util
    android-manifest.serialization)
  (:require
    [clojure.contrib.zip-filter :as zf]
    [clojure.contrib.zip-filter.xml :as zfx]
    [clojure.zip :as zip]
    [clojure.xml :as xml]))

(defrecord Intent-Filter [name type filters])
(defrecord Android-App [name version package sdk-version filters])


(defn- find-names-of
  "Find all android components, for example activities or services from 
androidmanifest.xml files using zipper traversals."
  ([xml type]
    (let [package-name (xml1-> xml (attr :package))
          ;; find all activities, services, receivers that have at least one intent filter
          components (xml-> xml :application zf/children #(-> % zip/node :tag #{:activity :receiver :service} ))]
    (into #{} 
      (xml-> components :intent-filter :action (attr :android:name))))))


(defn load-android-manifest [app-name manifest]
  "Parse android app manifest."
  (let [x             (zip/xml-zip (xml/parse (input-stream manifest)))
        package-name  (xml1-> x (attr :package))
        version       (xml1-> x (attr :android:versionCode))
        sdk-version   (or (xml1-> x :uses-sdk (attr :android:minSdkVersion)) "0")
        actions       (find-names-of x :activity)
        services      (find-names-of x :service)
        receivers     (find-names-of x :receiver)]
    (Android-App. 
      app-name 
      version 
      package-name
      sdk-version
      nil)))

(defn load-apps-from-disk 
  "Parse android app manifest xml files."
  [manifest-files]
  (pmap 
    (fn [f]
      (let [app-name (-> f .getParentFile .getName)]
        (load-android-manifest app-name f)))
    manifest-files))

(defn load-apps-from-zip
  "Parse android app manifest files within a zip archive."
  [zip-file]
  (process-entries zip-file load-android-manifest ".*AndroidManifest.xml"))
  
(defn unique-apps 
  "Sort by descending version, filter all apps where version and path are equals. Should result
in loading android apps without duplicates (same package, lower versions)."
  [apps]
  (distinct-by :package 
    (reverse 
      (sort-by :version apps)))) 


(defn find-manifests 
  "Find all AndroidManifest.xml files in any subdirectory of dir."
  [dir]
  (find-file dir #".*AndroidManifest.xml"))


(defn- process-intent-calls 
  "Load intent calling data."
  [entry-name arr]
  (let [[from to] (take-last 2 (keep-indexed #(when (= \/ %2) %1) entry-name))
        app-name  (subs entry-name (inc from) to)]
    (hash-map app-name (deserialize arr))))


(defn called-intents [m]
  (mapcat :called (vals m)))


(defn- x-of [m x]
  (let [ci (called-intents m)]
    (->> ci (map x) distinct)))


(defn actions-of [m]
  (x-of m :action))


(defn uris-of [m]
  (x-of m :uri))


(defn mimetypes-of [m]
  (x-of m :mimetype))


(defn uri-schemes-of [m]
  (->> m uris-of (filter (partial some #{\:})) (map #(subs % 0 (.indexOf % ":"))) distinct))
  

(defn- intent-stats 
  [intents]
  (let [explicits (filter :explicit? intents)
        implicits (remove :explicit? intents)]
    (println "#explicit intents:" (count explicits))
    (println "#implicit intents:" (count implicits))
    (let [valid-implicit-calls (filter #(and (contains? % :action) (or (contains? % :categories) (contains? % :data) (contains? % :uri))) implicits)]
      (println "implicit intents with action + (category|data|uri):" (count valid-implicit-calls)))))


(defn intent-call-stats 
  "Show some statistics abount intent calling data."
  [m]
  (intent-stats (called-intents m)))


(defn intent-query-stats 
  "Show some statistics abount intent querying data."
  [m]
  (intent-stats (mapcat :queried (vals m))))




(comment
  (def manifest (extract-entry "d:/android/reduced/android-20101127.zip" "android/TOOLS/-1119349709413775354/AndroidManifest.xml"))
  (def x (zip/xml-zip (xml/parse (input-stream manifest))))
  (identity (xml-> x :application zf/descendants :intent-filter zip/up zip/node #(:tag %)))
  
  
    (def x (reduce merge (process-entries "d:/Projekte/Thorsten/waterloo/intents.zip" process-intent-calls #".*clj")))
    (intent-call-stats x)
    (intent-query-stats x)
  )

