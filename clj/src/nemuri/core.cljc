(ns nemuri.core
  (:require [clojure.string :as str]))

(def graph-names
  ["health" "plan_growth" "gen_content" "ad_buy" "lp_optimize" "seo_research"
   "onboard" "billing" "inventory" "logistics" "takeback" "swap" "care"
   "retention" "finance" "pricing" "partner_scout" "compliance" "risk"
   "board_review"])

(def nsid->graph
  {"ai.gftd.apps.nemuri.health" "health"
   "ai.gftd.apps.nemuri.planGrowth" "plan_growth"
   "ai.gftd.apps.nemuri.generateContent" "gen_content"
   "ai.gftd.apps.nemuri.manageAds" "ad_buy"
   "ai.gftd.apps.nemuri.optimizeLp" "lp_optimize"
   "ai.gftd.apps.nemuri.researchSeo" "seo_research"
   "ai.gftd.apps.nemuri.onboardSubscriber" "onboard"
   "ai.gftd.apps.nemuri.chargeRecurring" "billing"
   "ai.gftd.apps.nemuri.planInventory" "inventory"
   "ai.gftd.apps.nemuri.dispatchShipment" "logistics"
   "ai.gftd.apps.nemuri.scheduleTakeback" "takeback"
   "ai.gftd.apps.nemuri.offerSwap" "swap"
   "ai.gftd.apps.nemuri.handleInquiry" "care"
   "ai.gftd.apps.nemuri.runRetention" "retention"
   "ai.gftd.apps.nemuri.reportPnl" "finance"
   "ai.gftd.apps.nemuri.experimentPricing" "pricing"
   "ai.gftd.apps.nemuri.scoutPartners" "partner_scout"
   "ai.gftd.apps.nemuri.ensureCompliance" "compliance"
   "ai.gftd.apps.nemuri.guardRisk" "risk"
   "ai.gftd.apps.nemuri.runBoardReview" "board_review"})

(def plan-price {"basic" 1000 "pair" 1800 "premium" 1800})

(defn now-iso []
  #?(:clj (.toString (java.time.Instant/now))
     :cljs (.toISOString (js/Date.))))

(defn blankish? [x]
  (or (nil? x) (and (string? x) (str/blank? x))))

(defn stable-id [prefix & parts]
  (let [n (-> (str/join "|" (map pr-str parts)) hash Math/abs)]
    (str prefix "-" n)))

(defn health [_]
  {:ok true :service "lg-nemuri-clj" :graphs graph-names :runtime "clj" :server_now (now-iso)})

(defn envelope [kind payload]
  (let [amount (long (or (:amount_jpy payload) (:amountJpy payload) 0))
        target-cac (long (or (:target_cac payload) (:targetCac payload) 0))
        po (long (or (:po_jpy payload) (:poJpy payload) 0))]
    (case kind
      "billing" {:allow (<= amount 3000) :approval_required (> amount 3000)}
      "ad_budget" {:allow (and (<= target-cac 3000) (<= amount 10000))
                   :approval_required (or (> target-cac 3000) (> amount 10000))}
      "purchase_order" {:allow (<= po 100000) :approval_required (> po 100000)}
      "legal_publish" {:allow false :approval_required true}
      {:allow true :approval_required false})))

(defn plan-growth [payload]
  {:decisions [{:budget [{:channel (or (:channel payload) "search")
                          :daily_jpy (or (:daily_jpy payload) 1000)
                          :target_cac (min 3000 (or (:target_cac payload) 2500))}]
                :messages ["free takeback" "JPY1000/month" "move-out disposal pain"]
                :new_segments ["students" "solo movers"]}]
   :dispatched ["gen_content" "ad_buy"]
   :dry_run true})

(defn gen-content [payload]
  (let [sections (vec (or (:sections payload) ["hero" "pain" "pricing"]))]
    {:decisions (mapv (fn [section]
                        {:section section
                         :copy (str "Nubatama " section ": JPY1000/month with free takeback.")})
                      sections)
     :dispatched sections
     :dry_run true}))

(defn ad-buy [payload]
  (let [move {:channel (or (:channel payload) "search")
              :daily_jpy (or (:daily_jpy payload) 1000)
              :target_cac (or (:target_cac payload) 2500)}
        env (envelope "ad_budget" move)]
    {:decisions [move]
     :envelope env
     :dispatched (if (:allow env) [(:channel move)] [])
     :dry_run true}))

(defn lp-optimize [payload]
  {:decisions (mapv (fn [section]
                      {:section section
                       :promote_id (str section "-control")
                       :challenger_copy (str "Fresh challenger for " section)})
                    (or (:sections payload) ["hero" "pain" "how" "pricing" "trust" "faq"]))
   :dispatched (vec (or (:sections payload) ["hero" "pain" "how" "pricing" "trust" "faq"]))
   :dry_run true})

(defn seo-research [_]
  {:decisions [{:keyword "布団 サブスク" :intent "subscription" :title "月1000円の洗える敷布団"}
               {:keyword "粗大ゴミ 布団 回収" :intent "disposal" :title "解約時無料回収の敷布団"}]
   :dry_run true})

(defn onboard [payload]
  (let [did (:did payload)
        plan (or (:plan payload) "basic")]
    (if (or (blankish? did) (blankish? (:address_ref payload)))
      {:validated false :error "did and addressRef are required" :next_actions []}
      {:sub_id (stable-id "sub" did plan)
       :validated true
       :next_actions ["billing.initial" "logistics.outbound" "inventory.reserve"]
       :dry_run true})))

(defn billing [payload]
  (let [kind (or (:kind payload) "recurring")
        plan (or (:plan payload) "basic")
        amount (long (or (:amount_jpy payload) (get plan-price plan 1000)))
        env (envelope "billing" {:amount_jpy amount :kind kind})]
    {:stripe_id ""
     :amount_jpy amount
     :envelope_ok (:allow env)
     :approval_required (:approval_required env)
     :status (if (:allow env) "charge_planned" "needs_approval")
     :dry_run true}))

(defn inventory [payload]
  (let [forecast (long (or (:forecast_units payload) 10))
        reorder (max 0 (- forecast (long (or (:on_hand_units payload) 3))))
        po (* reorder 3000)
        env (envelope "purchase_order" {:po_jpy po})]
    {:forecast_units forecast
     :reorder_units reorder
     :po_jpy po
     :po_status (if (:allow env) "issued_plan" "needs_approval")
     :approval_required (:approval_required env)
     :dry_run true}))

(defn logistics [payload]
  (let [carrier (or (:carrier payload) "yamato")
        sub-id (or (:sub_id payload) (:subId payload) "draft")]
    {:tracking_id (str carrier "-" sub-id "-out")
     :carrier carrier
     :status "label_planned"
     :dry_run true}))

(defn takeback [payload]
  (let [carrier (or (:carrier payload) "yamato")
        sub-id (or (:sub_id payload) (:subId payload) "draft")]
    {:tracking_id (str carrier "-" sub-id "-takeback")
     :recycle_manifest (stable-id "manifest" sub-id)
     :status "pickup_planned"
     :dry_run true}))

(defn swap [payload]
  {:decisions [{:cohort "anniversary-12m" :offer "fresh swap + free takeback"}]
   :dispatched [(or (:sub_id payload) (:subId payload) "cohort")]
   :dry_run true})

(defn care [payload]
  (let [body (str/lower-case (str (:body payload "")))
        refund? (or (str/includes? body "refund") (str/includes? body "返金"))]
    {:intent (if refund? "refund" "general")
     :reply_ja (if refund? "返金/解約条件を確認し、必要な手続きを案内します。" "お問い合わせありがとうございます。確認してご案内します。")
     :refund_proposed_jpy (if refund? 3000 0)
     :dry_run true}))

(defn retention [_]
  {:decisions [{:cohort "month-10" :offer "fresh-swap reminder" :expected_save_rate 120}]
   :dispatched ["mailer.plan"]
   :dry_run true})

(defn finance [payload]
  {:healthy true
   :summary (str "P&L snapshot for " (or (:window payload) "1d") ": baseline within guardrails.")
   :alerts []})

(defn pricing [_]
  {:decisions [{:kind "upper-tier" :price_floor_jpy 1000 :experiment "premium accessories"}]
   :dispatched ["pricing_review"]
   :approval_required true
   :dry_run true})

(defn partner-scout [_]
  {:decisions [{:type "supplier" :name "compressed futon wholesaler" :target_terms "unit cost <= JPY3000"}
               {:type "recycler" :name "hanmou recycler" :target_terms "manifest per takeback"}]
   :approval_required true
   :dry_run true})

(defn compliance [_]
  {:dispatched ["tokutei-review" "tos-review" "recycling-manifest-review"]
   :approval_required true
   :dry_run true})

(defn risk [payload]
  (let [mode (or (:mode payload) "scan")]
    {:breaches []
     :kill_switch []
     :mode mode
     :dry_run true}))

(defn board-review [_]
  {:diagnosis "No critical bottleneck in deterministic CLJ plan."
   :decisions [{:block "channels" :actor "growthStrategist" :change "keep CAC under JPY3000"}]
   :okrs ["validate landing-page promise" "keep payback below 9 months"]
   :dispatched ["plan_growth" "finance" "risk"]
   :dry_run true})
