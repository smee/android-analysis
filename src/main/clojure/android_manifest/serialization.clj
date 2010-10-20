(ns android-manifest.serialization
  (:use [clojure.pprint :only [pprint]])
  (:import [java.io File FileWriter FileReader PushbackReader]))

(defn serialize [file-name obj]  
  "Serialize the native clojure datastructure obj to file."
  (dorun
    (with-open [w (java.io.FileWriter. (java.io.File. file-name))] 
      (binding [*out* w 
                *print-dup* true
                *print-length* nil] 
        (pprint obj)))
    :done))


(defn deserialize [filename]
  "Read clojure datastructure from file."
  (with-open [r (PushbackReader. (FileReader. filename))]
    (read r)))
