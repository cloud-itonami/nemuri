(ns nemuri.core-test
  (:require [clojure.test :refer [deftest is]]
            [nemuri.core :as core]
            [nemuri.registry :as registry]))

(deftest graph-surface-test
  (is (= 20 (count core/graph-names)))
  (is (= "billing" (get core/nsid->graph "ai.gftd.apps.nemuri.chargeRecurring")))
  (is (= "board_review" (get core/nsid->graph "ai.gftd.apps.nemuri.runBoardReview"))))

(deftest envelope-test
  (is (true? (:allow (core/envelope "billing" {:amount_jpy 1000}))))
  (is (true? (:approval_required (core/envelope "billing" {:amount_jpy 5000}))))
  (is (true? (:approval_required (core/envelope "legal_publish" {})))))

(deftest business-graphs-test
  (is (= "needs_approval" (:status (core/billing {:kind "refund" :amount_jpy 5000}))))
  (is (= "charge_planned" (:status (core/billing {:kind "recurring" :plan "basic"}))))
  (is (= "yamato-sub-1-out" (:tracking_id (core/logistics {:sub_id "sub-1"}))))
  (is (= 3 (count (:next_actions (core/onboard {:did "did:example:a" :plan "basic" :address_ref "pref://addr"})))))
  (is (true? (:approval_required (core/compliance {})))))

(deftest registry-test
  (is (:ok (registry/dispatch "health" {})))
  (is (= "unknown_graph" (:error (registry/dispatch "missing" {}))))
  (is (= "charge_planned"
         (:status (registry/dispatch-nsid "ai.gftd.apps.nemuri.chargeRecurring"
                                          {:kind "recurring" :plan "basic"})))))
