(ns android.analysis.manifest
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
    [clojure.xml :as xml]
    clojure.walk)
  (:import
    org.xmlpull.v1.XmlPullParser
    ))

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
  (if (= \. (first name))
    (str pname name)
    name))

;;  Represent an intent filter.
(defrecord Intent-Filter [class type actions categories datas])

(defn- create-intent-filter [cls type i-filter]
  (Intent-Filter. cls type
    (xml-> i-filter :action (attr :name))
    (xml-> i-filter :category (attr :name))
    (for [data (xml-> i-filter :data zip/node #(:attrs %))]
      data)))
  

(defn- find-android-components
  "Find all android components, for example activities or services from 
androidmanifest.xml files using zipper traversals."
  [x]
  (let [package-name (xml1-> x (attr :package))
        components (xml-> x :application zf/children [#(-> % zip/node :tag #{:activity :receiver :service} )])]
    (for [c components]
      (let [cls  (activity-class package-name (xml1-> c (attr :name)))
            type (xml1-> c zip/node #(:tag %))]
        (for [i-filter (xml-> c zf/children :intent-filter)]
          (create-intent-filter cls type i-filter))))))
  
;;  Datastructure to hold relevant infos about an android application.
(defrecord Android-App [name version package sdk-version filters])

(defn load-android-manifest [app-name manifest]
  "Parse android app manifest."
  (let [doc           (remove-xml-namespaces (xml/parse (input-stream manifest)))
        x             (zip/xml-zip doc)
        package-name  (xml1-> x (attr :package))
        version       (xml1-> x (attr :versionCode))
        sdk-version   (or (xml1-> x :uses-sdk (attr :minSdkVersion)) "0")
        components    (find-android-components x)]
    (Android-App. app-name version package-name sdk-version components)))

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


(defn- find-manifests 
  "Find all AndroidManifest.xml files in any subdirectory of dir."
  [dir]
  (find-file dir #".*AndroidManifest.xml"))


(defn- pull-step 
  "lazy sequence of tag names."
  [xpp]
  (let [step (fn [xpp]
               (condp = (.next xpp)
                 XmlPullParser/START_TAG
                 (cons (keyword (.getName xpp))
                   (pull-step xpp))
                 XmlPullParser/END_TAG
                 (cons (keyword (str "/" (.getName xpp)))
                   (pull-step xpp))
                 XmlPullParser/TEXT
                 (let [text (.trim (.getText xpp))]
                   (if (empty? text)
                     (recur xpp)
                     (cons text
                       (pull-step xpp))))
                 nil))]
    (lazy-seq (step xpp))))

(defn- init-parser [filename]
  (doto (org.kxml2.io.KXmlParser.) 
    (.setFeature XmlPullParser/FEATURE_PROCESS_NAMESPACES true)
    (.setInput (clojure.java.io/reader filename))))

(defn create-intent-filters 
  "Extract all intent-filter definitions and create instances of android.IntentFilter.
Use it's .match method to decide whether an intent matches a filter."
  [filename]
  (let [parser (init-parser filename)
        tags   (pull-step parser)]
    
    (for [i-f (filter #(= :intent-filter %) tags)]
      (doto (android.IntentFilter.) 
        (.readFromXml parser)))))

(comment
  (def manifest (extract-entry "d:/android/reduced/android-20101127.zip" "android/TOOLS/-1119349709413775354/AndroidManifest.xml"))
  (def x (zip/xml-zip (xml/parse (input-stream manifest))))
  (identity (xml-> x :application zf/descendants :intent-filter zip/up zip/node #(:tag %)))

  (create-intent-filters "d:/AndroidManifest.xml")

  
  )