# Bybit constraints for this app

Source: Bybit V5, checked 2026-09-06 against commit `9aeb1b1` of [bybit-exchange/docs](https://github.com/bybit-exchange/docs) (`docs/v5`). Live site: https://bybit-exchange.github.io/docs/v5/guide

The full `.mdx` tree is not in git. Clone it locally (`README.md` in this folder); paths like `official-v5/error.mdx` then resolve on disk. If this file and an official page disagree, **the official page wins**.

The error `Invalid StartTime or EndTime, time range should not be larger than 7 days` is official error **181010**: *The time range between startTime and endTime cannot exceed 7 days* (`official-v5/error.mdx`). Related: **10001** request parameter error.

## The failure that caused this archive

`CardSync` builds one window from `MonthMath.syncBeginMillis` (first day of the month **12 months ago**) through `now`, then sends that same pair as:

- card `createBeginTime` / `createEndTime`
- points `startTime` / `endTime`
- transfers `startTime` / `endTime`

That is ~365 days. **Internal transfers reject any range larger than 7 days.**

```
GET /v5/asset/transfer/query-inter-transfer-list
```

Official rules (`official-v5/asset/transfer/inter-transfer-list.mdx`):

- neither time → last **7 days**
- only `startTime` → `[startTime, startTime + 7 days]`
- only `endTime` → `[endTime - 7 days, endTime]`
- both → **`endTime - startTime ≤ 7 days`**
- times are milliseconds, but matching is **second** precision
- `limit` is `[1, 50]`, default `20` (not 100)
- pagination is `cursor` / `nextPageCursor`, not `page`

To load 12 months of top-ups: walk consecutive windows of **at most 7 days**, then walk `cursor` inside each window. Do **not** send 12 months in one request. Do **not** drop times and hope the default returns a year (it returns 7 days). After a successful sync, the transfer fallback uses `lastSyncAt − 24h` so it does not re-walk a year.

Same 7-day cap: `query-universal-transfer-list`, funding history, order history, execution list, and many other private history endpoints. See `TIME-WINDOWS.md`.

## Endpoints we call

### 1. Card transactions

```
POST /v5/card/transaction/query-asset-records
```

Official: `official-v5/bybit-card/asset-records.mdx`

| Param | Official |
| --- | --- |
| `type` | Required unless `txnId` or `orderNo` is set. `SIDE_QUERY_AUTH` / `SIDE_QUERY_FINANCIAL` / `SIDE_QUERY_REFUND` |
| `limit` | Default 100, range `[1, 500]`. This app uses **500**. Official HTTP/Python/Node examples put `limit` and `page` on the **query string** (`?limit=&page=`). POST HMAC is still the JSON body (`official-v5/guide.mdx`). This app sends `page`/`limit` on the query **and** in JSON. |
| `page` | Default 1, min 1 |
| `createBeginTime` / `createEndTime` | Unix **ms**. Official page does **not** document a 7-day cap. Do not send 12 months or a 31-day month as one window — a wide `retCode=0` can under-return. This app walks **calendar months** (`current → previous → older`), clamps `createEndTime` to **now** (no future days), and slices any span **> 7 days** (`official-v5/error.mdx` 181010). After a checkpoint, current and previous months are still refreshed. |

`rate-limit.mdx` has **no** row for this path. AUTH, FINANCIAL, and REFUND share it; a month’s 7-day slices used to start together and return `10006`. HTTP calls to all `/v5/card/*` POSTs are spaced **≥ 400 ms** (`BybitThrottle.Card`), same pause as other undocumented card routes. Inflight cap 5 is not a per-path limit.

Pagination uses `result.totalCount` and `result.pageSize` (actual rows served). Do **not** compute `fetched` as `(page-1) * requestedLimit` — live pages are often 100 while we request 500. A 7-day slice that returns `10006` / Partial must not abort the remaining slices of that month. Coverage written before month-sized card windows is generation `0`; the next sync re-walks 12 months and then stores generation `2`.

`type` is required for our list queries. Omitting it without `txnId`/`orderNo` is illegal.

### 2. Card points (cashback)

```
POST /v5/card/reward/points/records
```

Official: `official-v5/bybit-card/point/records.mdx`

| Param | Official |
| --- | --- |
| `pageSize` | Default **10**, min 1. Official page does not publish a max. This app uses **50**. |
| `pageNo` | Default 1 |
| `startTime` / `endTime` | Unix timestamp. Official page does **not** document a 7-day cap. Parameter names match the 7-day family. This app asks **this calendar month** first (header cashback), then the previous month, then older months; `endTime` is never after now. Spans **> 7 days** are sliced up front (`181010`). After a checkpoint, current and previous months are still refreshed (`MonthMath.priorityBegin`). AUTH, FINANCIAL, REFUND, points, and funding for a month are scheduled together; card POSTs still go through `BybitThrottle.Card` (≥ 400 ms). Months stay current → previous → older. |
| `side` | `1` earn, `2` deduct. This app sends `side=1` and also filters `side=1` when classifying. |
| `type` | Optional filter, **no official enum**. The docs response sample says `CASHBACK`, but live mainnet returns numeric `1` (earn) and `5` (deduct). **Do not send `type=CASHBACK`** — that filter returns `totalCount=0`. Omit `type`. |

### 3. Internal transfers (top-ups)

```
GET /v5/asset/transfer/query-inter-transfer-list
```

**Hard cap: 7 days.** Details above. Rate limit: **60 req/min** (`official-v5/rate-limit.mdx`, Asset table).

Signing for GET is `timestamp + apiKey + recvWindow + queryString` (not JSON). Official: `official-v5/guide.mdx`.

### 4. API key info

GET /v5/user/query-api

Official: official-v5/user/apikey-info.mdx
No request params. Any permission can call it. GET HMAC of timestamp+key+recvWindow+empty query.

### 5. Card reward tier

```
POST /v5/card/reward/points/tier
```

Official: `official-v5/bybit-card/point/tier.mdx`

No request params. Body is `{}`. No time window (`startTime` / `endTime` do not exist on this path). POST HMAC of timestamp+key+recvWindow+JSON body (`official-v5/guide.mdx`). Official `rate-limit/rate-limit.mdx` does not publish a per-path limit for this route. Uses `BybitThrottle.Card` (≥ 400 ms).

### 6. Card reward point balance

```
POST /v5/card/reward/points/balance
```

Official: `official-v5/bybit-card/point/balance.mdx`

No request params. Body is `{}`. No time window. POST HMAC of timestamp+key+recvWindow+JSON body. Do not call `cashback/detail`. Official `rate-limit/rate-limit.mdx` does not publish a per-path limit for this route. Uses `BybitThrottle.Card` (≥ 400 ms).

### 7. Funding coins balance

```
GET /v5/asset/transfer/query-account-coins-balance
```

Official: `official-v5/asset/balance/all-balance.mdx`

| Param | Official |
| --- | --- |
| `accountType` | **Required.** This app sends `FUND`. |
| `coin` | Optional, uppercase, comma-separated. This app sends `USDT,USDC`. Query all coins if omitted. Mandatory only for `UNIFIED` (max 10 coins). |
| `memberId` | Optional; required only when a master key reads a sub-account. This app does **not** send it. |
| `withBonus` | Optional `0`/`1`. This app does **not** send it. |

No `startTime` / `endTime`. Rate limit: **5 req/s** (`official-v5/rate-limit/rate-limit.mdx`, Asset table). GET HMAC of `timestamp + apiKey + recvWindow + queryString` (`official-v5/guide.mdx`).

This path is **not** the Home «Баланс счёта» source. Summing FUND `USDT`+`USDC` `walletBalance` misses other spendable Funding coins. Home uses §8.

### 8. Asset overview (card / Funding available)

```
GET /v5/asset/asset-overview
```

Official: `official-v5/asset/balance/asset-overview.mdx`

| Param | Official |
| --- | --- |
| `memberId` | Optional; required only when a master key reads a sub-account. This app does **not** send it. |
| `valuationCurrency` | Optional. Defaults to `USD`. This app omits it. |
| `accountType` | Optional `assetAccountType`. Returns all accounts if omitted. This app omits it and reads `list[]` where `accountType=FundingAccount`. |

No `startTime` / `endTime`. Rate limit: **50 req/s** (`official-v5/rate-limit/rate-limit.mdx`, Asset table). GET HMAC of `timestamp + apiKey + recvWindow + empty query` (`official-v5/guide.mdx`). Official page: does not support regional accounts (KZ, GE, EU, etc.).

Home «Баланс счёта» is **card available**: `FundingAccount.totalEquity` + Earn category `Easy Earn` `equity` (Bybit Card spends Funding + Flexible Easy Earn). Do **not** use leftover `USDT`/`USDC` coin amounts, top-level `result.totalEquity` (Unified / bots), or non-Easy Earn categories.

### 9. Easy Earn yield

```
GET /v5/earn/yield
```

Official: `official-v5/finance/earn/easy-onchain/yield-history.mdx`

| Param | Official |
| --- | --- |
| `category` | **Required.** This app sends `FlexibleSaving` |
| `startTime` / `endTime` | Unix **ms**. Neither → last 7 days. Both → ≤ 7 days. This app always sends both, slices ≤ 7 days, clamps start to `now − 90 days` (official: past 3 months) |
| `limit` | `[1, 100]`, default 50. This app uses **100** |
| `cursor` | `nextPageCursor` |

Needs Earn on the key. GET HMAC of query string. `rate-limit.mdx` has no per-path row; this app pauses **400 ms** between windows.

## Auth (all private endpoints)

Headers: `X-BAPI-API-KEY`, `X-BAPI-TIMESTAMP` (ms), `X-BAPI-SIGN`, `X-BAPI-RECV-WINDOW` (default 5000).

Clock rule: `server_time - recv_window <= timestamp < server_time + 1000`.  
`10002` = phone clock vs Bybit. Use NTP / automatic date-time.

HMAC: lowercase hex of `HMAC_SHA256(timestamp + apiKey + recvWindow + bodyOrQuery)`.

Card read-only key needs **Bybit Card**. Wallet top-ups need **Wallet → Account Transfer**. `10005` = permission denied.

Mainnet: `https://api.bybit.com`. US / CN IP → HTTP 403.

## Common `retCode` we already map

From `official-v5/error.mdx`:

| Code | Official meaning |
| --- | --- |
| 0 | OK |
| 10001 | Request parameter error (includes illegal time range on some routes) |
| 10002 | Timestamp outside recv window |
| 10003 | Invalid API key |
| 10004 | Bad signature |
| 10005 | Permission denied |
| 10006 | Too many visits (rate limit) |
| 10009 | Region restricted |
| 10016 | Server error |
| 181008 | Both startTime and endTime are required (some routes) |
| 181009 | startTime must be < endTime |
| **181010** | **Range cannot exceed 7 days** |
| 33004 | API key expired |

HTTP 403 can be IP rate limit (600 req / 5 s / IP) or region. Official: wait ≥ 10 minutes after IP ban.

## Do not

- Send `startTime`/`endTime` spanning more than **7 days** on transfer (or any endpoint whose official page says ≤ 7 days).
- Reuse card `limit=100` on transfers (`max 50`) or assume points `pageSize` default is 100 (it is 10).
- Treat “omit times” as “get 12 months”. Transfer default is **7 days**.
- Invent query params, `type` values, or time units. Card times are ms; transfer times are ms with second-level matching; funding history uses **seconds**.
- Call live Bybit from unit tests.

## How to fetch more than 7 days

```
window = 7 days
from = syncBegin
while from < now:
    to = min(from + 7d, now)
    cursor = null
    repeat:
        GET ... startTime=from&endTime=to&limit=50&cursor=cursor
        pause (rate limit 60 req/min; this app spaces transfers ≥ 1000 ms)
        cursor = nextPageCursor
    until cursor empty
    from = to
```

Apply the same pattern to any other V5 history route whose official page says `endTime - startTime <= 7 days`.
