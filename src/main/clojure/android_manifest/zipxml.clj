(ns android-manifest.zipxml
  (:require 
    [clojure.zip :as zip]
     [clojure.xml :as xml]
    [clojure.contrib.zip-filter :as zf])
  (:use [clojure.contrib.zip-filter.xml :as zfx]))

