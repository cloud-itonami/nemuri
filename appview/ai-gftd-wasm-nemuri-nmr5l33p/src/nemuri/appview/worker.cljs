(ns nemuri.appview.worker
  "workerd :esm entry for ai-gftd-nemuri (`nemuri.gftd.ai` / `nubatama.net`).

  Replaces the dead dispatcher.gftd.ai hop (the k8s cluster it forwarded
  XRPC calls to is gone — owner directive: k8s deprecated, prune) with a
  direct in-Worker route: `POST /xrpc/{nsid}` (and `GET` for the two
  `query`-typed NSIDs) dispatches straight to the pure, zero-I/O
  `nemuri.core`/`nemuri.registry` plan surface via `nemuri.xrpc` (camelCase
  <-> snake_case + status mapping, shared with the JVM dev server). No
  network hop, no D1/Stripe/carrier/OPA/LLM/mailer call anywhere in this
  path — every handler returns `dry_run true` / `approval_required` per
  ADR-2606071200's envelope model; see ADR-2607061200 for what's still
  stubbed (`ai.gftd.apps.nemuri.getActiveLp` needs a persistence-backed
  implementation, which this Worker does not yet wire up). The unused D1
  reservation was retired on 2026-08-15; a future implementation must use
  the kotobase.net/R2 persistence plane."
  (:require [clojure.string :as str]
            [nemuri.core :as core]
            [nemuri.xrpc :as xrpc]))

(def ^:private xrpc-prefix "/xrpc/")
(def ^:private health-paths #{"/" "/health" "/ok"})

(defn- json-response [body status]
  (js/Response. (js/JSON.stringify (clj->js body))
                #js {:status status
                     :headers #js {"content-type" "application/json"
                                   "access-control-allow-origin" "*"}}))

(defn- cors-preflight []
  (js/Response. nil #js {:status 204
                         :headers #js {"access-control-allow-origin" "*"
                                       "access-control-allow-methods" "GET, POST, OPTIONS"
                                       "access-control-allow-headers" "content-type"
                                       "access-control-max-age" "86400"}}))

(defn- query-params->map
  "URLSearchParams -> a plain (still camelCase) keyword map, e.g. for
  `GET /xrpc/ai.gftd.apps.nemuri.reportPnl?window=7d`."
  [^js url]
  (let [out (atom {})]
    (.forEach (.-searchParams url) (fn [v k] (swap! out assoc (keyword k) v)))
    @out))

(defn- xrpc-response [nsid raw-payload]
  (let [{:keys [status body]} (xrpc/handle nsid raw-payload)]
    (json-response body status)))

(defn fetch-handler
  "`{fetch}` — the Worker's only export. `env`/`ctx` are accepted (workerd's
  calling convention) but unused: nothing on this path touches a binding."
  [^js req _env _ctx]
  (let [url    (js/URL. (.-url req))
        path   (.-pathname url)
        method (.-method req)]
    (cond
      (= "OPTIONS" method)
      (js/Promise.resolve (cors-preflight))

      (and (= "GET" method) (contains? health-paths path))
      (js/Promise.resolve (json-response (core/health {}) 200))

      (str/starts-with? path xrpc-prefix)
      (let [nsid (subs path (count xrpc-prefix))]
        (if (= "GET" method)
          (js/Promise.resolve (xrpc-response nsid (query-params->map url)))
          (-> (.json req)
              (.catch (fn [_] #js {}))
              (.then (fn [raw] (xrpc-response nsid (js->clj raw :keywordize-keys true)))))))

      :else
      (js/Promise.resolve (json-response {:ok false :error "NotFound"} 404)))))

(def handler #js {:fetch fetch-handler})
