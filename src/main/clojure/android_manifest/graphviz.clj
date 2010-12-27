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
           

(defn graphviz [real-external-refs]
  "Render potential android application activitiy references."
  (str 
    "digraph {" \newline
    ;"overlap=\"compress\"" \newline
    ;; app to action strings
    ;(apply str (interpose " " (get-def-strings real-external-refs :action-refs "green")))
    ;(apply str (interpose " " (get-def-strings real-external-refs :category-refs "blue")))
    (apply str (interpose " " (get-def-strings real-external-refs :service-refs "blue")))
    ;; dependent apps to action strings
    ;(apply str (interpose " " (get-ref-strings real-external-refs :action-refs "red")))
    ;(apply str (interpose " " (get-ref-strings real-external-refs :category-refs "chocolate")))
    (apply str (interpose " " (get-ref-strings real-external-refs :service-refs "chocolate")))
    "}"))

