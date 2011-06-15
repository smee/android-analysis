(ns android-manifest.util
  (:use [clojure.stacktrace :only (root-cause)]
        [clojure.java.io :only (file)]
        [clojure.contrib.pprint :only (cl-format)])
  (:require
    [clojure.string :as cs])
  (:import
    [java.io File]))

(defn map-values 
  "Change all map values by applying f to each one."
  [f m]
  (into {} (for [[k v] m] [k (f v)])))

(defn remove-empty-values 
  "Remove all key-values where value is empty."
  [m]
  (into {} (for [[k v] m :when (not (empty? v))] [k v])))

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
  (map #(do (when (= (rem %1 n) 0) (callback %1)) %2) (iterate inc 1) sequence))
  
(defn find-file 
  "Traverse directory dirpath depth first, return all files matching
the regular expression pattern. Per default returns only files, no directories."
  ([dirpath pattern] (find-file dirpath pattern true))
  ([dirpath pattern files-only?]
  (let [files (for [file (-> dirpath file file-seq) :when (re-matches pattern (.getName file))]
                file)]
    (if files-only?
      (filter (memfn isFile) files)
      files))))

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

(defn wrap-ignore-exceptions [f]
  (fn [& args]
    (ignore-exceptions (apply f args))))

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

(defn print-simple-table [a-map]
  (doseq [[k v] a-map] (println k " " v)))

;(defn starts-with-any 
  ;"Does the string s start with any string within str-set?"
  ;[str-set ^String s]
  ;(some #(when (.startsWith s %) %) str-set))
(defn starts-with-any 
  "Does the string s start with any string within str-set?"
  [str-set ^String s]
  (some str-set (map (partial subs s 0) (range 0 (count s)))))


(defn table
  "Given a seq of hash-maps, prints a plaintext table of the values of the hash-maps.
  If passed a list of keys, displays only those keys.  Otherwise displays all the
  keys in the first hash-map in the seq.
Source: http://briancarper.net/blog/527/printing-a-nicely-formatted-plaintext-table-of-data-in-clojure"
  ([xs]
    (table xs (keys (first xs))))
  ([xs ks]
    (when (seq xs)
      (let [f (fn [old-widths x]
                (reduce (fn [new-widths k]
                          (let [length (inc (count (str (k x))))]
                            (if (> length (k new-widths 0))
                              (assoc new-widths k length)
                              new-widths)))
                  old-widths ks))
            widths (reduce f {} (conj xs (zipmap ks ks)))
            total-width (reduce + (vals widths))
            format-string (str "~{"
                            (reduce #(str %1 "~" (%2 widths) "A") "" ks)
                            "~}~%")]
        (cl-format true format-string (map str ks))
        (cl-format true "~{~A~}~%" (repeat total-width \-))
        (doseq [x xs]
          (cl-format true format-string (map x ks)))))))

(defn extract-relative-path 
  "Extract path relative to base directory."
  [^File base ^File file]
  (-> base .toURI (.relativize (.toURI file)) .getPath))

(defn mapp
  "Use this instead of (partial map xy)"
  ([f] (partial map f))
  ([f x & args]
     (apply map (partial f x)
            args )))
  
(defn mapc [& args]
  "From: http://erl.nfshost.com/2011/05/22/map-mapp-and-mapc/
  Examples: 
     user> (mapc inc sq inc (range 1 6))
     (5 10 17 26 37)
	 user> ((mapc sq inc) (range 1 6))
     (4 9 16 25 36)"
  (let [[fns xs] (partition-by fn? args)
        g (apply comp fns)]
    (if (empty? xs)
      (partial map g)
      (apply map g xs ))))