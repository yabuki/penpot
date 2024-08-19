;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.setup.welcome-file
  (:require
   [app.common.logging :as l]
   [app.common.types.pages-list :as ctpl]
   [app.db :as db]
   [app.features.fdata :as feat.fdata]
   [app.rpc :as-alias rpc]
   [app.rpc.climit :as-alias climit]
   [app.rpc.commands.management :as management]
   [app.rpc.commands.profile :as profile]
   [app.rpc.doc :as-alias doc]
   [app.setup :as-alias setup]
   [app.setup.templates :as tmpl]
   [app.util.blob :as blob]
   [app.util.pointer-map :as pmap]
   [app.worker :as-alias wrk]))

(defn- decode-row
  "A generic decode row helper"
  [{:keys [data features] :as row}]
  (cond-> row
    features (assoc :features (db/decode-pgarray features #{}))
    data     (assoc :data (blob/decode data))))


(defn- update-welcome-text
  [conn file name]
  (let [page-id #uuid "2c6952ee-d00e-8160-8004-d2250b7210cb"
        text-id #uuid "765e9f82-c44e-802e-8004-d72a10b7b445"
        fdata   (:data file)
        page    (ctpl/get-page fdata page-id)
        objects (:objects page)
        text    (-> (get objects text-id)
                    (assoc :name "Welcome to Penpot" :position-data nil)
                    (assoc-in [:content :children 0 :children 0 :children 0 :text] (str "Welcome to Penpot, " name "!")))

        fdata (assoc-in fdata [:pages-index page-id :objects text-id] text)]

    (db/update! conn :file
                {:data (blob/encode fdata)}
                {:id (:id file)})))


(defn- update-welcome-file
  [{:keys [::db/conn] :as cfg} file-id name]
  (binding [pmap/*load-fn* (partial feat.fdata/load-pointer cfg file-id)]
    (when-let [file (db/get* conn :file {:id file-id}
                             {::db/remove-deleted false})]
      (let [file (-> file
                     (decode-row)
                     (update :data feat.fdata/process-pointers deref)
                     (update :data feat.fdata/process-objects (partial into {})))]
        (update-welcome-text conn file name)))))


(defn create-welcome-file
  [cfg profile]
  (try
    (let [cfg             (dissoc cfg ::db/conn)
          params          {:profile-id (:id profile)
                           :project-id (:default-project-id profile)}
          template-stream (tmpl/get-template-stream cfg "welcome")
          file-id         (-> (management/clone-template cfg params template-stream)
                              first)]

      (db/tx-run! cfg (fn [cfg]
                        (update-welcome-file cfg file-id (:fullname profile))
                        (profile/update-profile-props cfg (:id profile) {:welcome-file-id file-id}))))
    (catch Throwable cause
      (l/error :hint "unexpected error on create welcome file " :cause cause))))

