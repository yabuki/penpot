;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.rpc.commands.changes
  "Changes streaming prototype RPC methods"
  (:refer-clojure :exclude [assert])
  (:require
   [app.binfile.v1 :as bf.v1]
   [app.common.logging :as l]
   [app.common.schema :as sm]
   [app.db :as db]
   [app.http.sse :as sse]
   [app.loggers.audit :as-alias audit]
   [app.loggers.webhooks :as-alias webhooks]
   [app.media :as media]
   [app.rpc :as-alias rpc]
   [app.msgbus :as mbus]
   [app.rpc.commands.files :as files]
   [app.rpc.commands.projects :as projects]
   [app.rpc.doc :as-alias doc]
   [app.tasks.file-gc]
   [app.util.services :as sv]
   [app.util.time :as dt]
   [app.worker :as-alias wrk]
   [promesa.exec :as px]
   [promesa.exec.csp :as sp]
   [ring.response :as rres]))

(set! *warn-on-reflection* true)

(declare stream-changes)

(def ^:private
  schema:stream-changes
  [:map {:title "stream-changes"}
   [:file-id ::sm/uuid]])

(sv/defmethod ::stream-changes
  {::doc/added "undefined"
   ::sse/stream? true
   ::sm/params schema:stream-changes}
  [{:keys [::db/pool] :as cfg} {:keys [::rpc/profile-id file-id] :as params}]
  (files/check-read-permissions! pool profile-id file-id)
  (let [cfg (-> cfg
                (assoc ::file-id file-id)
                (assoc ::profile-id profile-id))
        res (sse/response (partial stream-changes cfg))]
    res))


(defn- stream-changes
  [{:keys [::mbus/msgbus ::profile-id ::file-id]}]
  (let [ch (sp/chan :buf 32)
        tp (dt/tpoint)]

    (l/trc :hint "start streaming changes"
           :profile-id (str profile-id)
           :file-id (str file-id))
    (try
      (mbus/sub! msgbus :topic file-id :chan ch)
      (loop []
        (let [timeout-ch (sp/timeout-chan 2000)
              [val port] (sp/alts! [timeout-ch ch])]
          (cond
            (identical? port timeout-ch)
            (when (sse/emit! :keepalive {})
              (recur))

            (nil? val)
            (do
              (sp/close! ch)
              (mbus/purge! msgbus [ch])
              (sse/emit! :end {}))

            (= :file-change (:type val))
            (when (sse/emit! :message val)
              (recur))

            :else
            (recur))))
      (finally
        (let [elapsed (tp)]
          (l/trc :hint "stop streaming changes"
                 :profile-id (str profile-id)
                 :file-id (str file-id)
                 :elapsed (dt/format-duration elapsed)))))))
