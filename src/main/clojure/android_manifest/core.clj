(ns android-manifest.core
  (:use 
    [clojure.set :only [intersection difference union map-invert]]
    [clojure.pprint :only (pprint)]
    [clojure.contrib.seq-utils :only (separate)]
    [clojure.contrib.zip-filter.xml :only (xml-> xml1-> attr)]
    android-manifest.lucene
    android-manifest.serialization
    android-manifest.util
    android-manifest.sdk)
  (:require
    [clojure.contrib.zip-filter :as zf]
    [clojure.contrib.zip-filter.xml :as zfx]
    [clojure.zip :as zip]
    [clojure.xml :as xml])
  (:import 
    java.io.File
    [java.util.zip ZipInputStream ZipEntry ZipFile]))


(defrecord Android-App [name version package actions categories services receivers sdkversion maybe-refs])


(defn file-exists [file]
  "Does file exist and is not empty?"
  (and (.exists file) (< 0 (.length file))))

(defn- find-names-of
  "Find all android components, for example activities or services from 
androidmanifest.xml files using zipper traversals."
  ([xml tg] (find-names-of xml tg :action))
  ([xml tg tg2] 
    (into #{} 
      ;(remove android-specific? 
        (xml-> xml :application tg :intent-filter tg2 (attr :android:name)))));)

(defn load-android-app [app-name manifest maybe-refs]
  "Parse android app manifest."
  (let [x             (zip/xml-zip (xml/parse manifest))
        package-name  (xml1-> x (attr :package))
        version       (xml1-> x (attr :android:versionCode))
        sdkversion    (or (xml1-> x :uses-sdk (attr :android:minSdkVersion)) "0")
        actions       (find-names-of x :activity)
        categories    (find-names-of x :activity :category)
        services      (find-names-of x :service)
        receivers     (find-names-of x :receiver)];;TODO providers
    (Android-App. 
      app-name 
      version 
      package-name
      actions
      categories
      services
      receivers
      sdkversion
      maybe-refs)))
 
(defn unique-apps [apps]
  "Sort by descending version, filter all apps where version and path are equals. Should result
in loading android apps without duplicates (same package, lower versions)."
  (distinct-by :package 
    (reverse 
      (sort-by :version apps)))) 
  
;;(defn load-apps-from-archive [zip-archive]
  
(defn load-apps-from-disk [manifest-files]
  (pmap 
    (fn [f]
      (let [app-name     (-> f .getParentFile .getName)
            classes-dex  (File. (.getParentFile f) "classes.dex")
            maybe-refs   (if (file-exists classes-dex) (deserialize (.toString classes-dex)) '())]
        (load-android-app app-name f maybe-refs)))
  manifest-files))

(defn- possible-call-map [apps]
  "Create map of all references in decompiled apps to app names that seem to call these actions."
  (reduce 
    (fn [m app] 
      (reduce 
        #(update-in %1 [%2] conj (:name app)) 
        m 
        (:maybe-refs app)))
    {} 
    apps))

(defn- query-references [actions calls-action?-map]
  (reduce 
    (fn [m action] 
      (assoc m action (get calls-action?-map action #{}))) 
    {}
    actions))

(defn find-possible-references [apps]
  (let [calls-action?-map (possible-call-map apps)]
  (pmap 
    (fn [{actions :actions categories :categories services :services receivers :receivers :as app}]
      (assoc app 
        :action-refs   (query-references actions calls-action?-map)
        :category-refs (query-references categories calls-action?-map)
        :service-refs  (query-references services calls-action?-map)
        :receiver-refs (query-references receivers calls-action?-map)))
    apps)))

 (defn valid-action? [s]
  (< 1 (count (filter #(= % \.)  s))))
 
 
 (defn- filter-references [ref-map name-app-map component-type]
  "Retain only apps that call an intent that is not defined within their own
   manifest.xml."
  (remove-empty-values
    (into {}
    (for [[action apps-calling-action] ref-map]
      (let [filtered-refs (remove #(contains? (get-in name-app-map  [% component-type]) action) apps-calling-action)]
        [action filtered-refs])))))

 (defn- name-app-map [apps]
   (into {} (map #(hash-map (:name %) %) apps)))
 
(defn filter-included-actions [apps]
  "Retain only apps that call an intent that is not defined within their own
   manifest.xml."
  (let [name-app-map (name-app-map apps)]
    (pmap
      (fn [{arefs :action-refs crefs :category-refs srefs :service-refs rrefs :receiver-refs :as app}]
        (assoc app 
          :action-refs   (filter-references arefs name-app-map :actions)
          :category-refs (filter-references crefs name-app-map :categories)
          :service-refs  (filter-references srefs name-app-map :services)
          :receiver-refs (filter-references rrefs name-app-map :receivers)))
      apps)))

(defn find-app [like]
  ""
  (fn [app] (.contains (:name app) like)))


(defn print-findings [real-external-refs all-apps manifest-files]
  (let [openintent-actions    (distinct (filter #(.contains % "openintent") (mapcat #(keys (:action-refs %)) real-external-refs)))
        openintent-categories (distinct (filter #(.contains % "openintent") (mapcat #(keys (:category-refs %)) real-external-refs)))
        action-call-freq      (apply merge (flatten
                                             (for [m (map :action-refs real-external-refs)]
                                               (for [[k v] m]
                                                 {k (count v)}))))
        sorted-freq              (into (sorted-map-by (fn [k1 k2] (compare (get action-call-freq k2) (get action-call-freq k1))))
                                   action-call-freq)
        no-defined-actions          (count (distinct (mapcat :actions all-apps)))
        no-external-actions-offered  (count (distinct (mapcat #(keys (remove-empty-values (:action-refs %))) real-external-refs)))
        no-external-services-offered (count (distinct (mapcat #(keys (remove-empty-values (:service-refs %))) real-external-refs)))
        no-apps-calling-external-action  (count (distinct (apply concat (mapcat #(vals (:action-refs %)) real-external-refs))))
        no-apps-calling-external-service (count (distinct (apply concat (mapcat #(vals (:service-refs %)) real-external-refs))))
        ]
  (println 
    "# manifests: "                         (count manifest-files)
    "\n# unique Apps: "                     (count all-apps)
    "\n# of defined actions: "              no-defined-actions
    "\n# of externally used actions offered from apps: " no-external-actions-offered    
    "\n# of externally used services offered from apps: " no-external-services-offered    
    "\n# apps calling external actions: "   no-apps-calling-external-action    
    "\n# apps calling external services: "   no-apps-calling-external-service    
    "\n% of apps relying on intent based relationships: <=" (double (* 100 (/ no-apps-calling-external-action (count all-apps))))
    "\n# openintents (actions): "           (count openintent-actions) openintent-actions
    "\n# of categories offered from apps: " (count (distinct (mapcat #(keys (remove-empty-values (:category-refs %))) real-external-refs)))
    "\n# apps calling foreign categories: " (count (distinct (mapcat #(vals (:category-refs %)) real-external-refs)))
    "\n# openintents (category): "          (count openintent-categories) openintent-categories
    "\nMost called actions: "               (take 3 sorted-freq)
    "\nMin. SDK Versions (version no./count: " (sort (frequencies (map :sdkversion all-apps)))
    )))

(defn- trim-maybe-refs [apps]
  "Remove all strings that are no known action name."
   (let [existing-actions (into #{} (concat (mapcat :actions apps) (mapcat :services apps) (mapcat :categories apps) (mapcat :receivers apps)))]
     (for [{p-a-c :maybe-refs :as app} apps]
       (assoc app :maybe-refs (filter existing-actions p-a-c)))))

(comment
    (def manifest-files (map #(File. %) (deserialize "d:/android/results/manifest-files-20101106.clj")))
)


(comment
  
  (set! *print-length* 15)
  (def app-sources "h:/android")
  (def manifest-files (find-file app-sources #".*AndroidManifest.xml"))
  (def apps (unique-apps (load-apps-from-disk manifest-files)))
  (def actions 
      (filter valid-action? 
        (distinct (mapcat :actions apps))))
  
  ;(serialize (str "d:/android/results/raw-" (date-string) ".clj") (map (partial into {}) apps))
  
  (def trimmed-apps (trim-maybe-refs apps))
  (def r (find-possible-references trimmed-apps))
  (def r2 (filter-included-actions r))
  (def r3 (map #(dissoc % :maybe-refs) r2))
  
  ;(def r4 (foreign-refs-only r3))
  (print-findings r3 apps manifest-files)
  ;(print-findings r3 apps (range 0 24000))

  (use 'android-manifest.graphviz :reload)
  (spit (str "d:/android/results/refviz-33k-" (date-string) ".dot") (graphviz r3))
  (binding [*print-length* nil]
    (spit "d:/android/results/real-refs-20k.json" (with-out-str (pprint-json r3))))
  )


(comment
  (defn sl [action]
    (search-lucene "t:\\Downloads\\android\\sdk-lucene" action))
  )

;; - export flag=true fuer activity ohne intent-filter vorhanden?
;; - abhaengigkeiten ohne filtern der android-actions durchfuehren, performance analysieren
;; - 