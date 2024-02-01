;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.http.sse
  "SSE (server sent events) helpers"
  (:refer-clojure :exclude [tap])
  (:require
   [app.common.data :as d]
   [app.common.logging :as l]
   [app.common.transit :as t]
   [app.http.errors :as errors]
   [app.util.events :as events]
   [clojure.data.json :as json]
   [promesa.exec :as px]
   [promesa.exec.csp :as sp]
   [promesa.util :as pu]
   [ring.response :as rres])
  (:import
   java.io.OutputStream))

(defn- write!
  [^OutputStream output ^bytes data]
  (l/trc :hint "writting data" :data data :length (alength data))
  (.write output data)
  (.flush output))

(defn encode-transit
  [[name data]]
  (try
    (let [data (with-out-str
                 (println "event:" (d/name name))
                 (println "data:" (t/encode-str data {:type :json-verbose}))
                 (println))]
      (.getBytes data "UTF-8"))
    (catch Throwable cause
      (l/err :hint "unexpected error on encoding value on sse stream"
             :cause cause)
      nil)))

(defn encode-json
  [[name data]]
  (try
    (let [data (with-out-str
                 (println "event:" (d/name name))
                 (println "data:" (json/write-str data))
                 (println))]
      (.getBytes data "UTF-8"))
    (catch Throwable cause
      (l/err :hint "unexpected error on encoding value on sse stream"
             :cause cause)
      nil)))

;; ---- PUBLIC API

(def default-headers
  {"Content-Type" "text/event-stream;charset=UTF-8"
   "Cache-Control" "no-cache, no-store, max-age=0, must-revalidate"
   "Pragma" "no-cache"})

(def ^:dynamic *close-channel* nil)

(defn emit!
  [event payload]
  (sp/put! events/*channel* [event payload]))

(defn response
  [handler & {:keys [buf encode-fn]
              :or {buf 32
                   encode-fn encode-transit}
              :as opts}]
  (fn [{:keys [exchange] :as request}]
    {::rres/headers default-headers
     ::rres/status 200
     ::rres/body (reify rres/StreamableResponseBody
                   (-write-body-to-stream [_ _ output]
                     (binding [events/*channel* (sp/chan :buf buf :xf (keep encode-fn))
                               *close-channel*  (sp/chan)]
                       (let [listener (events/start-listener
                                       (partial write! output)
                                       (fn []
                                         (sp/close! *close-channel*)
                                         (pu/close! output)))]
                         (try
                           (when-let [result (handler)]
                             (events/tap :end result))
                           (catch Throwable cause
                             (binding [l/*context* (errors/request->context request)]
                               (l/err :hint "unexpected error process streaming response"
                                      :cause cause))
                             (events/tap :error (errors/handle' cause request)))
                           (finally
                             (sp/close! events/*channel*)
                             (px/await! listener)))))))}))
