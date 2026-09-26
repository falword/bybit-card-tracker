# Bybit V5 time-window constraints (extracted)

Extract of official `startTime` / `endTime` rules, taken from a local `official-v5/` clone (not committed; see `README.md`). Source of truth:
https://bybit-exchange.github.io/docs/v5/guide

If both `startTime` and `endTime` are sent, **many** private history endpoints reject `endTime - startTime > 7 days`.
Error: `181010` / `retMsg` like `Invalid StartTime or EndTime, time range should not be larger than 7 days`.

## abandon/contract-transaction-log.mdx

- L19: |startTime |false |integer |The start timestamp (ms) <ul><li>startTime and endTime are not passed, return 7 days by default</li><li>Only startTime is passed, return range between startTime and startTime+7 days</li><li>Only endTime is passed

## account/borrow-history.mdx

- L15: |startTime |false |integer |The start timestamp (ms) <ul><li>startTime and endTime are not passed, return 30 days by default</li><li>Only startTime is passed, return range between startTime and startTime + 30 days </li><li>Only endTime is p

## account/transaction-log.mdx

- L24: |startTime |false |integer |The start timestamp (ms) <ul><li>startTime and endTime are not passed, return 24 hours by default</li><li>Only startTime is passed, return range between startTime and startTime+24 hours</li><li>Only endTime is pa

## acct-mode.mdx

- L57: <td><a href="order/order-list#">Get Order History</a></td><td>1. <code>orderStatus</code> is not passed, and all final orders are queried by default<br/>2. Parameters <code>baseCoin</code> and <code>settleCoin</code> are supported<br/>3. Ac

## affiliate/affiliate-info.mdx

- L47: |paySendAmount30Day |string |Payment amount in the last 30 days|

## affiliate/affiliate-sub-list.mdx

- L23: |startDate |false |string |Start date of the query period, format `YYYY-MM-DD`. The range between "startDate" and "endTime" cannot exceed 3 months|

## affiliate/affiliate-user-list.mdx

- L24: |need30 |false |boolean |`true`: return 30 days trading info; `false`(default): does not return 30 days trading info|
- L52: |> tradfiTradeVol30Day |string |tradfi trade volume in last 30 days (USDT). When `startDate` and `endDate` are in the input parameters, return 0|

## asset/delivery.mdx

- L22: |startTime |false |integer |The start timestamp (ms) <ul><li>startTime and endTime are not passed, return 30 days by default</li><li>Only startTime is passed, return range between startTime and startTime + 30 days </li><li>Only endTime is p

## asset/deposit/deposit-record.mdx

- L10: * `endTime` - `startTime` should be less than 30 days. Query last 30 days records by default.

## asset/deposit/internal-deposit-record.mdx

- L9: * The maximum difference between the start time and the end time is 30 days
- L20: |startTime |false |integer |Start time (ms). Default value: 30 days before the current time |

## asset/deposit/sub-deposit-record.mdx

- L10: `endTime` - `startTime` should be less than 30 days. Queries for the last 30 days worth of records by default.

## asset/fund-history.mdx

- L14: |createTimeFrom |false |string |Start timestamp (seconds). Must be used together with `createTimeTo`. The interval between `createTimeFrom` and `createTimeTo` cannot exceed 7 days. If neither is provided, defaults to the last 7 days |
- L15: |createTimeTo |false |string |End timestamp (seconds). Must be used together with `createTimeFrom`. The interval between `createTimeFrom` and `createTimeTo` cannot exceed 7 days. If neither is provided, defaults to the last 7 days |

## asset/settlement.mdx

- L21: |startTime |false |integer |The start timestamp (ms) <ul><li>startTime and endTime are not passed, return 30 days by default</li><li>Only startTime is passed, return range between startTime and startTime + 30 days </li><li>Only endTime is p

## asset/transfer/inter-transfer-list.mdx

- L10: * If startTime and endTime are not provided, the API returns data from the past 7 days by default.
- L11: * If only startTime is provided, the API returns records from startTime to startTime + 7 days.
- L12: * If only endTime is provided, the API returns records from endTime - 7 days to endTime.
- L13: * If both are provided, the maximum allowed range is 7 days (endTime - startTime ≤ 7 days).

## asset/transfer/unitransfer-list.mdx

- L16: * If startTime and endTime are not provided, the API returns data from the past 7 days by default.
- L17: * If only startTime is provided, the API returns records from startTime to startTime + 7 days.
- L18: * If only endTime is provided, the API returns records from endTime - 7 days to endTime.
- L19: * If both are provided, the maximum allowed range is 7 days (endTime - startTime ≤ 7 days).

## asset/withdraw/withdraw-record.mdx

- L10: * `endTime` - `startTime` should be less than 30 days. Query last 30 days records by default.

## broker/exchange-broker/exchange-earning.mdx

- L9: * `begin` & `end` are either entered at the same time or not entered, and latest 7 days data are returned by default

## broker/exchange-broker/sub-deposit-record.mdx

- L11: * `endTime` - `startTime` should be less than 30 days. Queries for the last 30 days worth of records by default.

## error.mdx

- L55: | 30135 |The leverage you select for USDT Perpetual trading cannot exceed the maximum leverage allowed by Institutional Lending. |
- L56: | 30136 |The leverage you select for USDC Perpetual or Futures trading cannot exceed the maximum leverage allowed by Institutional Lending. |
- L297: | 170216 |The leverage you select for Spot Trading cannot exceed the maximum leverage allowed by Institutional Lending |
- L471: | 181010 |The time range between startTime and endTime cannot exceed 7 days |

## event/trade/execution.mdx

- L11: * Default query window: **7 days** if `startTime`/`endTime` are not specified.
- L23: |startTime |false |integer |Start time in milliseconds. Default: 7 days ago|

## event/trade/order-list.mdx

- L12: * Default query window: **7 days** if `startTime`/`endTime` are not specified.
- L24: |startTime |false |integer |Start time in milliseconds. Default: 7 days ago|

## event/trade/settlement.mdx

- L11: * Default query window: **7 days** if `startTime`/`endTime` are not specified.
- L22: |startTime |false |integer |Start time in milliseconds. Default: 7 days ago|

## finance/earn/byusdt/history-apr.mdx

- L18: |range|**true**|integer|Time range: `1` = 7 days, `2` = 30 days, `3` = 180 days|

## finance/earn/easy-onchain/apr-history.mdx

- L22: |startTime|false|integer|Start timestamp (ms). If neither `startTime` nor `endTime` is provided, the last 7 days of data is returned by default|

## finance/earn/easy-onchain/hourly-yield.mdx

- L18: | startTime   | false    | integer  | The start timestamp (ms).<ul><li> 1. If both are not provided, the default is to return data from the last 7 days.</li><li>2. If both are provided, the difference between the endTime and startTime must 

## finance/earn/easy-onchain/order-history.mdx

- L20: | startTime   | false    | integer  | The start timestamp (ms).<ul><li> 1. If both are not provided, the default is to return data from the last 7 days.</li><li>2. If both are provided, the difference between the endTime and startTime must 

## finance/earn/easy-onchain/yield-history.mdx

- L20: | startTime   | false    | integer  | The start timestamp (ms).<ul><li> 1. If both are not provided, the default is to return data from the last 7 days.</li><li>2. If both are provided, the difference between the endTime and startTime must 

## finance/pwm/asset-manager/all-order.mdx

- L22: - Neither `startTime` nor `endTime` passed: returns data from the last 7 days
- L23: - Both passed: returns data from `max(endTime - 7 days, startTime)` to `endTime`
- L24: - Only `startTime` passed: returns data from `startTime` to `startTime + 7 days`
- L25: - Only `endTime` passed: returns data from `endTime - 7 days` to `endTime`

## finance/pwm/customize-plan/create.mdx

- L8: The total number of **Active** and **Pending** plans for the current user cannot exceed **20**.

## finance/pwm/investment-plan/asset-trend.mdx

- L14: |startTime|false|int|Start timestamp (ms). Default: current time minus 7 days|

## finance/pwm/investment-plan/fund-nav.mdx

- L18: |startTime|false|int|Start timestamp (ms). Default: current time minus 7 days|

## finance/rwa/nav-chart.mdx

- L9: * `startTime` defaults to 7 days before `endTime`. `endTime` defaults to current time.
- L21: |startTime|false|integer|Start timestamp (Unix seconds). Default: `endTime - 7 days`|

## finance/rwa/order.mdx

- L9: * For paginated listing: `startTime` defaults to 7 days ago, `endTime` defaults to now. The earliest accessible time is 180 days ago.
- L23: |startTime|false|integer|Start timestamp (Unix seconds). Default: 7 days ago; earliest: 180 days ago|

## market/iv.mdx

- L14: * This endpoint can query the last 2 years worth of data, but make sure [`endTime` - `startTime`] <= 30 days.

## new-crypto-loan/loan-coin.mdx

- L33: |> annualizedInterestRate7D|string |The lowest annualized interest rate for fixed borrowing for 7 days that the market can currently provide. If there is no lending in the current market, then it is empty string|
- L34: |> annualizedInterestRate14D|string |The lowest annualized interest rate for fixed borrowing for 14 days that the market can currently provide. If there is no lending in the current market, then it is empty string|
- L35: |> annualizedInterestRate30D|string |The lowest annualized interest rate for fixed borrowing for 30 days that the market can currently provide. If there is no lending in the current market, then it is empty string|

## order/execution.mdx

- L28: |startTime |false |integer |The start timestamp (ms) <ul><li>startTime and endTime are not passed, return 7 days by default</li><li>Only startTime is passed, return range between startTime and startTime+7 days</li><li>Only endTime is passed

## order/order-list.mdx

- L30: |startTime |false |integer |The start timestamp (ms)<ul><li>startTime and endTime are not passed, return 7 days by default</li><li>Only startTime is passed, return range between startTime and startTime+7 days</li><li>Only endTime is passed,

## position/close-pnl.mdx

- L16: |startTime |false |integer |The start timestamp (ms) <ul><li>startTime and endTime are not passed, return 7 days by default</li><li>Only startTime is passed, return range between startTime and startTime+7 days</li><li>Only endTime is passed

## position/close-position.mdx

- L21: |startTime |false |integer |The start timestamp (ms) <ul><li>startTime and endTime are not passed, return 1 days by default</li><li>Only startTime is passed, return range between startTime and startTime+1 days</li><li>Only endTime is passed

## position/move-position-history.mdx

- L16: |startTime |false |number |The order creation start timestamp. The interval is 7 days|
- L17: |endTime |false |number |The order creation end timestamp. The interval is 7 days|

## pre-upgrade/close-pnl.mdx

- L23: |startTime |false |integer |The start timestamp (ms) <ul><li>startTime and endTime are not passed, return 7 days by default</li><li>Only startTime is passed, return range between startTime and startTime+7 days</li><li>Only endTime is passed

## pre-upgrade/execution.mdx

- L35: |startTime |false |integer |The start timestamp (ms) <ul><li>startTime and endTime are not passed, return 7 days by default</li><li>Only startTime is passed, return range between startTime and startTime+7 days</li><li>Only endTime is passed

## pre-upgrade/order-list.mdx

- L37: |startTime |false |integer |The start timestamp (ms) <ul><li>`startTime` and `endTime` must be passed together or both are not passed</li><li>endTime - startTime <= 7 days</li><li>If both are not passed, it returns recent 7 days by default<

## pre-upgrade/transaction-log.mdx

- L27: |startTime |false |integer |The start timestamp (ms) <ul><li>startTime and endTime are not passed, return 7 days by default</li><li>Only startTime is passed, return range between startTime and startTime+7 days</li><li>Only endTime is passed

## rfq/trade/public-trades.mdx

- L16: |startTime |false |integer|The timestamp (ms), `startTime` and `endTime` of the order transaction are 30 days|
- L17: |endTime |false |integer|The closing timestamp (ms), `startTime` and `endTime` of the order are 30 days|

## spot-margin-uta/historical-interest.mdx

- L22: |startTime|false|integer|The start timestamp (ms) <ul><li>Either both time parameters are passed or neither is passed.</li><li>Returns 7 days data when both are not passed</li><li>Supports up to 30 days interval when both are passed</li></u

## spread/trade/order-history.mdx

- L23: |startTime |false |long |The start timestamp (ms)<ul><li>startTime and endTime are not passed, return 7 days by default</li><li>Only startTime is passed, return range between startTime and startTime+7 days</li><li>Only endTime is passed, re

## spread/trade/trade-history.mdx

- L21: |startTime |false |long |The start timestamp (ms)<ul><li>startTime and endTime are not passed, return 7 days by default</li><li>Only startTime is passed, return range between startTime and startTime+7 days</li><li>Only endTime is passed, re

## strategy/create-strategy.mdx

- L21: |Running time (`duration`) |5 minutes – 24 hours |
