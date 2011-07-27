(ns android.tools.create-lucene-index
  (:use [android.tools.lucene])
  (:gen-class))

(defn -main [& args]
  (if (= 2 (count args))
    (let [lucene-dir   (first args)
          dir-to-index (second args)]
      (printf "\n\nCreating lucene index at %s for all smali files in %s... (may take a while)\n\n" lucene-dir dir-to-index)
      (create-lucene-index dir-to-index lucene-dir)
      (println "Done."))
    ;;else
    (println "Please call with two parameters: [where to store lucene index] [directory to index]")))