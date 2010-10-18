(ns android-manifest.core
  (:use [clojure.xml :as xml]
        [clojure.contrib.zip-filter.xml :as zip]
        android-manifest.lucene
        android-manifest.serialization)
  (:import java.io.File))

(defn files-in [dir]
  (for [file (seq (.listFiles (File. dir)))]
    file))

(defn find-file [dirpath pattern]
  "Traverse directory dirpath depth first, return all files matching
the regular expression pattern"
  (for [file (-> dirpath File. file-seq) 
        :when (re-matches pattern (.getName file))]
    file))

(defn is-action? [element]
  (= :action (:tag element)))

(defn get-actions [xml]
  (distinct
    (filter identity
      (map 
        #(-> % :attrs :android:name)
        (filter is-action? xml))))) 



(defn create-non-android-action-map [xml]
  (let [package-name        (-> xml first :attrs :package)
        version             (-> xml first :attrs :android:versionName)
        all-actions         (get-actions xml)
        non-android-actions (filter #(and 
                                       (not (.startsWith % "android.")) 
                                       (not (.startsWith % "com.android."))
                                       (not (.startsWith % "com.google.android."))) all-actions)]
    (hash-map 
      :package package-name
      :version version
      :actions (into #{} non-android-actions))))

 (defn extract-path-part [idx path]
   (let [paths (.split path "\\\\")
         app-name (nth paths idx)]
     app-name))
 
(defn shorten-path [{r :references-from p :path :as m}] 
   "Extract application name from references pathes."
   (assoc m
     ;; my path is d:/android/decompiled/site-name/app-name
     :path (extract-path-part 4 p)))
     
 
(defn search-action-references [actions lucene-index-dir]
  (into {}
    (for [a actions]
      [a (distinct 
           (map 
             (partial extract-path-part 8) 
             (search-lucene lucene-index-dir a)))])))

(defn find-all-references [manifest-files lucene-index-dir]
  "Search the lucene index saved at lucene-index-dir for action references
   defined in any manifest stored under the manifest-dir.
   Returns a map with keys :package (package name of the manifest), :actions (non-android action strings) and
   :references-from (paths of smali files containing any of these actions)."
  (let
    [actions-map           (map #(create-non-android-action-map (xml-seq (xml/parse %))) manifest-files)
     path-actions-map      (map #(assoc %2 :path (.getParent %1)) manifest-files actions-map)]
    (for [{actions :actions :as m} path-actions-map]
      (shorten-path
        (assoc m :references-from (search-action-references actions lucene-index-dir))))))

  
(defn change-values [m f]
  "Change all map values by applying f to each one."
  (into {} (for [[k v] m] [k (f v)])))

(defn remove-empty-values [m]
  (into {} (for [[k v] m :when (not (empty? v))] [k v])))

 (defn foreign-refs-only [maps]
   "Remove all potentially external action references, if they came from
    the application itself."
   (for [{r :references-from p :path :as m} maps]
     (assoc m :references-from (remove-empty-values (change-values r (partial filter #(not= % p)))))))
 
 
 
(comment
   
  (find-all-references "d:/android/decompiled" "d:/android/lucene-index")

;; or
(set! *print-length* 15)
 (def app-sources "D:/android/decompiled")
 (def index-dir "D:/android/lucene-index")
 
 ;; create sequence of maps with :path, :actions, :references-from
 (def r (foreign-refs-only         
            (find-all-references (find-file android-source-dir #".*AndroidManifest.xml") index-dir)))
 ;; or
 (def r (foreign-refs-only         
            (find-all-references (map #(File. %) (deserialize "d:/android/all-manifests")) index-dir)))
 
 
)
