(ns ednism.cache
  (:require
   [clojure.core.cache :as cache]))

(def C (atom (cache/fifo-cache-factory {})))

(defn lookup! [k f]
  (swap! C cache/through-cache k f)
  (get @C k))

(defn invalidate! [k]
  (swap! C dissoc k)
  :ok)
