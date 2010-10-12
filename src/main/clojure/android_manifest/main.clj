(ns android-manifest.main
  (:use [android-manifest.core])
  (:gen-class))

(defn -main [& args]
  (print (str (first (find-all-references "d:/android/manifests" "d:/android/lucene-index"))))
)
           
(-main)


