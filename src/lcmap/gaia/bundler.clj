(ns lcmap.gaia.bundler
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure.java.io       :as io]
            [clojure.java.shell :refer [sh]]
            [comb.template         :as comb]
            [lcmap.gaia.config     :refer [config]]
            [lcmap.gaia.file       :as file]
            [lcmap.gaia.gdal       :as gdal]
            [lcmap.gaia.nemo       :as nemo]
            [lcmap.gaia.raster     :as raster]
            [lcmap.gaia.storage    :as storage]
            [lcmap.gaia.util       :as util]))

(defn download 
  [url name]
  (let [in (io/input-stream url)
        out (io/output-stream name)]
    (io/copy in out)))

(defn ccd_date
  [x]
  ; properties on row of segment table
  true)

(defn training_date
  [x]
  ; properties on row of tile table
  true)

(defn prediction_date
  [x]
  ; properties on row of prediction table
  true)

(defn production_date
  [x]
  true)

(defn get-metadata-values
  [detail]

  (hash-map :pubdate 2019    ; year published
            :endrange 2017   ; last year of observations used
            :westbc          ; bounding coordinates
            :eastbc
            :northbc
            :southbc
            :processing_date ; the final processing date




            ))

(defn get-bundle-values
  [tile year]
  (hash-map :foo "bar"))

(defn get-tiffs
  [details]
  ; download tiffs based on url in details
  (doseq [detail details]
    (try
      (log/infof (format "downloading: %s" (:name detail)))
      (download (:url detail) (:name detail))
      (catch Exception e
        (if (string/includes? (.getMessage e) "403 for URL")
          (do (log/infof (format "received 403 response for %s, setting acl to public read" (:url detail)))
              (storage/set_public_acl (:object-key detail))
              (download (:url detail) (:name detail)))
          (throw (ex-info (format "Error downloading tiff %s" (:url detail)) 
                          {:type "data-request-error" :message (.getMessage e)} (.getCause e)))))))
  details)

(defn validate-tifs
  [details]
  (doseq [detail details]
    (let [name (:name detail)]
      (if (gdal/compressed? name)
        (do (log/infof (format "%s size is %s, and greater than %s, attempting to compress" name size threshold))
            (gdal/compress_geotiff name))
        (log/infof (format "%s appears compressed" name)))))
  details)

(defn generate-layer-metadata
  [details]
  (doseq [detail details
          :let [template (slurp (:metadata-template detail))          ; read in template
                output (string/replace (:name detail) #".tif" ".xml") ; calc output name
                values (get-metadata-values detail)                   ; calc sub values
                metadata (comb/eval template values)]]                ; sub in values ;(comb/eval "Hello <%= name %>" {:name "Alice"})
    (spit output metadata))                                           ; write out file
  details)

(defn generate-bundle-metadata
  [tile date name] ;LCMAP_CU_003010_2010_20181222_V01_CCDC.xml
  (let [template (slurp "templates/bundle_template.xml")
        values (get-bundle-values tile date)
        metadata (comb/eval template values)]
    (spit name metadata)
    name))

(defn generate-cog
  [details cog_name] ; https://trac.osgeo.org/gdal/wiki/CloudOptimizedGeoTIFF
  (let [names (map :name details) 
        lcpri_name (first (filter #(re-matches #"(.*)LCPRI(.*)" %) names))]
    (gdal/generate-cog lcpri_name cog_name)
    cog_name))

(defn generate-observation-list
  [tx ty tile date name]
  (let [results (nemo/observations tx ty)
        dates   (get results "dates")
        newlined (string/join "\n" dates)]
    (spit name newlined)
    name))

(defn assemble-bundle
  [object_names tiff_names metadata_names]
  (let [observations (:observations object_names)
        bundle       (:bundle object_names)
        cog          (:cog object_names)
        cmd (flatten ["tar" "-cf" bundle cog observations tiff_names metadata_names]) 
        result (apply sh cmd)]
    (if (= 0 (:exit result))
      bundle
      (let [msg (format "problem creating tarball %s, %s" bundle (:err result))]
        (log/errorf msg)
        (throw (ex-info msg {:type "data-generation-error" :message msg}))))))

(defn push-bundle
  [names]
  (let [bundle (:bundle names)
        meta   (:bundle-meta names)
        cog    (:cog names)
        dest   (str (:storage-location config) "/")]
    (util/copy-file bundle (str dest bundle))
    (util/copy-file meta (str dest meta))
    (util/copy-file cog (str dest cog))
    (str dest bundle)))

(defn cleanup
  [names]
  (doseq [name names]
    (util/delete name)))

(defn output-names
  [tile date]
  (let [region   (:region config)
        ccdver   (:ccd_ver config)
        year     (first (string/split date #"-"))
        year_ptn (re-pattern (str year "_"))
        today    (util/today-as-str) 
        elements ["LCMAP" region tile year today ccdver]
        base_str (-> (string/join "_" elements) (str "_")) 
        obs_str  (string/replace base_str year_ptn "")]
    (hash-map
     :observations (str obs_str  "ACQS.txt")
     :bundle       (str base_str "CCDC.tar")
     :bundle-meta  (str base_str "CCDC.xml")
     :cog          (str base_str "CCDC.tif"))))

(defn create
  [{tile :tile tx :tx ty :ty date :date :as all}]
  (let [output_names (output-names tile date)
        tiff_details  (storage/latest_tile_tifs tile date raster/product_details)
        tiff_names   (map :name tiff_details)
        xml_names    (map (fn [i] (string/replace i #".tif" ".xml")) tiff_names)
        all_names    (concat tiff_names (vals output_names))]

    (log/infof "received request to create bundle with params: %s" all)

    (try
      ; download tiffs
      (log/infof "downloading tiffs for: %s" all)
      (get-tiffs tiff_details)
      ; validate tif compression
      (log/infof "validating tiff compression")
      (validate-tifs tiff_details)
      ; generate layer metadata
      (log/infof "generating layer metadata")
      (generate-layer-metadata tiff_details)
      ; generate observations list
      (log/infof "generating observation list")
      (generate-observation-list tx ty tile date (:observations output_names))
      ; generate cog
      (log/info "generating COG")
      (generate-cog tiff_details (:cog output_names))
      ; create bundle level metadata
      (log/infof "generating bundle metadata")
      (generate-bundle-metadata tile date (:bundle-meta output_names))
      ; assemble bundle
      (log/infof "assembling bundle")
      (assemble-bundle output_names tiff_names xml_names)
      ; store bundle
      (log/infof "delivering bundle")
      (push-bundle output_names)
      ; cleanup
      (log/infof "cleaning up files")
      (cleanup all_names)

      (merge all {:status "success" :tar (:bundle output_names)})

      (catch Exception e
        (let [msg (format "problem generating tile bundle for tile: %s date: %s, message: %s" tile date (.getMessage e))]
          (log/error msg)
          (throw (ex-info msg {:type "data-generation-error" :message msg} (.getCause e))))))))
