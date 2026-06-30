-- nemuri domain schema (Cloudflare D1, ADR-2606071200 / ADR-2606041900 pattern).
-- 1 AT record = 1 row. PII (address/payment) is NOT stored here (T3 Preferences / Stripe).
-- RLS canonical columns (ADR-0095): actor_did / org_did / created_at on every table.

CREATE TABLE IF NOT EXISTS subscriptions (
  sub_id        TEXT PRIMARY KEY,
  did           TEXT NOT NULL,
  plan          TEXT NOT NULL CHECK (plan IN ('basic','pair','premium')),
  status        TEXT NOT NULL DEFAULT 'pending',
  start_ts      TEXT NOT NULL,
  min_term_end  TEXT NOT NULL,
  address_ref   TEXT,                 -- T3 Preferences ref only
  actor_did     TEXT NOT NULL,
  org_did       TEXT NOT NULL DEFAULT 'anon',
  created_at    TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sub_status ON subscriptions(status, min_term_end);
CREATE INDEX IF NOT EXISTS idx_sub_did ON subscriptions(did);

CREATE TABLE IF NOT EXISTS shipments (
  tracking_id      TEXT PRIMARY KEY,
  sub_id           TEXT NOT NULL,
  kind             TEXT NOT NULL CHECK (kind IN ('outbound','takeback','swap')),
  carrier          TEXT,
  recycle_manifest TEXT,
  status           TEXT NOT NULL DEFAULT 'dispatched',
  actor_did        TEXT NOT NULL,
  org_did          TEXT NOT NULL DEFAULT 'anon',
  created_at       TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ship_sub ON shipments(sub_id);

CREATE TABLE IF NOT EXISTS billing_events (
  id              TEXT PRIMARY KEY,
  sub_id          TEXT NOT NULL,
  kind            TEXT NOT NULL,
  amount_jpy      INTEGER NOT NULL,
  stripe_id       TEXT,
  stripe_customer TEXT,
  status          TEXT NOT NULL,
  actor_did       TEXT NOT NULL,
  org_did         TEXT NOT NULL DEFAULT 'anon',
  created_at      TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_bill_sub ON billing_events(sub_id);
CREATE INDEX IF NOT EXISTS idx_bill_status ON billing_events(status);

CREATE TABLE IF NOT EXISTS inquiries (
  id          TEXT PRIMARY KEY,
  did         TEXT,
  channel     TEXT NOT NULL,
  intent      TEXT,
  body        TEXT,
  resolution  TEXT,
  status      TEXT NOT NULL DEFAULT 'open',
  actor_did   TEXT NOT NULL,
  org_did     TEXT NOT NULL DEFAULT 'anon',
  created_at  TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_inq_status ON inquiries(status);

CREATE TABLE IF NOT EXISTS lp_variants (
  variant_id   TEXT PRIMARY KEY,
  section      TEXT NOT NULL CHECK (section IN ('hero','pain','how','pricing','trust','faq')),
  copy         TEXT NOT NULL,
  cvr_permille INTEGER NOT NULL DEFAULT 0,
  impressions  INTEGER NOT NULL DEFAULT 0,
  conversions  INTEGER NOT NULL DEFAULT 0,
  status       TEXT NOT NULL DEFAULT 'testing' CHECK (status IN ('testing','promoted','retired')),
  actor_did    TEXT NOT NULL DEFAULT 'did:web:nemuri.gftd.ai:actor:lpOptimizer',
  org_did      TEXT NOT NULL DEFAULT 'anon',
  created_at   TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_lp_section ON lp_variants(section, status);

CREATE TABLE IF NOT EXISTS board_decisions (
  id           TEXT PRIMARY KEY,
  kind         TEXT NOT NULL,
  kpi_snapshot TEXT,                  -- JSON string
  decisions    TEXT,                  -- JSON string
  okrs         TEXT,                  -- JSON string
  actor_did    TEXT NOT NULL DEFAULT 'did:web:nemuri.gftd.ai:actor:ceoOrchestrator',
  org_did      TEXT NOT NULL DEFAULT 'anon',
  created_at   TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_board_kind ON board_decisions(kind, created_at);

-- Seed: the launch LP copy (each section's first promoted variant).
INSERT OR IGNORE INTO lp_variants (variant_id, section, copy, status, created_at) VALUES
 ('seed-hero',    'hero',    '敷きっぱなし、捨てるの面倒。月1,000円で、新品の布団。いらなくなったら、無料で引き取り。', 'promoted', '2026-06-07T00:00:00Z'),
 ('seed-pain',    'pain',    '布団の処分＝粗大ゴミ予約・¥400〜・運び出し。一人暮らしの「地味に最悪」を、まるごと解決します。', 'promoted', '2026-06-07T00:00:00Z'),
 ('seed-how',     'how',     '届く → 洗って使う（洗える布団）→ 1年で新品に交換 or 解約で無料回収。', 'promoted', '2026-06-07T00:00:00Z'),
 ('seed-pricing', 'pricing', 'ベーシック ¥1,000 / ペア ¥1,800 / プレミアム ¥1,800（年2回交換＋カバー＋防水シーツ）', 'promoted', '2026-06-07T00:00:00Z'),
 ('seed-trust',   'trust',   '抗菌・防臭・防ダニ。新品保証。最低12ヶ月（解約時無料引き取りの原資）。', 'promoted', '2026-06-07T00:00:00Z'),
 ('seed-faq',     'faq',     'Q. 洗えますか？ → はい、丸洗いできる敷布団です。Q. 解約は？ → 12ヶ月以降いつでも、回収無料。', 'promoted', '2026-06-07T00:00:00Z');
