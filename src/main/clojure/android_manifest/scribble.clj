(ns android-manifest.scribble
  (:use android-manifest.serialization
     [clojure.contrib.pprint :only (pprint)]))

(defn get-actions [all-apps app-name]
  (get-in all-apps [app-name :actions]))

(defn filter-references [ref-map r]
  "Retain only apps that call an intent that is not defined within their own
   manifest.xml."
  (into {}
    (for [[action dependent-apps] ref-map]
      (let [filtered-refs (remove #(contains? (get-actions r %) action) dependent-apps)]
        [action filtered-refs]))))

(defn filter-included-actions [r]
  "Retain only apps that call an intent that is not defined within their own
   manifest.xml."
  (into {}
   (for [[app-name {refs :references-from :as m}] r]
     [app-name (assoc m :references-from (filter-references refs r))])))
    

(defn valid-action? [s]
  (< 1 (count (filter #(= % \.)  s))))


(defn get-ref-strings [all-apps]
  (distinct (flatten
    (for [[app-name {refs :references-from}] all-apps]
      (for [[action apps] refs :when (valid-action? action)]
        (for [app apps]
          (str
            \" app \" "[shape=box] \n"
            \" app \" "->" \" action \" " [color=red] ;\n")))))))


(defn get-action-def-strings [all-apps]
  (let [action-defs (for [[app-name attr-map] all-apps]
                      [app-name 
                       (for [[action dependent-apps] (:references-from attr-map) :when (not-empty dependent-apps)]
                         action)])]
    
    (for [[name actions] action-defs :when (not-empty actions)]
      (str
        \" name \" "[shape=box] ; \n"
        \" name \" "->{"
        (apply str (for [action actions]
                     (str "\"" action "\" ")))
        "} [color=green] ; \n"))))
           

(defn graphviz [real-external-refs]
  (str 
    "digraph {\n"
    ;; app to action strings
    (apply str (interpose " " (get-action-def-strings real-external-refs)))
    ;; dependent apps to action strings
    (apply str (interpose " " (get-ref-strings real-external-refs)))
    "}"))

;; script start
(def android-apps (deserialize "results/potential-refs"))

(def real-external-refs 
  (filter 
    (fn [[_ {v :references-from}]] (not-empty v)) 
    (filter-included-actions android-apps)))
  
(printf "Got %d real references between apps.\n" (count real-external-refs))
(binding [*print-length* 10]
  (pprint (take 10 real-external-refs)))

(comment
  (use 'clojure.contrib.json)
  ;; write output as json file
  (spit "results/real-refs" (with-out-str (pprint-json real-external-refs)))
  ;; visualize results via graphviz
  (spit "results/real-external-refs.dot" (graphviz real-external-refs))
  )
;; total number of referenced intents?
;; # of openintents?