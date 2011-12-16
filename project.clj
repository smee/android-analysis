(defproject android-manifest "1.0.0-SNAPSHOT"
  :description "FIXME: write"
  :dev-dependencies  [[vimclojure/server "2.2.0-SNAPSHOT"]
                      [marginalia "0.6.0"]
                      [slamhound "1.2.0"]
                      [clojure-refactoring "0.5.0"]
                      ] 
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/data.zip "0.1.0"]
                 [org.clojure/data.json "0.1.2"]
                 [org.clojure/core.incubator "0.1.0"]
                 [org.apache.lucene/lucene-core "3.0.2"]
                 [incanter "1.3.0-SNAPSHOT" :exclusions [swank-clojure]]
                 [org.clojars.smee/archive "0.2.0-SNAPSHOT"]
                 [org.clojars.smee/common "1.1.0"]
                 [congomongo "0.1.8-SNAPSHOT"]
                 [asm/asm-all "3.3.1"]
                ])
