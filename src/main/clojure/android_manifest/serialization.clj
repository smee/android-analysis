(ns android-manifest.serialization
  (:use [clojure.pprint :only [pprint]]
        [clojure.java.io :only (file reader)])
  (:import [java.io File FileWriter FileReader PushbackReader]))

(defn serialize 
  "Serialize the native clojure datastructure obj to file."
  ([file-name obj] (serialize file-name obj false))  
  ([file-name obj append?]
    (with-open [w (java.io.FileWriter. (file file-name) append?)] 
      (binding [*out* w 
                *print-length* nil] 
          (if (seq? obj)
            ;;serialize big sequences without retaining its head...
            (do
              (print \()
              (dorun (map prn obj)) ;;make sure members of sequences may be garbage collected upon realization
              (print \)))  
            (prn obj))))))


(defn deserialize [f]
  "Read clojure datastructure from file."
  (with-open [r (PushbackReader. (reader f))]
    (read r)))
