(ns android-manifest.process
  (:use 
    [android-manifest.scribble :only (valid-action?)]
    clojure.contrib.java-utils
    [clojure.contrib.io :only (copy to-byte-array)])
  (:import
    [java.io File ByteArrayInputStream]
    [java.util.zip ZipInputStream ZipEntry]))

(defn extract-relative-path [^File dir ^File file]
  (-> dir .toURI (.relativize (.toURI file)) .getPath))

(defn recreate-dirs-in [rel-path dir]
  (.mkdirs (File. dir rel-path)))

(defn extract-zipentry
  "Extract file from zip, returns byte[]."
  [instream filename]
  (with-open [zip-stream (ZipInputStream. instream)]
    (loop [entry (.getNextEntry zip-stream)]
      (when (not (nil? entry))
        (if (.endsWith (.getName entry) filename)
          (to-byte-array zip-stream)
          (recur (.getNextEntry zip-stream)))))))

(defn extract-zip [from & files-in-zip]
  "Sequence of byte[] of zip entries. Return value undefined if any file does not exist"
  (for [filename files-in-zip]
    (do
      (println from)
      (with-open [instream (java.io.FileInputStream. from)]
        (extract-zipentry instream filename)))))

(defn- process-file [main-dir-f outp-dir-f filename process-fn file]
  (let [rel-path (extract-relative-path main-dir-f file)
          outfile  (File. (File. outp-dir-f rel-path) filename)]
      (recreate-dirs-in rel-path outp-dir-f)
      (when (and (.isFile file) (not (.exists outfile)))
        (if-let [contents (first (extract-zip file filename))]
          (copy 
            (process-fn contents) 
            outfile)))))

(defn- extract-and-convert [main-dir outp-dir filename process-fn]
    (let [main-dir-f (as-file main-dir)
          outp-dir-f (as-file outp-dir)]
      (map
        (partial process-file main-dir-f outp-dir-f filename process-fn)
        (file-seq main-dir-f))))

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
    (and (> val 32) (< val 127))))

(defn possible-android-identifiers [contents]
  "Extract all strings from a binary dexfile (dalvik bytecode)
   that looks and tastes like an android action reference string."
  (->> contents 
    String.
    (partition-by printable?)
    (remove (comp not printable? first))
    (remove (comp not valid-action?))
    (map (partial apply str))
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
  (doall (extract-smali "D:\\android\\apps\\original\\" "t:/android/market"))
  
  (def contents (to-byte-array (java.io.File. "h:/classes.dex")))

  )
      