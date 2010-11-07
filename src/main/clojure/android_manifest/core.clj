(ns android-manifest.core
  (:use [clojure.xml :as xml]
        ;;[clojure.contrib.zip-filter.xml :as zip]
        [clojure.set :only [intersection difference union map-invert]]
        [clojure.pprint :only (pprint)]
        [clojure.contrib.seq-utils :only (separate)]
        android-manifest.lucene
        android-manifest.serialization
        android-manifest.util)
  (:import java.io.File))


(defrecord Android-App [name version package actions categories action-refs category-refs sdkversion possible-action-calls])

(defn collect-android:name [xml tag]
  (distinct
    (filter identity
      (map 
        #(-> % :attrs :android:name)
        (filter #(= tag (:tag %)) xml))))) 

(defn android-specific? [s]
  (contains? 
    #{"android.accounts.AccountAuthenticator"      
       "android.accounts.LOGIN_ACCOUNTS_CHANGED"
       "android.app.action.DEVICE_ADMIN_ENABLED"     
       "android.app.action.ENTER_CAR_MODE"
       "android.appwidget.action.ALARM_ACTION"     
       "android.appwidget.action.APPWIDGET_CONFIGURE"
       "android.appwidget.action.APPWIDGET_DELETED"    
       "android.appwidget.action.APPWIDGET_DISABLED"
       "android.appwidget.action.APPWIDGET_ENABLED"    
       "android.appwidget.action.APPWIDGET_PICK"
       "android.appwidget.action.APPWIDGET_UPDATE"    
       "android.appwidget.action.CLICK_WIDGET_01"
       "android.appwidget.action.CLICK_WIDGET_02"   
       "android.appwidget.action.CLICK_WIDGET_03"
       "android.appwidget.action.CLICK_WIDGET_04"
       "android.appwidget.action.CLICK_WIDGET_05"
"android.appwidget.action.LEFTBUTTON"
"android.appwidget.action.LOADNETWORK"
"android.appwidget.action.RIGHTBUTTON"
"android.appwidget.action.UPDATE_WIDGET_ALL"
"android.appwidget.action.UPDATE_WIDGET_ME"
"android.bluetooth.adapter.action.STATE_CHANGED"
"android.bluetooth.device.action.ACL_CONNECTED"
"android.bluetooth.device.action.FOUND"
"android.bluetooth.headset.action.STATE_CHANGED"
"android.bluetooth.intent.action.NAME_CHANGED"
"android.bluetooth.intent.action.PAIRING_CANCEL"
"android.bluetooth.intent.action.PAIRING_REQUEST"
"android.bluetooth.intent.action.REMOTE_DEVICE_CLASS_UPDATED"
"android.bluetooth.intent.action.REMOTE_DEVICE_DISCONNECTED"
"android.content.SyncAdapter"
"android.credentials.UNLOCK"
"android.intent.action.ABOUT"
"android.intent.action.ACTION_APPROTECT"
"android.intent.action.ACTION_APPROTECT_CLR"
"android.intent.action.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE"
"android.intent.action.ACTION_POWER_CONNECTED"
"android.intent.action.ACTION_POWER_DISCONNECTED"
"android.intent.action.ACTION_SHUTDOWN"
"android.intent.action.ACTIVE_WORKOUT"
"android.intent.action.AIRPLANE_MODE"
"android.intent.action.ALARM"
"android.intent.action.ALARM_CHANGED"
"android.intent.action.ALL_APPS"
"android.intent.action.ANSWER"
"android.intent.action.ANY_DATA_STATE"
"android.intent.action.ATTACH_DATA"
"android.intent.action.BATTERY_CHANGED"
"android.intent.action.BATTERY_LOW"
"android.intent.action.BATTERY_OKAY"
"android.intent.action.BOOT"
"android.intent.action.BOOT_COMPLETED"
"android.intent.action.CALL"
"android.intent.action.CALL_BUTTON"
"android.intent.action.CALL_PRIVILEGED"
"android.intent.action.CAMERA_BUTTON"
"android.intent.action.CATEGORIESLIST"
"android.intent.action.CHOOSER"
"android.intent.action.CLOSE_SYSTEM_DIALOGS"
"android.intent.action.COMMENTSLIST"
"android.intent.action.CONFIGURATION3"
"android.intent.action.CONFIGURATION_CHANGED"
"android.intent.action.CONTENT_CHANGED"
"android.intent.action.CREATE_LIVE_FOLDER"
"android.intent.action.CREATE_SHORTCUT"
"android.intent.action.DATA_SMS_RECEIVED"
"android.intent.action.DATE_CHANGED"
"android.intent.action.DELETE"
"android.intent.action.DETAILS"
"android.intent.action.DEVICE_STORAGE_LOW"
"android.intent.action.DEVICE_STORAGE_OK"
"android.intent.action.DIAL"
"android.intent.action.DOCK_EVENT"
"android.intent.action.DOWNLOAD_COMPLETED"
"android.intent.action.EDIT"
"android.intent.action.EVENT_REMINDER"
"android.intent.action.Edit"
"android.intent.action.GET_CONTENT"
"android.intent.action.GOAL_START"
"android.intent.action.GTALK_CONNECTED"
"android.intent.action.HEADSET_PLUG"
"android.intent.action.HOME"
"android.intent.action.INITIAL_VIEW"
"android.intent.action.INPUT"
"android.intent.action.INPUT_METHOD_CHANGED"
"android.intent.action.INSERT"
"android.intent.action.LABELVIEW"
"android.intent.action.LAUNCH"
"android.intent.action.LOCALE_CHANGED"
"android.intent.action.LOCATION"
"android.intent.action.LOGIN"
"android.intent.action.MAIN"
"android.intent.action.MAP"
"android.intent.action.MEDIA_BAD_REMOVAL"
"android.intent.action.MEDIA_BUTTON"
"android.intent.action.MEDIA_CHECKING"
"android.intent.action.MEDIA_EJECT"
"android.intent.action.MEDIA_MOUNTED"
"android.intent.action.MEDIA_NOFS"
"android.intent.action.MEDIA_REMOVED"
"android.intent.action.MEDIA_SCANNER_FINISHED"
"android.intent.action.MEDIA_SCANNER_STARTED"
"android.intent.action.MEDIA_SEARCH"
"android.intent.action.MEDIA_SHARED"
"android.intent.action.MEDIA_UNMOUNTABLE"
"android.intent.action.MEDIA_UNMOUNTED"
"android.intent.action.MUSIC_PLAYER"
"android.intent.action.Map"
"android.intent.action.NEW_OUTGOING_CALL"
"android.intent.action.PACKAGE_ADDED"
"android.intent.action.PACKAGE_CHANGED"
"android.intent.action.PACKAGE_DATA_CLEARED"
"android.intent.action.PACKAGE_INSTALL"
"android.intent.action.PACKAGE_REMOVED"
"android.intent.action.PACKAGE_REPLACED"
"android.intent.action.PACKAGE_RESTARTED"
"android.intent.action.PHONE_STATE"
"android.intent.action.PHOTO"
"android.intent.action.PHOTO_ALBUM"
"android.intent.action.PICK"
"android.intent.action.PICK_ACTIVITY"
"android.intent.action.PICK_AUTORUN_KILLER"
"android.intent.action.PICK_AUTORUN_KILLER2"
"android.intent.action.PICK_EASY_KILLER2"
"android.intent.action.PICK_EASY_PROTECTOR"
"android.intent.action.PICK_QUICK_KILLER"
"android.intent.action.PICK_SAVE_BATTERY_KILLER"
"android.intent.action.PICK_SECURITY_MANAGER"
"android.intent.action.PICK_TASK_MANAGER"
"android.intent.action.PRODUCTDESCRIPTION"
"android.intent.action.PROVIDER_CHANGED"
"android.intent.action.PROXY_CHANGE"
"android.intent.action.Pick"
"android.intent.action.REBOOT"
"android.intent.action.REFERENCE"
"android.intent.action.REMOTE_INTENT"
"android.intent.action.RINGTONE_PICKER"
"android.intent.action.RUN"
"android.intent.action.SCAN"
"android.intent.action.SCAN_WIFINETWORK"
"android.intent.action.SCREEN_OFF"
"android.intent.action.SCREEN_ON"
"android.intent.action.SCREEN_RECEIVER"
"android.intent.action.SEARCH"
"android.intent.action.SEARCHLIST"
"android.intent.action.SEARCH_HURO"
"android.intent.action.SEARCH_LONG_PRESS"
"android.intent.action.SEARCH_NAME_LIST"
"android.intent.action.SEARCH_NAME_LIST_DETAILS"
"android.intent.action.SEARCH_SYOKUJI"
"android.intent.action.SEARCH_YADO"
"android.intent.action.SECURITY_GUARDER_SERVICE"
"android.intent.action.SELECTION3"
"android.intent.action.SEND"
"android.intent.action.SENDTO"
"android.intent.action.SEND_MULTIPLE"
"android.intent.action.SERVICE_STATE"
"android.intent.action.SET_WALLPAPER"
"android.intent.action.SHOPPING3"
"android.intent.action.SHOW"
"android.intent.action.SHOW_ALL"
"android.intent.action.SHUTDOWN"
"android.intent.action.SIM_STATE_CHANGED"
"android.intent.action.SPLASH"
"android.intent.action.STARTSCREEN3"
"android.intent.action.START_TTS_ENGINE"
"android.intent.action.SUBSTITUTIONS3"
"android.intent.action.SYNC"
"android.intent.action.SYNC_STATE_CHANGED"
"android.intent.action.Search"
"android.intent.action.TIMEZONE_CHANGED"
"android.intent.action.TIME_SET"
"android.intent.action.TIME_TICK"
"android.intent.action.TOOLS"
"android.intent.action.TWITTER"
"android.intent.action.TWITTER_FOLLOW"
"android.intent.action.TWITTER_PROFILE"
"android.intent.action.UMS_CONNECTED"
"android.intent.action.UMS_DISCONNECTED"
"android.intent.action.USERS"
"android.intent.action.USER_PRESENT"
"android.intent.action.USE_TTS"
"android.intent.action.VIEW"
"android.intent.action.VIEW_DETAIL"
"android.intent.action.VOICE_COMMAND"
"android.intent.action.View"
"android.intent.action.WALLPAPER_CHANGED"
"android.intent.action.WEBVIEW"
"android.intent.action.WEB_SEARCH"
"android.intent.action.WIDGET_RECEIVER"
"android.intent.action.WIZARDSETTINGS3"
"android.intent.action.WIZARDTWITTERQ3"
"android.intent.action.edit"
"android.intent.action.view"
"android.intent.action.web"
"android.intent.category.EMBED"
"android.intent.category.DEFAULT"
"android.intent.category.LAUNCHER"
"android.location.GPS_FIX_CHANGE"
"android.media.AUDIO_BECOMING_NOISY"
"android.media.RINGER_MODE_CHANGED"
"android.media.VIBRATE_SETTING_CHANGED"
"android.media.action.IMAGE_CAPTURE"
"android.media.action.MEDIA_PLAY_FROM_SEARCH"
"android.media.action.STILL_IMAGE_CAMERA"
"android.media.action.VIDEO_CAMERA"
"android.media.action.VIDEO_CAPTURE"
"android.net.conn.CONNECTIVITY_CHANGE"
"android.net.http.NETWORK_STATE"
"android.net.wifi.RSSI_CHANGED"
"android.net.wifi.SCAN_RESULTS"
"android.net.wifi.STATE_CHANGE"
"android.net.wifi.WIFI_AP_STATE_CHANGED"
"android.net.wifi.WIFI_STATE_CHANGED"
"android.net.wifi.supplicant.CONNECTION_CHANGE"
"android.net.wifi.supplicant.STATE_CHANGE"
"android.pineone.intent.action.PUSH_CLIENT"
"android.provider.Telephony.MMS_RECEIVED"
"android.provider.Telephony.SECRET_CODE"
"android.provider.Telephony.SIM_FULL"
"android.provider.Telephony.SMS_RECEIVED"
"android.provider.Telephony.SMS_REJECTED"
"android.provider.Telephony.WAP_PUSH_RECEIVED"
"android.settings.DATE_SETTINGS"
"android.settings.LOCATION_SOURCE_SETTINGS"
"android.settings.SETTINGS"
"android.settings.WIRELESS_SETTINGS"
"android.speech.tts.engine.CHECK_TTS_DATA"
"android.speech.tts.engine.GET_SAMPLE_TEXT"
"android.speech.tts.engine.INSTALL_TTS_DATA"
"android.speech.tts.engine.TTS_DATA_INSTALLED"
"com.android.contacts.action.FILTER_CONTACTS"
"com.android.launcher.action.INSTALL_SHORTCUT"
"com.android.vending.INSTALL_REFERRER"
"com.google.android.c2dm.intent.RECEIVE"}
     s))

(defn file-exists [file]
  (and (.exists file) (< 0 (.length file))))
  
(defn load-android-app [manifest-file]
  "Parse android app manifest."
  (let [xml                 (do #_(println "parsing " manifest-file )(xml-seq (xml/parse manifest-file)))
        app-name            (-> manifest-file .getParentFile .getName)
        package-name        (-> xml first :attrs :package)
        version             (-> xml first :attrs :android:versionName)
        all-actions         (collect-android:name xml :action)
        sdkversion          (or (->> xml (filter #(= :uses-sdk (:tag %))) first :attrs :android:minSdkVersion) "1")
        non-android-actions (into #{} (remove android-specific? all-actions))
        all-categories      (collect-android:name xml :category)
        non-android-categories (into #{} (remove android-specific? all-categories))
        classes-dex         (File. (.getParentFile manifest-file) "classes.dex")
        possible-action-calls (if (file-exists classes-dex) (deserialize (.toString classes-dex)) '())]
    
    (Android-App. 
      app-name 
      version 
      package-name
      non-android-actions
      non-android-categories
      {}
      {}
      sdkversion
      possible-action-calls)))
 
(defn load-unique-apps [manifest-files]
  "Sort by descending version, filter all apps where version and path are equals. Should result
in loading android apps without duplicates (same package, lower versions)."
  (let [android-apps (map load-android-app manifest-files)]
    (distinct-by :package (reverse (sort-by :version android-apps)))))

(defn unique-app-names [apps]
  (set (map :name apps)))

(defn- possible-action-call-map [apps]
  "Create map of all action references in decompiled apps to app names that seem to call these actions."
  (reduce 
    (fn [m app] (reduce 
                  #(update-in %1 [%2] conj (:name app)) 
                  m 
                  (:possible-action-calls app)))
    {} 
    apps))

(defn- query-references [actions calls-action?-map]
  (reduce (fn [res action] (assoc res action (get calls-action?-map action #{}))) {} actions))

(defn find-possible-references [apps]
  (let [calls-action?-map (possible-action-call-map apps)]
  (map 
    (fn [{actions :actions categories :categories :as app}]
      (assoc app 
        :action-refs   (query-references actions calls-action?-map)
        :category-refs (query-references categories calls-action?-map)))
    apps)))

 (defn valid-action? [s]
  (< 1 (count (filter #(= % \.)  s))))
 
 
 (defn- filter-references [ref-map name-app-map k]
  "Retain only apps that call an intent that is not defined within their own
   manifest.xml."
  (into {}
    (for [[action apps-calling-action] ref-map]
      (let [filtered-refs (remove #(contains? (get-in name-app-map  [% k]) action) apps-calling-action)]
        [action filtered-refs]))))

 (defn- name-app-map [apps]
   (into {} (map #(hash-map (:name %) %) apps)))
 
(defn filter-included-actions [apps]
  "Retain only apps that call an intent that is not defined within their own
   manifest.xml."
  (let [name-app-map (name-app-map apps)]
    (for [{arefs :action-refs crefs :category-refs :as app} apps]
      (assoc app 
        :action-refs   (filter-references arefs name-app-map :actions)
        :category-refs (filter-references crefs name-app-map :categories)))))

(defn find-app [like]
  ""
  (fn [app] (.contains (:name app) like)))


(defn print-findings [real-external-refs all-apps manifest-files]
  (let [openintent-actions    (distinct (filter #(.contains % "openintent") (mapcat #(keys (:action-refs %)) real-external-refs)))
        openintent-categories (distinct (filter #(.contains % "openintent") (mapcat #(keys (:category-refs %)) real-external-refs)))
        action-call-freq      (apply merge (flatten
                                             (for [m (map :action-refs real-external-refs)]
                                               (for [[k v] m]
                                                 {k (count v)}))))
        sorted-freq              (into (sorted-map-by (fn [k1 k2] (compare (get action-call-freq k2) (get action-call-freq k1))))
                                   action-call-freq)
        no-defined-actions          (count (distinct (mapcat :actions all-apps)))
        no-external-actions-offered (count (distinct (mapcat #(keys (remove-empty-values (:action-refs %))) real-external-refs)))
        no-apps-calling-external    (count (distinct (apply concat (mapcat #(vals (:action-refs %)) real-external-refs))))
        ]
  (println 
    "# manifests: "                         (count manifest-files)
    "\n# unique Apps: "                     (count all-apps)
    "\n# of defined actions: "              no-defined-actions
    "\n# of externally used actions offered from apps: " no-external-actions-offered    
    "\n# apps calling external actions: "   no-apps-calling-external    
    "\n% of apps involved in real intent based relationships: <=" (double (* 100 (/ no-apps-calling-external (count all-apps))))
    "\n# openintents (actions): "           (count openintent-actions) openintent-actions
    "\n# of categories offered from apps: " (count (distinct (mapcat #(keys (remove-empty-values (:category-refs %))) real-external-refs)))
    "\n# apps calling foreign categories: " (count (distinct (mapcat #(vals (:category-refs %)) real-external-refs)))
    "\n# openintents (category): "          (count openintent-categories) openintent-categories
    "\nMost called actions: "               (take 3 sorted-freq)
    "\nMin. SDK Versions (version no./count: " (sort (frequencies (map :sdkversion all-apps)))
    )))

(defn- trim-possible-action-calls [apps]
  "Remove all strings that are no known action name."
   (let [existing-actions (into #{} (mapcat :actions apps))]
     (for [{p-a-c :possible-action-calls :as app} apps]
       (assoc app :possible-action-calls (filter existing-actions p-a-c)))))

(comment
   
 

 (def manifest-files (map #(File. %) (deserialize "d:/android/results/manifest-files-20101106.clj")))
 
)

(comment
  
  (set! *print-length* 15)
  (def app-sources "h:/android")
  (def manifest-files (find-file app-sources #".*AndroidManifest.xml"))
  (def apps (load-unique-apps manifest-files))
  
  (serialize (str "d:/android/results/raw-" (date-string) ".clj") (map (partial into {}) apps))
  ;(def apps (deserialize "d:/android/results/raw-20101107.clj"))
  
  (def trimmed-apps (trim-possible-action-calls apps))
  (def r (find-possible-references trimmed-apps))
  (def r2 (filter-included-actions r))
  (def r3 (map #(dissoc % :possible-action-calls) r2))
  
  ;(def r4 (foreign-refs-only r3))
  (print-findings r3 apps manifest-files)
  ;(print-findings r3 apps (range 0 24000))

  (use 'android-manifest.graphviz)
  (spit (str "d:/android/results/refviz-" (date-string) ".dot") (graphviz r3))
  (binding [*print-length* nil]
    (spit "d:/android/results/real-refs-20k.json" (with-out-str (pprint-json r3))))
  )

