(ns ednism.core
  (:refer-clojure :exclude [get keys])
  (:require
   [clojure.string :as str]
   [saw.core :as saw]
   [ednism.store.core :as store]
   [ednism.store.file :as file]
   [ednism.store.ssm :as ssm]
   [ednism.cache :as cache]))

(defn put [path m]
  (store/put path m)
  (cache/invalidate! path))

(defn get [path & {:keys [:cache?] :or {cache? true}}]
  (if cache?
    (cache/lookup! path store/get)
    (store/get path)))

(defn history [path]
  (->> (store/history path)
       (sort-by :version)
       (reverse)))

(defn keys [path]
  (store/keys path))

(defn delete [path]
  (store/delete path)
  (cache/invalidate! path))

(defn path? [thing]
  (or (str/starts-with? (name thing) "/")
      (.contains (name thing) ":")))

(defn clear-cache! []
  (cache/clear!))

(defn init! [config]
  (ssm/init! config))
