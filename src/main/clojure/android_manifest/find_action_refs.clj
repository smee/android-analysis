(ns android-manifest.find-action-refs
  (:use [android-manifest.core])
  (:gen-class))

(defn -main [& args]
    (if (= 2 (count args))
      (let [lucene-dir   (first args)
            android-source-dir (second args)]
        (println "Querying lucene index for action references...")
        (doall 
          (for [refmap (foreign-refs-only       
                         (find-all-references (find-file android-source-dir #".*AndroidManifest.xml") lucene-dir))]
            (println refmap)))
        (println "Done."))
      ;; else
      (println "Please call with two parameters: /path/to/lucene/index /path/to/decompiled/apps")))