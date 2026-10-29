# Scenario Verification Results

Date: 2026-10-29. Target: deployed authenticated application. Requests used the browser session and the same extraction, intent, shopping, repair, quote, and review APIs as the UI. No model selections were mocked or forced in these live calls. Provider calls incur normal usage costs.

No PayPal order or capture was created by this verification. Records were created for shopping tests and left intact for inspection. Complete payment, verified-webhook receipt, and post-payment analyst acceptance are not established by these checks.

## Initial Live Results

| Guide case | Observed outcome | Assessment |
| --- | --- | --- |
| 1: 16GB development laptop | APPROVE, Portable Computer 16, $854.40 | Correct |
| 2: laptop only, no extras | APPROVE, Portable Computer 16, $854.40 | Correct selection; does not exercise repair |
| 3: 32GB under $1,000 | BLOCK, Workstation Computer 32, $1,221.60 | Budget rejection correct; extracted `laptops` also caused an unintended category mismatch |
| 4: reject underspecified laptops | APPROVE, Portable Computer 16, $854.40 | Correct selection; bad-spec case remains a controlled test |
| 5: $860, CA Standard | APPROVE, $854.40 | Correct |
| 5: $860, CA Express | BLOCK, $872.40 | Correct |
| 5: $860, NY Standard | BLOCK, $869.82 | Correct |
| 6: one USB keyboard | APPROVE, one unit, $39.00 | Correct; no quantity repair needed |
| 6 merchant variant: six wireless keyboards | HOLD, `QUANTITY_LIMIT` | Correct merchant evidence boundary |
| 7 tax-pending printer | HOLD, `UNKNOWN_TAX_TREATMENT` | Correct |
| 7 purchase-terms-pending software | HOLD, `UNKNOWN_PURCHASE_TERMS` | Correct |
| 7 billing-interval-pending subscription | HOLD, `UNKNOWN_BILLING_INTERVAL` | Correct |
| 7 stock-pending SSD | HOLD, `INSUFFICIENT_STOCK` | Correct |
| 7 sold-out webcam | No candidate selected; model cited stock | Correct safe refusal, not a recorded HOLD cart |
| 7 two limited headphones | HOLD, `INSUFFICIENT_STOCK` | Correct; extractor also wrongly added a quantity specification |
| 7 price-pending projector | No candidate selected; model cited absent price | Correct safe refusal |
| 7 unsupported Texas destination | Controlled live quote request returns HOLD | No UI route for unsupported destination; quote API check separate |
| 8 optional 4K monitor | APPROVE, Studio Monitor 27, $292.80 | Selected product valid; extractor wrongly treated optional preference as required |
| 8 required 4K monitor | APPROVE, Studio Monitor 27, $292.80 | Correct; no MODIFY expected for matching product |
| 9 tablet | BLOCK, Travel Tablet 8, $357.60 | Defect: extracted `electronics` instead of `tablet` |
| 9 wireless keyboard | APPROVE, $93.00 | Correct |
| 9 mesh chair | APPROVE, $206.40 | Correct |
| 9 three notebook packs | BLOCK, correct three-unit cart, $60.60 | Defect: category `notebooks` and invented `quantity` specification |
| 9 party kit | BLOCK, correct kit, $60.60 | Defect: category `party supplies` instead of `party` |
| 10 missing budget | HTTP 502 on repeated requests | Defect: model returned null currency with null budget; parser rejected clarification draft |
| 10 completed follow-up | APPROVE, $854.40 | Correct |
| 10 32GB under $500 | No candidate selected | Correct safe refusal; impossible catalog constraints are not automatically an extraction contradiction |
| 11 named Portable Computer 16 | BLOCK, correct laptop, $854.40 | Defect: extracted category `Portable Computer` |

For all selected non-approved initial cases, repair requests returned a no-safe-repair result without creating a replacement cart. The merchant gaps cannot be invented away, and the false specification/category constraints must be corrected by the buyer rather than silently removed by repair.

## Corrections and Retest

- Extraction instructions distinguish catalog category identifiers from product names, broad categories, quantities, optional preferences, and product-name wording.
- Missing budget plus unspecified currency now returns a USD-denominated clarification draft without inventing a budget. A specified budget with malformed/missing currency still fails closed.
- Category and specification fields are editable/removable in intent review so buyers can correct advisory extraction before saving policy.
- The UI distinguishes no selection from merchant HOLD and policy BLOCK. It no longer labels an absent selection as revision 1, recommends repair generically for missing facts, or borrows a previous run's latest decision.
- The guide labels repeatable repair, bad-specification, quantity-correction, and MODIFY cases as controlled tests instead of guaranteed live model outcomes.

Real Bedrock reruns with the revised extraction prompt returned `tablet`, `laptop`, `stationery`, and `party` for the failing product categories. After explicit notebook/party name examples were added, those two requests returned empty required-specification maps. The optional 4K request returned no hard resolution requirement; the incomplete laptop request returned a missing budget for clarification. These are observed samples, not a guarantee of identical future model output.

Live shopping reruns with explicitly reviewed/corrected boundaries produced:

| Purchase | Result |
| --- | --- |
| Tablet, `tablet`, RAM 8GB | APPROVE, $357.60 |
| Three notebook packs, `stationery`, no invented quantity attribute | APPROVE, three units, $60.60 |
| Party kit, `party`, no invented name-derived attribute | APPROVE, $60.60 |
| Named laptop, `laptop`, RAM 16GB | APPROVE, $854.40 |

A blocked revision's review request returned HTTP 409, as required. The corrected approved laptop revision's review returned HTTP 200. A live Texas quote request returned HTTP 200 with quote status HOLD and `DESTINATION_SHIPPING_RULE_UNAVAILABLE`.

The analyst request "Why was this purchase approved?" returned HTTP 200 in `AGENT_BUILDER_SECURED_MCP` mode, with completed `search_receipts`, `get_metrics`, and `get_decisions` calls and a citation to the actual recorded validation event. It reported zero captured amount and no receipts rather than inventing payment evidence. Its response was verbose and handover-oriented, so presentation quality remains separate from verified tool execution. Paid-receipt analysis was not tested because no new payment was authorized.

## Controlled Regression Coverage

Local browser regression: an intentionally incorrect extraction (`notebooks`, `quantity: 3`) was corrected through the new review controls. The submitted contract contained `stationery` and no invented quantity specification. A subsequent empty selection showed no cart revision, no repair action, and NOT EVALUATED rather than an older run's APPROVE. These UI responses were isolated fixtures; they are not counted as live provider results.

The guide's full named regression suite passed: seed-catalog scenario cases, merchant API authorization/CSRF, quote expiry and fingerprint changes, deterministic repair, checkout review/policy checks, and webhook verification/idempotency. Model parser tests additionally cover incomplete currency/budget clarification and the revised extraction instruction contract.

These tests deliberately supply candidate/proposal combinations to prove BLOCK, safe extras removal, quantity repair, non-repairable core violations, MODIFY, and HOLD. They do not prove the live Shopping Agent will choose a bad cart. No genuine user constraint was disabled to force payment approval.

## Deployment Boundary

The initial matrix and corrected-contract shopping calls ran against the deployed backend. Revised extraction instructions were tested directly against Bedrock, and parser/UI changes were verified locally. This report does not claim that those source changes are already deployed. Re-run the affected buyer requests after deployment; model output remains variable and must be reviewed before confirmation. Payment capture and paid-receipt acceptance still require explicit buyer approval.