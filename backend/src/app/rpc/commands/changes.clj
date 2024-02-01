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
  schema:stream-file-changes
  [:map {:title "stream-file-changes"}
   [:file-id ::sm/uuid]])

(sv/defmethod ::stream-file-changes
  {::doc/added "undefined"
   ::sse/stream? true
   ::sm/params schema:stream-file-changes}
  [{:keys [::db/pool] :as cfg} {:keys [::rpc/profile-id file-id] :as params}]
  (files/check-read-permissions! pool profile-id file-id)
  (let [cfg (-> cfg
                (assoc ::file-id file-id)
                (assoc ::profile-id profile-id))]
    (sse/response (partial stream-changes cfg)
                  :encode-fn sse/encode-json)))

(defn- stream-changes
  [{:keys [::mbus/msgbus ::profile-id ::file-id]}]
  (let [sub-ch   (sp/chan :buf 32)
        close-ch sse/*close-channel*
        tpoint   (dt/tpoint)]

    (l/trc :hint "start streaming file changes"
           :profile-id (str profile-id)
           :file-id (str file-id))
    (try
      (mbus/sub! msgbus :topic file-id :chan sub-ch)
      (loop []
        (let [timeout-ch (sp/timeout-chan 2000)
              [val port] (sp/alts! [timeout-ch sub-ch close-ch])]
          (cond
            (identical? port timeout-ch)
            (when (sse/emit! :keepalive {})
              (prn (promesa.exec/current-thread))
              (recur))

            (or (nil? val)
                (identical? port close-ch))
            (do
              (sp/close! sub-ch)
              (mbus/purge! msgbus [sub-ch])
              (sse/emit! :end {}))

            (= :file-change (:type val))
            (when (sse/emit! :message val)
              (recur))

            :else
            (recur))))

      (finally
        (let [elapsed (tpoint)]
          (l/trc :hint "stop streaming file changes"
                 :profile-id (str profile-id)
                 :file-id (str file-id)
                 :elapsed (dt/format-duration elapsed)))))))
