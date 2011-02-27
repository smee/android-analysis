(ns android-manifest.scribble
  (:require 
    [clojure.contrib.zip-filter :as zf]
    [clojure.contrib.zip-filter.xml :as zfx]
    [clojure.zip :as zip]
    [clojure.xml :as xml]))


(defn xml-zipper [s]
  "Create zipper from xml."
  (zip/xml-zip (xml/parse (new org.xml.sax.InputSource
                               (new java.io.StringReader s)))))


(comment

(def x (xml-zipper (slurp "H:\\android\\LIBRARIES\\127597760486969162\\AndroidManifest.xml")))

(zfx/xml1-> x (zfx/attr :package))
(zfx/xml1-> x (zfx/attr :android:versionName))
(zfx/xml-> x zf/descendants :intent-filter :action (zfx/attr :android:name))
(zfx/xml1-> x zf/children :uses-sdk (zfx/attr :android:minSdkVersion))

(defn make-lazy [f start]
  (lazy-seq 
      (if-let [val (f start)]
          (cons start (make-lazy f (+ 10 start)))
          (concat (range start (+ 10 start)) (make-lazy f (+ 10 start))))))

(take 40 (make-lazy #(when (< (rand 100) 80) %) 0)) 

  (def files-to-fix
    (filter identity
      (for [f (filter #(.isFile %) (file-seq (file "results/market-apps")))]
        (with-open [r (java.io.FileReader. (file f))]
          (when (not= \( (char (.read r)))
            f)))))
  (doseq [f files-to-fix]
    (spit f (str \( (slurp f) \))))
)

(comment
  (def jars (elatexam.logs.util/files-in "d:/android/apps/jars" #".*classes.dex"))  
  (for [f (take 30 (repeatedly #(rand-nth jars)))]
    (clojure.java.io/copy f (clojure.java.io/file "d:/android/sample" (str (hash f) ".jar"))))
  
  )