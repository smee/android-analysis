(ns android.experiments.daily
  (:use
    [incanter core charts]
    [org.clojars.smee.file :only (find-files)]
    [clojure.contrib.singleton :only (per-thread-singleton)]))


(defn- use-time-axis 
  "Replace domain axis by date/time axis."
  [chart]
  (let [plot (.getPlot chart)
        axis (doto (org.jfree.chart.axis.DateAxis.) (.setDateFormatOverride (df)))]
    (.setDomainAxis plot axis)))


(def df (per-thread-singleton #(java.text.SimpleDateFormat. "yyyyMMdd")))

(defn read-date-stats [input-dir] 
  (let [apks (find-files input-dir)
        days (map #(.format (df) (java.util.Date. (.lastModified %))) apks)
        by-day (group-by identity days)] 
    by-day))

(defn show-daily-chart [by-day]
  (let [x (map #(.getTime (.parse (df) %)) (keys by-day))
        y (map count (vals by-day))
        chart (doto (scatter-plot x y 
                                  :title "Apps per day"
                                  :x-label "Date" 
                                  :y-label "no. of apps downloaded") (use-time-axis))]
    (view chart)))