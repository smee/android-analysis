(ns android.market.archive 
  "Functions to help processing zip archive contents."
  (:use
    [clojure.contrib.io :only (to-byte-array as-file copy delete-file-recursively)])
  (:import
    [java.io File ByteArrayInputStream BufferedOutputStream FileOutputStream]
    [java.util.zip ZipInputStream ZipOutputStream ZipEntry ZipFile]))

(defn extract-entry
  "Extract file from zip, returns byte[]."
  [zipfile filename]
  (with-open [zf (ZipFile. zipfile)]
    (if-let [entry (.getEntry zf filename)]
      (to-byte-array (.getInputStream zf entry)))))

(defn- filter-entries [zf regex]
  (filter #(re-matches regex (.getName %))
            (enumeration-seq (.entries zf))))

(defn get-entries
  "Sequence of name of all entries of a zip archive matching a regular expression."
  ([^File zipfile] (get-entries zipfile #".*"))
  ([^File zipfile regex] 
    (with-open [zf (ZipFile. zipfile)]
      (doall (map (fn [^ZipEntry ze] (.getName ze)) (filter-entries zf regex))))))

(defn process-entries
  "Run function for every entry (two parameters: entry name and contents as byte-array), 
returns sequence of results (not lazy)."
  ([zipfile func] (process-entries zipfile func #".*"))
  ([zipfile func regex]
    (with-open [zf (ZipFile. zipfile)]
      (doall 
        (pmap #(func (.getName %) (to-byte-array (.getInputStream zf %))) (filter-entries zf regex))))))  
      

(defn- unix-path [path]
  (.replaceAll path "\\\\" "/"))

(defn- trim-leading-str [s to-trim]
  (subs s  (.length to-trim)))

(defn copy-to-zip 
  "Copy all files under root-dir into a zip archive located at zip-file. Uses pathes relative to root-dir."
  ([zip-file root-dir] (copy-to-zip zip-file root-dir false))
  ([zip-file root-dir move?]
    (let [root (str (unix-path root-dir) \/)]
      (with-open [zip-os (-> zip-file
                           (FileOutputStream.)
                           (BufferedOutputStream.)
                           (ZipOutputStream.))]
        (doseq [file (file-seq (as-file root-dir)) :when (and true (.isFile file))]
          (let [path (unix-path (trim-leading-str (str file) root))]
            (.putNextEntry zip-os (doto (ZipEntry. path)
                                    (.setTime (.lastModified file))))
            (copy file zip-os)))
        (when move?
          (delete-file-recursively root-dir))))))