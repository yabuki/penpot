;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.http.sse
  "SSE (server sent events) helpers"
  (:require
   [app.common.data :as d]
   [app.common.exceptions :as ex]
   [app.common.logging :as l]
   [app.common.transit :as t]
   [app.http.errors :as errors]
   [app.util.events :as events]
   [clojure.data.json :as json]
   [cuerdas.core :as str]
   [promesa.exec :as px]
   [promesa.exec.csp :as sp]
   [promesa.util :as pu]
   [ring.request :as rreq]
   [ring.response :as rres])
  (:import
   java.io.OutputStream))

(def ^:dynamic *close-channel* nil)

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

(defn emit!
  [event payload]
  (sp/put! events/*channel* [event payload]))

(defn get-content-encoder
  [request]
  (let [ctype (rreq/get-header request "content-type")]
    (cond
      (str/includes? ctype "application/transit+json")
      encode-transit
      (str/includes? ctype "application/json")
      encode-json
      :else
      encode-transit)))

(defn response
  [handler & {:keys [buf encode-fn]
              :or {buf 32}
              :as opts}]
  (fn [{:keys [exchange] :as request}]
    {::rres/headers default-headers
     ::rres/status 200
     ::rres/body
     (reify rres/StreamableResponseBody
       (-write-body-to-stream [_ _ output]
         (let [encode-fn (or encode-fn (get-content-encoder request))
               events-ch (sp/chan :buf buf :xf (keep encode-fn))
               close-ch  (sp/chan)
               listener  (events/start-listener
                          events-ch
                          (fn [data]
                            (l/trc :hint "writting data" :data data :length (alength ^bytes data))
                            (.write ^OutputStream output ^bytes data)
                            (.flush ^OutputStream output))

                          (fn [cause]
                            (if (or (instance? java.io.IOException cause)
                                    (instance? java.nio.channels.ClosedChannelException cause))
                              nil
                              (binding [l/*context* (errors/request->context request)]
                                (l/wrn :hint "unexpected exception on listener" :cause cause))))

                          (fn []
                            (l/trc :hint "listener terminated")
                            (sp/close! close-ch)
                            (sp/close! events-ch)
                            (pu/close! output)))]

           (try
             (binding [events/*channel* events-ch
                       *close-channel*  close-ch]
               (when-let [result (handler)]
                 (emit! :end result)))

             (catch Throwable cause
               (let [cause' (pu/unwrap-exception cause)]
                 (if (and (ex/error? cause)
                          (= ::events/abort (:type (ex-data cause))))
                   (l/inf :hint "process aborted")
                   (binding [l/*context* (errors/request->context request)]
                     (l/err :hint "unexpected exception on streaming process"
                            :cause cause)
                     (emit! :error (errors/handle' cause request))))))

             (finally
               (sp/close! events-ch)
               (px/await! listener))))))}))
