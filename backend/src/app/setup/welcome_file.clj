;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.setup.welcome-file
  (:require
   [app.common.logging :as l]
   [app.db :as db]
   [app.rpc.commands.files-update :as fupdate]
   [app.rpc.commands.management :as management]
   [app.rpc.commands.profile :as profile]
   [app.setup.templates :as tmpl]))

(def ^:private page-id #uuid "2c6952ee-d00e-8160-8004-d2250b7210cb")
(def ^:private shape-id #uuid "765e9f82-c44e-802e-8004-d72a10b7b445")

(def ^:private update-path
  [:pages-index page-id :objects shape-id
   :content :children 0 :children 0 :children 0])

(defn- update-welcome-shape
  [_ file name]
  (let [text (str "Welcome to Penpot, " name "!")]
    (update file :data update-in update-path assoc :text text)))

(defn create-welcome-file
  [cfg {:keys [id] :as profile}]
  (try
    (let [cfg             (dissoc cfg ::db/conn)
          params          {:profile-id (:id profile)
                           :project-id (:default-project-id profile)}
          template-stream (tmpl/get-template-stream cfg "welcome")
          file-id         (-> (management/clone-template cfg params template-stream)
                              first)]

      (db/tx-run! cfg (fn [cfg]
                        (fupdate/update-file cfg file-id update-welcome-shape name)
                        (profile/update-profile-props cfg id {:welcome-file-id file-id}))))

    (catch Throwable cause
      (l/error :hint "unexpected error on create welcome file " :cause cause))))

