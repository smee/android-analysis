(ns android-manifest.core
  (:use [clojure.xml :as xml]
        ;;[clojure.contrib.zip-filter.xml :as zip]
        [clojure.set :only [intersection difference union map-invert]]
        [clojure.pprint :only (pprint)]
        [clojure.contrib.seq-utils :only (separate)]
        android-manifest.lucene
        android-manifest.serialization
        android-manifest.util)
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
  (contains? 
     #{"android.intent.action.main" 
       "android.intent.category.launcher"
       "android.intent.category.default"}
     (.toLowerCase s)))

(defn load-android-app [manifest-file]
  (let [xml                 (do #_(println "parsing " manifest-file )(xml-seq (xml/parse manifest-file)))
        app-name            (-> manifest-file .getParentFile .getName)
        package-name        (-> xml first :attrs :package)
        version             (-> xml first :attrs :android:versionName)
        all-actions         (collect-android:name xml :action)
        non-android-actions (into #{} (remove android-specific? all-actions))
        all-categories      (collect-android:name xml :category)
        non-android-categories (into #{} (remove android-specific? all-categories))]
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
               (partial extract-path-part 5) 
               (search-lucene lucene-index-dir s))))])))

(defn load-unique-apps [manifest-files]
  (let [android-apps (map load-android-app manifest-files)]
    (distinct-by :package (reverse (sort-by :version android-apps))) ;; sort by version descending, filter all apps where version and path are equals
    ))

(defn unique-app-names [apps]
  (set (map :name apps)))

(defn find-all-references-less-mem [manifest-files lucene-index-dir avail-app-names]
  (->> manifest-files
    load-unique-apps
    (pmap 
      (fn [{actions :actions categories :categories :as app}]
        (assoc app 
          :action-refs   (query-references actions    lucene-index-dir avail-app-names)
          :category-refs (query-references categories lucene-index-dir avail-app-names)))) 
    ))


(defn find-all-references [manifest-files lucene-index-dir]
  "Search the lucene index saved at lucene-index-dir for action references
   defined in any manifest stored under the manifest-dir.
   Returns a map with keys :package (package name of the manifest), :actions (non-android action strings) and
   :references-from (paths of smali files containing any of these actions)."
  (let
    [reduced-apps (load-unique-apps manifest-files)
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
 
 (defn valid-action? [s]
  (< 1 (count (filter #(= % \.)  s))))
 
 
 (defn filter-references [ref-map name-app-map key]
  "Retain only apps that call an intent that is not defined within their own
   manifest.xml."
  (into {}
    (for [[action dependent-apps] ref-map]
      (let [filtered-refs (remove #(contains? (get-in name-app-map  [% key]) action) dependent-apps)]
        [action filtered-refs]))))

(defn filter-included-actions [r]
  "Retain only apps that call an intent that is not defined within their own
   manifest.xml."
  (let [name-app-map (into {} (map #(hash-map (:name %) %) r))]
    (for [{arefs :action-refs crefs :category-refs :as app} r]
      (assoc app 
        :action-refs   (filter-references arefs name-app-map :actions)
        :category-refs (filter-references crefs name-app-map :categories)))))

(defn find-app [like]
  ""
  (fn [app] (.contains (:name app) like)))




(defn print-findings [real-external-refs all-apps manifest-files]
  (let [openintent-actions    (distinct (filter #(.contains % "openintent") (mapcat #(keys (:action-refs %)) real-external-refs)))
        openintent-categories (distinct (filter #(.contains % "openintent") (mapcat #(keys (:category-refs %)) real-external-refs)))
        action-call-freq      (apply merge-with + (flatten
                                                    (for [m (map :action-refs real-external-refs)]
                                                      (for [[k v] m :when (valid-action? k)]
                                                        {k (count v)}))))
      sorted-freq              (into (sorted-map-by (fn [k1 k2] (compare (get action-call-freq k2) (get action-call-freq k1))))
                                 action-call-freq)]
  (println 
    "# manifests: "                         (count manifest-files)
    "\n# unique Apps: "                     (count all-apps)
    "\n# of externally used actions offered from apps: "    (count (distinct (mapcat #(keys (remove-empty-values (:action-refs %))) real-external-refs)))
    "\n# apps calling external actions: "    (count (distinct (mapcat #(vals (:action-refs %)) real-external-refs)))
    "\n# openintents (actions): "           (count openintent-actions) openintent-actions
    "\n# of categories offered from apps: " (count (distinct (mapcat #(keys (remove-empty-values (:category-refs %))) real-external-refs)))
    "\n# apps calling foreign categories: " (count (distinct (mapcat #(vals (:category-refs %)) real-external-refs)))
    "\n# openintents (category): "          (count openintent-categories) openintent-categories
    "\nMost called actions: "               (take 3 sorted-freq))))

(comment
   
  (set! *print-length* 15)
  (def app-sources "h:/android")
  (def index-dir "h:/lucene-index-all")
 
 ;; or
 (def manifest-files (map #(File. %) (deserialize "d:/android/results/manifest-files-20101102.clj")))
 (def manifest-files (find-file app-sources #".*AndroidManifest.xml"))
 
   (let  [all-refs (find-all-references manifest-files index-dir)
          all-apps (foreign-refs-only all-refs)
          real-external-refs (filter #(or 
                                        (not-empty (remove-empty-values (:category-refs %))) 
                                        (not-empty (remove-empty-values (:action-refs %)))) 
                               (filter-included-actions all-apps))]
 
       (serialize "h:/results/unique-refs.clj" all-apps))
       ;(print-findings real-external-refs all-apps manifest-files )
)

(comment
    (set! *print-length* 15)
    (def avail-app-names (unique-app-names (load-unique-apps manifest-files)))
    (def avail-app-names (deserialize "d:/android/results/unique-names-20101102.clj"))
    
     (with-open [w (java.io.FileWriter. (java.io.File. "d:/android/results/all-refs-20101102.clj" ) false)] 
        (binding [*out* w 
                  *print-length* nil]
          (dorun (map prn (find-all-references-less-mem 
                            manifest-files 
                            index-dir 
                            avail-app-names)))))


 (let [apps (load-unique-apps manifest-files)
       name-app-map (into {} (map #(hash-map (:name %) %) apps))]
   (with-open [w (java.io.FileWriter. (java.io.File. "d:/android/results/all-refs-20101102-reduced.clj" ) false)] 
     (binding [*out* w 
               *print-length* nil]
       (with-open [r (java.io.PushbackReader. (java.io.FileReader. "d:/android/results/all-refs-20101102.clj"))]
         (loop [app (read r)]
           (prn (assoc app 
                  :action-refs   (filter-references (:action-refs app) name-app-map :actions)
                  :category-refs (filter-references (:category-refs app) name-app-map :categories)))
           (recur (read r)))))))

 )
(comment
  (def apps (load-unique-apps manifest-files))
  (def apps (deserialize "h:/results/manifests.clj"))
  (def actions (filter valid-action? (distinct (mapcat :actions apps))))

  (def splitted-actions (separate #(.startsWith % "android") actions)) 
  (serialize "h:/results/android-actions-itw.clj" (sort android-actions))
  
  (serialize "d:/android/results/called-actions.clj"   
  (with-open [r (java.io.PushbackReader. (java.io.FileReader. "d:/android/results/all-refs-20101102-reduced.clj"))]
    (loop [app (read r false nil) called-actions #{}]
      (if (nil? app)
        called-actions
        (let [referenced-actions (for [[k v] (:action-refs app) :when (not-empty v)] k)]
          (recur 
            (read r false nil) 
            (union called-actions (set referenced-actions))))))))
  )

