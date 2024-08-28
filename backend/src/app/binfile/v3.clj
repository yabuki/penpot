;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.binfile.v3
  "A sqlite3 based binary file exportation with support for exportation
  of entire team (or multiple teams) at once."
  (:refer-clojure :exclude [read])
  (:require
   [app.common.data.macros :as dm]
   [app.binfile.common :as bfc]
   [app.common.data :as d]
   [app.common.features :as cfeat]
   [app.common.json :as json]
   [app.common.logging :as l]
   [app.common.schema :as sm]
   [app.common.transit :as t]
   [app.common.types.file :as ctf]
   [app.common.uuid :as uuid]
   [clojure.java.io :as jio]
   [app.util.time :as dt]
   [app.config :as cf]
   [datoteka.io :as io]
   [app.db :as db]
   [app.db.sql :as sql]
   [app.storage :as sto]
   [buddy.core.codecs :as bc])
  (:import
   java.util.zip.ZipOutputStream
   java.util.zip.ZipEntry
   java.io.OutputStreamWriter))

(def schema:file
  [:map
   [:id ::sm/uuid]
   [:data ctf/schema:data]
   [:features ::cfeat/features]])

(def encode-file
  (sm/encoder schema:file sm/json-transformer))

(defn write-export!
  [{:keys [::ids ::include-libraries ::embed-assets ::output] :as cfg}]
  (when (and include-libraries embed-assets)
    (throw (IllegalArgumentException.
            "the `include-libraries` and `embed-assets` are mutally excluding options")))

  (with-open [^ZipOutputStream output (ZipOutputStream. output)]
    (let [ids     (into ids (when include-libraries (bfc/get-libraries cfg ids)))
          detach? (and (not embed-assets) (not include-libraries))]
      (doseq [file-id ids]
        (let [file (cond-> (bfc/get-file cfg file-id)
                     detach?
                     (-> (ctf/detach-external-references file-id)
                         (dissoc :libraries))

                     embed-assets
                     (update :data #(bfc/embed-assets cfg % file-id)))
              file (encode-file file)]

              ;; file (json/encode file :indent true :key-fn json/write-camel-key)
              ;; file (bc/str->bytes file)]

          (.putNextEntry output (ZipEntry. (str "files/" file-id ".json")))
          (let [writer (OutputStreamWriter. output "UTF-8")]
            (json/write writer file :indent true :key-fn json/write-camel-key)
            (.flush writer))
          (.closeEntry output))))))

(defn export-files!
  "Do the exportation of a specified file in custom penpot binary
  format. There are some options available for customize the output:

  `::include-libraries`: additionally to the specified file, all the
  linked libraries also will be included (including transitive
  dependencies).

  `::embed-assets`: instead of including the libraries, embed in the
  same file library all assets used from external libraries."

  [{:keys [::ids] :as cfg} output]

  (dm/assert!
   "expected a set of uuid's for `::ids` parameter"
   (and (set? ids)
        (every? uuid? ids)))

  (dm/assert!
   "expected instance of jio/IOFactory for `input`"
   (satisfies? jio/IOFactory output))

  (let [id (uuid/next)
        tp (dt/tpoint)
        ab (volatile! false)
        cs (volatile! nil)]
    (try
      (l/info :hint "start exportation" :export-id (str id))
      (with-open [output (io/output-stream output)]
        (write-export! (assoc cfg ::output output)))

      (catch java.io.IOException _cause
        ;; Do nothing, EOF means client closes connection abruptly
        (vreset! ab true)
        nil)

      (catch Throwable cause
        (vreset! cs cause)
        (vreset! ab true)
        (throw cause))

      (finally
        (l/info :hint "exportation finished" :export-id (str id)
                :elapsed (str (inst-ms (tp)) "ms")
                :aborted @ab
                :cause @cs)))))

