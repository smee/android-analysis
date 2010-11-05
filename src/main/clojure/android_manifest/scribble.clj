(ns android-manifest.scribble
  (:use 
    android-manifest.serialization
     [clojure.contrib.pprint :only (pprint)]
     [clojure.set :only (map-invert)]
     android-manifest.util))


(comment
;; script start
(def android-apps (deserialize "results/unique-refs.clj"))

(def real-external-refs 
  (filter 
    #(or 
       (not-empty (remove-empty-values (:category-refs %))) 
       (not-empty (remove-empty-values (:action-refs %))))
    (filter-included-actions android-apps)))
  
(binding [*print-length* 10]
  (pprint (take 10 real-external-refs)))
(printf "Got %d real references between apps.\n" (count real-external-refs))


  (use 'clojure.contrib.json)
  ;; write output as json file
  (binding [*print-length* nil]
    (spit "results/real-refs-16k.json" (with-out-str (pprint-json real-external-refs))))
  ;; visualize results via graphviz
  (spit "results/real-external-refs-unique.dot" (graphviz real-external-refs))
  )




;; total number of referenced intents?
;; # of openintents?