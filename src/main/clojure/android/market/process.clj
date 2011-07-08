(ns android.market.process
  (:use 
    [android-manifest.core :only (valid-action?)]
    [android-manifest.util :only (ignore-exceptions find-file extract-relative-path)]
    [android-manifest.serialization :only (serialize)]
    [android.market.download :only (construct-output-file) ]
    [android.analysis.hash :only (md5)]
    ;clojure.contrib.java-utils
    [clojure.java.io :only (file make-parents)]
    [clojure.contrib.io :only (copy to-byte-array)])
  (:import
    [java.io File ByteArrayInputStream]
    Dex2Jar)
  (:require
    [android.market.archive :as archive]))


(defn- process-app [main-dir-f outp-dir-f filename process-fn zip-file]
  (let [rel-path (extract-relative-path main-dir-f zip-file)
        outfile  (construct-output-file outp-dir-f (.getName zip-file))]
      (make-parents outfile)
      (when (not (.exists outfile))
        (do 
          (println "processing" filename "in" zip-file)
          (if-let [contents (archive/extract-entry zip-file filename)]
            (copy 
              (process-fn contents) 
              outfile)
            :success)))))

(defn- extract-and-convert [main-dir outp-dir file-in-app process-fn]
    (let [main-dir-f (file main-dir)
          outp-dir-f (file outp-dir)]
      (count (pmap
                 #(ignore-exceptions 
                    (process-app main-dir-f outp-dir-f file-in-app process-fn %))
                 (filter #(and (.isFile %) (not (.endsWith (.getName %) ".403"))) 
                         (file-seq main-dir-f))))))

(defn decode-binary-xml [instream]
  "Decode android manifest files."
  (let [decoder (brut.androlib.res.decoder.XmlPullStreamDecoder. 
                  (brut.androlib.res.decoder.AXmlResourceParser.)
                  (.getResXmlSerializer (brut.androlib.res.AndrolibResources.)))
        baos (java.io.ByteArrayOutputStream.)]
    (do
      (.decode decoder instream baos)
      (String. (.toByteArray baos)))))


(defn extract-android-manifests [main-dir outp-dir]
  "Extract and decrypt all AndroidManifest.xml files from all android apps
found in main-dir and copy the results into a mirrored folder hierarchy at
outp-dir."
  (extract-and-convert 
    main-dir 
    outp-dir 
    "AndroidManifest.xml" 
    (fn [byte-arr] (decode-binary-xml (ByteArrayInputStream. byte-arr)))))



(defn dex2jar [main-dir outp-dir]
  "Extract dalvik bytecode (classes.dex) from android apps and convert
them to java bytecode."
  (extract-and-convert 
    main-dir 
    outp-dir 
    "classes.dex" 
    (fn [byte-arr] (Dex2Jar/doData byte-arr))))



(defn printable? [ch]
  "Is this character in [33..126]?"
  (let [val (int ch)]
    (or 
      (and (>= val (int \a)) (<= val (int \z)))
      (and (>= val (int \A)) (<= val (int \Z)))
      (and (>= val (int \0)) (<= val (int \9)))
      (contains? #{\. \- \_} ch))))

(defn possible-android-identifiers [contents]
  "Extract all strings from a binary dexfile (dalvik bytecode)
   that looks and tastes like an android action reference string."
  (->> contents 
    String.
    (partition-by printable?)
    (remove #(>= 6 (count %)))
    (remove (comp not printable? first))
    (filter valid-action?)
    ;(filter (re-find #"([.a-zA-Z0-9]+)"))
    distinct
    (map (partial apply str))))


(defn extract-bytecode-strings [main-dir outp-dir]
  (extract-and-convert 
    main-dir 
    outp-dir 
    "classes.dex"                       
    (fn [byte-arr] (prn-str (possible-android-identifiers (String. byte-arr))))))

(defn find-intents [app-file]
  "Try to identify all intents that get used for external calls in this android app.
Uses static analysis via the findbugs infrastructure."
  (read-string (analyze.AnalyzeAndroidApps/findIntents (file app-file))))

(defn count-intent-constructors [app-file]
  "Count all constructor invocations for intent objects."
  (analyze.AnalyzeAndroidApps/countIntentConstructors (file app-file)))

(defn- process-all-files [main-dir outp-dir process-fn]
  (let [main-dir-f (file main-dir)]
    (pmap (fn [f]
            (let [outfile  (construct-output-file outp-dir (.getName f))
                  lockfile (construct-output-file outp-dir (str (.getName f) ".lock"))]
              (make-parents outfile)
              (when (and (not (.exists outfile)) (not (.exists lockfile)))
                (do 
                  (.createNewFile lockfile)
                  (println "processing" f "into" outfile)
                  (serialize outfile (process-fn f))
                  (.delete lockfile)))))
          (filter #(and (.isFile %) (not (.endsWith (.getName %) ".403"))) (file-seq main-dir-f)))))

(defn extract-intents [main-dir outp-dir]
    (process-all-files main-dir outp-dir find-intents))

(defn extract-intent-constructors [main-dir outp-dir]
    (process-all-files main-dir outp-dir count-intent-constructors))

(defn hash-contents 
  "Map of zipentry names to md5 hashes of their contents"
  [zipfile]
  (into {} (archive/process-entries zipfile #(vec [%1 (md5 %2)]))))

(defn hash-zip-contents [main-dir outp-dir]
  (process-all-files main-dir outp-dir hash-contents))

(comment
  
  (possible-android-identifiers (String. (to-byte-array (java.io.File. "h:/classes.dex"))))
  
  (println "new manifests: " (extract-android-manifests "e:/android/original" "e:/android/manifests"))
  ;(println "new classes.dex: " (extract-bytecode-strings "D:\\android\\apps\\original\\" "d:/android/apps/dex"))
  (println "dex2jar: " (dex2jar "e:/android/original" "e:/android/jars"))
  (do
    (println "find intents: " 
      (extract-intents "e:/android/jars" "e:/android/intents")))
  (do
    (println "count intent constructors: " 
      (extract-intent-constructors "e:/android/jars" "e:/android/intent constructor counts")))
  
  (serialize "d:/temp/foo" (find-intents (file "D:\\android\\jars\\ARCADE\\-1007597263548681988\\classes.dex")))
  
  (def contents (to-byte-array (java.io.File. "h:/classes.dex")))
  
  (dorun
    (for [f (find-file "h:/android" #".*classes.dex")] 
      (let [s (slurp f)] 
        (spit f (vec (possible-android-identifiers s))))))

  )

