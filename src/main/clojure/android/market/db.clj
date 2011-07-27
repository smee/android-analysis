(ns android.market.db
  (:require [redis.core :as redis]))

(redis/with-server
  {:host "127.0.0.1" :port 6379 :db 0}
  (do
    (println "Reply:" (redis/hmset :foo2 :a 1 :b :2 :c "3"))
    (println "Getting value of key 'foo'")
    (println "Reply:" (redis/hgetall :foo2))))

