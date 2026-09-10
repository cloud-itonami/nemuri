(ns nemuri.appview.worker-test
  "Node-test of the actual compiled `nemuri.appview.worker/fetch-handler` —
  same ns, same :esm-target source, as `wrangler.jsonc`'s `main`. Uses the
  real WHATWG `Request`/`Response`/`URL` globals Node 18+ ships (no fetch
  polyfill/mocking), so this exercises the identical code path the Worker
  runs under workerd."
  (:require [cljs.test :refer [deftest is async]]
            [nemuri.appview.worker :as worker]))

(defn- req
  ([path] (req "GET" path nil))
  ([method path body]
   (let [opts (if body
                #js {:method method
                     :body (js/JSON.stringify (clj->js body))
                     :headers #js {"content-type" "application/json"}}
                #js {:method method})]
     (js/Request. (str "https://nemuri.gftd.ai" path) opts))))

(defn- json-then [^js resp cb]
  (.then (.json resp) cb))

(deftest health-test
  (async done
    (-> (worker/fetch-handler (req "/health") #js {} #js {})
        (.then (fn [^js resp]
                 (is (= 200 (.-status resp)))
                 (json-then resp (fn [^js body]
                                    (is (true? (.-ok body)))
                                    (is (= 20 (.-length (.-graphs body))))
                                    (done))))))))

(deftest onboard-xrpc-test
  (async done
    (-> (worker/fetch-handler
         (req "POST" "/xrpc/ai.gftd.apps.nemuri.onboardSubscriber"
              {:did "did:example:a" :plan "basic" :addressRef "pref://addr"})
         #js {} #js {})
        (.then (fn [^js resp]
                 (is (= 200 (.-status resp)))
                 (json-then resp (fn [^js body]
                                    (is (true? (.-validated body)))
                                    (is (some? (.-subId body)))
                                    (is (= 3 (.-length (.-nextActions body))))
                                    (done))))))))

(deftest onboard-missing-required-field-test
  (async done
    (-> (worker/fetch-handler
         (req "POST" "/xrpc/ai.gftd.apps.nemuri.onboardSubscriber" {:plan "basic"})
         #js {} #js {})
        (.then (fn [^js resp]
                 (is (= 404 (.-status resp)))
                 (done))))))

(deftest charge-recurring-camelcase-roundtrip-test
  (async done
    (-> (worker/fetch-handler
         (req "POST" "/xrpc/ai.gftd.apps.nemuri.chargeRecurring"
              {:subId "sub-1" :kind "recurring" :plan "basic"})
         #js {} #js {})
        (.then (fn [^js resp]
                 (is (= 200 (.-status resp)))
                 (json-then resp (fn [^js body]
                                    (is (true? (.-envelopeOk body)))
                                    (is (false? (.-approvalRequired body)))
                                    (done))))))))

(deftest report-pnl-query-via-get-test
  (async done
    (-> (worker/fetch-handler (req "GET" "/xrpc/ai.gftd.apps.nemuri.reportPnl?window=7d" nil) #js {} #js {})
        (.then (fn [^js resp]
                 (is (= 200 (.-status resp)))
                 (json-then resp (fn [^js body]
                                    (is (true? (.-healthy body)))
                                    (done))))))))

(deftest unknown-nsid-test
  (async done
    (-> (worker/fetch-handler (req "POST" "/xrpc/ai.gftd.apps.nemuri.getActiveLp" {}) #js {} #js {})
        (.then (fn [^js resp]
                 (is (= 404 (.-status resp)))
                 (done))))))

(deftest not-found-test
  (async done
    (-> (worker/fetch-handler (req "/nope") #js {} #js {})
        (.then (fn [^js resp]
                 (is (= 404 (.-status resp)))
                 (done))))))
