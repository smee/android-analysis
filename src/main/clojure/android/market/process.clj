(ns android.market.process
  (:use 
    [org.clojars.smee
     [util :only (ignore-exceptions)]
     [file :only (find-files extract-relative-path)]
     [serialization :only (serialize)]
     [time :only (date-string)]]
    [android.market.download :only (construct-output-file) ]
    [android.analysis.hash :only (md5)]
    [clojure.java.io :only (file as-file make-parents)]
    [clojure.contrib.io :only (copy to-byte-array)])
  (:import
    [java.io File ByteArrayInputStream]
    Dex2Jar)
  (:require
    [archive :as archive]
    [clojure.contrib.string :as string]
    [clojure.stacktrace :as stack]))


(defn- process-app-in-zip [outp-dir-f file-in-zip process-fn zip-file]
  (let [outfile  (construct-output-file outp-dir-f (.getName zip-file))]
    (when-let [contents (archive/extract-entry zip-file file-in-zip)]
      (println "processing" file-in-zip "in" zip-file)
      (make-parents outfile)
      (copy  (process-fn contents)  outfile)
      :success)))

(defn- extract-and-convert [archives outp-dir file-in-app process-fn]
    (let [outp-dir-f (as-file outp-dir)]
      (count (pmap
               #(try 
                  (process-app-in-zip outp-dir-f file-in-app process-fn %)
                  (catch Exception e 
                    (println "Error for" (str %))
                    (.printStackTrace e)))
               archives))))

(defn skip-files-in-archives 
  "Build a function that returns true if a given file exists already in 
one of the given archives."
  [archives]
  (let [available? (into #{} (map #(last (string/split #"/" %)) (ignore-exceptions (mapcat archive/get-entries archives))))]
    (fn [^File f] (available? (.getName f)))))

(defn skip-files-in-dir 
  "Build a function that returns true if a given file exists already in the directory dir
or its children."
  [dir]
  (let [available? (into #{} (map (memfn getName) (find-files dir #".*\d{4}\d*" true)))]
    (fn [^File f] (available? (.getName f)))))


(defn decode-binary-xml 
  "Decode android manifest files."
  [instream]
  (let [decoder (brut.androlib.res.decoder.XmlPullStreamDecoder. 
                  (brut.androlib.res.decoder.AXmlResourceParser.)
                  (.getResXmlSerializer (brut.androlib.res.AndrolibResources.)))
        baos (java.io.ByteArrayOutputStream.)]
    (do
      (.decode decoder instream baos)
      (String. (.toByteArray baos)))))


(defn extract-android-manifests 
  "Extract and decrypt all AndroidManifest.xml files from all android apps
found in main-dir and copy the results into a mirrored folder hierarchy at
outp-dir."
  [main-dir skip? outp-dir]
  (extract-and-convert 
    (remove skip? (find-files main-dir #".*\d{4}\d*" true))
    outp-dir 
    "AndroidManifest.xml" 
    (fn [byte-arr] (decode-binary-xml (ByteArrayInputStream. byte-arr)))))



(defn extract-jars 
  "Extract dalvik bytecode (classes.dex) from android apps and convert
them to java bytecode."
  [main-dir skip? outp-dir]
  (extract-and-convert 
    (remove skip? (find-files main-dir #".*\d{4}\d*" true)) 
    outp-dir 
    "classes.dex" 
    (fn [byte-arr] (Dex2Jar/doData byte-arr))))


;;;;;;;;;;;;;;;;  extract intents via static analysis   ;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn find-intents 
  "Try to identify all intents that get used for external calls in this android app.
Uses static analysis via the findbugs infrastructure."
  [app-file]
  (try
    (read-string (analyze.AnalyzeAndroidApps/findIntents (file app-file)))
    (catch Exception e  
      (stack/print-stack-trace e)
      {:called {} :queried {}})))

(defn count-intent-constructors 
  "Count all constructor invocations for intent objects."
  [app-file]
  (println app-file)
  (analyze.AnalyzeAndroidApps/countIntentConstructors (file app-file)))

(defn- process-all-files [files outp-dir process-fn]
  (pmap (fn [f]
          (let [outfile  (construct-output-file outp-dir (.getName f))]
            (println "processing" f "into" outfile)
            (serialize outfile (process-fn f))))
        files))


(defn extract-intents 
  "Extract as much infos about intent objects created in android apps via static analysis."
  ([jars-dir intents-dir] (extract-intents jars-dir (skip-files-in-archives (find-files intents-dir #".*\.zip")) intents-dir)) 
  ([jars-dir skip? intents-dir]
    (let [files (remove skip? (find-files jars-dir))
          now (date-string)
          output-dir (str intents-dir "/" now)]
      (dorun (process-all-files files output-dir find-intents))
      now)))

(defn extract-intent-constructors [main-dir]
    (process-all-files main-dir count-intent-constructors))

;;;;;;;;;;;;;;;;; calculate MD5 hashes of zip entries ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn hash-contents 
  "Map of zipentry names to md5 hashes of their contents"
  [zipfile]
  (ignore-exceptions
    (into {} (archive/process-entries zipfile #(vec [%1 (md5 %2)])))))

(defn hash-zip-contents
  ([main-dir outp-dir] (hash-zip-contents main-dir (skip-files-in-archives (find-files outp-dir #".*\.zip")) outp-dir)) 
  ([main-dir skip? outp-dir]
    (let [files (remove skip? (find-files main-dir))
          now (date-string)
          output-dir (str outp-dir "/" now)]
      (dorun (process-all-files files output-dir hash-contents))
      now)))


