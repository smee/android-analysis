(defproject android-manifest "1.0.0-SNAPSHOT"
  :description "FIXME: write"
  :dev-dependencies  [[vimclojure/server "2.2.0-SNAPSHOT"]
                      [marginalia "0.6.0"]
                      [slamhound "1.2.0"]
                      [clojure-refactoring "0.5.0"]
                      ] 
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.apache.lucene/lucene-core "3.0.2"]
                 [incanter "1.2.3" :exclusions [swank-clojure]]
                 [org.clojars.nathell/redis-clojure "1.2.7"]
                 [org.clojars.smee/archive "0.1.0-SNAPSHOT"]
                 [org.clojars.smee/common "1.0.0-SNAPSHOT"]
                 ;; database
;                 [clojureql "1.1.0-SNAPSHOT"]
;                 [lobos "0.8.0-SNAPSHOT"]
;                 [com.h2database/h2 "1.3.154"]
                 [congomongo "0.1.6-SNAPSHOT"]
                ])
