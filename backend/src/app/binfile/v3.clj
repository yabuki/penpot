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

(defn- write-entry!
  [^ZipOutputStream output ^String path data]
  (.putNextEntry output (ZipEntry. path))
  (let [writer (OutputStreamWriter. output "UTF-8")]
    (json/write writer data :indent true :key-fn json/write-camel-key)
    (.flush writer))
  (.closeEntry output))

(defn- get-file
  [{:keys [::embed-assets ::include-libraries] :as cfg} file-id]

  (when (and include-libraries embed-assets)
    (throw (IllegalArgumentException.
            "the `include-libraries` and `embed-assets` are mutally excluding options")))

  (let [detach?  (and (not embed-assets) (not include-libraries))]
    (cond-> (bfc/get-file cfg file-id)
      detach?
      (-> (ctf/detach-external-references file-id)
          (dissoc :libraries))

      embed-assets
      (update :data #(bfc/embed-assets cfg % file-id))

      :always
      (encode-file))))

(defn write-export!
  [{:keys [::ids ::include-libraries ::embed-assets ::output] :as cfg}]
  (with-open [^ZipOutputStream output (ZipOutputStream. output)]
    (let [ids      (into ids (when include-libraries (bfc/get-libraries cfg ids)))
          sobjects (volatile! #{})
          storage  (sto/resolve cfg)]

      (doseq [file-id ids]
        (let [file         (get-file cfg file-id)
              data         (:data file)
              media        (bfc/get-file-media cfg file)

              typographies (not-empty (:typographies data))
              plugins-data (not-empty (:plugin-data data))
              components   (not-empty (:components data))
              colors       (not-empty (:colors data))

              pages-index  (:pages-index data)
              file         (dissoc file :data)
              data         (-> data
                               (dissoc :pages-index)
                               (dissoc :colors)
                               (dissoc :media)
                               (dissoc :recent-colors)
                               (dissoc :typographies)
                               (dissoc :plugin-data))
              ]


          (write-entry! output (str "files/" file-id ".json") file)
          (write-entry! output (str "files/" file-id "/data.json") data)

          (doseq [[page-id data] pages-index]
            (write-entry! output (str "files/" file-id "/pages/" page-id ".json") data))

          (when-let [media (not-empty media)]
            (vswap! sobjects into bfc/xf-map-media-id media)
            (write-entry! output (str "files/" file-id "/media.json") media))

          (when components
            (write-entry! output (str "files/" file-id "/components.json") components))

          (when colors
            (write-entry! output (str "files/" file-id "/colors.json") colors))

          (when typographies
            (write-entry! output (str "files/" file-id "/typographies.json") typographies))

          (when plugins-data
            (write-entry! output (str "files/" file-id "/plugins-data.json") plugins-data))))

      (when-let [sobjects (-> sobjects deref not-empty)]
        (doseq [id sobjects]
          (let [sobject (sto/get-object storage id)]
            (write-entry! output (str "objects/" id ".json") (meta sobject))
            (with-open [input (sto/get-object-data storage sobject)]
              (.putNextEntry output (ZipEntry. (str "objects/" id ".bin")))
              (io/copy! input output (:size sobject))
              (.closeEntry output))))))))

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

