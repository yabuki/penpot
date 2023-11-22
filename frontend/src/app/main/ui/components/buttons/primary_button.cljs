;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.main.ui.components.buttons.primary-button
  (:require-macros [app.main.style :as stl])
  (:require
   [app.main.ui.components.radio-buttons :refer [radio-button radio-buttons]]
   [app.common.data.macros :as dm]
   [app.main.ui.icons :as i]
   [rumext.v2 :as mf]))

(mf/defc primary-button
  {::mf/wrap-props false}
  [{:keys [children type on-click ]}]
  (let []
    ;; This div wrapper is to select the "theme" if you change to "light" will show the light-themed component
    [:.default
     [:& radio-buttons {:selected "first"
                        ;;:on-change set-radio-selected
                        :name "listing-style"}
      [:& radio-button {:icon i/view-as-list-refactor
                        :value "first"
                        :id :first}]
      [:& radio-button {:icon i/flex-grid-refactor
                        :value "second"
                        :id :second}]

      [:& radio-button {:icon i/arrow-refactor
                        :value "third"
                        :id :third}]

      [:& radio-button {:icon i/board-refactor
                        :value "forth"
                        :id :forth}]]]
    #_[:*
     [:div.default
      [:button  {:class (stl/css :button-primary)}
       i/add-refactor]]
     [:div.light
      [:button  {:class (stl/css :button-primary)}
       i/add-refactor]]
     ]))
