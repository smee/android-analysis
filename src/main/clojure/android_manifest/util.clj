(ns android-manifest.util)

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

