(ns ednism.core-test
  (:require
   [clojure.test :refer :all]
   [saw.core :as saw]
   [ednism.core :as e]))

(deftest file-store-test
  (is (= 2 2)))


(defn setup []
  (e/init! (merge (saw/session)
                  {:region "us-east-1"})))

(defn put-get [k v]
  (let [path (format "ssm:/ednism/%s" (name k))]
    (e/put path v)
    (e/get path)))

(deftest ^:integration ssm-store-test
  (setup)
  (let [cfg {:a 1
             :b "string"
             :c true
             :e ["a" "b" "c"]
             :d :keyword}]
    (is (= cfg
           (put-get :test cfg)))))
