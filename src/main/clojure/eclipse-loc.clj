(use 'android.market.archive)
(require 'clojure.contrib.string)
(require 'clojure.contrib.io)


(let [lines         (process-entries "d:/android/eclipse-loc.zip" (fn [_ in] (clojure.contrib.io/read-lines in)))
        java-lines (map (partial filter #(.startsWith % "Java")) lines)
        splitted    (map (partial clojure.contrib.string/split #"\s+") (reduce concat java-lines))]
    (reduce + (->> l3 (map last) (map #(Integer/parseInt %)))))
