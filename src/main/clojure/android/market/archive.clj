(ns android.market.archive 
  (:use
    [clojure.contrib.io :only (to-byte-array)])
  (:import
    [java.io File ByteArrayInputStream]
    [java.util.zip ZipInputStream ZipEntry ZipFile]))

(defn extract-zipentry
  "Extract file from zip, returns byte[]."
  [zipfile filename]
  (with-open [zf (ZipFile. zipfile)]
    (if-let [entry (.getEntry zf filename)]
      (to-byte-array (.getInputStream zf entry)))))