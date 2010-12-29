(ns android-manifest.graphviz)

(defn get-ref-strings [all-apps slot-value color]
  (distinct (flatten
    (for [{app-name :name refs slot-value} all-apps]
      (for [[action dep-apps] refs]
        (for [dep-app dep-apps]
          (str
            \" dep-app \" "[shape=box] \n"
            \" dep-app \" "->" \" action \" " [color=" color ", style=dotted] ;\n")))))))


(defn get-def-strings [all-apps slot-name color]
  (let [action-defs (for [{name :name refs slot-name} all-apps]
                       [name
                        (for [[action dependent-apps] refs :when (not-empty dependent-apps)]
                         action)])]
    
    (for [[name actions] action-defs :when (not-empty actions)]
      (str
        \" name \" "[shape=box] ; \n"
        \" name \" "->{"
        (apply str (for [action actions]
                     (str "\"" action "\" ")))
        "} [color=" color ",arrowhead=\"diamond\"] ; \n"))))
           

(defn graphviz [apps]
  "Render potential android application activitiy references."
  (str 
    "digraph {" \newline
    ;"overlap=\"compress\"" \newline
    ;; app to action strings
    (apply str (interpose " " (get-def-strings apps :action-refs "green")))
    (apply str (interpose " " (get-def-strings apps :category-refs "blue")))
    (apply str (interpose " " (get-def-strings apps :service-refs "chocolate")))
    ;; dependent apps to action strings
    (apply str (interpose " " (get-ref-strings apps :action-refs "green")))
    (apply str (interpose " " (get-ref-strings apps :category-refs "blue")))
    (apply str (interpose " " (get-ref-strings apps :service-refs "chocolate")))
    "}"))

