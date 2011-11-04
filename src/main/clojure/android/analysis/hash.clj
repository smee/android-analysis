(ns android.analysis.hash
  (:import
     (java.security 
       NoSuchAlgorithmException
       MessageDigest)
     (java.math BigInteger))
  (:use
    [archive :only (process-entries)]
    [org.clojars.smee 
     [file :only (find-files)]
     [map :only (remove-empty-values map-values)]
     [serialization]]
    [clojure.contrib.datalog.util :only (reverse-map)]))

(defn md5
  "Compute the hex MD5 sum of a byte array."
  [#^bytes b]
  (let [alg (doto (MessageDigest/getInstance "MD5")
              (.reset)
              (.update b))]
    (try
      (.toString (new BigInteger 1 (.digest alg)) 16)
      (catch NoSuchAlgorithmException e
        (throw (new RuntimeException e))))))

(defn md5-of [obj]
  (md5 (.getBytes (print-str obj))))


(defn package-of-class 
  "extract package of class file name , e.g. (package-of-class \"a/b/c/d.class\") returns
\"a/b/c\""
  [clz]
  (let [idx (.lastIndexOf clz "/")]
    (if (not= -1 idx) (subs clz 0 idx) "")))

(defn hashes-per-package 
  "Takes map of class file names to md5 hashes, returns map of packages to set of md5
hashes of the files in that package."
  [hashes]
  (let [hpp (->> hashes
              reverse-map
              (group-by (comp package-of-class second))
              (map-values (comp (partial into (sorted-set)) (partial map first))))]
    hpp))

(defn not-distinct
  "Returns a lazy sequence of the elements of coll that have duplicates"
  {:added "1.0"}
  [coll]
    (let [step (fn step [xs seen]
                   (lazy-seq
                    ((fn [[f :as xs] seen]
                      (when-let [s (seq xs)]
                        (if ((complement contains?) seen f) 
                          (recur (rest s) (conj seen f))
                          (cons f (step (rest s) seen)))))
                     xs seen)))]
      (distinct (step coll #{}))))

(defn android-libraries [hashes]
  (let [hpp (pmap #(->> % second hashes-per-package (map-values md5-of)) hashes)
        duplicates (not-distinct (apply concat hpp))]
    duplicates))


(comment
  
    (let [hashes (for [f (find-files "e:/android/classes-md5/" #".*zip")] 
                   (process-entries f #(list % (deserialize %2)) #".*\d{4}\d*"))
        libs (android-libraries hashes)
        ]
    (serialize "e:/android/identified-libs" libs)
    )
    )