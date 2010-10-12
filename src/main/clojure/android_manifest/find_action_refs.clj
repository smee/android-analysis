(ns android-manifest.find-action-refs
  (:use [android-manifest.core])
  (:gen-class))

(defn -main [& args]
    (if (= 2 (count args))
      (let [lucene-dir   (first args)
            manifest-dir (second args)]
        (println "Querying lucene index for action references...")
        (doall 
          (for [refmap (find-all-references manifest-dir lucene-dir)]
            (println refmap)))
        (println "Done."))
      ;; else
      (println "Please call with two parameters: /path/to/lucene/index /path/to/mined/manifests")))