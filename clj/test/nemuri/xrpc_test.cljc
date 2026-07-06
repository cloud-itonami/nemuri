(ns nemuri.xrpc-test
  (:require [clojure.test :refer [deftest is testing]]
            [nemuri.xrpc :as xrpc]))

(deftest key-transform-test
  (testing "request->payload: camelCase -> snake_case, deep"
    (is (= {:sub_id "s1" :daily_jpy 500 :nested {:target_cac 3000}}
           (xrpc/request->payload {:subId "s1" :dailyJpy 500 :nested {:targetCac 3000}}))))
  (testing "response->wire: snake_case -> camelCase, deep, through vectors"
    (is (= {:trackingId "t1" :budget [{:dailyJpy 500 :targetCac 2500}]}
           (xrpc/response->wire {:tracking_id "t1" :budget [{:daily_jpy 500 :target_cac 2500}]}))))
  (testing "single-word keys are a no-op both ways"
    (is (= {:carrier "yamato" :status "ok"} (xrpc/request->payload {:carrier "yamato" :status "ok"})))
    (is (= {:carrier "yamato" :status "ok"} (xrpc/response->wire {:carrier "yamato" :status "ok"})))))

(deftest handle-onboard-test
  (let [{:keys [status body]} (xrpc/handle "ai.gftd.apps.nemuri.onboardSubscriber"
                                            {:did "did:example:a" :plan "basic" :addressRef "pref://addr"})]
    (is (= 200 status))
    (is (true? (:validated body)))
    (is (some? (:subId body)))
    (is (= 3 (count (:nextActions body))))))

(deftest handle-onboard-validation-error-test
  (let [{:keys [status body]} (xrpc/handle "ai.gftd.apps.nemuri.onboardSubscriber" {:plan "basic"})]
    (is (= 404 status))
    (is (some? (:error body)))))

(deftest handle-charge-recurring-test
  (let [{:keys [status body]} (xrpc/handle "ai.gftd.apps.nemuri.chargeRecurring"
                                            {:subId "sub-1" :kind "recurring" :plan "basic"})]
    (is (= 200 status))
    (is (= "charge_planned" (:status body)))
    (is (true? (:envelopeOk body)))))

(deftest handle-unknown-nsid-test
  (let [{:keys [status body]} (xrpc/handle "ai.gftd.apps.nemuri.getActiveLp" {})]
    (is (= 404 status))
    (is (= "unknown_nsid" (:error body)))
    (is (contains? xrpc/not-implemented-nsids "ai.gftd.apps.nemuri.getActiveLp"))))

(deftest handle-plan-growth-nested-test
  (let [{:keys [status body]} (xrpc/handle "ai.gftd.apps.nemuri.planGrowth" {})]
    (is (= 200 status))
    (is (= "search" (get-in body [:decisions 0 :budget 0 :channel])))
    (is (contains? (get-in body [:decisions 0 :budget 0]) :targetCac))))
