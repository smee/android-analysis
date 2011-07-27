(ns android.experiments.xmlpull
  (:import
    org.xmlpull.v1.XmlPullParser))

;;;; Parsing via xmlpullparser
(defn- attrs [xpp]
  (for [i (range (.getAttributeCount xpp))]
    [(keyword (.getAttributeName xpp i))
     (.getAttributeValue xpp i)]))

(defn- ns-decs [xpp]
  (let [d (.getDepth xpp)]
    (for [i (range (.getNamespaceCount xpp (dec d)) (.getNamespaceCount xpp d))]
      (let [prefix (.getNamespacePrefix xpp i)]
        [(keyword (str "xmlns" (when prefix (str ":" prefix))))
         (.getNamespaceUri xpp i)]))))

(defn- attr-hash [xpp]
  (into {} (concat (ns-decs xpp) (attrs xpp))))

(defn- pull-step 
  "lazy sequence of tags with attributes as maps. Keys: :tag :depth :attr"
  [xpp]
  (let [step (fn [xpp]
               (condp = (.next xpp)
                 XmlPullParser/START_TAG
                 (cons {:tag (keyword (.getName xpp)) :depth (.getDepth xpp) :attr (attr-hash xpp)}
                   (pull-step xpp))
                 XmlPullParser/END_TAG
                 (cons {:tag (keyword (str "/" (.getName xpp))) :depth (.getDepth xpp)}
                   (pull-step xpp))
                 XmlPullParser/TEXT
                 (cons nil (pull-step xpp))
                 nil))]
    (remove nil? (lazy-seq (step xpp)))))

(defn- init-parser [filename]
  (doto (org.kxml2.io.KXmlParser.) 
    (.setFeature XmlPullParser/FEATURE_PROCESS_NAMESPACES true)
    (.setInput (clojure.java.io/reader filename))))

(defn create-intent-filters 
  "Extract all intent-filter definitions and create instances of android.IntentFilter.
Use it's .match method to decide whether an intent matches a filter."
  [filename]
  (let [parser (init-parser filename)
        tags   (pull-step parser)]    
    (for [i-f (filter #(= :intent-filter (:tag %)) tags)]
      (doto (android.IntentFilter.) 
        (.readFromXml parser)))))