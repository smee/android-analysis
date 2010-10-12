(ns android-manifest.core
  (:use [clojure.xml :as xml]
        [clojure.contrib.zip-filter.xml :as zip]
        android-manifest.lucene))

(defn files-in [dir]
  (for [file (seq (.listFiles (java.io.File. dir)))]
    file))

(defn parse-xmls [dir]
  (for [file (files-in dir)]
    (xml-seq (xml/parse file))))

(defn is-action? [element]
  (= :action (:tag element)))

(defn get-actions [xml]
  (filter (comp not nil?)
    (map 
      #(-> % :attrs :android:name)
      (filter is-action? xml)))) 


(comment
  (set! *print-length* 15))

(defn create-non-android-action-map [xml]
  (let [package-name        (-> xml first :attrs :package)
        all-actions         (into #{} (get-actions xml))
        non-android-actions (filter #(and (not (.startsWith % "android.")) (not (.startsWith % "com.android."))) all-actions)]
    (hash-map :package package-name :actions (into #{} non-android-actions))))

(defn load-manifests [dir]
  (map create-non-android-action-map (parse-xmls dir)))

(defn filter-non-empty-actions [maps]
  (filter (fn [{actions :actions}] (not (empty? actions))) maps))


(defn find-all-references [manifest-dir lucene-index-dir]
  "Search the lucene index saved at lucene-index-dir for action references
defined in any manifest stored under the manifest-dir.
Returns a map with keys :package (package name of the manifest), :actions (non-android action strings) and
:references-from (paths of smali files containing any of these actions)."
  (let
    [actions-map           (load-manifests manifest-dir)
     non-emtpy-actions-map (filter-non-empty-actions actions-map)]
    (for [{actions :actions package :package } non-emtpy-actions-map]
      (hash-map 
        :package package 
        :actions actions 
        :references-from (mapcat #(search-lucene lucene-index-dir %) actions)))))

(comment
  (find-all-references "d:/android/manifests" "d:/android/lucene-index"))

