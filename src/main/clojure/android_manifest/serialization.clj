(ns android-manifest.serialization
  (:use [clojure.pprint :only [pprint]])
  (:import [java.io File FileWriter FileReader PushbackReader]))

(defn serialize 
  "Serialize the native clojure datastructure obj to file."
  ([file-name obj] (serialize file-name obj false))  
  ([file-name obj append?]
    (dorun
      (with-open [w (java.io.FileWriter. (java.io.File. file-name) append?)] 
        (binding [*out* w 
                  *print-dup* true
                  *print-length* nil] 
          (pprint obj))))))


(defn deserialize [filename]
  "Read clojure datastructure from file."
  (with-open [r (PushbackReader. (FileReader. filename))]
    (read r)))
