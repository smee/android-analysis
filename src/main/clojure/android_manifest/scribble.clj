(ns android-manifest.scribble
  (:use 
    android-manifest.serialization
     [clojure.contrib.pprint :only (pprint)]
     [clojure.set :only (map-invert)]))

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
    (for [{arefs :action-refs crefs :category-refs :as m} r]
      (assoc m 
        :action-refs (filter-references arefs name-app-map :actions)
        :category-refs (filter-references crefs name-app-map :categories)))))
    

;;;;;;;;;;;;;;;; GraphVIZ ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn valid-action? [s]
  (< 1 (count (filter #(= % \.)  s))))


(defn get-ref-strings [all-apps slot-value color]
  (distinct (flatten
    (for [{app-name :name refs slot-value} all-apps]
      (for [[action dep-apps] refs :when (valid-action? action)]
        (for [dep-app dep-apps]
          (str
            \" dep-app \" "[shape=box] \n"
            \" dep-app \" "->" \" action \" " [color=" color ", style=dotted] ;\n")))))))


(defn get-def-strings [all-apps slot-name color]
  (let [action-defs (for [{name :name refs slot-name} all-apps]
                       [name
                        (for [[action dependent-apps] refs :when (not-empty dependent-apps)]
                         action)])]
    
    (for [[name actions] action-defs :when (not-empty actions)]
      (str
        \" name \" "[shape=box] ; \n"
        \" name \" "->{"
        (apply str (for [action actions]
                     (str "\"" action "\" ")))
        "} [color=" color ",arrowhead=\"diamond\"] ; \n"))))
           

(defn graphviz [real-external-refs]
  (str 
    "digraph {\n"
    ;; app to action strings
    (apply str (interpose " " (get-def-strings real-external-refs :action-refs "green")))
    (apply str (interpose " " (get-def-strings real-external-refs :category-refs "blue")))
    ;; dependent apps to action strings
    (apply str (interpose " " (get-ref-strings real-external-refs :action-refs "red")))
    (apply str (interpose " " (get-ref-strings real-external-refs :category-refs "chocolate")))
    "}"))
;;;;;;;;;;;;;;;; GraphVIZ ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn print-findings [real-external-refs all-apps manifest-files]
  (let [openintent-actions (distinct (filter #(.contains % "openintent") (mapcat #(keys (:action-refs %)) real-external-refs)))
        openintent-categories (distinct (filter #(.contains % "openintent") (mapcat #(keys (:action-refs %)) real-external-refs)))
        action-call-freq (apply merge-with + (flatten
                                             (for [m (map :action-refs real-external-refs)]
                                               (for [[k v] m :when (valid-action? k)]
                                                 {k (count v)}))))
      sorted-freq (into (sorted-map-by (fn [k1 k2] (compare (get action-call-freq k2) (get action-call-freq k1))))
                    action-call-freq)]
  (println 
    "# manifests: " (count manifest-files)
    "\n# unique Apps: " (count all-apps)
    "\n# of actions offered from apps: " (count (distinct (mapcat #(keys (:action-refs %)) real-external-refs)))
    "\n# apps calling foreign actions: " (count (distinct (mapcat #(vals (:action-refs %)) real-external-refs)))
    "\n# openintents (actions): " (count openintent-actions) openintent-actions
    "\n# of categories offered from apps: " (count (distinct (mapcat #(keys (:category-refs %)) real-external-refs)))
    "\n# apps calling foreign categories: " (count (distinct (mapcat #(vals (:category-refs %)) real-external-refs)))
    "\n# openintents (category): " (count openintent-categories) openintent-categories
    "\nMost called actions: " (take 3 sorted-freq))))

(comment
;; script start
(def android-apps (deserialize "results/refs.x"))

(def real-external-refs 
  (filter 
    #(or 
       (not-empty (:category-refs %)) 
       (not-empty (:action-refs %)))
    (filter-included-actions android-apps)))
  
(binding [*print-length* 10]
  (pprint (take 10 real-external-refs)))
(printf "Got %d real references between apps.\n" (count real-external-refs))


  (use 'clojure.contrib.json)
  ;; write output as json file
  (spit "results/real-refs-unique.json" (with-out-str (pprint-json real-external-refs)))
  ;; visualize results via graphviz
  (spit "results/real-external-refs-unique.dot" (graphviz real-external-refs))
  )




;; total number of referenced intents?
;; # of openintents?