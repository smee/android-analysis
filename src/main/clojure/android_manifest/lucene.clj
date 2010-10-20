(ns android-manifest.lucene
  "Lucene related functions for creating/querying an index."
  (:import 
    org.apache.lucene.analysis.standard.StandardAnalyzer
    [org.apache.lucene.document Document Field Field$Store Field$Index]
    [org.apache.lucene.index IndexWriter IndexReader IndexWriter$MaxFieldLength]
    org.apache.lucene.queryParser.QueryParser
    org.apache.lucene.search.IndexSearcher
    [org.apache.lucene.store SimpleFSDirectory FSDirectory]
    org.apache.lucene.util.Version
    [java.io File FileReader]))

(defn- index-file [index-writer file]
  (when (.contains (.getCanonicalPath file) "smali")
    (.addDocument index-writer (doto (Document.)
                                 (.add (Field. "path" (.toString file) Field$Store/YES,Field$Index/NOT_ANALYZED))
                                 (.add (Field. "contents" (FileReader. file)))))))
  
(defn- index-all-recursively [index-writer file]
  (if (.isDirectory file)
    (doseq [child (.listFiles file)]
      (index-all-recursively index-writer child))
    (index-file index-writer file)))
  
(defn create-lucene-index 
  "Create or append to a lucene index in lucene-index-dir. Read the contents of all files in dir
recursively and add them to the index."
  [dir lucene-index-dir]
  (let [analyzer     (new StandardAnalyzer Version/LUCENE_CURRENT)
        index-writer (new IndexWriter 
                       (new SimpleFSDirectory (File. lucene-index-dir))
                       analyzer
                       true
                       IndexWriter$MaxFieldLength/UNLIMITED)]
    (index-all-recursively index-writer (new File dir))
    (.optimize index-writer)
    (.close index-writer)))

(defn search-lucene-seq [index-directory queries-seq]
  (with-open [lucene-dir     (#_org.apache.lucene.store.NIOFSDirectory. FSDirectory/open (new File index-directory))
              index-reader   (IndexReader/open lucene-dir)]
    (let [index-searcher (new IndexSearcher index-reader)
          analyzer       (new StandardAnalyzer Version/LUCENE_CURRENT)
          query-parser   (new QueryParser Version/LUCENE_CURRENT "contents" analyzer)]
      
      (doall 
        (for [q queries-seq]
          (let [query (.parse query-parser q)
                hits  (.search index-searcher query 1000000)]
            (doall
              (for [hit (.scoreDocs hits)]
                (.get (.doc index-searcher (.doc hit)) "path")))))))))

(defn search-lucene 
  "Search an existing lucene index using the given query. Syntax of the query can be found at 
http://lucene.apache.org/java/2_4_0/queryparsersyntax.html"
  [index-directory query-string]
  (with-open [lucene-dir     (FSDirectory/open (new File index-directory))
              index-reader   (IndexReader/open lucene-dir)]
    (let [index-searcher (new IndexSearcher index-reader)
          analyzer       (new StandardAnalyzer Version/LUCENE_CURRENT)
          query-parser   (new QueryParser Version/LUCENE_CURRENT "contents" analyzer)
          query          (.parse query-parser query-string)
          hits           (.search index-searcher query 1000000)]
      (doall
        (for [hit (.scoreDocs hits)]
          (.get (.doc index-searcher (.doc hit)) "path"))))))