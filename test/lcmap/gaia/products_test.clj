(ns lcmap.gaia.products-test
  (:require [clojure.test :refer :all]
            [lcmap.gaia.products :as products]
            [lcmap.gaia.file     :as file]
            [lcmap.gaia.util     :as util]
            [lcmap.gaia.test-resources :as tr]))


(def first_pixel (first tr/pixel_map))
(def first_segments_predictions (first (vals first_pixel))) 
(def response_set (set [:pixelx :pixely :val]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;    CHANGE PRODUCT TESTS    ;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest time-of-change-single-model-test
  (let [result (products/time-of-change (first (:segments first_segments_predictions))  tr/query_ord 100 -100)]
    (is (= (set (keys result))  response_set))))

(deftest time-of-change-chip-level-test
  (let [results (map #(products/time-of-change (-> % (keys) (first)) (-> % (vals) (first)) tr/query_ord) tr/pixel_map)
        first_result (first results)]
    (is (= (count results) 10000))
    (is (= (set (keys first_result)) response_set))))

(deftest time-since-change-single-model-test
  (let [result (products/time-since-change (first (:segments first_segments_predictions)) tr/query_ord 100 -100)]
    (is (= (set (keys result)) response_set))))

(deftest time-since-change-chip-level-test
  (let [results (map #(products/time-since-change (-> % (keys) (first)) (-> % (vals) (first)) tr/query_ord) tr/pixel_map)
        non_nils (filter (fn [i] (some? (:val i))) results)]
    ;(is (= (count non_nils) 763))
    (is (= (count results) 10000))))

(deftest magnitude-of-change-single-model-test
  (let [result (products/magnitude-of-change (first (:segments first_segments_predictions)) tr/query_ord 100 -100)]
    (is (= (set (keys result)) response_set))))

(deftest magnitude-of-change-chip-level-test
  (let [results (map #(products/magnitude-of-change (-> % (keys) (first)) (-> % (vals) (first)) tr/query_ord) tr/pixel_map)
        gt_zero (filter (fn [i] (> (:val i) 0)) results)]
    (is (= (count gt_zero) 387))
    (is (= (count results) 10000))))

(deftest length-of-segment-single-model-test
  (let [result (products/length-of-segment (first (:segments first_segments_predictions)) tr/query_ord 100 -100)]
    (is (= (set (keys result)) response_set))))

(deftest length-of-segment-chip-level-test
  (let [results (map #(products/length-of-segment (-> % (keys) (first)) (-> % (vals) (first)) tr/query_ord) tr/pixel_map)
        gt_zero (filter (fn [i] (> (:val i) 0)) results)]
    (is (= (count gt_zero) 5633))
    (is (= (count results) 10000))))

(deftest curve-fit-single-model-test
  (let [result (products/curve-fit (first (:segments first_segments_predictions)) tr/query_ord 100 -100)]
    (is (= (set (keys result)) response_set))))

(deftest curve-fit-chip-level-test
  (let [results (map #(products/curve-fit (-> % (keys) (first)) (-> % (vals) (first)) tr/query_ord) tr/pixel_map)
        gt_zero (filter (fn [i] (> (:val i) 0)) results)]
    (is (= (count gt_zero) 9989))
    (is (= (count results) 10000))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;    CLASSIFICATION PRODUCT TESTS    ;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest falls_between_eday_sday-coll-test
  (let [map_a {:follows_eday true :precedes_sday false}
        map_b {:precedes_sday true :follows_eday false}
        expected [map_a map_b]]
    (is (= (products/falls-between-eday-sday map_a map_b) expected))))

(deftest falls_between_eday_sday-coll-test
  (let [map_a {:follows_eday true :precedes_sday false}
        map_b {:precedes_sday true :follows_eday false}
        expected [map_a map_b]]
    (is (= (products/falls-between-eday-sday map_a map_b) expected))))

(deftest falls_between_eday_sday-map-test
  (let [map_a {:follows_eday false :precedes_sday true}
        map_b {:precedes_sday true :follows_eday false}]
    (is (= (products/falls-between-eday-sday map_a map_b) map_b))))

(deftest falls_between_eday_sday-nonmap-test
  (let [map_a {:follows_eday true :precedes_sday false}
        map_b {:precedes_sday true :follows_eday false}
        map_c {:precedes_sday true :follows_eday false}
        expected [map_a map_b]]
    (is (= (products/falls-between-eday-sday [map_a map_b] map_c) expected))))

(deftest falls_between_bday_sday-coll-test
  (let [map_a {:follows_bday true :precedes_sday false}
        map_b {:precedes_sday true :follows_bday false}
        expected [map_a map_b]]
    (is (= (products/falls-between-bday-sday map_a map_b) expected))))

(deftest nbr_test
  (let [first_nbr (products/nbr tr/first_segment)
        last_nbr  (products/nbr tr/last_segment)]
    (is (> first_nbr 0.12))
    (is (< first_nbr 0.14))
    (is (> last_nbr  0.023))
    (is (< last_nbr  0.024))))

(deftest get_class_test
  (let [first_class (products/get-class tr/first_probs)
        last_class  (products/get-class tr/last_probs)]
    (is (= first_class 7))
    (is (= last_class 2))))

(deftest first_date_of_class_test
  (let [sorted_predictions (util/sort-by-key tr/first_grouped_predictions :date)]
    (is (= "1995-07-01" (products/first-date-of-class sorted_predictions 7)))
    (is (= "2012-07-01" (products/first-date-of-class sorted_predictions 5)))
    (is (= nil (products/first-date-of-class sorted_predictions 4)))))

(deftest mean_test
  (let [coll [4 2 6 88 7]]
    (is (= (float 21.4) (products/mean coll)))))

(deftest mean_probabilities_test
  (let [preds [{:prob [0 1 2 3 4 5 6 7 8]} {:prob [7 8 9 5 8 7 6 5 5]}]]
    (is (= [3.5 4.5 5.5 4.0 6.0 6.0 6.0 6.0 6.5]
           (products/mean-probabilities preds)))))

(deftest classify_positive_nbr_test
  (let [first_segment (first tr/first_sorted_segments)
        sday (-> first_segment (:sday) (util/to-ordinal))
        nbrdiff (products/nbr first_segment)
        segment_probabilities (filter (fn [i] (= (util/to-ordinal (:sday i)) sday)) tr/first_probabilities)
        sorted_probabilities (util/sort-by-key segment_probabilities :date)
        segment_model (merge first_segment {:probabilities sorted_probabilities})]
    (is (= 7 (products/classify segment_model tr/query_ord 0 nbrdiff)))))

(deftest classify_negative_nbr_test
  (is true))

(deftest classify_else_test
  (is true))



