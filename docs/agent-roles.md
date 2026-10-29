# Agent Roles and Merchant Simulation

This document separates implemented model tasks from the revised target architecture. A model task is not necessarily an independently deployed autonomous agent.

| Role | Model / executor | Purpose | Current status |
| --- | --- | --- | --- |
| Intent Agent | Nova Micro via Bedrock | Extract explicit goals, budget, constraints, and clarification questions for user review | Implemented extraction task; cannot approve payment |
| Intent reasoning reviewer | Nova Lite via Bedrock | Review an extracted draft for ambiguities and contradictions | Implemented advisory task, not an autonomous shopping agent |
| Shopping Agent | Nova Lite plus Spring Boot orchestration and simulator tools | Select supplied candidate IDs; calculate a quote and validate it in Java | Initial one-search/one-selection pass implemented; model query iteration, progress, spend/time budgets and live provider run remain unverified |
| Repair role | Java deterministic repair over simulator-backed revisions; Nova Lite advice remains advisory | Revise the same cart lineage, re-quote, and count attempts across requests | Integrated same-run repair, fresh quote and three-attempt persisted budget implemented; alternative search for replacement candidates remains open |
| Decision explainer | Nova Micro via Bedrock | Explain persisted intent/decision evidence without inventing payment outcomes | Implemented explanation task |
| Receipt Analyst | Elastic Agent Builder or Bedrock narration over backend-retrieved evidence | Answer captured and decision-only questions with receipt/event/run/revision citations | Owner-authorized event/receipt retrieval and citations implemented; attached tools/invocation inside Agent Builder remain unimplemented; Bedrock narration fallback is distinct |

## Components That Are Not AI Agents

- **Merchant simulator:** Configurable test inventory, product terms, availability, and deterministic cart quotes. It does not select products, force violations, or approve payments.
- **Java policy engine:** Authoritative APPROVE/MODIFY/HOLD/BLOCK decisions and total calculation. Models cannot override it.
- **PayPal payment orchestration:** OAuth, buyer approval, server capture, and webhook verification. Actual PayPal Sandbox evidence, never simulator-generated financial events.
- **Elasticsearch:** Evidence persistence, lexical/semantic search when configured, and ordered event retrieval. Current metrics use `_search` with `search_after`, not ES|QL; Java computes financial metrics.

## Target Shopping Loop

1. Load the saved Intent Contract and an authorized shopping run.
2. Nova Lite requests bounded merchant search/detail tools.
3. The backend returns simulator products with explicit facts and stable candidate IDs.
4. Nova proposes candidate IDs and quantities; Java hydrates facts and obtains a simulator quote.
5. Java evaluates policy. Missing evidence means HOLD; violations may trigger up to three persisted repair attempts.
6. The user reviews the exact final approved revision and quote.
7. PayPal buyer approval and backend capture proceed only while authorization and quote validity hold.
8. A verified PayPal webhook finalizes the lineage-bound receipt for later analysis.

Nova is the reasoning model within the Shopping Agent, not the merchant, price authority, or payment approver. The same Nova Lite client may support shopping, reasoning, and repair with separate prompts/task configuration. No additional agent framework is required solely to implement this loop.

## Simulation Disclosure

Runtime merchant data is intentionally simulated and configurable. Label it on UI, stored evidence, receipts, and walkthroughs: "Simulated merchant catalog and quotes. Real PayPal Sandbox payment lifecycle. No physical order or retailer settlement." Changing the dataset must change the available choices; neither success nor a particular bad cart may be scripted. Channel3 and PayPal Catalog discovery code have been removed from the active product path; historical records retain original source labels.