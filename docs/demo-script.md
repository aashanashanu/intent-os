# IntentOS Demo Script

Target runtime: 2 minutes 40 seconds. Use the labeled simulator, actually configured Nova model, PayPal Sandbox buyer/business pair and verified webhook; use Elastic only if available. Never force a candidate or violation for the recording.

| Time | Screen/action | Narration |
| --- | --- | --- |
| 0:00–0:15 | Open the public app; show API/integration readiness. | “IntentOS checks agent purchases against the user’s intent before money moves.” |
| 0:15–0:35 | Create an intent: development laptop, budget, required RAM, no recurring charges or add-ons. | “The intent becomes explicit policy. Nova helps extract facts, but Java owns every decision.” |
| 0:35–0:58 | Enter a supported destination and shipping method; run Nova candidate selection. Show the quote and disclosure. | “Nova can choose only from simulator candidates. The server calculates the quote and holds when evidence is missing.” |
| 0:58–1:18 | Show the actual policy result. If the selected cart naturally violates policy, invoke repair; otherwise show the clean proposal without fabricating a violation. | “Java validates each revision. Repairs stay in the same run and receive a fresh quote.” |
| 1:18–1:32 | Review the exact approved revision. | “This review is bound to the saved revision and quote; a change requires a new review.” |
| 1:32–1:55 | Continue to PayPal Sandbox, approve as test buyer, return to IntentOS. | “The buyer approves the PayPal Sandbox transaction. This is a test payment to our configured Sandbox merchant, not retailer settlement.” |
| 1:55–2:10 | Show capture status and verified-webhook AgentReceipt once it arrives. | “PayPal’s verified capture webhook finalizes one auditable receipt.” |
| 2:10–2:28 | Search by a violation/product/status; show ES|QL metrics including prevented amount. | “Receipts are searchable, while metrics are computed deterministically from the event history.” |
| 2:28–2:40 | Ask “Why was this proposal blocked?” and show the cited receipt ID. | “The Receipt Analyst answers from retrieved evidence, not model memory, and cites the receipt it used.” |

## Recording Checks

- Keep the demo under three minutes; use a prepared Sandbox buyer so login latency does not consume the recording.
- Change simulator data only before the run; do not script a desired candidate or result.
- Wait for the verified webhook before showing the finalized receipt.
- Cover BLOCK/HOLD and repair behavior with automated tests; show runtime violations only if they arise naturally from the model proposal and supplied facts.
- Keep credentials, API keys, account emails, and unrelated browser tabs out of the recording.
