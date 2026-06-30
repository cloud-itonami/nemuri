# nemuri CLJ task runtime

This replaces the old `lg_nemuri` Python LangGraph scaffold with a small CLJ
server/registry.

The runtime preserves the graph names and `ai.gftd.apps.nemuri.*` XRPC mapping.
It does not call D1, Stripe, carrier APIs, ad APIs, OPA, LLMs, or mailers.
Money, legal, purchase-order, and contract operations return explicit
`approval_required` / `dry_run` plan results.

Run tests:

```sh
clojure -M:test
```
