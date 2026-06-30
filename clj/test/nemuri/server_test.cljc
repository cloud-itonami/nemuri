(ns nemuri.server-test
  (:require [clojure.test :refer [deftest is]]
            [jsonista.core :as json]
            [nemuri.server :as server])
  (:import [java.net HttpURLConnection URL ServerSocket]
           [java.nio.charset StandardCharsets]))

(def mapper (json/object-mapper {:decode-key-fn keyword :encode-key-fn name}))

(defn free-port []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(defn request [base method path body]
  (let [conn ^HttpURLConnection (.openConnection (URL. (str base path)))]
    (.setRequestMethod conn method)
    (.setRequestProperty conn "accept" "application/json")
    (when body
      (.setDoOutput conn true)
      (.setRequestProperty conn "content-type" "application/json")
      (with-open [out (.getOutputStream conn)]
        (.write out (.getBytes (json/write-value-as-string body mapper) StandardCharsets/UTF_8))))
    (let [status (.getResponseCode conn)
          stream (if (< status 400) (.getInputStream conn) (.getErrorStream conn))]
      {:status status :body (json/read-value (slurp stream) mapper)})))

(deftest http-surface-test
  (let [port (free-port)
        srv (server/create-server port)
        base (str "http://127.0.0.1:" port)]
    (try
      (.start srv)
      (let [ok (request base "GET" "/ok" nil)
            run (request base "POST" "/runs" {:assistant_id "billing"
                                              :input {:kind "recurring" :plan "basic"}})
            xrpc (request base "POST" "/xrpc/ai.gftd.apps.nemuri.onboardSubscriber"
                          {:did "did:example:a" :plan "basic" :addressRef "pref://addr"})
            missing (request base "POST" "/runs" {:assistant_id "missing" :input {}})]
        (is (= 200 (:status ok)))
        (is (= 20 (count (get-in ok [:body :graphs]))))
        (is (= "charge_planned" (get-in run [:body :status])))
        (is (= true (get-in xrpc [:body :validated])))
        (is (= 404 (:status missing))))
      (finally
        (.stop srv 0)))))
