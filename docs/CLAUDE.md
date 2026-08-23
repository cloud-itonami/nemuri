# ai-gftd-project-nemuri — 月1,000円 洗える敷布団サブスク (フルLLM自律運営)

> **Standalone owner / retirement boundary (2026-07-19)**: canonical source is
> `orgs/gftdcojp/ai-gftd-nemuri`. The old Python `lg/` pod was never deployed
> and was pruned on 2026-07-07; no Helm release existed. The numbered-root
> TS/Svelte facade and its dispatcher hop are also retired. Its typed D1
> handlers remain provenance for a future CLJC port, not deployable source.
> Exact source paths and dispositions are recorded in `MIGRATION.edn`.

設計 provenance = 旧 root `90-docs/adr/2606071200-nemuri-llm-operated-subscription-bmc-actors.md`。

## Overview

既製品 (Amazon 圧縮敷布団・洗える・抗菌防臭防ダニ・シングル) を在庫商材に、**運営をすべて LLM アクターで自律運転**するマットレスサブスク。BMC 9ブロック + LP を担当アクター(三位一体: magatama actor + LangGraph graph + Lexicon NSID)に割り当て、keiei CEO オーケストレータが週次 OODA でキャンバスを回す。

- **モデル**: Rent-to-own (最低契約12ヶ月)。¥1,000/月、ユーザーが洗濯、圧縮便、解約時無料回収(粗大ゴミ解消)。
- **ユニットエコノミクス**: 継続20ヶ月で粗利率46.5% / LTV-CAC=3.1x / 回収~8ヶ月。最大レバー=継続月数。

## Identifier

| 層 | 値 |
|---|---|
| Global brand | **Nubatama (ぬばたま)** |
| Brand domain | `nubatama.net` (canonical, Cloudflare Registrar, 2026-06-07 取得)。`.com` は第三者保有・取得不可 → `.net` のみ正式 |
| Primary DID | `did:web:nemuri.gftd.ai` |
| Handle | `nemuri.gftd.ai` |
| nanoid | `nmr5l33p` |
| NSID | `ai.gftd.apps.nemuri.*` |

> ブランド/インフラ分離 (Consensys パターン): 対外は `nubatama.net`、内部 handle/DID/NSID/lexicon は `nemuri` のまま不変。appview Worker route に `nubatama.net/*` を追加 (wrangler.jsonc)。

## Actor roster (19)

`did:web:nemuri.gftd.ai:actor:<name>`。自律クラス A=完全自動 / B=ポリシー封筒内自動・超過時人間承認。

growthStrategist(planGrowth) / contentSmith(generateContent) / adBuyer(manageAds, B) / lpOptimizer(optimizeLp) / seoResearcher(researchSeo) / onboardConcierge(onboardSubscriber) / billingClerk(chargeRecurring, B) / inventoryPlanner(planInventory, B) / logisticsDispatcher(dispatchShipment) / takebackCoordinator(scheduleTakeback) / swapScheduler(offerSwap) / careAgent(handleInquiry) / retentionAgent(runRetention) / financeController(reportPnl) / pricingExperimenter(experimentPricing, B) / partnerScout(scoutPartners, B) / complianceOfficer(ensureCompliance, B) / riskSentinel(guardRisk, 緊急権限) / ceoOrchestrator(runBoardReview, keiei)

## Persistence

- **Domain** = 現在は永続化なし。CLJ task runtime と appview Worker は plan-only で、D1/Stripe/Carrier/Ads/OPA/LLM/mailer を直接呼ばない。未使用だった `ai-gftd-nemuri` D1 は 2026-08-15 に R2 archive 後 retire。将来の永続化は kotobase.net/R2 plane を使う。
- **State (PII/住所/決済)** = T3 Preferences / Stripe。R2 にも AT Record にも書かない (ADR-0018)。
- **Checkpointer** = なし。CLJ runtime は deterministic request/response surface。

## Layout

```
clj/{deps.edn, bb.edn, langgraph.edn, Dockerfile,
  src/nemuri/{core,registry,server,xrpc}.cljc, test/nemuri/*.cljc}
appview/ai-gftd-wasm-nemuri-nmr5l33p/{magatama.jsonld, wrangler.jsonc,
  package.json, shadow-cljs.edn,
  src/nemuri/appview/worker.cljs, test/nemuri/appview/worker_test.cljs,
  d1/migrations/0001_nemuri.sql}
00-contracts/lexicons/ai/gftd/apps/nemuri/*.json (21 methods + 6 records)
00-contracts/policies/nemuri/envelope.rego  (OPA: data.nemuri.envelope)
CEO OODA is represented by the CLJ `board_review` plan surface.
```

> 2026-07-06 update (ADR-2607061200): the numbered-root `svelte/` + `src/app.ts`
> ("SvelteKit adapter fallback") layout this note replaces was never actually
> built and is now superseded — per ADR-2606290000 ("worker は全て CLJC の
> みに") SvelteKit isn't the target substrate for new gftdcojp Workers
> anyway. The appview Worker is a plain shadow-cljs `:esm` fetch worker (the
> `orgs/kotoba-lang/kotobase-cljc-worker` pattern): `nemuri.appview.worker`
> requires `nemuri.core` / `nemuri.registry` / `nemuri.xrpc` straight from
> `clj/src` as a shadow-cljs source-path — one `.cljc` plan surface, run on
> the JVM (`clj/test`, `nemuri.server`) *and* compiled into the Worker, no
> parallel TS/Svelte reimplementation. Build:
> `cd appview/ai-gftd-wasm-nemuri-nmr5l33p && npm install && npm run build`
> -> `out/worker.js` (`wrangler.jsonc` `main`). `npm test` runs the same
> fetch-handler under Node's real `Request`/`Response`/`URL` globals
> (`:worker-test`, node-test target) — no mocking.

## ガバナンス封筒 (envelope.rego)

価格下限¥1,000 / CAC上限¥3,000 / 広告日次上限 / PO月次上限 / 自動返金≤¥3,000 / 法務公開・契約は常に人間ゲート。riskSentinel が異常検知で class-B 凍結(kill-switch)。

## Deploy checklist (forward work)

1. Lexicon 3-step: `bundle-lexicons.mjs` → `gen-pds-lexicon-registry.mjs` → atproto `wrangler deploy`
2. 永続化が必要になった場合は kotobase.net/R2 adapter を実装し、実ルートのテストを追加
3. lg-nemuri-clj build/push from `clj/Dockerfile` + Helm chart (`50-infra/vultr/lg-nemuri-pool/`, lg-yukkuri-pool ミラー)
4. OPA に envelope.rego ロード
5. Stripe Billing / 3PL / 反毛リサイクル業者 契約 + 特商法ページ法務レビュー
6. `deps.toml` `[[projects]]` / `[[mitama_actors]]` 登録

## ⚠️ 未実装 / stubbed (2026-07-06 現在、ADR-2607061200)

- **appview Worker は結線済み**: `POST /xrpc/{nsid}`（`reportPnl` は `query` なので
  `GET` も可）が lexicon 21 method 中 20 を `nemuri.core`/`nemuri.registry` の
  pure handler に直結（dispatcher.gftd.ai 経由なし。そのホップは恒久的に死んで
  いる — k8s クラスタ自体が撤去済み）。
- **`ai.gftd.apps.nemuri.getActiveLp`（`query`）のみ未実装**: lexicon には
  存在するが `nemuri.core/nsid->graph` に対応 graph が無く、永続化された
  `lp_variants` の read が要る唯一の NSID。旧D1 migration は schema/seed の
  provenance として残すが、実装時は kotobase.net/R2 adapter を使う。
  他 19 NSID の "zero I/O / pure" 前提が崩れるため、意図的に後続タスクとして
  残してある（`nemuri.xrpc/not-implemented-nsids` に明示）。呼ぶと
  `unknown_nsid`（404）。
- graph の外部 API 実体 (Stripe charge / 各キャリア label / Ads API / mailer 送信)
  は依然 binding 経由の結線が必要。現状 CLJ graph は intent/plan +
  envelope-like guardrail result のみを返し、money/legal/PO/contract 系は
  `approvalRequired` / `dryRun` を明示する（Worker はこれをそのまま透過する
  だけで、承認・実行ロジックは一切持たない）。
