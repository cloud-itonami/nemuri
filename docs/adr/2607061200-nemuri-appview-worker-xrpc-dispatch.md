# ADR-2607061200: nemuri appview Worker — direct XRPC dispatch to the pure CLJ plan surface (dispatcher.gftd.ai removed, not replaced)

**Status**: accepted
**Date**: 2026-07-06
**Scope**: `orgs/gftdcojp/ai-gftd-nemuri` (`appview/ai-gftd-wasm-nemuri-nmr5l33p`)

## Context

`dispatcher.gftd.ai` (a router that forwarded Cloudflare Worker XRPC calls to
k8s pods) is permanently dead — the k8s cluster behind it is gone (owner
directive: k8s deprecated, prune). This is one of several org apps that
referenced it.

Unlike `club-shinshi` (which had a *live* broken dispatcher call ported
directly into its Worker against D1 bindings it already owned), scouting
`ai-gftd-nemuri` found it was **never actually wired up**:

- `wrangler.jsonc` declared `"main": "src/app.cljc"` and a `DISPATCHER_URL`
  var pointing at `dispatcher.gftd.ai/xrpc`, with routes for `nubatama.net`
  and `nemuri.gftd.ai`.
- Neither `src/app.cljc` nor any `svelte/` tree existed on disk. The repo's
  own `docs/CLAUDE.md` had flagged this layout — `svelte/src/{lib/api.cljc,
  routes/+page.svelte, routes/xrpc/[nsid]/+server.cljc, routes/_d1/+server.cljc}`
  plus `src/app.cljc` as "SvelteKit adapter の fallback" — as 未実装
  (scaffold段階), i.e. aspirational, never built.
- The real backend logic already existed as clean, portable `.cljc`:
  `clj/src/nemuri/{core,registry,server}.cljc`. `nemuri.server` is a working
  JVM dev server (`com.sun.net.httpserver`) with its own `/xrpc/{nsid}` route,
  tested by `clj/test/nemuri/server_test.cljc`.

Two things had to be verified rather than assumed before building anything:

1. **Was the "SvelteKit + `.cljc` route files" layout ever a real, working
   toolchain anywhere in this org, or aspirational?** Investigated across
   the superproject: no `vite.config.*`/`svelte.config.*` anywhere
   recognizes a `.cljc` extension; no `+server.cljc`/`+page.cljc` file exists
   anywhere under `orgs/`. The one already-shipped comparable app
   (`club-shinshi`'s `ai-gftd-wasm-shinshi-sh1n5h1x`) is plain `.ts` under
   SvelteKit, with `wrangler.jsonc` pointing `main` at the standard
   `@sveltejs/adapter-cloudflare` output — not a `.cljc` file. Separately,
   **ADR-2606290000** ("worker は全て CLJC のみに", 2026-06-29) already
   commits this org to *abolishing* SvelteKit for new/modified Workers, in
   favor of a raw shadow-cljs `:esm` fetch worker. `orgs/kotoba-lang/
   kotobase-cljc-worker` is a real, shipped example of exactly that pattern:
   `shadow-cljs.edn` compiles `.cljc`/`.cljs` source into `out/worker.js`
   (`:esm` target, `{:exports {default ns/handler}}`), and `wrangler.jsonc`
   `main` points at that compiled JS, never at `.cljc` source directly.
   **Conclusion: the SvelteKit layout was vaporware; the org's actual,
   working, current-policy pattern is the shadow-cljs `:esm` fetch worker.**
   This ADR builds that pattern, not the SvelteKit one.
2. **Does the "zero I/O, pure functions" claim about the 20 NSID handlers
   actually hold, and do they really compile/run under cljs (required for a
   shadow-cljs Worker build), not just the JVM?** Confirmed by reading
   `clj/src/nemuri/{core,registry}.cljc` (no D1/Stripe/carrier/Ads/OPA/LLM/
   mailer references anywhere) and by an actual shadow-cljs compile + `node`
   run of both namespaces under `:node-script`/`:node-test` targets before
   writing any Worker code — 0 warnings, correct output. Confirmed against
   the true source of the NSID contracts, `00-contracts/lexicons/ai/gftd/
   apps/nemuri/*.json` in the org monorepo (`ai-gftd-apps-gftdcojp`), not
   guessed from the actor roster prose.

## Decision

1. **New appview Worker**: `appview/ai-gftd-wasm-nemuri-nmr5l33p/{package.json,
   shadow-cljs.edn, src/nemuri/appview/worker.cljs, test/nemuri/appview/
   worker_test.cljs}`, following the `kotobase-cljc-worker` pattern —
   `shadow-cljs.edn`'s `:worker` build (`:esm` target) has a source-path
   `../../clj/src` that pulls `nemuri.core`/`nemuri.registry`/`nemuri.xrpc`
   straight from the shared pure-logic tree; no reimplementation, no
   parallel TS/Svelte surface. `npm run build` -> `out/worker.js`;
   `wrangler.jsonc`'s `main` now points there (was `src/app.cljc`, which
   never existed).
2. **New shared pure ns `clj/src/nemuri/xrpc.cljc`**: converts between the
   lexicon's camelCase wire format (`subId`, `addressRef`, `dailyJpy`, ...)
   and the snake_case keys `nemuri.core`'s handlers use (`:sub_id`,
   `:address_ref`, `:daily_jpy`, ...), deep through nested maps/vectors, and
   dispatches via `nemuri.registry/dispatch-nsid` with the same status
   convention `nemuri.server/handle-xrpc` already uses (any `:error` key on
   the result -> 404, else 200) — so the Worker and the existing, tested JVM
   dev server agree on behavior for the same NSID + payload. Verified
   correct against every one of the 20 lexicon output schemas field-by-field
   (see Verification below); `nemuri.server` itself is untouched (it has its
   own inline, narrower camel->snake and is already covered by its own
   tests — this is additive, not a refactor).
3. **Worker routing**: any `/xrpc/{nsid}` accepts either `POST` (JSON body ->
   payload map, the atproto `procedure` convention — 19 of the 20 live
   NSIDs) or `GET` (querystring -> payload map, the `query` convention —
   `reportPnl`, e.g. `?window=7d`); the router doesn't gate by the lexicon's
   declared `procedure`/`query` type, so either verb reaches any NSID (lenient
   by design — the pure handlers are idempotent/side-effect-free either way).
   `GET /`, `/health`, `/ok` -> `nemuri.core/health` directly (parity with
   `nemuri.server`'s convenience health route); `OPTIONS` -> CORS preflight;
   anything else -> 404. No `dispatcher.gftd.ai` call anywhere in this path,
   ever — the hop is removed, not replaced.
4. **`wrangler.jsonc` cleanup**: removed the dead `vars.DISPATCHER_URL` and
   the `DISPATCHER_INTERNAL_SECRET` comment (that secret authenticated only
   to the dead dispatcher). `NEMURI_DB` (D1) binding is left in place,
   unused by any route yet — reserved for `getActiveLp` (below).

## What's live vs. stubbed

**20 of the lexicon's 21 procedure/query NSIDs are live** (the other 6
lexicon files — `billingEvent`, `boardDecision`, `inquiry`, `lpVariant`,
`shipment`, `subscription` — are AT-proto `record` schemas, not routes),
dispatched straight to the pure plan surface, zero I/O, every response's
`dryRun`/`approvalRequired` flags passed through unchanged: `health`,
`planGrowth`, `generateContent`, `manageAds`, `optimizeLp`, `researchSeo`,
`onboardSubscriber`, `chargeRecurring`, `planInventory`, `dispatchShipment`,
`scheduleTakeback`, `offerSwap`, `handleInquiry`, `runRetention`,
`reportPnl`, `experimentPricing`, `scoutPartners`, `ensureCompliance`,
`guardRisk`, `runBoardReview` — exactly the 20 `nsid->graph` entries already
in `nemuri.core` (and exactly the 20 NSIDs this task's scouting pass named).

**1 NSID remains stubbed and is explicitly out of scope here**:
`ai.gftd.apps.nemuri.getActiveLp` (a `query`, per the lexicon — not among the
20 named above, and not in `nemuri.core/nsid->graph` either) needs an actual
read of the D1 `lp_variants` table (`d1/migrations/0001_nemuri.sql` already
seeds it), which is real I/O the rest of this cut deliberately doesn't own.
Routing it hits `nemuri.registry/dispatch-nsid`'s generic `unknown_nsid`
fallback (404), tracked explicitly in code as
`nemuri.xrpc/not-implemented-nsids`. Follow-up: wire a D1-backed read path
once the Worker is ready to own any I/O at all (today it owns none, by
design — see `docs/CLAUDE.md`'s persistence note).

Separately unimplemented, unchanged from before this ADR (not a regression,
never existed): actual Stripe charge execution, carrier label purchase, Ads
API calls, mailer sends, OPA envelope evaluation as a real policy engine
(`nemuri.core/envelope` is a hardcoded stand-in for the Rego policy),
legal-copy publish. Every handler already flags these `dryRun`/
`approvalRequired`; this ADR doesn't change that contract, only removes the
dead network hop and makes the 20 pure handlers actually reachable over
HTTP from the Worker.

## Verification

- **JVM**: `cd clj && clojure -M:test` — 11 tests / 38 assertions / 0
  failures (pre-existing `nemuri.core-test` + `nemuri.server-test`, plus new
  `nemuri.xrpc-test` covering the camelCase<->snake_case transform and
  `xrpc/handle` against `onboardSubscriber`, `chargeRecurring`, `planGrowth`
  (nested budget lines), and the `getActiveLp` 404 fallback).
- **cljs / Worker**: `cd appview/ai-gftd-wasm-nemuri-nmr5l33p && npm install
  && npm test` — `shadow-cljs compile worker-test` (0 warnings) + `node
  out/node-tests.js` — 7 tests / 15 assertions / 0 failures, exercising the
  actual compiled `fetch-handler` against real Node `Request`/`Response`/
  `URL` globals (no mocking): health, onboard success + validation-error
  (404), `chargeRecurring` camelCase round-trip, `reportPnl` via `GET` with
  a query string, `getActiveLp` 404, and an unmatched path 404.
  `npx shadow-cljs release worker` -> `out/worker.js` (0 warnings, 127 KB),
  then directly invoked the compiled module's exported `{fetch}` from `node`
  against `/xrpc/ai.gftd.apps.nemuri.onboardSubscriber` and `/health` —
  correct camelCase JSON both times.
  `npx wrangler deploy --dry-run` parses `wrangler.jsonc` cleanly and reports
  the `NEMURI_DB` D1 binding, no dispatcher var. **`wrangler deploy` (live)
  was intentionally NOT run** — this is a brand-new build that needs review
  before going live.
- **Lexicon field-shape audit**: every one of the 20 procedure/query NSIDs'
  declared output properties (e.g. `chargeRecurring`'s `stripeId`/
  `envelopeOk`/`approvalRequired`; `planGrowth`'s nested `decisions[].budget[]`
  with `dailyJpy`/`targetCac`; `runRetention`'s `expectedSaveRate`; etc.) were
  checked field-by-field against `nemuri.core`'s actual return maps run
  through `nemuri.xrpc/response->wire` — all match. Some handlers return
  additional fields beyond what the lexicon schema declares (e.g. `dryRun`,
  `envelope`, `decisions` on NSIDs whose schema only declares `dispatched`);
  these are additive and left as-is, not stripped, since the schemas don't
  set `additionalProperties: false`. One pre-existing minor drift noted, not
  fixed here (out of scope — it's `nemuri.core`'s business logic, not the
  Worker's routing): `planInventory`'s lexicon enum for `poStatus` is
  `["issued", "needs_approval"]`, but `nemuri.core/inventory` returns
  `"issued_plan"` for the success case, not `"issued"`.

## Consequences

- `ai-gftd-nemuri` is no longer scaffold-only for its 20 core NSIDs; it can
  be deployed (after review) and will serve dry-run/plan responses with no
  external dependency at all.
- `docs/CLAUDE.md`'s Layout and 未実装 sections were updated to match reality
  (the SvelteKit-shaped layout note is kept, marked superseded, so future
  readers don't rediscover the same dead end).
- `getActiveLp` is the one forward-work item this ADR creates: it needs a
  real D1 read, which is a deliberate scope boundary (the other 20 stay
  provably zero-I/O), not an oversight.

## 2026-08-15 persistence retirement note

The reserved `NEMURI_DB` binding was never used by a live route:
`getActiveLp` still returned 404, and the preceding 30 days contained no
writes and only the retirement audit read. The seven-row database (one
migration row and six source-controlled LP seeds) was exported to
`cloud-itonami-backup/d1-retired/2026-08-15/` in R2 and the binding was
removed. A future `getActiveLp` implementation must use the
kotobase.net/R2 persistence plane rather than recreating this D1.
