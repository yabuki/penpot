;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.util.events
  "A generic asynchronous events notifications subsystem; used mainly
  for mark event points in functions and be able to attach listeners
  to them. Mainly used in http.sse for progress reporting."
  (:refer-clojure :exclude [tap run!])
  (:require
   [app.common.data.macros :as dm]
   [app.common.exceptions :as ex]
   [app.common.logging :as l]
   [promesa.exec :as px]
   [promesa.exec.csp :as sp]))

(def ^:dynamic *channel* nil)
(def ^:dynamic *abort* nil)

(defn tap
  [type data]
  (when-let [channel *channel*]
    (px/sleep 2000)
    (prn "TAP" (some-> *abort* deref))
    ;; Raise a special exception for process abort
    (when (some-> *abort* deref)
      (ex/raise :type ::abort))
    (sp/put! channel [type data])))

(def ^:private noop
  (constantly nil))

(defn start-listener
  [channel on-event on-error on-close]
  (px/thread
    {:virtual true}
    (try
      (loop []
        (when-let [event (sp/take! channel)]
          (on-event event)
          (recur)))
      (catch Throwable cause
        (on-error cause))
      (finally
        (on-close)))))

(defn run-with!
  "A high-level facility for to run a function in context of event
  emiter."
  [f on-event on-error on-close]
  (let [events-ch (sp/chan :buf 32)
        listener  (start-listener events-ch
                                  on-event
                                  on-error
                                  (fn []
                                    (sp/close! events-ch)
                                    (on-close)))]
    (try
      (binding [*channel* (sp/chan :buf 32)]
        (f))
      (finally
        (sp/close! events-ch)
        (px/await! listener)))))
