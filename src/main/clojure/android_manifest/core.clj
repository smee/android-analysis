(ns android-manifest.core
  (:use [clojure.xml :as xml]
        ;;[clojure.contrib.zip-filter.xml :as zip]
        [clojure.set :only [intersection]]
        [clojure.pprint :only (pprint)]
        android-manifest.lucene
        android-manifest.serialization
        android-manifest.util
        [android-manifest.scribble :only (filter-included-actions print-findings)])
  (:import java.io.File))


(defrecord Android-App [name version package actions categories action-refs category-refs])

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
        (filter #(= tag (:tag %)) xml))))) 

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
    ;; search parallel for external references by querying the lucene index
    (pmap 
      (fn [{actions :actions categories :categories :as app}]
        (assoc app 
          :action-refs   (query-references actions    lucene-index-dir avail-app-names)
          :category-refs (query-references categories lucene-index-dir avail-app-names))) 
      reduced-apps)))

  

 (defn foreign-refs-only [maps]
   "Remove all potentially external action references, if they came from
    the application itself."
   (for [{refs :action-refs name :name :as m} maps]
     (assoc m :action-refs 
       (remove-empty-values 
         (map-values (partial filter #(not= % name)) refs)))))
 
 
 
(comment
   
  (set! *print-length* 15)
  (def app-sources "D:/android/decompiled")
  (def index-dir "D:/android/lucene-index")
 
 ;; or
 (def real-external-refs
   (let  [manifest-files (map #(File. %) (deserialize "d:/android/all-manifests"))
          all-refs (find-all-references manifest-files index-dir)
          all-apps (foreign-refs-only all-refs)
          real-external-refs (filter #(or (not-empty (remove-empty-values (:category-refs %))) (not-empty (remove-empty-values (:action-refs %)))) (filter-included-actions all-apps))]
     (do 
       (serialize "results/unique-refs.clj" all-apps)
       (print-findings real-external-refs all-apps manifest-files )
       real-external-refs)))
)

(comment
    (set! *print-length* 15)
    (find-file app-sources #".*AndroidManifest.xml")
  )
