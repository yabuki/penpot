;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.main.data.workspace.common
  (:require
   [app.common.data.macros :as dm]
   [app.common.logging :as log]
   [app.common.types.shape.layout :as ctl]
   [app.main.data.changes :as dch]
   [app.main.data.workspace.state-helpers :as wsh]
   [app.main.data.workspace.undo :as dwu]
   [app.util.router :as rt]
   [beicon.v2.core :as rx]
   [potok.v2.core :as ptk]))

;; Change this to :info :debug or :trace to debug this module
(log/set-level! :warn)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; HELPERS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn initialized?
  "Check if the state is properly initialized in a workspace. This means
  it has the `:current-page-id` and `:current-file-id` properly set."
  [state]
  (and (uuid? (:current-file-id state))
       (uuid? (:current-page-id state))))

(defn interrupt?
  [e]
  (= e :interrupt))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UNDO
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Toolbar
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn toggle-toolbar-visibility
  []
  (ptk/reify ::toggle-toolbar-visibility
    ptk/UpdateEvent
    (update [_ state]
      (update-in state [:workspace-local :hide-toolbar] not))))

(defn hide-toolbar
  []
  (ptk/reify ::hide-toolbar
    ptk/UpdateEvent
    (update [_ state]
      (assoc-in state [:workspace-local :hide-toolbar] true))))

(defn show-toolbar
  []
  (ptk/reify ::show-toolbar
    ptk/UpdateEvent
    (update [_ state]
      (assoc-in state [:workspace-local :hide-toolbar] false))))
