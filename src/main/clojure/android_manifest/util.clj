(ns android-manifest.util
  (:use [clojure.stacktrace :only (root-cause)]
        [clojure.java.io :only (file)])
  (:require
    [clojure.string :as cs]))

(defn map-values 
  "Change all map values by applying f to each one."
  [f m]
  (into {} (for [[k v] m] [k (f v)])))

(defn remove-empty-values 
  "Remove all key-values where value is empty."
  [m]
  (into {} (for [[k v] m :when (not (empty? v))] [k v])))

(defn reverse-map
  "Reverse the keys/values of a map"
  [m]
  (into {} (map (fn [[k v]] [v k]) m)))

(defn sort-by-value
  "Sort map by values. If two values are equal, sort by keys. Sort order may be 
:ascending or :descending"
  ([my-map] (sort-by-value my-map :ascending))
  ([my-map sort-order]
    (into 
      (sorted-map-by (fn [key1 key2] 
                       (let[val-res (compare (get my-map key1) (get my-map key2))
                            res (if (zero? val-res)
                                  (compare key1 key2)
                                  val-res)]
                         (if (= :ascending sort-order)
                           res
                           (* -1 res))))) 
      my-map)))

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
  
(defn find-file 
  "Traverse directory dirpath depth first, return all files matching
the regular expression pattern"
  [dirpath pattern]
  (for [file (-> dirpath file file-seq) 
        :when (re-matches pattern (.getName file))]
    file))

(defn date-string 
  "Get date as string with format yyyyMMdd."
  ([]
    (date-string (java.util.Date.)))
  ([date]
    (.format (java.text.SimpleDateFormat. "yyyyMMdd") date)))

(defmacro ignore-exceptions 
  "Catch any exception and print the message of its root cause."
  [ & body ]
  `(try 
     ~@body
     (catch Exception e# (.println System/err (root-cause e#)))))

(defn unchunk 
  "Disable the chunking behaviour introduced in clojure 1.1"
  [s]
  (when (seq s)
    (lazy-seq
      (cons (first s)
        (unchunk (next s))))))

(defn sleep-random 
  "Sleep for a random amount of milliseconds between [min,max]."
  [min max]
  {:pre [(<= min max) (>= min 0)]}
  (Thread/sleep (+ min (.nextInt (java.util.Random.) (- max min)))))

(defn print-latex-table [a-map]
  (letfn [(f [string] (cs/replace string "_" "\\_"))]
    (str "\\begin{longtable}{lr}" \newline
      (apply str (for [[k v] a-map] (str (f k) " & " (f v) " \\\\" \newline)))
      "\\end{longtable}" \newline)))