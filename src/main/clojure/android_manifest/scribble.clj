(ns android-manifest.scribble
  (:use android-manifest.serialization
     [clojure.contrib.pprint :only (pprint)]))

(defn filter-references [ref-map r]
  "Retain only apps that call a intent that is not defined within their own
   manifest.xml."
  (into {}
    (filter (comp empty? vals)
      (for [[action apps] ref-map]
        (let [filtered-refs (filter #((complement contains?) (get-in r [% :actions]) action) apps)]
          [action filtered-refs])))))
    
(defn filter-included-actions [r]
  "Retain only apps that call a intent that is not defined within their own
   manifest.xml."
  (into {}
   (for [[app-name {refs :references-from :as m}] r]
     [app-name (assoc m :references-from (filter-references refs r))])))
    
(def android-apps (deserialize "results/potential-refs"))

(def real-external-refs (filter 
         (fn [[_ {v :references-from}]] (not-empty v)) 
         (filter-included-actions android-apps)))
  
(printf "Got %d real references between apps.\n" (count real-external-refs))
(pprint real-external-refs)
