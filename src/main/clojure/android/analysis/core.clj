(ns android.analysis.core
  (:use 
    [clojure.contrib.zip-filter.xml :only (xml-> xml1-> attr)]
    [clojure.java.io :only (input-stream)]
    android.market.archive
    android-manifest.util
    android-manifest.serialization)
  (:require
    [clojure.contrib.zip-filter :as zf]
    [clojure.contrib.zip-filter.xml :as zfx]
    [clojure.zip :as zip]
    [clojure.xml :as xml]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Android documentation for intent filter:
;; ========================================
;; Only three aspects of an Intent object are consulted when the object is tested against an intent filter:
;;
;;   * action
;;   * data (both URI and data type)
;;   * category
;; 
;; Actions:
;; ========
;;   ...while an Intent object names just a single action, a filter may list more than one. 
;;   The list cannot be empty; a filter must contain at least one <action> element, or it will block all intents.
;; 
;;   To pass this test, the action specified in the Intent object must match one of the actions listed in the filter. If the object or the filter does not specify an action, the results are as follows:
;;    * If the filter fails to list any actions, there is nothing for an intent to match, so all intents fail the test. No intents can get through the filter.
;;    * On the other hand, an Intent object that doesn't specify an action automatically passes the test — as long as the filter contains at least one action.
;;
;; Categories:
;; ===========
;;   For an intent to pass the category test, every category in the Intent object must match a category in the filter. 
;;   The filter can list additional categories, but it cannot omit any that are in the intent.
;;   In principle, therefore, an Intent object with no categories should always pass this test, 
;;   regardless of what's in the filter. That's mostly true. However, with one exception, Android 
;;   treats all implicit intents passed to startActivity() as if they contained at least one category: 
;;   "android.intent.category.DEFAULT" (the CATEGORY_DEFAULT constant). Therefore, activities that 
;;   are willing to receive implicit intents must include "android.intent.category.DEFAULT" in their 
;;   intent filters. 
;;
;;   ... the DEFAULT category ... is there because the Context.startActivity() and Activity.startActivityForResult() methods treat all intents as if they contained the DEFAULT category — with just two exceptions:
;;     * Intents that explicitly name the target activity
;;     * Intents consisting of the MAIN action and LAUNCHER category

;;
;; Data
;; ====
;;    There are separate attributes — scheme, host, port, and path ... Each of these attributes is optional...
;;    When the URI in an Intent object is compared to a URI specification in a filter, it's compared only to the 
;;    parts of the URI actually mentioned in the filter. For example, if a filter specifies only a scheme, 
;;    all URIs with that scheme match the filter. If a filter specifies a scheme and an authority but no path, 
;;    all URIs with the same scheme and authority match, regardless of their paths. If a filter specifies a scheme, 
;;    an authority, and a path, only URIs with the same scheme, authority, and path match. However, a path 
;;    specification in the filter can contain wildcards to require only a partial match of the path.
;;    
;;    The type attribute of a <data> element specifies the MIME type of the data. It's more common in filters than 
;;    a URI. Both the Intent object and the filter can use a "*" wildcard for the subtype field — for example, 
;;    "text/*" or "audio/*" — indicating any subtype matches.
;;    
;;    The data test compares both the URI and the data type in the Intent object to a URI and data type specified 
;;    in the filter. The rules are as follows:
;;    
;;       1. An Intent object that contains neither a URI nor a data type passes the test only if the 
;;          filter likewise does not specify any URIs or data types.
;;       2. An Intent object that contains a URI but no data type (and a type cannot be inferred from 
;;          the URI) passes the test only if its URI matches a URI in the filter and the filter likewise 
;;          does not specify a type. This will be the case only for URIs like mailto: and tel: that do not refer to actual data.
;;       3. An Intent object that contains a data type but not a URI passes the test only if the filter 
;;          lists the same data type and similarly does not specify a URI.
;;       4. An Intent object that contains both a URI and a data type (or a data type can be inferred 
;;          from the URI) passes the data type part of the test only if its type matches a type listed 
;;          in the filter. It passes the URI part of the test either if its URI matches a URI in the 
;;          filter or if it has a content: or file: URI and the filter does not specify a URI. In other 
;;          words, a component is presumed to support content: and file: data if its filter lists only a data type.
    
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord Intent-Filter [class type actions categories])
(defrecord Android-App [name version package sdk-version filters])


(defn- find-names-of
  "Find all android components, for example activities or services from 
androidmanifest.xml files using zipper traversals."
  ([xml type]
    (let [package-name (xml1-> xml (attr :package))
          ;; find all activities, services, receivers that have at least one intent filter
          components (xml-> xml :application zf/children #(-> % zip/node :tag #{:activity :receiver :service} ))]
    (into #{} 
      (xml-> components :intent-filter :action (attr :android:name))))))


(defn load-android-manifest [app-name manifest]
  "Parse android app manifest."
  (let [x             (zip/xml-zip (xml/parse (input-stream manifest)))
        package-name  (xml1-> x (attr :package))
        version       (xml1-> x (attr :android:versionCode))
        sdk-version   (or (xml1-> x :uses-sdk (attr :android:minSdkVersion)) "0")
        actions       (find-names-of x :activity)
        services      (find-names-of x :service)
        receivers     (find-names-of x :receiver)]
    (Android-App. 
      app-name 
      version 
      package-name
      sdk-version
      nil)))

(defn load-apps-from-disk 
  "Parse android app manifest xml files."
  [manifest-files]
  (pmap 
    (fn [f]
      (let [app-name (-> f .getParentFile .getName)]
        (load-android-manifest app-name f)))
    manifest-files))

(defn load-apps-from-zip
  "Parse android app manifest files within a zip archive."
  [zip-file]
  (process-entries zip-file load-android-manifest ".*AndroidManifest.xml"))
  
(defn unique-apps 
  "Sort by descending version, filter all apps where version and path are equals. Should result
in loading android apps without duplicates (same package, lower versions)."
  [apps]
  (distinct-by :package 
    (reverse 
      (sort-by :version apps)))) 


(defn- find-manifests 
  "Find all AndroidManifest.xml files in any subdirectory of dir."
  [dir]
  (find-file dir #".*AndroidManifest.xml"))



