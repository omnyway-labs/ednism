(ns ednism.store.ssm
  (:refer-clojure :exclude [keys get update])
  (:require
   [clojure.string :as str]
   [saw.core :as saw]
   [saw.util :refer [error-as-value]]
   [ednism.store.core :refer :all])
  (:import
   (com.amazonaws.services.simplesystemsmanagement
    AWSSimpleSystemsManagementClientBuilder)
   [com.google.common.util.concurrent
    RateLimiter]
   (com.amazonaws.services.simplesystemsmanagement.model
    DescribeParametersResult
    PutParameterRequest
    PutParameterResult
    GetParameterRequest
    GetParameterResult
    GetParameterHistoryRequest
    GetParametersByPathRequest
    GetParametersByPathResult
    DeleteParameterRequest
    Parameter
    ParameterType
    ParameterNotFoundException)))

(def rate-limiter (RateLimiter/create 1.0))

(defn rate-limit! []
  (.acquire rate-limiter))

(defonce ^:private client (atom nil))

(defn make-client [region]
  (-> (AWSSimpleSystemsManagementClientBuilder/standard)
      (.withCredentials (saw/creds))
      (.withRegion region)
      .build))

(defn path? [name]
  (str/starts-with? name "/"))

(defn as-type [parameter-type]
  (condp = parameter-type
    "String" :string
    "StringList" :string-list
    "SecureString" :secure-string))

(defn as-parameter [p]
  {:name    (.getName p)
   :value   (.getValue p)
   :version (.getVersion p)
   :type    (as-type (.getType p))})

(defn as-parameters [result]
  {:next-token (.getNextToken result)
   :parameters (map as-parameter (.getParameters result))})

(defn as-history* [result]
  (map (fn [p]
         {:name      (.getName p)
          :value     (read-string (.getValue p))
          :version   (.getVersion p)
          :timestamp (.getLastModifiedDate p)}) result))

(defn as-history [result]
  {:next-token (.getNextToken result)
   :parameters (flatten (as-history* (.getParameters result)))})

(defn put-kv* [name value overwrite?]
  (->> (doto (PutParameterRequest.)
         (.withName name)
         (.withType "SecureString")
         (.withValue value)
         (.withOverwrite overwrite?))
       (.putParameter @client)
       (.getVersion)))

(defn get-kv* [name]
  (error-as-value
   (->> (doto (GetParameterRequest.)
          (.withName name)
          (.withWithDecryption true))
        (.getParameter @client)
        (.getParameter)
        (.getValue))))

(defn- get-by-path*
  ([path]
   (->> (doto (GetParametersByPathRequest.)
          (.withPath path)
          (.withMaxResults (int 10))
          (.withRecursive  true)
          (.withWithDecryption true))
        (.getParametersByPath @client)
        (as-parameters)))
  ([path token]
   (->> (doto (GetParametersByPathRequest.)
          (.withPath path)
          (.withMaxResults (int 10))
          (.withRecursive  true)
          (.withNextToken  token)
          (.withWithDecryption true))
        (.getParametersByPath @client)
        (as-parameters))))

(defn- get-by-path [path]
  (loop [{:keys [next-token parameters]}  (get-by-path* path)
         acc  []]
   (if-not next-token
      (conj acc parameters)
      (recur (get-by-path* path next-token)
             (conj acc parameters)))))

(defn delete* [name]
  (error-as-value
   (->> (doto (DeleteParameterRequest.)
          (.withName name))
        (.deleteParameter @client))))

(defn history*
  ([name]
   (->> (doto (GetParameterHistoryRequest.)
          (.withName name)
          (.withWithDecryption true))
        (.getParameterHistory @client)
        (as-history)))
  ([name token]
   (->> (doto (GetParameterHistoryRequest.)
          (.withName name)
          (.withWithDecryption true)
          (.withNextToken token))
        (.getParameterHistory @client)
        (as-history))))

(defn- get-history* [path]
  (loop [{:keys [next-token parameters]} (history* path)
         acc  []]
   (if-not next-token
      (conj acc parameters)
      (recur (history* path next-token)
             (conj acc parameters)))))

(defn- as-key [path]
  (-> path
      (str/split #"/")
      last
      keyword))

(defn read-string-safely [s]
  (binding [*read-eval* false]
    (when (and (string? s) (not (str/blank? s)))
      (read-string s))))

(defn- as-value [raw-str]
  (let [v (try (read-string-safely raw-str)
               (catch Exception e raw-str))]
    (cond
      (symbol? v) raw-str
      (map? v)    v
      :else       v)))

(defn maybe-empty? [m]
  (when-not (empty? m) m))

(defn- parse-response [res]
  (->> (map (fn [{:keys [name value]}]
              {(as-key name) (as-value value)}) res)
       (into {})
       (maybe-empty?)))

(defmethod put :ssm [path cfg]
  (let [path (as-path path)]
    (if (map? cfg)
      (doseq [[k v] cfg]
        (-> (str path "/" (name k))
            (put-kv* (pr-str v) true))
        (rate-limit!))
      (put-kv* path (pr-str cfg) true))))

(defmethod get :ssm [path]
  (let [path (as-path path)]
    (or (->> (get-by-path path)
             (apply concat)
             (parse-response))
        (->> (get-kv* path)
             (as-value)))))

(defmethod history :ssm [path]
  (->> (as-path path)
       (get-history*)
       (apply concat)))

(defmethod keys :ssm [root]
  (->> (as-path root)
       (get-by-path)
       (apply concat)))

(defmethod delete :ssm [path]
  (let [path (as-path path)
        ks (keys path)]
    (if (empty? ks)
      (delete* path)
      (doseq [{:keys [name]} ks]
        (rate-limit!)
        (delete* name)))))

(defn init! [auth]
  (saw/login auth)
  (reset! client (make-client (:region auth))))
