(ns nemuri.registry
  (:require [nemuri.core :as core]))

(defn dispatch [graph payload]
  (case graph
    "health" (core/health payload)
    "plan_growth" (core/plan-growth payload)
    "gen_content" (core/gen-content payload)
    "ad_buy" (core/ad-buy payload)
    "lp_optimize" (core/lp-optimize payload)
    "seo_research" (core/seo-research payload)
    "onboard" (core/onboard payload)
    "billing" (core/billing payload)
    "inventory" (core/inventory payload)
    "logistics" (core/logistics payload)
    "takeback" (core/takeback payload)
    "swap" (core/swap payload)
    "care" (core/care payload)
    "retention" (core/retention payload)
    "finance" (core/finance payload)
    "pricing" (core/pricing payload)
    "partner_scout" (core/partner-scout payload)
    "compliance" (core/compliance payload)
    "risk" (core/risk payload)
    "board_review" (core/board-review payload)
    {:error "unknown_graph" :graph graph :graphs core/graph-names}))

(defn dispatch-nsid [nsid payload]
  (if-let [graph (get core/nsid->graph nsid)]
    (dispatch graph payload)
    {:error "unknown_nsid" :nsid nsid :nsids (sort (keys core/nsid->graph))}))
