(ns lcmap.gaia.storage
  (:require [clojure.tools.logging :as log]
            [lcmap.gaia.config :refer [config]]
            [cheshire.core :as json]
            [amazonica.aws.s3 :as s3]
            [java-time :as jt]))

(def client-config
  {:access-key (:storage-access-key config)
   :secret-key (:storage-secret-key config)
   :endpoint   (:storage-endpoint config)
   :client-config {:path-style-access-enabled true}})

(defn list_buckets
  ([cfg]
   (let [objects (s3/list-buckets cfg)]
     (map :name objects)))
  ([]
   (list_buckets client-config)))

(defn list_bucket_contents
  [bucketname]
  (let [objects (s3/list-objects-v2 client-config :bucket-name bucketname)
        summaries (:object-summaries objects)]
    (map :key summaries)))

(defn create_bucket
  [bucketname]
  (s3/create-bucket client-config bucketname))

(defn put_json
  [bucketname keyname data]
  (let [encoded_data (json/encode data)
        byte_data (.getBytes encoded_data)
        byte_stream (java.io.ByteArrayInputStream. byte_data)
        metadata {:content-length (count byte_data)}]
    (try
      (s3/put-object client-config :bucket-name bucketname :key keyname :input-stream byte_stream :metadata metadata)
      true
      (catch Exception e (log/errorf "Error putting data to object store: %s" e)
        false))))

(defn put_tiff
  [bucketname keyname fpath]
  (let [jfile (java.io.File. fpath)]
    (try
      (s3/put-object client-config :bucket-name bucketname :key keyname :file jfile)
      true
      (catch Exception e (log/errorf "Error putting data to object store: %s" e)
        false))))

(defn drop_json
  [bucketname keyname]
  (s3/delete-object client-config :bucket-name bucketname :key keyname))

(defn drop_bucket
  [bucketname]
  (s3/delete-bucket client-config :bucket-name bucketname))

(defn get_json
  [bucket filename]
  ;(log/infof "get_json request for bucket: %s  and file: %s" bucket filename)
  (try
    (let [s3object (s3/get-object client-config :bucket-name bucket :key filename) ; need to add a :prefix modeling ard storage on hsm
          s3content (clojure.java.io/reader (:object-content s3object))]
      (json/parse-stream s3content))
    (catch Exception e ;(log/errorf "Error retrieving data from object store: %s" e)
        false)))

(defn get_url
  ([bucketname filename expire]
   (.toString (s3/generate-presigned-url client-config bucketname filename expire)))
  ([bucketname filename]
   (let [today (jt/local-date)
         tomorrow (jt/plus today (jt/days 1))]
     (get_url bucketname filename tomorrow))))

