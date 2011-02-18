(ns android.analysis.intent
  "Functions for inspecting the results of the intent calling static analysis of android apps."
  (:use 
    [clojure.java.io :only (input-stream)]
    android.market.archive
    android-manifest.util
    android-manifest.serialization))


;;;;;;; Load inputs ;;;;;;;;;;;;;;;;;;
(defn extract-app-name 
  "Return name of last directory of the given absolute file."
  ([abs-path] (extract-app-name abs-path \/))
  ([abs-path sep]
  (let [[from to] (take-last 2 (keep-indexed #(when (= sep %2) %1) abs-path))
        app-name  (subs abs-path (inc from) to)]
    app-name)))

(defn- process-intent-calls 
  "Load intent calling data."
  [entry-name arr]
  (hash-map (extract-app-name entry-name) (deserialize arr)))



(defn load-intents-zip [file]
  (reduce merge (process-entries file process-intent-calls #".*clj")))

(defn load-intent-constructor-counts-zip [file]
  (reduce merge (process-entries file process-intent-calls  #".*intent-count")))

;;;;;;; Extract data  ;;;;;;;;;;;;;;;;;;
(defn- called-intents-app [app]
  (apply concat (vals (:called app))))
(defn- queried-intents-app [app]
  ;; fix stupid bug in the static analysis:
  ;;   used vector in place of hashmap :(
  (apply concat (vals  (apply hash-map (:queried app)))))

(defn called-intents [m]
   (map called-intents-app (vals m)))

(defn queried-intents [m]
  (map queried-intents-app (vals m))) 

(defn- x-of [m x]
  (let [ci (called-intents m)]
    (sort-by-value
      (->> ci (map x) (filter identity) frequencies)
      :descending)))

(defn actions-of [m]
  (x-of m :action))

(defn uris-of [m]
  (x-of m :uri))

(defn mimetypes-of [m]
  (x-of m :mimetype))

(defn- uri-schemes [m]
  (sort-by-value 
    (->> m uris-of keys (filter (partial some #{\:})) (map #(subs % 0 (.indexOf % ":"))) frequencies)
    :descending))
  

(defn uri-schemes-of [m]
  "Unique uri schemes used."
  (distinct (keys (uri-schemes m))))

  
;;;;;;;;;;;
;;
;; statistic outputs
;;
;;;;;;;;;;;
(defn- intent-stats 
  [intents]
  (let [allintents (apply concat intents)
        explicits (filter :explicit? allintents)
        implicits (remove :explicit? allintents)]
    (println "#explicit intents:" (count explicits))
    (println "#implicit intents:" (count implicits))
    (let [valid-implicit-calls (filter #(and (contains? % :action) (or (contains? % :categories) (contains? % :data) (contains? % :uri))) implicits)]
      (println "implicit intents with action + (category|data|uri):" (count valid-implicit-calls)))))


(defn intent-call-stats 
  "Show some statistics abount intent calling data."
  [m]
  (intent-stats (called-intents m)))


(defn intent-query-stats 
  "Show some statistics abount intent querying data."
  [m]
  (intent-stats (queried-intents m)))

(defn called-implicit-intents [x]
  (remove :explicit? (distinct (apply concat (called-intents x)))))

(defn fan-out 
  "Number of unique intents per app that is used for calling other activities, services, broadcast receivers."
  [x]
   (into (sorted-set) (frequencies (map count (map #(remove :explicit? (distinct %)) (called-intents x))))))

(defn most-used-actions 
  "Group all implicit intents by action, return sorted map of action=>no. of intents"
  [intents]
  (let [implicits (remove :explicit? (apply concat (called-intents intents)))
        groups (group-by :action implicits)
        counts (map-values count groups)]
  (take 10 (sort-by-value counts :descending))))

(defn calc-recall
  "Calculate the quotient of number of intents found via static analysis to number of intents constructed
in an app."
  ([] (calc-recall 
        (load-intents-zip "d:/Projekte/Thorsten/waterloo/intentslist.zip")
        (load-intent-constructor-counts-zip "d:/Projekte/Thorsten/waterloo/intents2andCounts.zip")))
  ([il ic]
    (let [sa-counts (map-values #(+ (count (called-intents-app %)) (count (queried-intents-app %))) x)
          individual-recalls (for [[n c] ic]  (if (zero? c) 1 (/ (sa-counts n) c)))]
      (double (/ (reduce + individual-recalls) (count individual-recalls))))))

(comment
  
    (def x (load-intents-zip "d:/Projekte/Thorsten/waterloo/intentslist.zip"))
    (def c (load-intent-constructor-counts-zip "d:/Projekte/Thorsten/waterloo/intents2andCounts.zip"))
    (calc-recall (x c))
    
    (def x (deserialize "d:/android/allintents2.clj"))
    (def old-versions (deserialize "d:/android/oldversions.clj"))
    ;; remove infos about all old versions
    (def y (into {} (remove (fn [[n v]] (old-versions n)) x)))
    (print-simple-table (fan-out y))
    
    (intent-call-stats x)
    (intent-query-stats x)
    
  )