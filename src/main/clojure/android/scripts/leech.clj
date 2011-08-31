(ns android.scripts.leech
  (:use 
    [android.market.leech :only (batch-download-newest)]
    [android.market.download :only (download-all-apps)]
    [clojure.contrib.io :only (file)]
    [org.clojars.smee.time :only (date-string)]))


(def cred-files 
  ["marketcredentials.properties" 
   "marketcredentials2.properties" 
   "marketcredentials3.properties" 
   "marketcredentials4.properties" 
   "marketcredentials5.properties"
   "marketcredentials6.properties"
   "marketcredentials7.properties"])

 (time
   (do
     (batch-download-newest cred-files)
     (apply 
       (partial download-all-apps (file "results/market-apps") "e:/android/original") 
       cred-files)))
