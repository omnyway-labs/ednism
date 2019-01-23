(ns ednism.store.file
  (:refer-clojure :exclude [get keys update])
  (:require
   [clojure.tools.reader.edn :as edn]
   [clojure.java.io :as jio]
   [ednism.store.core :refer :all])
  (:import
   [java.io PushbackReader]))

(defn file-exists? [f]
  (.exists (jio/file f)))

(defn read-edn-file [f]
  (when (file-exists? f)
    (with-open [rdr (-> f jio/reader (PushbackReader.))]
      (edn/read rdr))))

(defmethod get :file [path]
  (-> (as-path path)
      (read-edn-file)))

(defmethod put :file [path cfg]
  :nop)

(defmethod put-kv :file [_ _ _]
  :nop)
