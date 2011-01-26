(ns android.market.category
  (:use [android-manifest.util :only (reverse-map)]))

(def categories-i18n
  {"APP_WALLPAPER"          "APP_WALLPAPER" ;;todo   
   "APP_WIDGETS"            "Tools"     
   "ARCADE"                 "Arcade & Actionspiele" 
   "BOOKS_AND_REFERENCE"    "Bücher & Nachschlagewerke"     
   "BRAIN"                  "Rätsel & Denksport"                 
   "BUSINESS"               "Geschäftlich"          
   "CARDS"                  "Karten- & Glücksspiele"                   
   "CASUAL"                 "Gelegenheitsspiele"           
   "COMICS"                 "Comics"               
   "COMMUNICATION"          "Kommunikation"             
   "DEMO"                   "Demo"         
   "EDUCATION"              "Bildung"              
   "ENTERTAINMENT"          "Unterhaltung"             
   "FINANCE"                "Finanzen"        
   "GAME_WALLPAPER"         "GAME_WALLPAPER"    ;;todo    
   "GAME_WIDGETS"           "GAME_WIDGETS"      ;;todo     
   "HEALTH"                 "Gesundheit"               
   "HEALTH_AND_FITNESS"     "Gesundheit & Fitness"          
   "LIBRARIES"              "Softwarepakete"                  
   "LIBRARIES_AND_DEMO"     "Bibliotheken & Demos"                 
   "LIFESTYLE"              "Lifestyle"                  
   "MEDIA_AND_VIDEO"        "Medien & Videos"                 
   "MEDICAL"                "Medizin"             
   "MULTIMEDIA"             "Multimedia" 
   "MUSIC_AND_AUDIO"        "Musik & Audio"
   "NEWS"                   "News und Wetter"           
   "NEWS_AND_MAGAZINES"     "Nachrichten & Magazine"   
   "PERSONALIZATION"        "Personalisierung"                
   "PHOTOGRAPHY"            "Fotografie"   
   "PRODUCTIVITY"           "Effizienz-Tools"        
   "REFERENCE"              "Nachschlagen"          
   "SHOPPING"               "Shopping"       
   "SOCIAL"                 "Soziale Netze"                
   "SPORTS"                 "Sport"
   "SPORTS_GAMES"           "Sport"               
   "THEMES"                 "Themen"
   "TOOLS"                  "Tools"
   "TRANSPORTATION"         "Verkehr"
   "TRAVEL"                 "Reisen"
   "TRAVEL_AND_LOCAL"       "Reisen & Lokales"
   "WEATHER"                "Wetter"})

(def all-known-categories (set(keys categories-i18n)))
(def ger-en (reverse-map categories-i18n))

(defn get-category [app]
  {:post [(not-empty %)]}
  (let [c (:category app)]
    ;(println "Input:" c)
    (if (contains? all-known-categories c)
      c
      (get ger-en c))))
