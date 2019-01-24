(ns ednism.core-test
  (:require
   [clojure.test :refer :all]
   [saw.core :as saw]
   [ednism.core :as e]))

(deftest file-store-test
  (is (= {:a 1}
         (do
           (e/put "file:/tmp/foo" {:a 1})
           (e/get "file:/tmp/foo")))))

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
           (put-get :test cfg)))
    (is (= 1 (put-get :kv 1)))
    (is (= 1
           (-> (e/history "ssm:/ednism/kv")
               first
               :version)))
    (is (= :ok  (e/delete "ssm:/ednism")))))
