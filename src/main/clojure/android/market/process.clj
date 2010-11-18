(ns android.market.process
  (:use 
    [android-manifest.core :only (valid-action?)]
    [android-manifest.util :only (ignore-exceptions)]
    ;clojure.contrib.java-utils
    [clojure.java.io :only (file make-parents)]
    [clojure.contrib.io :only (copy to-byte-array)])
  (:import
    [java.io File ByteArrayInputStream]
    [java.util.zip ZipInputStream ZipEntry]))

(defn extract-relative-path [^File dir ^File file]
  (-> dir .toURI (.relativize (.toURI file)) .getPath))

(defn recreate-dirs-in [rel-path dir]
  (.mkdirs (file dir rel-path)))

(defn extract-zipentry
  "Extract file from zip, returns byte[]."
  [instream filename]
  (ignore-exceptions
    (with-open [zip-stream (ZipInputStream. instream)]
      (loop [entry (.getNextEntry zip-stream)]
        (when (not (nil? entry))
          (if (.endsWith (.getName entry) filename)
            (to-byte-array zip-stream)
            (recur (.getNextEntry zip-stream))))))))

(defn extract-zip [from & files-in-zip]
  "Sequence of byte[] of zip entries. Return value undefined if any file does not exist"
  (for [filename files-in-zip]
    (do
      (println "extracting from" from "the files:" files-in-zip)
      (with-open [instream (-> from java.io.FileInputStream. java.io.BufferedInputStream.)]
        (extract-zipentry instream filename)))))

(defn- process-file [main-dir-f outp-dir-f filename process-fn input-file]
  (let [rel-path (extract-relative-path main-dir-f input-file)
        outfile  (file outp-dir-f rel-path filename)]
      (make-parents outfile)
      (when (not (.exists outfile))
        (if-let [contents (first (extract-zip input-file filename))]
          (copy 
            (process-fn contents) 
            outfile)))))

(defn- extract-and-convert [main-dir outp-dir filename process-fn]
    (let [main-dir-f (file main-dir)
          outp-dir-f (file outp-dir)]
      (pmap
        #(ignore-exceptions (process-file main-dir-f outp-dir-f filename process-fn %))
        (filter #(.isFile %) (file-seq main-dir-f)))))

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
  (extract-and-convert 
    main-dir 
    outp-dir 
    "AndroidManifest.xml" 
    (fn [byte-arr] (decode-binary-xml (ByteArrayInputStream. byte-arr)))))



(defn printable? [ch]
  "Is this character in [33..126]?"
  (let [val (int ch)]
    (or 
      (and (>= val (int \a)) (<= val (int \z)))
      (and (>= val (int \A)) (<= val (int \Z)))
      (and (>= val (int \0)) (<= val (int \9)))
      (contains? #{\. \- \_} ch)
      )))

(defn possible-android-identifiers [contents]
  "Extract all strings from a binary dexfile (dalvik bytecode)
   that looks and tastes like an android action reference string."
  (->> contents 
    String.
    (partition-by printable?)
    (remove (comp not printable? first))
    (remove #(>= 6 (count %)))
    (filter valid-action?)
    (map (partial apply str))
    ;(filter (re-find #"([.a-zA-Z0-9]+)"))
    distinct))


(defn extract-smali [main-dir outp-dir]
  (extract-and-convert 
    main-dir 
    outp-dir 
    "classes.dex"                       
    (fn [byte-arr] (prn-str (possible-android-identifiers (String. byte-arr))))))

(comment
  
  (possible-android-identifiers (String. (to-byte-array (java.io.File. "h:/classes.dex"))))
  
   (doall (extract-android-manifests "D:\\android\\apps\\original" "h:/android"))
  (doall (extract-smali "D:\\android\\apps\\original\\" "h:/android"))
  
  (def contents (to-byte-array (java.io.File. "h:/classes.dex")))
  
  (dorun
    (for [f (find-file "h:/android" #".*classes.dex")] 
      (let [s (slurp f)] 
        (spit f (vec (possible-android-identifiers s))))))

  )
      