(ns android.analysis.intent
  "Functions for inspecting the results of the intent calling static analysis of android apps."
  (:use 
    [clojure.java.io :only (input-stream)]
    android.market.archive
    android-manifest.util
    android-manifest.serialization))

(defn- process-intent-calls 
  "Load intent calling data."
  [entry-name arr]
  (let [[from to] (take-last 2 (keep-indexed #(when (= \/ %2) %1) entry-name))
        app-name  (subs entry-name (inc from) to)]
    (hash-map app-name (deserialize arr))))


(defn called-intents [m]
  (mapcat :called (vals m)))

(defn queried-intents [m]
  (mapcat :called (vals m)))

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
  (let [explicits (filter :explicit? intents)
        implicits (remove :explicit? intents)]
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



(comment
  
    (def x (reduce merge (process-entries "d:/Projekte/Thorsten/waterloo/intents.zip" process-intent-calls #".*clj")))
    (def x (deserialize "d:/android/allintents.clj"))
    
    (intent-call-stats x)
    (intent-query-stats x)
  )