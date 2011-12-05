(ns android.experiments.daily
  (:use
    [incanter core charts
     [stats :only (linear-model)]]
    [org.clojars.smee 
     [file :only (find-files)]
     [map :only (map-values)]
     [util :only (per-thread-singleton)]]
    ))

(def df (per-thread-singleton #(java.text.SimpleDateFormat. "yyyyMMdd")))

(defn- use-time-axis 
  "Replace domain axis by date/time axis."
  [chart]
  (let [plot (.getPlot chart)
        axis (doto (org.jfree.chart.axis.DateAxis.) (.setDateFormatOverride (df)))]
    (.setDomainAxis plot axis)))



(defn read-date-stats [input-dir] 
  (let [apks (find-files input-dir)
        days (map #(.format (df) (java.util.Date. (.lastModified %))) apks)
        by-day (group-by identity days)] 
    (map-values count by-day)))

(defn show-daily-chart [by-day]
  (let [x (map #(.getTime (.parse (df) %)) (keys by-day))
        y (vals by-day)
        lm (linear-model y x)
        chart (doto (scatter-plot x y 
                                  :title "Apps per day"
                                  :x-label "Date" 
                                  :y-label "no. of apps downloaded") 
                (use-time-axis)
                (add-lines x (:fitted lm) :series-label "Trend (OLS Regression)"))]
    (view chart)))