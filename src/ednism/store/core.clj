(ns ednism.store.core
  (:refer-clojure :exclude [get keys])
  (:require
   [clojure.string :as str]))

(defn scheme-of [f]
  (cond
    (str/starts-with? (name f) "/")
    :ssm

    (.contains (name f) ":")
    (-> (str/split f #":")
        (first)
        (keyword))

    :else :ssm))

(defn as-path [f]
  (or (-> (str/split f #":")
          (second))
      f))

(defmulti put-kv (fn [path value] (scheme-of path)))

(defmulti put (fn [path cfg-map] (scheme-of path)))

(defmulti get (fn [path] (scheme-of path)))

(defmulti delete (fn [path] (scheme-of path)))

(defmulti keys (fn [path] (scheme-of path)))

(defmulti history (fn [path] (scheme-of path)))
