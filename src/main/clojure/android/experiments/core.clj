(ns android.experiments.core
  (:use 
    [clojure.set :only [intersection difference union map-invert]]
    [clojure.pprint :only (pprint)]
    [clojure.data.zip.xml :only (xml-> xml1-> attr)]
    android.tools.lucene
    [org.clojars.smee map seq serialization util]
    android.experiments.sdk
    )
  (:require
    [clojure.data.zip :as zf]
    [clojure.data.zip.xml :as zfx]
    [clojure.zip :as zip]
    [clojure.xml :as xml])
  (:import 
    java.io.File))


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
            maybe-refs   (set (if (file-exists classes-dex) (deserialize (.toString classes-dex)) '()))]
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


(defn- trim-maybe-refs [apps]
  "Remove all strings that are no known action name."
   (let [existing-actions (into #{} (concat (mapcat :actions apps) (mapcat :services apps) (mapcat :categories apps) (mapcat :receivers apps)))]
     (for [{p-a-c :maybe-refs :as app} apps]
       (assoc app :maybe-refs (filter existing-actions p-a-c)))))


(defn- print-statistics [apps name def-symbol ref-symbol]
  (let [no-action-defining-apps (count (remove empty? (map ref-symbol apps)))
        action-refs             (apply merge (map ref-symbol apps))
        openintent-actions      (filter #(.contains % "openintent") (keys action-refs))
        no-apps-calling-external-a (count (distinct (apply concat (vals action-refs))))
        call-freq (into (sorted-map-by >) (reverse-map (map-values count action-refs)))]
    
    (println 
      (str name ":\n" (apply str (repeat (count name) "-")))
      "\n# of definitions:"      (count (distinct (mapcat def-symbol apps)))
      "\n# of externally used:"  (count action-refs)    
      "\n# apps defining:"      no-action-defining-apps
      "\n# apps calling:"       no-apps-calling-external-a
      "\n% of apps relying on intent based relationships: <=" (double (* 100 (/ no-apps-calling-external-a (count apps))))
      "\n# openintents:"           (count openintent-actions) openintent-actions
      "\ntop freq. called:" (take 5 call-freq)
      "\n")))

(defn print-findings [all-apps manifest-files]
  (println 
    "# manifests: "                         (count manifest-files)
    "\n# unique Apps: "                     (count all-apps)
    ;"\nMost called actions: "               (take 3 sorted-freq)
    "\nMin. SDK Versions (version no./count: " (sort (frequencies (map :sdkversion all-apps))))
  (print-statistics all-apps "Actions" :actions :action-refs)
  (print-statistics all-apps "Categories" :categories :category-refs)
  (print-statistics all-apps "Services" :services :service-refs)
  (print-statistics all-apps "Receivers" :receivers :receiver-refs))


(defn sdk-actions [lucene-dir apps ref-key]
  "Return map of action strings to seq of documentation files that contain this string."
  (let [action-refs (apply merge (map ref-key apps))
        action-doc-map (into {}
                         (for [a (keys action-refs)] 
                           [a (search-lucene lucene-dir a)]))]
  (keys 
    (dissoc (remove-empty-values action-doc-map) 0))))

;;;;;;;;;;;;;;;;;;;;; old ;;;;;;;;;;;;;;;;;;;;;;

(defn printable? 
  "Is this character in [33..126]?"
  [ch]
  (let [val (int ch)]
    (or 
      (and (>= val (int \a)) (<= val (int \z)))
      (and (>= val (int \A)) (<= val (int \Z)))
      (and (>= val (int \0)) (<= val (int \9)))
      (contains? #{\. \- \_} ch))))

(defn possible-android-identifiers 
  "Extract all strings from a binary dexfile (dalvik bytecode)
   that look and taste like an android action reference string."
  [contents]
  (->> contents 
    String.
    (partition-by printable?)
    (remove #(>= 6 (count %)))
    (remove (comp not printable? first))
    (filter valid-action?)
    ;(filter (re-find #"([.a-zA-Z0-9]+)"))
    distinct
    (map (partial apply str))))


(defn extract-bytecode-strings [main-dir outp-dir]
  (extract-and-convert 
    main-dir 
    outp-dir 
    "classes.dex"                       
    (fn [byte-arr] (prn-str (possible-android-identifiers (String. byte-arr))))))

(comment
  
  (set! *print-length* 15)
  (def app-sources "h:/android")
  (def manifest-files (find-files app-sources #".*AndroidManifest.xml"))
  (def apps (unique-apps (load-apps-from-disk manifest-files)))
  (def actions 
      (filter valid-action? 
        (distinct (mapcat :actions apps))))
  
  ;(serialize (str "d:/android/results/raw-" (date-string) ".clj") (map (partial into {}) apps))
  
  (def trimmed-apps (trim-maybe-refs apps))
  (def r (find-possible-references trimmed-apps))
  (def r2 (filter-included-actions r))
  (def r3 (map #(dissoc % :maybe-refs) r2))
  
  (print-findings r3 manifest-files)

  (use 'android.tools.graphviz :reload)
  (spit (str "d:/android/results/refviz-33k-" (date-string) ".dot") (graphviz r3))
  (binding [*print-length* nil]
    (spit "d:/android/results/real-refs-20k.json" (with-out-str (pprint-json r3))))
  )



(comment
  ;; 
  (def lucene-dir "t:\\Downloads\\android\\sdk-lucene")
  (def sdk-defined
    (concat
      (sdk-actions lucene-dir r3 :action-refs)
      (sdk-actions lucene-dir r3 :category-refs)
      (sdk-actions lucene-dir r3 :service-refs)
      (sdk-actions lucene-dir r3 :receiver-refs)))
  )

;; - export flag=true fuer activity ohne intent-filter vorhanden?
;; - 