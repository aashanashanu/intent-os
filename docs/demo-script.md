# IntentOS Demo Script

Target runtime: 2 minutes 40 seconds. Use a configured PayPal Sandbox buyer/business pair, Channel3 API key, PayPal webhook, and Elastic deployment. Do not expose keys or claim the Channel3 retailer receives the Sandbox payment.

| Time | Screen/action | Narration |
| --- | --- | --- |
| 0:00–0:15 | Open the public app; show API/integration readiness. | “IntentOS checks agent purchases against the user’s intent before money moves.” |
| 0:15–0:35 | Create an intent: development laptop, budget, required RAM, no recurring charges or add-ons. | “The intent becomes explicit policy. Nova helps extract facts, but Java owns every decision.” |
| 0:35–0:58 | Search Channel3 offers; compare retailer, price, in-stock state, offer link, and retrieval time. Select one. | “Channel3 supplies live retailer offers. We preserve their source and recheck the chosen offer before checkout.” |
| 0:58–1:18 | Show a deliberately invalid proposal containing an unrequested warranty and subscription; run validation/repair and show violations and final result. | “The initial cart is blocked. The repair removes disallowed extras, and the server revalidates it.” |
| 1:18–1:32 | Enter confirmed tax and shipping, validate the selected offer; show approval or a budget block. | “Unknown costs are never treated as zero. Only a complete, approved amount can proceed.” |
| 1:32–1:55 | Continue to PayPal Sandbox, approve as test buyer, return to IntentOS. | “The buyer approves the PayPal Sandbox transaction. This is a test payment to our configured Sandbox merchant, not retailer settlement.” |
| 1:55–2:10 | Show capture status and verified-webhook AgentReceipt once it arrives. | “PayPal’s verified capture webhook finalizes one auditable receipt.” |
| 2:10–2:28 | Search by a violation/product/status; show ES|QL metrics including prevented amount. | “Receipts are searchable, while metrics are computed deterministically from the event history.” |
| 2:28–2:40 | Ask “Why was this proposal blocked?” and show the cited receipt ID. | “The Receipt Analyst answers from retrieved evidence, not model memory, and cites the receipt it used.” |

## Recording Checks

- Keep the demo under three minutes; use a prepared Sandbox buyer so login latency does not consume the recording.
- Ensure the selected Channel3 offer is still in stock and price-matched at checkout.
- Wait for the verified webhook before showing the finalized receipt.
- Show at least one blocked proposal with budget/add-on/recurring violations and its repaired, revalidated result.
- Keep credentials, API keys, account emails, and unrelated browser tabs out of the recording.
