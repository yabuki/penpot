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

(def encode-file-data
  (sm/encoder ctf/schema:data sm/json-transformer))

(defn get-entries-for-file
  [{:keys [data id] :as file}]
  (let [data  (encode-file-data data)
        data' (select-keys data [:id :pages :options])]
    (cons (->Entry (str "files/" id ".json")
                   (json/encode data'))
          (for [[page-id data] (:pages-index data)]
            (->Entry (str "files/" id "/" page-id ".json")
                     (json/encode data))))))

(defn process-export-for-files
  [files on-entry]
  (loop [manifest (generate-manifest :type "files")
         files    (seq files)
         result   []]
    (if-let [file (first files)]
      (let [result' (map on-entry (get-entries-for-file file))]
        (recur (update manifest :files (fnil conj []) id)
               (rest files)
               (into result result')))

      (let [result' (on-entry (Entry. "manifest.json" (json/encode manifest)))]
        (conj result result')))))
