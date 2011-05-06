(ns android.analysis.hash
  (:import
     (java.security 
       NoSuchAlgorithmException
       MessageDigest)
     (java.math BigInteger)))

(defn md5
  "Compute the hex MD5 sum of a byte array."
  [#^bytes b]
  (let [alg (doto (MessageDigest/getInstance "MD5")
              (.reset)
              (.update b))]
    (try
      (.toString (new BigInteger 1 (.digest alg)) 16)
      (catch NoSuchAlgorithmException e
        (throw (new RuntimeException e))))))

(defn md5-of [obj]
  (md5 (.getBytes (print-str obj))))