(ns app.main.ui.components.buttons.simple-button
  (:require-macros [app.main.style :as stl])
  (:require
   [rumext.v2 :as mf]))

(mf/defc simple-button
  [{:keys [children]}]
  [:button {:class (stl/css :button)} children])

