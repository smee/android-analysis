(ns android.analysis.manifest
  (:use 
    [clojure.contrib.zip-filter.xml :only (xml-> xml1-> attr)]
    [clojure.java.io :only (input-stream)]
    android-manifest.util
    android-manifest.serialization)
  (:require
    [android.market.archive :as archive]
    [clojure.contrib.zip-filter :as zf]
    [clojure.contrib.zip-filter.xml :as zfx]
    [clojure.zip :as zip]
    [clojure.xml :as xml]
    clojure.walk)
)

(defn- trim-ns 
  "Remove substring from keyword up to the first occurance of the character :"
  [kw] 
  (let [kw-string (name kw)
        fixed-kw (if (some #{\:} kw-string) (subs kw-string (inc (.indexOf kw-string ":"))) kw-string)]
    (keyword fixed-kw)))

(defn- remove-xml-namespaces 
  "Ignore all xml namespace prefixes by removing them recursively from all map keys."
  [m]
  (let [f (fn [[k v]]  (if (keyword? k) [(trim-ns k) v] [k v]))]
    (clojure.walk/postwalk 
      (fn [x] (if (map? x) (into {} (map f x)) x)) 
      m)))

(defn- activity-class 
  "Append package name and activity name iff the name starts with a dot."
  [pname name]
  (cond
    (= \. (first name)) (str pname name)
    (not-any? #{\.} name) (str pname \. name)
    :else name))

#_(defn- create-intent-filter 
  "Reuse android's implementation of IntentFilter from their source."
  [m]
  (let [i-f (android.IntentFilter.)]
    (when-let [actions (:actions m)]
      (doseq [a actions] (.addAction i-f a)))
    (when-let [categories (:categories m)]
      (doseq [c categories] (.addCategory i-f c)))
    (when-let [data (:data m)]
      (doseq [d data]
        (do
          (when-let [host (:host d)]
            (.addDataAuthority i-f host (:port d)))
          (when-let [scheme (:scheme d)]
            (.addDataScheme i-f scheme))
          (when-let [mimetype (:mimeType d)]
          (.addDataType i-f mimetype))
          (when-let [p (:path d)]
            (.addDataPath i-f p android.PatternMatcher/PATTERN_LITERAL))
          (when-let [p (:pathPrefix d)]
            (.addDataPath i-f p android.PatternMatcher/PATTERN_PREFIX))
          (when-let [p (:pathPattern d)]
            (.addDataPath i-f p android.PatternMatcher/PATTERN_SIMPLE_GLOB)))))
    i-f))
    

;;  Represent an android component (activity, service, broadcastreceiver).
(defrecord Android-Component [class type filters exported?])

(defn- create-components [cls type i-filters exported?]
  (let [data (for [i-filter i-filters]
               (hash-map 
                 :actions (xml-> i-filter :action (attr :name))
                 :categories (xml-> i-filter :category (attr :name))
                 :data (for [data (xml-> i-filter :data zip/node #(:attrs %))] data)))]
    (Android-Component. cls type data exported?))) 
      
  

(defn- find-android-components
  "Find all android components, for example activities or services from 
androidmanifest.xml files using zipper traversals."
  [x]
  (let [package-name (xml1-> x (attr :package))
        components (xml-> x :application zf/children [#(-> % zip/node :tag #{:activity :receiver :service} )])]
    (for [c components]
      (let [cls       (activity-class package-name (xml1-> c (attr :name)))
            type      (xml1-> c zip/node #(:tag %))
            i-filters (xml-> c zf/children :intent-filter)
            exported? (Boolean/valueOf (xml1-> c (attr :exported)))]
        (create-components cls type i-filters exported?)))))
  

(defn extract-app-name [name]
  (last (.split name "/")))

;;  Datastructure to hold relevant infos about an android application.
(defrecord Android-App [name version package sdk-version shared-uid components])

(defn load-android-manifest [app-name manifest]
  "Parse android app manifest."
  (let [doc           (remove-xml-namespaces (xml/parse (input-stream manifest)))
        x             (zip/xml-zip doc)
        package-name  (xml1-> x (attr :package))
        version       (xml1-> x (attr :versionCode))
        shared-uid    (xml1-> x (attr :sharedUserId))
        sdk-version   (or (xml1-> x :uses-sdk (attr :minSdkVersion)) "0")
        components    (find-android-components x)]
    (Android-App. (extract-app-name app-name) version package-name sdk-version shared-uid components)))

(defn load-apps-from-disk 
  "Parse android app manifest xml files."
  [manifest-files]
  (pmap 
    (fn [f]
      (let [app-name (-> f .getName)]
        (load-android-manifest app-name f)))
    manifest-files))

(defn load-apps-from-zip
  "Parse android app manifest files within a zip archive. Ignores every exception."
  [zip-file]
  (filter identity
          (archive/process-entries zip-file load-android-manifest #".*\d\d\d\d(\d)+")))
  
(defn unique-apps 
  "Sort by descending version, filter all apps where version and path are equals. Should result
in loading android apps without duplicates (same package, lower versions)."
  [apps]
  (distinct-by :package 
    (reverse 
      (sort-by :version apps)))) 

(defn exported? [component]
  (:exported? component))

(defn components [app]
  (:components app))

(defn intent-filters [component]
  (:filters component))

(defn unique-intent-filters [app]
  (distinct (mapcat intent-filters (components app))))

(defn- find-manifests
  "Find all AndroidManifest.xml files in any subdirectory of dir."
  [dir]
  (find-file dir #".*AndroidManifest.xml"))

(defn extract-intent-filters [app]
  (mapcat :filters (components app)))

(defn intent-filter-actions [app]
  (set ( mapcat :actions (extract-intent-filters app))))

(defn exported-components 
  "Filter all exported android components. Exported means either the explicit export flag or a
nonempty intent-filter seq."
  [{cs :components}]
  (filter #(or (exported? %) (not-empty (intent-filters %))) cs))

(defn explicit-components [{cs :components}]
  (filter exported? cs))

(defn implicit-components [{cs :components}]
  (filter #(not-empty (intent-filters %)) cs))


(defn fan-in [apps]
   (into (sorted-set) (frequencies (map count (exported-components apps)))))


(defn names-of-old-app-versions 
  "Find the names of all apps that have a newer version. Returns a set."
  [apps]
  (let [name-of (comp extract-app-name :name)
        unique-names-all (map name-of apps)
        unique-names (map name-of (unique-apps apps))]
    (clojure.set/difference (set unique-names-all) (set unique-names))))



(comment
  (def manifest (extract-entry "d:/android/reduced/android-20101127.zip" "android/TOOLS/-1119349709413775354/AndroidManifest.xml"))
  (def x (zip/xml-zip (xml/parse (input-stream manifest))))
  (identity (xml-> x :application zf/descendants :intent-filter zip/up zip/node #(:tag %)))

  (create-intent-filters "d:/AndroidManifest.xml")
  (def app (first (load-apps-from-disk [(java.io.File. "d:/AndroidManifest.xml")])))
  (some #(filter exported-components  %) mf)
  
  (def mf (unique-apps (load-apps-from-zip "d:/android/reduced/android-20101127.zip")))
  (def mf (deserialize "d:/android/parsed-manifests.clj"))

  )