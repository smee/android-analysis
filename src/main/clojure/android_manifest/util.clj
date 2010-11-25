(ns android-manifest.util
  (:use [clojure.stacktrace :only (root-cause)]))

(defn map-values [f m]
  "Change all map values by applying f to each one."
  (into {} (for [[k v] m] [k (f v)])))

(defn remove-empty-values [m]
  "Remove all key-values where value is empty."
  (into {} (for [[k v] m :when (not (empty? v))] [k v])))

(defn distinct-by
  "Returns a lazy sequence of object with duplicates removed,
  where duplicates are defined by applying the function func to each item.
  Calling (distinct-by _ identity) is equivalent to (clojure.core/distinct _)."
  [func coll]
    (let [step (fn step [xs seen]
                 (lazy-seq
                   ((fn [[f :as xs] seen]
                      (when-let [s (seq xs)]
                        (let [f-val (func f)]
                          (if (contains? seen f-val) 
                            (recur (rest s) seen)
                            (cons f (step (rest s) (conj seen f-val)))))))
                     xs seen)))]
      (step coll #{})))

(defn try-times*
  "Executes thunk. If an exception is thrown, will retry. At most n retries
  are done. If still some exception is thrown it is bubbled upwards in
  the call chain.
  http://stackoverflow.com/questions/1879885/clojure-how-to-to-recur-upon-exception"
  [n thunk]
  (loop [n n]
    (if-let [result (try
                      [(thunk)]
                      (catch Exception e
                        (when (zero? n)
                          (throw e))))]
      (result 0)
      (recur (dec n)))))

(defmacro try-times
  "Executes body. If an exception is thrown, will retry. At most n retries
  are done. If still some exception is thrown it is bubbled upwards in
  the call chain."
  [n & body]
  `(try-times* ~n (fn [] ~@body)))

(defn seq-counter 
  "calls callback after every n'th entry in sequence is evaluated with current index as parameter."
  [sequence n callback]
  (map #(do (if (= (rem %1 n) 0) (callback %1)) %2) (iterate inc 1) sequence))
  
(defn find-file [dirpath pattern]
  "Traverse directory dirpath depth first, return all files matching
the regular expression pattern"
  (for [file (-> dirpath java.io.File. file-seq) 
        :when (re-matches pattern (.getName file))]
    file))

(defn date-string 
  "Get date as string with format yyyyMMdd."
  ([]
    (date-string (java.util.Date.)))
  ([date]
    (.format (java.text.SimpleDateFormat. "yyyyMMdd") date)))

(defmacro ignore-exceptions [ & body ]
  "Catch any exception and print the message of its root cause."
  `(try 
     ~@body
     (catch Exception e# (println (root-cause e#)))))

(defn unchunk [s]
  "Disable the chunking behaviour introduced in clojure 1.1"
  (when (seq s)
    (lazy-seq
      (cons (first s)
        (unchunk (next s))))))

(defn sleep-random [min max]
  (Thread/sleep (+ min (.nextInt (java.util.Random.) (- max min)))))