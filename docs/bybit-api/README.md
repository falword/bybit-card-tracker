# Bybit API notes

CardTrack talks only to Bybit mainnet (`https://api.bybit.com`). This folder is the checklist for those calls. It is not a copy of Bybit's documentation.

Live V5 docs: https://bybit-exchange.github.io/docs/v5/guide  
Source repo: https://github.com/bybit-exchange/docs

Upstream publishes that repo without a license, so the full V5 tree is **not** in this git history. Clone it on your machine when you change a request. `official-v5/` is gitignored.

## Start here

| File | Use |
| --- | --- |
| [CONSTRAINTS.md](CONSTRAINTS.md) | What this app sends: windows, page sizes, auth, and each endpoint it calls. |
| [TIME-WINDOWS.md](TIME-WINDOWS.md) | Extract of official `startTime` / `endTime` rules (7 / 14 / 30 days, 24 hours). |

## Endpoints this app calls

| App call | Official page |
| --- | --- |
| `POST /v5/card/transaction/query-asset-records` | https://bybit-exchange.github.io/docs/v5/bybit-card/asset-records |
| `POST /v5/card/reward/points/records` | https://bybit-exchange.github.io/docs/v5/bybit-card/point/records |
| `POST /v5/card/reward/points/tier` | https://bybit-exchange.github.io/docs/v5/bybit-card/point/tier |
| `POST /v5/card/reward/points/balance` | https://bybit-exchange.github.io/docs/v5/bybit-card/point/balance |
| `GET /v5/asset/transfer/query-inter-transfer-list` | https://bybit-exchange.github.io/docs/v5/asset/transfer/inter-transfer-list |
| `GET /v5/asset/fundinghistory` | https://bybit-exchange.github.io/docs/v5/asset/fund-history |
| `GET /v5/asset/transfer/query-account-coins-balance` | https://bybit-exchange.github.io/docs/v5/asset/balance/all-balance |
| `GET /v5/asset/asset-overview` | https://bybit-exchange.github.io/docs/v5/asset/balance/asset-overview |
| `GET /v5/earn/yield` | https://bybit-exchange.github.io/docs/v5/finance/earn/easy-onchain/yield-history |
| `GET /v5/user/query-api` | https://bybit-exchange.github.io/docs/v5/user/apikey-info |

Signing, error codes, and rate limits:

- https://bybit-exchange.github.io/docs/v5/guide
- https://bybit-exchange.github.io/docs/v5/error
- https://bybit-exchange.github.io/docs/v5/rate-limit

## Local snapshot

Do not invent endpoint rules. If `docs/bybit-api/official-v5/` is missing, clone official V5 next to these notes:

```bash
git clone --depth 1 --filter=blob:none --sparse https://github.com/bybit-exchange/docs.git /tmp/bybit-docs-official
git -C /tmp/bybit-docs-official sparse-checkout set docs/v5
rsync -a --delete /tmp/bybit-docs-official/docs/v5/ docs/bybit-api/official-v5/
git -C /tmp/bybit-docs-official rev-parse HEAD > docs/bybit-api/SNAPSHOT.txt
```

`SNAPSHOT.txt` records which upstream commit you checked. The `.mdx` files stay untracked.

Before changing a Bybit call, read `CONSTRAINTS.md` and the official page for that path: method, parameter names, required fields, time window, limit, pagination, and signing. A 12-month range is not valid on every endpoint. Internal transfers reject any window longer than 7 days.
