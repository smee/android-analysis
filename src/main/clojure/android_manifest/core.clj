(ns android-manifest.core
  (:use [clojure.xml :as xml]
        ;;[clojure.contrib.zip-filter.xml :as zip]
        [clojure.set :only [intersection]]
        [clojure.pprint :only (pprint)]
        android-manifest.lucene
        android-manifest.serialization
        android-manifest.util)
  (:import java.io.File))


(defrecord Android-App [name version package actions categories action-refs-from category-refs])

(defn find-file [dirpath pattern]
  "Traverse directory dirpath depth first, return all files matching
the regular expression pattern"
  (for [file (-> dirpath File. file-seq) 
        :when (re-matches pattern (.getName file))]
    file))


(defn collect-android:name [xml tag]
  (distinct
    (filter identity
      (map 
        #(-> % :attrs :android:name)
        (filter #(= tag (:tag element)) xml))))) 

(defn android-specific? [s]
  (or
     (.startsWith s "android.") 
     (.startsWith s "com.android.")
     (.startsWith s "com.google.android.")))

(defn load-android-app [manifest-file]
  (let [xml                 (xml-seq (xml/parse manifest-file))
        app-name            (-> manifest-file .getParentFile .getName)
        package-name        (-> xml first :attrs :package)
        version             (-> xml first :attrs :android:versionName)
        all-actions         (collect-android:name xml :action)
        non-android-actions (into #{} (filter (comp not android-specific?) all-actions))
        all-categories      (collect-android:name xml :category)
        non-android-categories (into #{} (filter (comp not android-specific?) all-categories))]
    (Android-App. 
      app-name 
      version 
      package-name
      non-android-actions
      non-android-categories
      {}
      {})))

 (defn extract-path-part [idx path]
   (let [paths (.split path "\\\\")
         app-name (nth paths idx)]
     app-name))
 
 
 
(defn query-references [strings lucene-index-dir avail-app-names]
  "Create map of action names to a set of app names that reference this action.
  Ignores all found app names that are not in avail-app-names."
  (into {}
    (for [s strings]
      [s (intersection 
           avail-app-names 
           (set 
             (map 
               ;; my index was created on a directory hierarchy where the app was at depth 8
               (partial extract-path-part 8) 
               (search-lucene lucene-index-dir s))))])))


(defn find-all-references [manifest-files lucene-index-dir]
  "Search the lucene index saved at lucene-index-dir for action references
   defined in any manifest stored under the manifest-dir.
   Returns a map with keys :package (package name of the manifest), :actions (non-android action strings) and
   :references-from (paths of smali files containing any of these actions)."
  (let
    [android-apps    (map load-android-app manifest-files)
     reduced-apps    (distinct-by :package (reverse (sort-by :version android-apps))) ;; sort by version descending, filter all apps where version and path are equals
     avail-app-names (set (map :name reduced-apps))]
    ;; search for external references by querying the lucene index
    (for [{actions :actions categories :categories :as app} reduced-apps]
      (assoc app 
        :action-refs   (query-references actions    lucene-index-dir avail-app-names)
        :category-refs (query-references categories lucene-index-dir avail-app-names)))))

  

 (defn foreign-refs-only [maps]
   "Remove all potentially external action references, if they came from
    the application itself."
   (for [{refs :references-from name :name :as m} maps]
     (assoc m :references-from 
       (remove-empty-values 
         (map-values (partial filter #(not= % name)) refs)))))
 
 
 
(comment
   
  (set! *print-length* 15)
  (def app-sources "D:/android/decompiled")
  (def index-dir "D:/android/lucene-index")
 
  ;; create sequence of maps with :path, :actions, :references-from
 (def r (foreign-refs-only         
          (find-all-references (find-file app-sources #".*AndroidManifest.xml") index-dir)))
 ;; or
 (def r
   (let  [manifest-files (map #(File. %) (deserialize "d:/android/all-manifests"))
          all-refs (find-all-references manifest-files index-dir)]
     (foreign-refs-only all-refs)))

 
 (count r)
 (serialize "results/unique-refs.clj" r)
)

(comment
    (set! *print-length* 15)
  (def apps
    (let  [manifest-files (map #(File. %) (deserialize "d:/android/all-manifests"))
           android-apps   (map load-android-app manifest-files)]
      (distinct (mapcat :categories android-apps))))
  )
