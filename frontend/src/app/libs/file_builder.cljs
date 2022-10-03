;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.libs.file-builder
  (:require
   [app.common.data :as d]
   [app.common.file-builder :as fb]
   [app.common.uuid :as uuid]
   [app.util.dom :as dom]
   [app.util.webapi :as wapi]
   [app.util.zip :as uz]
   [app.worker.export :as e]
   [beicon.core :as rx]
   [cuerdas.core :as str]
   [promesa.core :as p]))

(defn parse-data [data]
  (as-> data $
    (js->clj $ :keywordize-keys true)
    ;; Transforms camelCase to kebab-case
    (d/deep-mapm
     (fn [[key value]]
       (let [value (if (= (type value) js/Symbol)
                     (keyword (js/Symbol.keyFor value))
                     value)
             key (-> key d/name str/kebab keyword)]
         [key value])) $)))

(defn export-file
  [kk export-type]

  (let [kk (assoc kk
                  :name "test"
                  :file-name "test"
                  :is-shared false)

        files-stream (->> (rx/of {(:id kk) kk})
                          (rx/share))

        manifest-stream
        (->> files-stream
             (rx/map #(e/create-manifest (uuid/next) (:id kk) export-type %))
             (rx/map (fn [a]
                       (vector "manifest.json" a))))

        render-stream
        (->> files-stream
             (rx/flat-map vals)
             (rx/flat-map e/process-pages)
             (rx/observe-on :async)
             (rx/flat-map e/get-page-data)
             (rx/share))

        ;; colors-stream
        ;; (->> files-stream
        ;;      (rx/flat-map vals)
        ;;      (rx/map #(vector (:id %) (get-in % [:data :colors])))
        ;;      (rx/filter #(d/not-empty? (second %)))
        ;;      (rx/map e/parse-library-color))

        ;; typographies-stream
        ;; (->> files-stream
        ;;      (rx/flat-map vals)
        ;;      (rx/map #(vector (:id %) (get-in % [:data :typographies])))
        ;;      (rx/filter #(d/not-empty? (second %)))
        ;;      (rx/map e/parse-library-typographies))

        ;; media-stream
        ;; (->> files-stream
        ;;      (rx/flat-map vals)
        ;;      (rx/map #(vector (:id %) (get-in % [:data :media])))
        ;;      (rx/filter #(d/not-empty? (second %)))
        ;;      (rx/flat-map e/parse-library-media))

        ;; components-stream
        ;; (->> files-stream
        ;;      (rx/flat-map vals)
        ;;      (rx/filter #(d/not-empty? (get-in % [:data :components])))
        ;;      (rx/flat-map e/parse-library-components))

        ;; deleted-components-stream
        ;; (->> files-stream
        ;;      (rx/flat-map vals)
        ;;      (rx/filter #(d/not-empty? (get-in % [:data :deleted-components])))
        ;;      (rx/flat-map e/parse-deleted-components))

        pages-stream
        (->> render-stream
             (rx/map e/collect-page))]

    (rx/merge
     (->> render-stream
          (rx/map #(hash-map
                    :type :progress
                    :file (:id kk)
                    :data (str "Render " (:file-name %) " - " (:name %))))
          (rx/catch
           (fn [err]
             (println "ERRRRRRRRRRRR2" err))))

     (->> (rx/merge
           manifest-stream
           pages-stream
          ;;  components-stream
          ;;  deleted-components-stream
          ;;  media-stream
          ;;  colors-stream
          ;;  typographies-stream
           )
          (rx/reduce conj [])
          (rx/with-latest-from files-stream)
          (rx/flat-map (fn [[data files]]
                         (->> (uz/compress-files data)
                              (rx/map #(vector kk %)))))

          (rx/catch
           (fn [err]
             (println "ERRRRRRRRRRRR1" err)))))))

(deftype File [^:mutable file]
  Object

  (addPage [_ name]
    (set! file (fb/add-page file {:name name}))
    (str (:current-page-id file)))

  (addPage [_ name options]
    (set! file (fb/add-page file {:name name :options options}))
    (str (:current-page-id file)))

  (closePage [_]
    (set! file (fb/close-page file)))

  (addArtboard [_ data]
    (set! file (fb/add-artboard file (parse-data data)))
    (str (:last-id file)))

  (closeArtboard [_]
    (set! file (fb/close-artboard file)))

  (addGroup [_ data]
    (set! file (fb/add-group file (parse-data data)))
    (str (:last-id file)))

  (closeGroup [_]
    (set! file (fb/close-group file)))

  (createRect [_ data]
    (set! file (fb/create-rect file (parse-data data)))
    (str (:last-id file)))

  (createCircle [_ data]
    (set! file (fb/create-circle file (parse-data data)))
    (str (:last-id file)))

  (createPath [_ data]
    (set! file (fb/create-path file (parse-data data)))
    (str (:last-id file)))

  (createText [_ data]
    (set! file (fb/create-text file (parse-data data)))
    (str (:last-id file)))

  (createImage [_ data]
    (set! file (fb/create-image file (parse-data data)))
    (str (:last-id file)))

  (createSVG [_ data]
    (set! file (fb/create-svg-raw file (parse-data data)))
    (str (:last-id file)))

  (closeSVG [_]
    (set! file (fb/close-svg-raw file)))

  (asMap [_]
    (clj->js file))

  (test [_]
   (p/create
    (fn [resolve reject]
      (->> (export-file file :all)
           (rx/subs
            (fn [value]
              (when  (not (contains? value :type))
                (let [[file export-blob] value]
                  (println "file" file)
                  (println "export-blob" export-blob)
                  (println (wapi/create-uri export-blob))
                  (dom/trigger-download "test" export-blob))))))))))

(defn create-file-export [^string name]
  (File. (fb/create-file name)))


(defn exports []
  #js { :createFile    create-file-export })
