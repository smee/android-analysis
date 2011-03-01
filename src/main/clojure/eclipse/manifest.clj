(ns eclipse.manifest
  (:use
    [clojure.contrib.string :only (replace-re)]
    [clojure.java.io :only (file reader)]
    [clojure.contrib.io :only (read-lines)]
    [clojure.string :only (trim)]
    [android.market.archive :only (extract-entry process-entries)]))

(defn find-plugins [& dirs]
  (let [fseqs (map #(file-seq (file %)) dirs)
        fseq (apply concat fseqs)
        in-plugin-dir (filter #(= "plugins" (.getName (.getParentFile %))) fseq)
        files (filter (memfn isFile) in-plugin-dir)]
    (filter #(.endsWith (.getName %) ".jar") files)))

(defn unique-plugins
  "By jar file name without version."
  [& dirs]
  (let [plugins (apply find-plugins dirs)]        
    (->> plugins
      (map (memfn getName))
      (map (partial replace-re #"_.*" ""))
      sort
      distinct)))

(defn- file-type [f]
  (let [name (.toLowerCase (str f))]
    (cond 
      (.endsWith name ".jar") :jar
      (.endsWith name "manifest.mf") :manifest
      (.contains name "[b@") :byte-arr
      :else f)))

(defmulti read-manifest file-type)
(defmethod read-manifest :jar [plugin]
  (process-entries plugin #(read-manifest %2) #".*MANIFEST.MF"))
(defmethod read-manifest :manifest [f]
  (-> f reader read-lines))
(defmethod read-manifest :byte-arr [f]
  (-> f reader read-lines))
(defmethod read-manifest nil [f]
  (println "Could not read manifest from " f))

(defn bundle-name [manifest]
  (let [sn "Bundle-SymbolicName:"
        name-line (first (filter #(.startsWith % sn) manifest))]
    (when name-line
      (let [bundle-name (trim (subs name-line (count sn)))]
        (if (some #{\;} bundle-name)
          (subs bundle-name 0 (.indexOf bundle-name (int \;)))
          bundle-name)))))

(defn find-manifest-files [& dirs]
  (let [fseqs (map #(file-seq (file %)) dirs)
        fseq  (apply concat fseqs)]
    (filter #(= (.toUpperCase (.getName %)) "MANIFEST.MF") fseq)))

(defn read-all-bundle-names [plugins]
  (pmap (comp bundle-name read-manifest) plugins))


(comment
  (def p (unique-plugins #_"D:/cygwin/tmp/eclipse/_marketmirrors"
                          "D:/eclipse/"))
  (read-manifest (file "D:/_vm-eclipse/yoxos-complete/eclipse/plugins/zigen.plugin.db_1.0.6.v20080209.jar"))
  
  (def plugins (find-plugins "D:/_vm-eclipse/yoxos-complete/eclipse/plugins" "D:/_vm-eclipse/marketplace"))
  (def names (distinct (read-all-bundle-names plugins)))
  (count names)
  
  ;; count all manifests from
  ;; * eclipse marketplace
  ;; * yoxos complete (selected everything and the kitchen sink)
  ;; * real life eclipse installations
  ;; * current source code from dev.eclipse.org/git/
  (def mfs (map bundle-name 
             (concat
               (process-entries (file "d:/_vm-eclipse/src-manifests.zip.jar") #(read-manifest %2) #".*manifest")
               (apply concat (pmap read-manifest (find-plugins "D:/_vm-eclipse/yoxos-complete/eclipse/plugins" "D:/_vm-eclipse/marketplace" "d:/eclipse")))
               (pmap read-manifest (find-manifest-files "D:/_vm-eclipse/yoxos-complete/eclipse/plugins")))))
    (println "number of unique plugins (by symbolic name)" (count (distinct mfs)))
  
  )