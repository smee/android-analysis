(ns android.market.process
  (:use 
    [android-manifest.core :only (valid-action?)]
    [android-manifest.util :only (ignore-exceptions)]
    ;clojure.contrib.java-utils
    [clojure.java.io :only (file make-parents)]
    [clojure.contrib.io :only (copy to-byte-array)])
  (:import
    [java.io File ByteArrayInputStream]
    [java.util.zip ZipInputStream ZipEntry ZipFile]))

(defn extract-relative-path [^File dir ^File file]
  (-> dir .toURI (.relativize (.toURI file)) .getPath))

(defn extract-zipentry
  "Extract file from zip, returns byte[]."
  [zipfile filename]
  (with-open [zf (ZipFile. zipfile)]
    (if-let [entry (.getEntry zf filename)]
      (to-byte-array (.getInputStream zf entry)))))

(defn- process-app [main-dir-f outp-dir-f filename process-fn zip-file]
  (let [rel-path (extract-relative-path main-dir-f zip-file)
        outfile  (file outp-dir-f rel-path filename)]
      (make-parents outfile)
      (when (not (.exists outfile))
        (do (println "processing" filename "in" zip-file)
          (if-let [contents (extract-zipentry zip-file filename)]
            (copy 
              (process-fn contents) 
              outfile))))))

(defn- extract-and-convert [main-dir outp-dir file-in-app process-fn]
    (let [main-dir-f (file main-dir)
          outp-dir-f (file outp-dir)]
      (dorun
        (pmap
          #(ignore-exceptions 
             (process-app main-dir-f outp-dir-f file-in-app process-fn %))
          (filter #(.isFile %) (file-seq main-dir-f))))))

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

(defn possible-android-identifiers-unrolled [contents]
  "Handmade loop instead of sequence abstractions only, should help perfomance in this special case."
  (loop [result #{} ]))

(defn extract-smali [main-dir outp-dir]
  (extract-and-convert 
    main-dir 
    outp-dir 
    "classes.dex"                       
    (fn [byte-arr] (prn-str (possible-android-identifiers (String. byte-arr))))))

(comment
  
  (possible-android-identifiers (String. (to-byte-array (java.io.File. "h:/classes.dex"))))
  
  (extract-android-manifests "D:\\android\\apps\\original" "h:/android")
  (extract-smali "D:\\android\\apps\\original\\" "h:/android")
  
  (def contents (to-byte-array (java.io.File. "h:/classes.dex")))
  
  (dorun
    (for [f (find-file "h:/android" #".*classes.dex")] 
      (let [s (slurp f)] 
        (spit f (vec (possible-android-identifiers s))))))

  )
