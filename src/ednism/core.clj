(ns ednism.core
  (:refer-clojure :exclude [get keys])
  (:require
   [clojure.string :as str]
   [saw.core :as saw]
   [ednism.store.core :as store]
   [ednism.store.file :as file]
   [ednism.store.ssm :as ssm]))

(defn put-kv [k v]
  (store/put-kv k v))

(defn put [path m]
  (store/put path m))

(defn get [path]
  (store/get path))

(defn history [path]
  (store/history path))

(defn keys [path]
  (store/keys path))

(defn delete [path]
  (store/delete path))

(defn init! [config]
  (ssm/init! config))
