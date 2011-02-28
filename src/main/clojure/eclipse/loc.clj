(ns eclipse.loc
  (:use 
    android.market.archive
    [clojure.contrib.string :only (split)]
    [clojure.contrib.io :only (read-lines)]))


(defn parse-cloc-output [archive] 
  (let [lines    (process-entries archive (fn [_ in] (read-lines in)))
      java-lines (map (partial filter #(.startsWith % "Java")) lines)
      splitted   (map (partial split #"\s+") (reduce concat java-lines))
      loc (->> splitted (map last) (map #(Integer/parseInt %)))
      comments (reduce + (->> splitted (map #(nth % 3)) (map #(Integer/parseInt %))))]
    (hash-map :loc (reduce + loc) :comments comments :individual-loc loc)))


(comment
  (use '(incanter core stats charts))
  (parse-cloc-output "D:/Dropbox/My Dropbox/Arbeit/Projekte/Thorsten/waterloo/eclipse-loc.zip")
  (def x (:individual-loc (def x (:individual-loc (parse-cloc-output "d:/android/sample/src/loc.zip")))))
  (def t* (bootstrap x median :size 10000))
  (quantile t* :probs [0.025 0.975])
  (mean t*)
  (median x)
  )

