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
     [map :only (remove-empty-values map-values reverse-map)]
     [serialization]]))

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

(defn android-libraries 
  "FIXME: stupid, duplicates may exist when comparing different sets of hashes!
also, duplicates may exist between multiple versions of one app (private lib). We need to
make sure duplicates are only counted if a package occurs in different apps!"
  [hashes]
  (let [hpp (pmap #(->> % second hashes-per-package (map-values md5-of)) hashes)
        duplicates (not-distinct (apply concat hpp))]
    duplicates))

(comment
  
    (let [hashes (for [f (take 1 (find-files "/media/sf_android/classes-md5/" #".*99.zip"))] 
                   (process-entries f #(list % (deserialize %2)) #".*\d{4}\d*"))
        libs (map android-libraries hashes)
        ]
    (serialize "e:/android/identified-libs" libs)
    )
;; experimental code for finding the number of unique vs. duplicated library classes in android apps
    (use 'clojure.java.io 'org.clojars.smee.serialization)
    (require '[clojure.string :as s])
    
    (with-open [^java.io.Writer bw (writer "/media/sf_android/concattedhashes")] 
      (doseq [file (find-files "/media/sf_android/classes-md5/" #".*.zip")] 
        (println file) (flush) 
        (process-entries file #(doseq [[k v] (seq (deserialize %2))] 
                                 (.write bw (str \[ \" k \" \space \" v \" \space \" %1 \" \] \newline))) 
                         #".*\d{4}\d*")))
    ;;bash> sort -k 2 -T . concattedhashes | gzip -c >> byhash.gz&
    ;; m2 is a map of id to package of the app (unique identifier per app, irrespective of version)
    (def m2 (reduce merge (for [[p m] m] (reduce #(assoc % %2 p) {}  m))))
    ;; TODO classifier case:
    ;; - one tupel: unique
    ;; - multiple tupel, all ids have different packages: real library
    ;; - multiple tupel, all same package: unique
    ;; - multiple tupel, mixed: ???
    (defn classifier [m tupels]
      (let [single? (= 1 (count tupels))
            all-packages-equal? (= 1 (count (distinct (map last tupels))))
            key (cond 
                  single? :unique
                  all-packages-equal? :private-lib
                  :else :lib)] 
        (reduce (fn [m [class hash name]]
                  (update-in m [name key] (fnil inc 0))) m tupels)))
    
    (with-open [rdr (-> "/media/sf_android/byhash.gz" input-stream java.util.zip.GZIPInputStream. java.io.InputStreamReader. java.io.BufferedReader.)
                ;w (-> "/media/sf_android/with-package.gz" output-stream java.util.zip.GZIPOutputStream. java.io.OutputStreamWriter. java.io.BufferedWriter.)
                ]
      (->> rdr
        line-seq
        (map read-string) 
        (map (fn [[cl hash name]] (vector cl hash name (get m2 (subs  name 6)))))
        ;(map #(.write w (str (pr-str %) "\n")))
        ;dorun
        (partition-by second)
        ;(take 10) doall
        (reduce classifier {})
        (serialize "/media/sf_android/counted")
        ))
    ;; write csv
    (def m (into (sorted-map) (deserialize "/media/sf_android/counted")))
    (with-open [w (writer "/media/sf_android/libs-public.csv")]
      (do 
        (.write w "id;unique classes;library classes;private library classes\n")
        (doseq [[name {:keys [lib unique private-lib]}] m]
          (.write w (s/join ";" [(subs name 6) (or unique 0) (or lib 0) (or private-lib 0)]))
          (.write w "\n"))))
    nil
    )