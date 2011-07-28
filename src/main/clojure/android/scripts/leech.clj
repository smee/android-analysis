(ns android.scripts.leech
  (:use 
    [android.market.leech :only (batch-download-newest)]
    [android.market.download :only (download-all-apps)]
    [clojure.contrib.io :only (file)]
    [android.tools.util :only (date-string)]))


(def cred-files ["marketcredentials.properties" "marketcredentials2.properties" "marketcredentials3.properties" "marketcredentials4.properties" "marketcredentials5.properties"])
 (batch-download-newest cred-files)

(download-all-apps 
   (file "results/market-apps") 
   "/home/steffen/smbtest/original"
   "marketcredentials.properties" 
   "marketcredentials2.properties" 
   "marketcredentials3.properties" 
   "marketcredentials4.properties" 
   "marketcredentials5.properties"
   "marketcredentials6.properties"
   "marketcredentials7.properties")
