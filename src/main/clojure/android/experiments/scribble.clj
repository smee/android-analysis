(ns android.experiments.scribble
  (:require 
    clojure.string
    [clojure.data.zip :as zf]
    [clojure.data.zip.xml :as zfx]
    [clojure.zip :as zip]
    [clojure.xml :as xml])
  (:use [clojure.java.io :only (file make-parents)]
        [org.clojars.smee 
         [map :only (map-values)]
         [serialization :only (deserialize-all)]]))


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

;;;;;;;;;;;;;;;;;;;;;;;;;; misc, experiments ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment
  (use 'android.market.process)
  
  (possible-android-identifiers (String. (to-byte-array (java.io.File. "h:/classes.dex"))))
  
  (let [now           (date-string)
        mf-dir        "g:/android/manifests/"
        output-dir    (str mf-dir now)
        skip?         (skip-files-in-archives (find-files mf-dir #".*\.zip"))
        num-extracted (extract-android-manifests "e:/android/original" skip? output-dir)] 
    (archive/copy-to-zip (file mf-dir (str now ".zip")) output-dir true)
    num-extracted)
  
  (extract-jars "e:/android/original" (skip-files-in-dir "e:/android/jars") "e:/android/jars")


  (let [i-dir "g:/android/intents/"
        output-dir (extract-intents "e:/android/jars" i-dir)]
    (archive/copy-to-zip (file i-dir (str output-dir ".zip")) (str i-dir output-dir) true))
  
  (do
    (println "count intent constructors: " 
      (extract-intent-constructors "e:/android/jars" "e:/android/intent constructor counts")))
  
  (serialize "d:/temp/foo" (find-intents (file "D:\\android\\jars\\ARCADE\\-1007597263548681988\\classes.dex")))
  
  (def contents (to-byte-array (java.io.File. "h:/classes.dex")))
  
  (dorun
    (for [f (find-files "h:/android" #".*classes.dex")] 
      (let [s (slurp f)] 
        (spit f (vec (possible-android-identifiers s))))))

  )



;;;;;;;; write all metadata into directories by packagename, files per versioncode

(defn construct-path-parts 
  "Create a hierarchy of folders by splitting the string on dots and creating subdirectories."
  [package]
  (clojure.string/split #"\." package))

(defn construct-output-dir 
  "Create a directory hierarchy from a numeric string. Creates the directories if they don't exists."
  [dir package]
  (let [out (apply file (flatten (list dir (construct-path-parts package))))]
    (do
      (make-parents (file out "dummy"))
      out)))


(defn sort-file [f output-dir]
  (let [m (flatten (deserialize-all f))
        gr (map-values first (group-by :packageName m))]
    (doseq [[package {version :versionCode :as metadata}] gr]
      (let [out (construct-output-dir output-dir package)
            out-file (file out (str version))]
        (append-spit out-file metadata)))))

(defn sort-metadata [input-dir output-dir]
  (let [files (find-files input-dir)
        logged-f (seq-counter files 50 #(println % "files done."))]
    (pmap #(sort-file % output-dir) logged-f)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (use '[org.clojars.smee.serialization :only [deserialize]]
       '[clojure.java.io :only [writer file]]
       '[org.clojars.smee [file :only [find-files]] [seq :only [wrap-time-estimator]]]
       '[archive :only [process-entries]])
  (with-open [out (-> "e:/android/all-hashes.csv" file writer)] 
    (let [archives (find-files "e:/android/classes-md5" #".*zip")]
      (doseq [archive (wrap-time-estimator (count archives) 1 archives)]
        (println archive)
        (process-entries archive 
                         (fn [name contents] (doseq [[class hash] (deserialize contents)] (.write out (str name \; class \; hash \newline)))) 
                         #".*\d{4}\d*"))))
  )