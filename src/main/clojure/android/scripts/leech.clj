(ns android.scripts.leech
  (:use 
    [android.market.leech :only (batch-download-newest)]
    [android.market.download :only (download-all-apps)]
    [clojure.java.io :only (file)]
    [org.clojars.smee.time :only (date-string)]
    [org.clojars.smee.file :only (find-files)]))


(def cred-files  (find-files "." #"marketcredentials.*.properties"))

 (time
   (do
     (batch-download-newest cred-files)
     (apply 
       (partial download-all-apps (file "results/market-apps") "e:/android/original") 
       cred-files)))
