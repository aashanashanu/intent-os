# Simulated Merchant Foundation

Status: simulator, bounded Nova proposal, same-run repair, revision review, simulator-backed PayPal order path, capture-time revalidation, and the responsive UI journey are implemented and covered by focused tests. Quote volume durability/reservations and external live PayPal/Bedrock verification remain open. Channel3 and PayPal Catalog discovery surfaces have been removed.

## Disclosure

Simulated merchant catalog and quotes. Real PayPal Sandbox payment lifecycle. No physical order or retailer settlement.

The merchant supplies configurable test data, not agent decisions. The seed inventory and example destination rates are simulation data, not actual merchant prices or legally accurate tax rates. No product is automatically selected and no violation/repair outcome is scripted.

## Configuration

The default catalog is [../backend/src/main/resources/merchant/catalog.json](../backend/src/main/resources/merchant/catalog.json). Product/variant IDs, price, stock, explicit purchase type, taxable status, attributes, quantity limits, destination rules, and shipping methods are data. Unknown purchase type, stock, price, tax treatment, or matching destination/rules cannot become an eligible quote.

| Variable | Default | Purpose |
| --- | --- | --- |
| `INTENTOS_MERCHANT_CATALOG` | `classpath:merchant/catalog.json` | Spring resource location; use an operator-managed `file:/absolute/path/catalog.json` for editable inventory |
| `INTENTOS_MERCHANT_QUOTE_DIRECTORY` | `.intentos/merchant-quotes` | Local-mode server-owned immutable quote snapshots; relative to backend working directory |
| `INTENTOS_MERCHANT_QUOTE_TTL_SECONDS` | `300` | Validity, constrained to 1-3600 seconds |

The catalog is read on each operation. A SHA-256 fingerprint binds every quote to its complete catalog/rules bytes: any change requires a new quote, including edits that do not change the manual version string. Quotes are created with unique server-generated IDs and create-only file writes. Treat the quote directory and catalog file as private operator-managed files; no public catalog administration endpoint is provided.

With Elasticsearch enabled, quotes are create-only documents in `merchant-quote-v1`, alongside the persisted shopping lineage. With Elasticsearch disabled, local quotes survive process restarts only when the directory is retained and do not survive container replacement without a durable mount. Retention/backup, inventory reservation/consumption, and concurrency-safe stock management remain release work. Do not infer payment eligibility from `READY`: it means merchant evidence is complete, not Java intent approval or final user review.

## Intent-Scoped APIs

Every route is under `/api/intents/{intentId}/merchant`, using the existing intent-access service and session/CSRF interceptor. When authentication is enabled, only the intent owner can use these operations. POST calls from a session require `X-CSRF-Token`.

| Method | Suffix | Behavior |
| --- | --- | --- |
| GET | `/products?q=portable` | Case-insensitive literal search of names/category/attributes; at most 50 products; response includes simulation disclosure, catalog fingerprint, destination rules, and retrieval time |
| GET | `/products/{productId}/variants/{variantId}` | Product facts, including explicit ONE_TIME or SUBSCRIPTION terms; no inferred values |
| POST | `/quotes` | Calculate and persist READY or HOLD quote for up to 50 distinct product/variant lines |
| GET | `/quotes/{quoteId}` | Read immutable original snapshot belonging to this intent; reading does not verify current validity |
| POST | `/quotes/{quoteId}/verify` | Require READY, unexpired quote and unchanged catalog fingerprint; otherwise conflict |

Example request using the disclosed simulator catalog:

```json
{
  "lines": [
    {"productId": "portable-computer", "variantId": "16gb-512gb", "quantity": 1}
  ],
  "country": "US",
  "region": "CA",
  "shippingMethod": "standard"
}
```

The user may supply a destination/shipping preference, but the request has no client-supplied price, tax, shipping amount, or purchase-type override. Unsupported regions/methods yield HOLD instead of free shipping. Subscription products require an explicit billing interval. Quote tax uses the configured rule on taxable line totals and shipping when configured taxable, rounding once to cents with HALF_UP. Unknown costs leave total/tax/shipping null on HOLD; `missingEvidence` contains machine-readable reasons.

Changed items, quantities, variants, or destination require a new quote request and ID. An old snapshot remains evidence of its original request only. The future approved cart/review binding must enforce these input identities before any payment operation.

## Verification

```sh
./backend/gradlew -p backend test --tests 'com.intentos.merchant.*'
```

Coverage includes calculated totals, unknown terms, destination/rate absence, insufficient stock, persistence across service instances, quote expiry, catalog changes, ownership, and CSRF enforcement. These tests make no PayPal payments or model calls.