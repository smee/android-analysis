(ns eclipse.market.core
  (:use 
    [clojure.java.io :only (reader input-stream file)]
    [clojure.contrib.zip-filter.xml]
    [clojure.contrib.string :only (replace-re)]
    [clojure.string :only (trim)]
    )
  (:require
    [clojure.contrib.zip-filter :as zf]
    [clojure.zip :as zip]
    [clojure.xml :as xml]))

(defn main-page []
  (input-stream "http://marketplace.eclipse.org/api/p"))


(defn zip [doc]
  (zip/xml-zip (xml/parse doc)))

(defn get-markets [m]
  (xml-> m :market))

(defn get-categories [market]
  (let [categories (xml-> market :category)
        names (map (attr :name) categories)
        counts (map (attr :count) categories)
        urls (map (attr :url) categories)]
    (map #(hash-map :name %1 :count %2 :url %3) names counts urls)))

(defn get-nodes [category]
  (let [url (category :url)
        api-url (str url "/api/p")
        nodes (xml-> (zip (input-stream api-url)) :category :node)
        names (map (attr :name) nodes)
        urls (map (attr :url) nodes)]
    (map #(hash-map :name %1 :url %2) names urls)))

(defn find-update-url [node]
  (let [x (zip (input-stream (str (node :url) "/api/p")))]
    (xml-> x :node zf/children :updateurl text)))

(defn fetch-market-update-urls []
  (let [markets (get-markets (zip (main-page)))
        categories (mapcat get-categories markets)
        nodes (mapcat get-nodes categories)]
  (remove empty? (mapcat find-update-url nodes))))

;;;;;;;;;;;;;; leech update sites
(defn cleanup-urls [urls]
  (for [url urls]
    (let [u (clojure.string/trim url)
          len (count u)] 
      (cond 
        (.endsWith u "/") (subs u 0 (- len 1))
        (.endsWith u "/site.xml") (subs u 0 (- len (count "/site.xml")))
        :else url))))

(defn make-absolute [base relative]
  (if (.startWith relative "http")
    relative
    (str base \/ relative)))

(defn fetch-features [url]
    (let [site-url (str url "/site.xml")
          x (zip (input-stream site-url))
          features (xml-> x :feature)
          ids (map (attr :id) features)
          urls (map (attr :url) features)
          versions (map (attr :version) features)]
    (map #(hash-map :id %1 :version %2 :url (make-absolute url %3)) ids versions urls)))

(defn fetch-site-xmls [urls]
  (pmap fetch-features urls))


;;;;;;;;;;;;;;;;; analyze local update site mirrors

(defn unique-plugins [& dirs]
  (let [fseqs (map #(file-seq (file %)) dirs)
        files (apply concat fseqs)
        plugins (->> files
                  (filter #(= "plugins" (.getName (.getParentFile %))))
                  (map (memfn getName))
                  (map (partial replace-re #"_.*" ""))
                  sort
                  distinct)
        ]        
  plugins))
(comment
  (def p (unique-plugins #_"D:/cygwin/tmp/eclipse/_marketmirrors"
                          "D:/eclipse/"))
  )