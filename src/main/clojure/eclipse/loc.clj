(ns eclipse.loc
  (:use 
    android.market.archive
    [clojure.contrib.string :only (split)]
    [clojure.java.io :only (copy file reader)]
    [incanter.core :exclude (copy)]
    [incanter stats charts]))


(defn parse-cloc-output [archive] 
  (let [lines    (process-entries archive (fn [_ in] (line-seq (reader in))))
      java-lines (map (partial filter #(.startsWith % "Java")) lines)
      splitted   (map (partial split #"\s+") (reduce concat java-lines))
      loc (->> splitted (map last) (map #(Integer/parseInt %)))
      comments (reduce + (->> splitted (map #(nth % 3)) (map #(Integer/parseInt %))))]
    (hash-map :loc (reduce + loc) :comments comments :individual-loc loc)))

  (defn choose-random-sample [i files to] 
    (for [f (take i (repeatedly #(rand-nth files)))]
    (copy f (file to (str (hash f) ".jar")))))
  
  
  (defn analyze-loc [archive est-count]
    (let [result (parse-cloc-output archive)
          locs (:individual-loc result)
          loc (:loc result)
          t* (bootstrap x median :size 10000)
          loc-per-unit (mean t*)]
      {:loc-sum (* loc-per-unit est-count)
       :loc-mean loc-per-unit
       :ci-95 (quantile t* :probs [0.05 0.95])
       :raw locs}))

(comment
  (parse-cloc-output "D:/Dropbox/My Dropbox/Arbeit/Projekte/Thorsten/waterloo/eclipse-loc.zip")
  (def x (:individual-loc (def x (:individual-loc (parse-cloc-output "d:/android/sample/src/loc.zip")))))
  (def t* (bootstrap x median :size 10000))
  (quantile t* :probs [0.025 0.975])
  (mean t*)
  (median x)
  
  (choose-random-sample 100 
    (eclipse.manifest/find-plugins "D:/_vm-eclipse/yoxos-complete/eclipse/plugins" "D:/_vm-eclipse/marketplace" "d:/eclipse")
    "d:/_vm-eclipse/sample")
  
  (def x (analyze-loc "D:/_vm-eclipse/sample/sourcecount-100.zip" 7923))
  )

