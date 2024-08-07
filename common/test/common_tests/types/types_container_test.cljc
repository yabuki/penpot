;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns common-tests.types.types-container-test
  (:require
;;    [app.common.data :as d]
;;    [app.common.test-helpers.components :as thc]
;;    [app.common.test-helpers.compositions :as tho]
   [app.common.test-helpers.files :as thf]
   [app.common.test-helpers.ids-map :as thi]
   [app.common.test-helpers.shapes :as ths]
   [app.common.types.container :as ctn]
;;    [app.common.text :as txt]
;;    [app.common.types.colors-list :as ctcl]
;;    [app.common.types.component :as ctk]
;;    [app.common.types.components-list :as ctkl]
;;    [app.common.types.file :as ctf]
;;    [app.common.types.pages-list :as ctpl]
;;    [app.common.types.typographies-list :as ctyl]
   [clojure.test :as t]))

(t/use-fixtures :each thi/test-fixture)

(t/deftest test-reduce-shapes
  (let [file (-> (thf/sample-file :file)
                 (ths/add-sample-shape :shape1))

        on-not-component (fn [shape context acc]
                           (println "shape" (:id shape) (:name shape))
                           (println "context" context)
                           acc)

        page (thf/current-page file)

        acc (ctn/reduce-shapes page :dummy
                               :shape-id (thi/id :shape1)
                               :on-not-component on-not-component)]
    (t/is (= acc :dummy))))
 