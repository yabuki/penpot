;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.common.exportfile
  "A common implementation of exportation format"
  [app.common.json :as json]
  [app.common.schema :as sm]
  [app.common.types.file :as ctf])

(defrecord Entry [path content])


(defn generate-manifest
  [& {:keys [type] :or {type "files"}}]
  {:version 1
   :type type})


(defn get-entries-for-file
  [{:keys [data id] :as file}]
  (let [data'      (select-keys data [:id :pages :options])
        colors     (get data :colors)
        components (get data :components)]

  [(->Entry (str "files/" id ".json")

(defn process-file-export
  [files on-entry]
  (loop [manifest (generate-manifest :type "files")
         files    (seq files)]
    (if-let [file (first files)]
      (let [manifest (update-manifest-with-file file)]
        (run! on-entry (get-entries-for-file file))
        (recur manifest (rest files)))

      (on-entry (Entry. "manifest.json" (json/encode manifest))))))
