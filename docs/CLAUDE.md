# ai-gftd-project-nemuri — 月1,000円 洗える敷布団サブスク (フルLLM自律運営)

共通ルールは `60-apps/CLAUDE.md`。設計 SSoT = `90-docs/adr/2606071200-nemuri-llm-operated-subscription-bmc-actors.md`。

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

## Persistence (ADR-2606041900 = yukkuri D1 パターン)

- **Domain** = Cloudflare D1 (`ai-gftd-nemuri`)。appview Worker `/_d1`(`x-internal-trust`, typed ops) が D1 を所有。CLJ task runtime は plan-only で D1/Stripe/Carrier/Ads/OPA/LLM/mailer を直接呼ばない。
- **State (PII/住所/決済)** = T3 Preferences / Stripe。D1 にも AT Record にも書かない (ADR-0018)。
- **Checkpointer** = なし。CLJ runtime は deterministic request/response surface。

## Layout

```
clj/{deps.edn, bb.edn, langgraph.edn, Dockerfile, src/nemuri/{core,registry,server}.cljc*, test/nemuri/*.cljc}
appview/ai-gftd-wasm-nemuri-nmr5l33p/{magatama.jsonld, wrangler.jsonc, src/app.cljc,
  svelte/src/{lib/api.cljc, routes/+page.svelte, routes/xrpc/[nsid]/+server.cljc, routes/_d1/+server.cljc},
  d1/migrations/0001_nemuri.sql}
00-contracts/lexicons/ai/gftd/apps/nemuri/*.json (21 methods + 6 records)
00-contracts/policies/nemuri/envelope.rego  (OPA: data.nemuri.envelope)
CEO OODA is represented by the CLJ `board_review` plan surface.
```

## ガバナンス封筒 (envelope.rego)

価格下限¥1,000 / CAC上限¥3,000 / 広告日次上限 / PO月次上限 / 自動返金≤¥3,000 / 法務公開・契約は常に人間ゲート。riskSentinel が異常検知で class-B 凍結(kill-switch)。

## Deploy checklist (forward work)

1. Lexicon 3-step: `bundle-lexicons.mjs` → `gen-pds-lexicon-registry.mjs` → atproto `wrangler deploy`
2. D1 作成 + `wrangler d1 migrations apply ai-gftd-nemuri` + wrangler.jsonc の `database_id` 差替
3. lg-nemuri-clj build/push from `clj/Dockerfile` + Helm chart (`50-infra/vultr/lg-nemuri-pool/`, lg-yukkuri-pool ミラー)
4. OPA に envelope.rego ロード
5. Stripe Billing / 3PL / 反毛リサイクル業者 契約 + 特商法ページ法務レビュー
6. `deps.toml` `[[projects]]` / `[[mitama_actors]]` 登録

## ⚠️ 未実装 (scaffold段階)

graph の外部 API 実体 (Stripe charge / 各キャリア label / Ads API / mailer 送信) は Worker側 binding 経由で結線が必要。現状 CLJ graph は intent/plan + envelope-like guardrail result のみを返し、money/legal/PO/contract 系は `approval_required` / `dry_run` を明示する。`src/app.cljc` は SvelteKit adapter の fallback。
