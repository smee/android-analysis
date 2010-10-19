(ns android-manifest.scribble
  (:use android-manifest.serialization
     [clojure.contrib.pprint :only (pprint)]))

(defn get-actions [all-apps app-name]
  (get-in all-apps [app-name :actions]))

(defn filter-references [ref-map r]
  "Retain only apps that call a intent that is not defined within their own
   manifest.xml."
  (into {}
    (for [[action apps] ref-map]
      (let [filtered-refs (remove #(contains? (get-actions r %) action) apps)]
        [action filtered-refs]))))

(defn filter-included-actions [r]
  "Retain only apps that call a intent that is not defined within their own
   manifest.xml."
  (into {}
   (for [[app-name {refs :references-from :as m}] r]
     [app-name (assoc m :references-from (filter-references refs r))])))
    
(def android-apps (deserialize "results/potential-refs"))

(def real-external-refs 
  (filter 
    (fn [[_ {v :references-from}]] (not-empty v)) 
    (filter-included-actions android-apps)))
  
(printf "Got %d real references between apps.\n" (count real-external-refs))
(binding [*print-length* 10]
  (pprint real-external-refs))

(comment
  (use 'clojure.contrib.json)
  (spit "results/real-refs" (with-out-str (pprint-json real-external-refs))))

(defn valid-action? [s]
  (< 1 (count (filter #(= % \.)  s))))

(defn get-ref-strings [foo]
  (for [[app-name {refs :references-from}] foo]
    (for [[action apps] refs :when (valid-action? action)]
      (for [a apps]
        (str \" a "\"->\"" action "\"\n")))))

(defn graphviz [foo]
  (str 
    "digraph {\n"
    ;; app to action strings
    (apply str (interpose " "
                 (for [[k v] foo] 
                   (str "\"" k "\"->{"
                     (apply str (for [a (:actions v)]
                                  (str "\"" a "\"")))
                     "} \n"))))
    (apply str (interpose " " (flatten (get-ref-strings foo))))
    "}"))