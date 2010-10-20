(ns android-manifest.scribble
  (:use android-manifest.serialization
     [clojure.contrib.pprint :only (pprint)]))

(defn get-actions [all-apps app-name]
  (get-in all-apps [app-name :actions]))

(defn filter-references [ref-map name-app-map]
  "Retain only apps that call an intent that is not defined within their own
   manifest.xml."
  (into {}
    (for [[action dependent-apps] ref-map]
      (let [filtered-refs (remove #(contains? (get-actions name-app-map %) action) dependent-apps)]
        [action filtered-refs]))))

(defn filter-included-actions [r]
  "Retain only apps that call an intent that is not defined within their own
   manifest.xml."
  (let [name-app-map (into {} (map #(hash-map (:name %) %) r))]
    (for [{refs :references-from :as m} r]
      (assoc m :references-from (filter-references refs name-app-map)))))
    

(defn valid-action? [s]
  (< 1 (count (filter #(= % \.)  s))))


(defn get-ref-strings [all-apps]
  (distinct (flatten
    (for [{app-name :name refs :references-from} all-apps]
      (for [[action dep-apps] refs :when (valid-action? action)]
        (for [dep-app dep-apps]
          (str
            \" dep-app \" "[shape=box] \n"
            \" dep-app \" "->" \" action \" " [color=red] ;\n")))))))


(defn get-action-def-strings [all-apps]
  (let [action-defs (for [{name :name refs :references-from} all-apps]
                       [name
                        (for [[action dependent-apps] refs :when (not-empty dependent-apps)]
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


(comment
;; script start
(def android-apps (deserialize "results/refs.x"))

(def real-external-refs 
  (filter 
    #(not-empty (:references-from %)) 
    (filter-included-actions android-apps)))
  
(printf "Got %d real references between apps.\n" (count real-external-refs))
(binding [*print-length* 10]
  (pprint (take 10 real-external-refs)))


  (use 'clojure.contrib.json)
  ;; write output as json file
  (spit "results/real-refs.json" (with-out-str (pprint-json real-external-refs)))
  ;; visualize results via graphviz
  (spit "results/real-external-refs.dot" (graphviz real-external-refs))
  )
;; total number of referenced intents?
;; # of openintents?