(ns android-manifest.core
  (:use [clojure.xml :as xml]
        [clojure.contrib.zip-filter.xml :as zip]
        [clojure.set :only [intersection]]
        [clojure.pprint :only (pprint)]
        android-manifest.lucene
        android-manifest.serialization
        android-manifest.util)
  (:import java.io.File))


(defrecord Android-App [name version package actions references-from])

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



(defn load-android-app [manifest-file]
  (let [xml                 (xml-seq (xml/parse manifest-file))
        app-name            (-> manifest-file .getParentFile .getName)
        package-name        (-> xml first :attrs :package)
        version             (-> xml first :attrs :android:versionName)
        all-actions         (get-actions xml)
        non-android-actions (into #{} (filter #(and 
                                                 (not (.startsWith % "android.")) 
                                                 (not (.startsWith % "com.android."))
                                                 (not (.startsWith % "com.google.android."))) all-actions))]
    (Android-App. 
      app-name 
      version 
      package-name
      non-android-actions
      {})))

 (defn extract-path-part [idx path]
   (let [paths (.split path "\\\\")
         app-name (nth paths idx)]
     app-name))
 
 
 
(defn search-action-references [actions lucene-index-dir avail-app-names]
  "Create map of action names to a set of app names that reference this action.
  Ignores all found app names that are not in avail-app-names."
  (into {}
    (for [a actions]
      [a (intersection 
           avail-app-names 
           (set 
             (map 
               ;; my index was created on a directory hierarchy where the app was at depth 8
               (partial extract-path-part 8) 
               (search-lucene lucene-index-dir a))))])))


(defn find-all-references [manifest-files lucene-index-dir]
  "Search the lucene index saved at lucene-index-dir for action references
   defined in any manifest stored under the manifest-dir.
   Returns a map with keys :package (package name of the manifest), :actions (non-android action strings) and
   :references-from (paths of smali files containing any of these actions)."
  (let
    [android-apps (map load-android-app manifest-files)
     ;; sort by version descending     
     sorted-apps  (reverse (sort-by :version android-apps))
     ;; filter all apps where version and path are equals
     reduced-apps (distinct-fn sorted-apps :package)
     avail-app-names (set (map :name reduced-apps))]
    ;; search for external references by querying the lucene index
    (filter #(contains? avail-app-names (:name %))
      (for [{actions :actions :as m} reduced-apps]
        (assoc m :references-from (search-action-references actions lucene-index-dir avail-app-names))))))

  

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
