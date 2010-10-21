(ns android-manifest.find-action-refs
  (:use [android-manifest core scribble]
    [clojure.contrib.json :only (pprint-json)])
  (:gen-class))

(defn -main [& args]
    (if (= 2 (count args))
      (let [lucene-dir   (first args)
            android-source-dir (second args)]
        (println "Querying lucene index for action references...")

          (let [manifest-files     (find-file android-source-dir #".*AndroidManifest.xml")
                android-apps             (foreign-refs-only (find-all-references manifest-files lucene-dir))
                real-external-refs (filter #(not-empty (:references-from %)) (filter-included-actions android-apps))
                output-filename    "real-refs-unique.json"
                output-dot         "real-external-refs-unique.dot"]
            (do
              (print-findings real-external-refs android-apps manifest-files)
              (spit output-filename (with-out-str (pprint-json real-external-refs)))
              (spit output-dot (graphviz real-external-refs))
              (println "Done. See results in " output-filename " and visualization in " output-dot))))
      ;; else
      (println "Please call with two parameters: /path/to/lucene/index /path/to/decompiled/apps")))