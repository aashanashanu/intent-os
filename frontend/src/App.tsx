import { useEffect, useState, type FormEvent } from 'react'
import {
  Activity,
  ArrowRight,
  ArrowUpRight,
  Check,
  CircleDollarSign,
  FileCheck2,
  Fingerprint,
  LayoutDashboard,
  LoaderCircle,
  LockKeyhole,
  Plus,
  ReceiptText,
  RefreshCw,
  Search,
  ShieldCheck,
  ShoppingBag,
  Sparkles,
  WalletCards,
  X,
} from 'lucide-react'
import './App.css'

type IntentContract = {
  intentId: string
  naturalLanguageRequest: string
  goal: string
  maxAmount: number
  currency: string
  allowRecurringPayments: boolean
  allowSubscriptions: boolean
  allowUnrequestedAddons: boolean
  maximumQuantities: Record<string, number>
  createdAt: string
}

type TransactionProposal = {
    proposalId: string
    intentId: string
    currency: string
    total: number
    shipping: number
    tax: number
    items: Array<{
      name: string
      category: string
      quantity: number
      unitPrice: number
      recurring: boolean
      subscription: boolean
      addOn: boolean
      merchant: string | null
      attributes: Record<string, string>
    }>
}

type AuthSession = {
  authenticated: boolean
  loginConfigured: boolean
  userId?: string
  name?: string
  email?: string
  csrfToken?: string
}

type IntegrationStatus = {
  simulatedMerchantConfigured: boolean
  elasticAgentBuilderConfigured: boolean
  paypalClientId: string
  paypalWebhookConfigured: boolean
  elasticPersistenceEnabled: boolean
  authenticationRequired: boolean
}

type PayPalOrder = { orderId: string; status: string; approvalUrl: string }
type PayPalCapture = { orderId: string; status: string; captureId: string; amount: number; currency: string }
type PendingPayPalOrder = PayPalOrder & { intentId: string; runId: string; revisionId: string; createdAt: string }
type AgentReceipt = {
  receiptId: string
  intentId: string
  intent?: IntentContract
  amount: number
  preventedAmount?: number
  currency: string
  paypalOrderId: string
  paypalCaptureId: string
  orderStatus: string
  captureStatus: string
  finalDecision: 'APPROVE' | 'MODIFY' | 'HOLD' | 'BLOCK'
  webhookEventId: string
  createdAt: string
  proposalHistory: TransactionProposal[]
  violations: Array<{ type: string; item: string | null; expected: string; actual: string | null }>
  repairs: DecisionEvent[]
  activityTimeline: DecisionEvent[]
  runId?: string | null
  revisionId?: string | null
}

type ReceiptMetrics = {
  totalProposals: number
  approvedProposals: number
  violatingProposals: number
  repairedProposals: number
  proposalsRequiringReview: number
  preventedAmount: number
  capturedAmount: number
  intentComplianceRate: number
  violationRate: number
  repairRate: number
  humanInterventionRate: number
  firstPassCompliantProposals?: number
  firstPassComplianceRate?: number
  blockedExposureProposals?: number
  blockedExposureAmount?: number
}

type ShoppingRevision = {
  revisionId: string
  proposalId: string
  runId: string
  revisionNumber: number
  parentRevisionId: string | null
  quoteId: string
  items: Array<{ itemId: string; productId: string; variantId: string; quantity: number }>
  validationDecision: string | null
  violationTypes: string[]
  repairAttempts: number
}

type ShoppingProposalResult = {
  run: { runId: string; createdAt: string }
  revision: ShoppingRevision | null
  quote: { quoteId: string; status: string; currency: string; subtotal: number; shipping: number | null; tax: number | null; total: number | null; missingEvidence: string[]; disclosure: string; lines: Array<{ product: { name: string; category: string; price: number; attributes: Record<string, string>; purchaseType: 'ONE_TIME' | 'SUBSCRIPTION' | null }; quantity: number }> } | null
  proposal: TransactionProposal | null
  validation: PolicyCheck | null
  outcome: string
}

type ShoppingHistoryPage = {
  items: Array<{ intentId: string; goal: string; run: { runId: string; createdAt: string }; revisions: ShoppingRevision[]; status: string; productIds: string[] }>
  page: number
  pageSize: number
  total: number
  incompleteData: boolean
}

type ShoppingRepairResult = Omit<ShoppingProposalResult, 'run'> & {
  repair: { explanation: string; status: string }
}

type ReceiptAnalystResult = { answer: string; receiptIds: string[]; evidenceCount: number; eventIds: string[]; runIds: string[]; revisionIds: string[]; retrievalMode: string; narrationMode: string; toolCalls?: string[] }

type IntentExtraction = {
  goal: string | null
  maxAmount: number | null
  currency: string
  allowedCategories: string[]
  requiredAttributes: Record<string, string>
  allowRecurringPayments: boolean
  allowSubscriptions: boolean
  allowUnrequestedAddons: boolean
  needsClarification: boolean
  clarificationQuestions: string[]
}

type PolicyCheck = {
  decision: 'APPROVE' | 'MODIFY' | 'HOLD' | 'BLOCK'
  calculatedTotal: number | null
  violations: Array<{ type: string; item: string | null; expected: string; actual: string | null }>
  recommendations: string[]
}

type DecisionEvent = {
  eventId: string
  proposalId: string
  revisionId?: string | null
  occurredAt: string
  eventType: string
  decision: 'APPROVE' | 'MODIFY' | 'HOLD' | 'BLOCK'
  calculatedTotal: number | null
  violations: Array<{ type: string; item: string | null; expected: string; actual: string | null }>
  message: string
  proposalSnapshot?: TransactionProposal | null
}

function formatMoney(amount: number | null | undefined, currency = 'USD') {
  if (amount === null || amount === undefined) return 'Not recorded'
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount)
}

function formatEventTitle(eventType: string) {
  return eventType.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function explainViolation(violation: PolicyCheck['violations'][number]) {
  const item = violation.item || 'This cart'
  const explanations: Record<string, string> = {
    BUDGET_EXCEEDED: `${item} exceeds your spending limit. Payment is blocked until the total fits your budget.`,
    RECURRING_PAYMENT: `${item} would create a recurring charge, but your intent does not allow recurring payments.`,
    RECURRING_PAYMENT_NOT_ALLOWED: `${item} would create a recurring charge you did not authorize.`,
    SUBSCRIPTION_NOT_ALLOWED: `${item} is a subscription. You asked for a purchase without subscriptions.`,
    UNREQUESTED_ADDON: `${item} is an extra you did not ask for. IntentOS will not let it pass into checkout.`,
    CATEGORY_NOT_ALLOWED: `${item} is outside the product categories you approved.`,
    REQUIRED_ATTRIBUTE_MISSING: `${item} does not have evidence of a required specification.`,
    REQUIRED_ATTRIBUTE_MISMATCH: `${item} does not meet your required specifications.`,
    REQUIRED_ATTRIBUTE_NOT_MET: `${item} does not meet a specification you requested. Required: ${violation.expected}; proposed: ${violation.actual ?? 'not provided'}.`,
    QUANTITY_EXCEEDED: `${item} includes more units than you allowed.`,
    MISSING_SHIPPING: 'Shipping costs are missing. IntentOS will not approve an unknown final price.',
    MISSING_TAX: 'Tax has not been confirmed. Payment is paused until the full cost is known.',
    MERCHANT_RESTRICTED: `${item} comes from a merchant your intent does not allow.`,
    PROHIBITED_ITEM: `${item} is on your excluded-items list, so it cannot proceed to payment.`,
    INTENT_EXPIRED: 'Your purchase authorization has expired. Confirm a fresh request before continuing.',
  }
  return explanations[violation.type] ?? `${item}: ${formatEventTitle(violation.type).toLowerCase()}. Your purchase remains paused until this is resolved.`
}

function explainEvent(event: DecisionEvent) {
  if (event.violations.length) return event.violations.map(explainViolation).join(' ')
  if (event.eventType === 'PAYMENT.CAPTURE.COMPLETED') return 'PayPal confirmed the capture through a verified webhook. Your payment evidence is now recorded.'
  if (event.eventType === 'PAYPAL_CAPTURE_RESULT') return 'PayPal returned a capture response. The final receipt still needs a verified payment webhook.'
  if (event.eventType.startsWith('REPAIR_')) return event.decision === 'APPROVE'
    ? 'The repaired cart passed the intent checks. You can review it before approving any payment.'
    : 'The repair was checked against your original intent. The cart still needs attention before payment.'
  if (event.decision === 'APPROVE' && event.eventType.includes('VALIDATION')) return 'This proposed cart meets your recorded purchase boundaries. No payment has been authorized by this check.'
  return event.message
}

function App() {
  const loginError = new URLSearchParams(window.location.search).get('paypalLoginError')
  const loginParams = new URLSearchParams(window.location.search)
  const loginDiagnostic = loginParams.get('paypalLoginStage')
    ? ` (${loginParams.get('paypalLoginStage')} step, HTTP ${loginParams.get('paypalLoginStatus')}, ${loginParams.get('paypalLoginDetail')})`
    : ''
  const todayLabel = new Intl.DateTimeFormat('en-US', {
    weekday: 'long', month: 'long', day: 'numeric', year: 'numeric',
  }).format(new Date()).toUpperCase()
  const [health, setHealth] = useState<'checking' | 'online' | 'offline'>('checking')
  const [auth, setAuth] = useState<AuthSession>({ authenticated: false, loginConfigured: false })
  const [integrations, setIntegrations] = useState<IntegrationStatus | null>(null)
  const [request, setRequest] = useState('')
  const [goal, setGoal] = useState('')
  const [maxAmount, setMaxAmount] = useState('')
  const [draftCategories, setDraftCategories] = useState<string[]>([])
  const [draftRequiredAttributes, setDraftRequiredAttributes] = useState<Record<string, string>>({})
  const [extracting, setExtracting] = useState(false)
  const [draftReady, setDraftReady] = useState(false)
  const [agentOperation, setAgentOperation] = useState<'idle' | 'intent' | 'shopping' | 'repair' | 'review' | 'payment'>('idle')
  const [extractionNeedsClarification, setExtractionNeedsClarification] = useState(false)
  const [limitedItemName, setLimitedItemName] = useState('')
  const [itemQuantityLimit, setItemQuantityLimit] = useState('')
  const [allowRecurringPayments, setAllowRecurringPayments] = useState(false)
  const [allowSubscriptions, setAllowSubscriptions] = useState(false)
  const [allowUnrequestedAddons, setAllowUnrequestedAddons] = useState(false)
  const [activeIntent, setActiveIntent] = useState<IntentContract | null>(null)
  const [shoppingRun, setShoppingRun] = useState<ShoppingProposalResult['run'] | null>(null)
  const [shoppingProposal, setShoppingProposal] = useState<ShoppingProposalResult | null>(null)
  const [shoppingReviewId, setShoppingReviewId] = useState('')
  const [shoppingBusy, setShoppingBusy] = useState(false)
  const [repairComparison, setRepairComparison] = useState<{ before: TransactionProposal; after: TransactionProposal } | null>(null)
  const [shoppingCountry, setShoppingCountry] = useState('US')
  const [shoppingRegion, setShoppingRegion] = useState('CA')
  const [shoppingShipping, setShoppingShipping] = useState('standard')
  const [shoppingHistory, setShoppingHistory] = useState<ShoppingHistoryPage | null>(null)
  const [historyQuery, setHistoryQuery] = useState('')
  const [historyStatus, setHistoryStatus] = useState('')
  const [historyFrom, setHistoryFrom] = useState('')
  const [historyTo, setHistoryTo] = useState('')
  const [saving, setSaving] = useState(false)
  const [activityEvents, setActivityEvents] = useState<DecisionEvent[]>([])
  const [checkoutBusy, setCheckoutBusy] = useState(false)
  const [pendingPayPalOrder, setPendingPayPalOrder] = useState<PendingPayPalOrder | null>(() => {
    try {
      const stored = window.sessionStorage.getItem('intentos.pendingPayPalOrder')
      return stored ? JSON.parse(stored) as PendingPayPalOrder : null
    } catch {
      return null
    }
  })
  const [paymentCapture, setPaymentCapture] = useState<PayPalCapture | null>(null)
  const [agentReceipts, setAgentReceipts] = useState<AgentReceipt[]>([])
  const [verifiedReceipts, setVerifiedReceipts] = useState<AgentReceipt[]>([])
  const [receiptEvidenceError, setReceiptEvidenceError] = useState('')
  const [receiptDetail, setReceiptDetail] = useState<AgentReceipt | null>(null)
  const [receiptMetrics, setReceiptMetrics] = useState<ReceiptMetrics | null>(null)
  const [receiptQuery, setReceiptQuery] = useState('')
  const [receiptStatus, setReceiptStatus] = useState('')
  const [receiptsLoading, setReceiptsLoading] = useState(false)
  const [analystQuestion, setAnalystQuestion] = useState('')
  const [analystResult, setAnalystResult] = useState<ReceiptAnalystResult | null>(null)
  const [analystLoading, setAnalystLoading] = useState(false)
  const [modelExplanation, setModelExplanation] = useState('')
  const [explanationLoading, setExplanationLoading] = useState(false)
  const [notice, setNotice] = useState(() => loginError === 'access_denied'
    ? 'PayPal sign-in was cancelled or not approved. Check the Sandbox buyer account and try again.'
    : loginError
      ? `PayPal sign-in could not be completed. Check the Sandbox app configuration and try again.${loginDiagnostic}`
      : '')
  const [noticeKind, setNoticeKind] = useState<'success' | 'error'>(() => loginError ? 'error' : 'success')

  useEffect(() => {
    const controller = new AbortController()

    if (loginError) {
      window.history.replaceState({}, '', `${window.location.pathname}${window.location.hash}`)
    }

    fetch('/api/health', { signal: controller.signal })
      .then((response) => {
        if (!response.ok) throw new Error('Health check failed')
        return response.json()
      })
      .then(() => setHealth('online'))
      .catch(() => {
        if (!controller.signal.aborted) setHealth('offline')
      })

    return () => controller.abort()
  }, [loginError])

  useEffect(() => {
    const controller = new AbortController()
    fetch('/api/integrations/status', { signal: controller.signal })
      .then((response) => {
        if (!response.ok) throw new Error('Integration status unavailable')
        return response.json()
      })
      .then((status: IntegrationStatus) => setIntegrations(status))
      .catch(() => {
        if (!controller.signal.aborted) setIntegrations(null)
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!auth.authenticated) return
    const controller = new AbortController()
    fetch('/api/shopping-history?page=0&pageSize=50', { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error('Shopping history could not be loaded.')
        setShoppingHistory(await response.json() as ShoppingHistoryPage)
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setNotice(error instanceof Error ? error.message : 'Shopping history could not be loaded.')
          setNoticeKind('error')
        }
      })
    return () => controller.abort()
  }, [auth.authenticated])

  useEffect(() => {
    const controller = new AbortController()
    fetch('/api/auth/session', { signal: controller.signal })
      .then((response) => response.json())
      .then((session: AuthSession) => setAuth(session))
      .catch(() => {
        if (!controller.signal.aborted) setAuth({ authenticated: false, loginConfigured: false })
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!activeIntent) return
    const intentId = activeIntent.intentId
    const controller = new AbortController()
    async function refreshEvidence() {
      try {
        const [eventsResponse, receiptsResponse, metricsResponse] = await Promise.all([
          fetch(`/api/intents/${intentId}/events`, { signal: controller.signal }),
          fetch(`/api/intents/${intentId}/receipts`, { signal: controller.signal }),
          fetch(`/api/intents/${intentId}/receipts/metrics`, { signal: controller.signal }),
        ])
        if (eventsResponse.ok) setActivityEvents(await eventsResponse.json() as DecisionEvent[])
        if (receiptsResponse.status === 401) {
          setReceiptEvidenceError('Your sign-in session has expired. Sign in with PayPal again, then reopen this purchase from Shopping history to view its receipt.')
        } else if (!receiptsResponse.ok) {
          setReceiptEvidenceError('The receipt could not be checked. Retrying automatically; this does not mean your payment failed. Do not pay again.')
        } else {
          const receipts = await receiptsResponse.json() as AgentReceipt[]
          setVerifiedReceipts(receipts)
          setReceiptEvidenceError('')
          if (!receiptQuery.trim() && !receiptStatus) setAgentReceipts(receipts)
        }
        if (metricsResponse.ok) setReceiptMetrics(await metricsResponse.json() as ReceiptMetrics)
      } catch {
        if (!controller.signal.aborted) setReceiptEvidenceError('Connection interrupted while checking your receipt. Retrying automatically. Do not pay again.')
      }
    }
    void refreshEvidence()
    const interval = window.setInterval(() => void refreshEvidence(), 3000)
    return () => { controller.abort(); window.clearInterval(interval) }
  }, [activeIntent, receiptQuery, receiptStatus])

  async function saveIntent(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setAgentOperation('intent')
    setNotice('')
    try {
      const response = await fetch('/api/intents', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {}) },
        body: JSON.stringify({
          userId: auth.authenticated ? auth.userId : 'demo-user',
          naturalLanguageRequest: request.trim(),
          goal: goal.trim(),
          maxAmount: Number(maxAmount),
          currency: 'USD',
          allowRecurringPayments,
          allowSubscriptions,
          allowUnrequestedAddons,
          maximumQuantities: limitedItemName.trim() && itemQuantityLimit
            ? { [limitedItemName.trim()]: Number(itemQuantityLimit) }
            : {},
          allowedCategories: draftCategories,
          requiredAttributes: draftRequiredAttributes,
          preferredAttributes: {},
          prohibitedItems: [],
          merchantRestrictions: [],
        }),
      })
      if (!response.ok) throw new Error('The API rejected this intent. Check the required fields and try again.')
      const createdIntent = await response.json() as IntentContract
      setActiveIntent(createdIntent)
      setVerifiedReceipts([])
      setReceiptEvidenceError('')
      setReceiptQuery('')
      setReceiptStatus('')
      setReceiptDetail(null)
      setModelExplanation('')
      const runResponse = await fetch(`/api/intents/${createdIntent.intentId}/shopping-runs`, {
        method: 'POST',
        headers: auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {},
      })
      if (!runResponse.ok) throw new Error('Intent saved, but a shopping run could not be started.')
      const createdRun = await runResponse.json() as ShoppingProposalResult['run']
      setShoppingRun(createdRun)
      setShoppingProposal(null)
      setRepairComparison(null)
      setShoppingReviewId('')
      void loadReceiptMetrics(createdIntent.intentId)
      setActivityEvents([])
      setAgentReceipts([])
      setReceiptMetrics(null)
      setAnalystResult(null)
      setPaymentCapture(null)
      setNotice('Your purchase boundaries are saved. The Shopping Agent is finding a cart for you.')
      setNoticeKind('success')
      setDraftReady(false)
      await runShoppingProposal(createdIntent, createdRun)
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Could not save the intent.')
      setNoticeKind('error')
    } finally {
      setSaving(false)
      setAgentOperation('idle')
    }
  }

  async function extractIntent() {
    if (!request.trim()) return
    setExtracting(true)
    setAgentOperation('intent')
    setDraftReady(false)
    setLimitedItemName('')
    setItemQuantityLimit('')
    setNotice('')
    try {
      const response = await fetch('/api/intents/extract', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {}) },
        body: JSON.stringify({ naturalLanguageRequest: request.trim() }),
      })
      if (!response.ok) throw new Error(response.status === 401 ? 'Sign in with PayPal to draft an intent.' : 'Could not extract the intent draft.')
      const draft = await response.json() as IntentExtraction
      if (draft.goal) setGoal(draft.goal)
      if (draft.maxAmount !== null) setMaxAmount(String(draft.maxAmount))
      setDraftCategories(draft.allowedCategories)
      setDraftRequiredAttributes(draft.requiredAttributes)
      setAllowRecurringPayments(draft.allowRecurringPayments)
      setAllowSubscriptions(draft.allowSubscriptions)
      setAllowUnrequestedAddons(draft.allowUnrequestedAddons)
      setExtractionNeedsClarification(draft.needsClarification)
      setDraftReady(true)
      setNotice(draft.needsClarification
        ? `Please clarify: ${draft.clarificationQuestions.join(' ')}`
        : 'Here is what I understood. Confirm your boundaries before I start looking for products.')
      setNoticeKind(draft.needsClarification ? 'error' : 'success')
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Could not extract the intent draft.')
      setNoticeKind('error')
    } finally {
      setExtracting(false)
      setAgentOperation('idle')
    }
  }

  function loadExample() {
    setDraftReady(false)
    setRequest('Find me a laptop for software development under $1,000. No subscriptions.')
    setGoal('Development laptop')
    setMaxAmount('1000')
    setLimitedItemName('Development laptop')
    setItemQuantityLimit('1')
    setAllowRecurringPayments(false)
    setAllowSubscriptions(false)
    setAllowUnrequestedAddons(false)
  }

  async function refreshIntentActivity(intentId: string) {
    const response = await fetch(`/api/intents/${intentId}/events`)
    if (response.ok) setActivityEvents(await response.json() as DecisionEvent[])
    void loadReceiptMetrics(intentId)
  }

  async function loadShoppingHistory() {
    const parameters = new URLSearchParams({ page: '0', pageSize: '50', q: historyQuery, status: historyStatus })
    if (historyFrom) parameters.set('from', new Date(historyFrom).toISOString())
    if (historyTo) parameters.set('to', new Date(historyTo).toISOString())
    const response = await fetch(`/api/shopping-history?${parameters}`)
    if (!response.ok) throw new Error(response.status === 401 ? 'Sign in to view shopping history.' : 'Shopping history could not be loaded.')
    setShoppingHistory(await response.json() as ShoppingHistoryPage)
  }

  async function openShoppingRun(entry: ShoppingHistoryPage['items'][number]) {
    try {
      const intentResponse = await fetch(`/api/intents/${entry.intentId}`)
      if (!intentResponse.ok) throw new Error('This intent is no longer accessible.')
      const intent = await intentResponse.json() as IntentContract
      setActiveIntent(intent)
      setVerifiedReceipts([])
      setReceiptEvidenceError('')
      setActivityEvents([])
      setAgentReceipts([])
      setReceiptDetail(null)
      setReceiptQuery('')
      setReceiptStatus('')
      const restoredRun = { ...entry.run, intentId: entry.intentId }
      setShoppingRun(restoredRun)
      setPaymentCapture(null)
      setRepairComparison(null)
      setModelExplanation('')
      const latestRevision = entry.revisions.at(-1) ?? null
        let quote: ShoppingProposalResult['quote'] = null
        let proposal: TransactionProposal | null = null
        if (latestRevision) {
          const quoteResponse = await fetch(`/api/intents/${entry.intentId}/merchant/quotes/${latestRevision.quoteId}`)
          if (quoteResponse.ok) {
            quote = await quoteResponse.json() as NonNullable<ShoppingProposalResult['quote']>
            proposal = {
              proposalId: latestRevision.proposalId,
              intentId: entry.intentId,
              currency: quote.currency,
              total: quote.total ?? 0,
              shipping: quote.shipping ?? 0,
              tax: quote.tax ?? 0,
              items: quote.lines.map((line) => ({
                name: line.product.name,
                category: line.product.category,
                quantity: line.quantity,
                unitPrice: line.product.price ?? 0,
                recurring: line.product.purchaseType === 'SUBSCRIPTION',
                subscription: line.product.purchaseType === 'SUBSCRIPTION',
                addOn: false,
                merchant: 'simulated merchant',
                attributes: line.product.attributes,
              })),
            }
          }
        }
        setShoppingProposal({ run: restoredRun, revision: latestRevision, quote, proposal, validation: null,
          outcome: 'Historical run restored. The stored quote is shown as immutable evidence and has not been revalidated.' })
      setShoppingReviewId('')
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Could not open this shopping run.')
      setNoticeKind('error')
    }
  }

  async function startShoppingRun() {
    if (!activeIntent) return
    setShoppingBusy(true)
    try {
      const response = await fetch(`/api/intents/${activeIntent.intentId}/shopping-runs`, {
        method: 'POST', headers: auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {},
      })
      if (!response.ok) throw new Error('A new shopping run could not be created.')
      setShoppingRun(await response.json() as ShoppingProposalResult['run'])
      setShoppingProposal(null)
      setRepairComparison(null)
      setShoppingReviewId('')
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Could not create a shopping run.')
      setNoticeKind('error')
    } finally {
      setShoppingBusy(false)
    }
  }

  async function proposeShopping() {
    if (!activeIntent || !shoppingRun) return
    await runShoppingProposal(activeIntent, shoppingRun)
  }

  async function runShoppingProposal(intent: IntentContract, run: ShoppingProposalResult['run']) {
    setShoppingBusy(true)
    setAgentOperation('shopping')
    setShoppingReviewId('')
    setRepairComparison(null)
    try {
      const response = await fetch(`/api/intents/${intent.intentId}/shopping-runs/${run.runId}/propose`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {}) },
        body: JSON.stringify({ country: shoppingCountry.trim(), region: shoppingRegion.trim(), shippingMethod: shoppingShipping.trim() }),
      })
      if (!response.ok) throw new Error(response.status === 503 ? 'Nova shopping is unavailable; no proposal or payment was created.' : 'Shopping proposal failed closed.')
      const result = await response.json() as ShoppingProposalResult
      setShoppingProposal(result)
      setNotice(result.validation?.decision === 'APPROVE'
        ? 'Your cart is ready. IntentOS checked it against your boundaries; review the items before any payment.'
        : 'IntentOS paused this cart before payment. Review the findings below and request a safe repair.')
      setNoticeKind('success')
      await refreshIntentActivity(intent.intentId)
      void loadShoppingHistory().catch(() => undefined)
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Shopping proposal failed.')
      setNoticeKind('error')
    } finally {
      setShoppingBusy(false)
      setAgentOperation('idle')
    }
  }

  async function repairShopping() {
    if (!activeIntent || !shoppingRun || !shoppingProposal?.revision) return
    setShoppingBusy(true)
    setAgentOperation('repair')
    setShoppingReviewId('')
    try {
      const response = await fetch(`/api/intents/${activeIntent.intentId}/shopping-runs/${shoppingRun.runId}/revisions/${shoppingProposal.revision.revisionId}/repair`, {
        method: 'POST', headers: auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {},
      })
      if (!response.ok) throw new Error(response.status === 409 ? 'No further safe repair is available. Review the cart manually.' : 'Repair could not be completed.')
      const repaired = await response.json() as ShoppingRepairResult
      if (!repaired.revision || !repaired.quote || !repaired.proposal || !repaired.validation) {
        setRepairComparison(null)
        setNotice(`${repaired.outcome} ${repaired.repair.explanation}`)
        setNoticeKind('error')
        await refreshIntentActivity(activeIntent.intentId)
        return
      }
      if (shoppingProposal.proposal) {
        setRepairComparison({ before: shoppingProposal.proposal, after: repaired.proposal })
      }
      setShoppingProposal({ ...repaired, run: shoppingRun })
      setNotice(repaired.validation?.decision === 'APPROVE'
        ? 'The repaired cart passed your intent checks. Review the changes before continuing.'
        : 'The repair was checked, but the cart still needs attention. Payment remains paused.')
      setNoticeKind('success')
      await refreshIntentActivity(activeIntent.intentId)
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Repair failed.')
      setNoticeKind('error')
    } finally {
      setShoppingBusy(false)
      setAgentOperation('idle')
    }
  }

  async function reviewShoppingRevision() {
    if (!activeIntent || !shoppingRun || !shoppingProposal?.revision) return
    setShoppingBusy(true)
    setAgentOperation('review')
    try {
      const response = await fetch(`/api/intents/${activeIntent.intentId}/shopping-runs/${shoppingRun.runId}/revisions/${shoppingProposal.revision.revisionId}/review`, {
        method: 'POST', headers: auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {},
      })
      if (!response.ok) throw new Error('This revision is no longer eligible for review. Refresh the quote or inspect the policy findings.')
      const review = await response.json() as { reviewId: string }
      setShoppingReviewId(review.reviewId)
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Final review failed.')
      setNoticeKind('error')
    } finally {
      setShoppingBusy(false)
      setAgentOperation('idle')
    }
  }

  async function checkoutShoppingRevision() {
    if (!activeIntent || !shoppingRun || !shoppingProposal?.revision || !shoppingReviewId || !integrations?.paypalClientId) return
    setCheckoutBusy(true)
    setAgentOperation('payment')
    const headers = { 'Content-Type': 'application/json', ...(auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {}) }
    try {
      const orderResponse = await fetch(`/api/intents/${activeIntent.intentId}/shopping-runs/${shoppingRun.runId}/revisions/${shoppingProposal.revision.revisionId}/orders`, { method: 'POST', headers })
      if (!orderResponse.ok) {
        const detail = await orderResponse.text()
        throw new Error(`PayPal order creation failed (${orderResponse.status}). ${detail.slice(0, 240) || 'The reviewed quote or current intent policy changed.'}`)
      }
      const order = await orderResponse.json() as PayPalOrder
      const pending: PendingPayPalOrder = {
        ...order,
        intentId: activeIntent.intentId,
        runId: shoppingRun.runId,
        revisionId: shoppingProposal.revision.revisionId,
        createdAt: new Date().toISOString(),
      }
      window.sessionStorage.setItem('intentos.pendingPayPalOrder', JSON.stringify(pending))
      setPendingPayPalOrder(pending)
      setNotice('PayPal order created. Open the approval link and complete buyer approval. Return here and choose Check approval and capture.')
      setNoticeKind('success')
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Could not start PayPal checkout.')
      setNoticeKind('error')
    } finally {
      setCheckoutBusy(false)
      setAgentOperation('idle')
    }
  }

  async function captureBuyerApprovedOrder() {
    if (!pendingPayPalOrder) return
    setCheckoutBusy(true)
    setAgentOperation('payment')
    const headers = { 'Content-Type': 'application/json', ...(auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {}) }
    try {
      const response = await fetch(`/api/intents/${pendingPayPalOrder.intentId}/orders/${pendingPayPalOrder.orderId}/capture`, {
        method: 'POST', headers,
      })
      if (!response.ok) {
        const detail = await response.text()
        if (response.status === 409) {
          throw new Error('PayPal has not confirmed buyer approval yet, or the quote/policy expired. Complete approval or create and review a fresh quote.')
        }
        throw new Error(`Server-side capture failed (${response.status}). ${detail.slice(0, 240)}`)
      }
      setPaymentCapture(await response.json() as PayPalCapture)
      if (activeIntent?.intentId !== pendingPayPalOrder.intentId) {
        const intentResponse = await fetch(`/api/intents/${pendingPayPalOrder.intentId}`)
        if (!intentResponse.ok) {
          throw new Error('PayPal capture returned, but the purchase could not be reopened. Do not pay again. Sign in and reopen it from Shopping history to view the receipt.')
        }
        setActiveIntent(await intentResponse.json() as IntentContract)
        setShoppingProposal(null)
        setShoppingReviewId('')
        setVerifiedReceipts([])
        setReceiptEvidenceError('')
      }
      setShoppingRun({ runId: pendingPayPalOrder.runId, createdAt: pendingPayPalOrder.createdAt })
      setReceiptQuery('')
      setReceiptStatus('')
      window.sessionStorage.removeItem('intentos.pendingPayPalOrder')
      setPendingPayPalOrder(null)
      await refreshIntentActivity(pendingPayPalOrder.intentId)
      await loadReceiptMetrics(pendingPayPalOrder.intentId)
      await loadShoppingHistory()
      setNotice('PayPal capture returned. The verified webhook will finalize the receipt.')
      setNoticeKind('success')
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Could not verify PayPal buyer approval.')
      setNoticeKind('error')
    } finally {
      setCheckoutBusy(false)
      setAgentOperation('idle')
    }
  }

  async function loadReceiptMetrics(intentId: string) {
    const response = await fetch(`/api/intents/${intentId}/receipts/metrics`)
    if (response.ok) setReceiptMetrics(await response.json() as ReceiptMetrics)
  }

  async function searchReceipts() {
    if (!activeIntent) return
    setReceiptsLoading(true)
    try {
      const parameters = new URLSearchParams({ q: receiptQuery, status: receiptStatus, limit: '50' })
      const response = await fetch(`/api/intents/${activeIntent.intentId}/receipts/search?${parameters}`)
      if (!response.ok) throw new Error('Receipt search failed.')
      setAgentReceipts(await response.json() as AgentReceipt[])
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Receipt search failed.')
      setNoticeKind('error')
    } finally {
      setReceiptsLoading(false)
    }
  }

  async function openReceiptDetail(receipt: AgentReceipt) {
    try {
      const response = await fetch(`/api/intents/${receipt.intentId}/receipts/${receipt.receiptId}`)
      if (!response.ok) throw new Error('Receipt detail is not available for this intent.')
      setReceiptDetail(await response.json() as AgentReceipt)
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Could not load receipt detail.')
      setNoticeKind('error')
    }
  }

  async function askReceiptAnalyst(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!activeIntent || !analystQuestion.trim()) return
    setAnalystLoading(true)
    setAnalystResult(null)
    try {
      const response = await fetch(`/api/intents/${activeIntent.intentId}/receipts/analyst`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {}) },
        body: JSON.stringify({ question: analystQuestion.trim(), status: receiptStatus || null }),
      })
      if (!response.ok) {
        if (response.status === 404) throw new Error('No matching receipt evidence was found.')
        if (response.status === 503) throw new Error('Receipt Analyst is unavailable. Check Bedrock access.')
        throw new Error('Receipt Analyst request failed.')
      }
      setAnalystResult(await response.json() as ReceiptAnalystResult)
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Receipt Analyst request failed.')
      setNoticeKind('error')
    } finally {
      setAnalystLoading(false)
    }
  }

  async function explainDecision() {
    if (!activeIntent || activityEvents.length === 0) return
    setExplanationLoading(true)
    setModelExplanation('')
    try {
      const response = await fetch(`/api/intents/${activeIntent.intentId}/explanation`)
      if (!response.ok) throw new Error(response.status === 503
        ? 'Decision explanation is unavailable. Check Bedrock access.'
        : 'Could not explain the recorded decision.')
      const result = await response.json() as { explanation: string }
      setModelExplanation(result.explanation)
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Could not explain the recorded decision.')
      setNoticeKind('error')
    } finally {
      setExplanationLoading(false)
    }
  }

  const latestEvent = activityEvents.at(-1) ?? null
  const latestRunReceipt = verifiedReceipts.find((receipt) => receipt.intentId === activeIntent?.intentId
    && (!shoppingRun || !receipt.runId || receipt.runId === shoppingRun.runId)) ?? null
  const latestDecision = shoppingProposal?.validation?.decision
    ?? shoppingProposal?.revision?.validationDecision
    ?? latestEvent?.decision
    ?? null
  const latestViolations = shoppingProposal?.validation?.violations
    ?? latestEvent?.violations
    ?? []
  const latestRunItems = shoppingProposal?.quote?.lines.map((line) => `${line.quantity} × ${line.product.name}`)
    ?? latestEvent?.proposalSnapshot?.items.map((item) => `${item.quantity} × ${item.name}`)
    ?? []
  const latestDecisionReason = latestViolations.length > 0
    ? latestViolations.map(explainViolation).join(' ')
    : latestDecision === 'APPROVE'
      ? `The proposed total ${formatMoney(shoppingProposal?.quote?.total ?? latestEvent?.calculatedTotal)} fits your ${formatMoney(activeIntent?.maxAmount)} limit and passed the recorded intent checks. You still control payment.`
      : latestDecision === 'HOLD' ? 'The cart needs more evidence or your review. IntentOS has paused checkout rather than guessing.'
        : shoppingProposal?.outcome ?? 'No cart has been evaluated. Your agent will check the proposed purchase before it can reach PayPal.'
  const busy = extracting || saving || shoppingBusy || checkoutBusy
  const journeySteps = [
    { title: 'Your request', detail: 'Tell your Shopping Agent what to buy', done: !!request.trim() || !!activeIntent, running: false, icon: ShoppingBag },
    { title: 'Understand intent', detail: extracting ? 'Reading your goal and boundaries' : draftReady && !saving ? 'Waiting for your confirmation' : activeIntent ? 'Purchase boundaries recorded' : 'Intent Agent', done: !!activeIntent, running: extracting || (draftReady && !saving) || (saving && agentOperation === 'intent'), icon: Fingerprint },
    { title: 'Shop & check', detail: agentOperation === 'repair' ? 'Repairing and rechecking the cart' : agentOperation === 'shopping' ? 'Finding a cart and checking your intent' : latestDecision ? latestDecision === 'APPROVE' ? 'Cart passed intent checks' : 'Payment paused for policy findings' : 'Shopping Agent + IntentOS', done: !!shoppingProposal, running: agentOperation === 'shopping' || agentOperation === 'repair', icon: ShieldCheck },
    { title: 'You approve', detail: pendingPayPalOrder ? 'Waiting for PayPal buyer approval' : shoppingReviewId ? 'Exact cart reviewed' : 'Review cart, then approve in PayPal', done: !!shoppingReviewId || !!paymentCapture || !!latestRunReceipt, running: checkoutBusy || !!pendingPayPalOrder, icon: WalletCards },
    { title: 'AgentReceipt', detail: latestRunReceipt ? 'Verified payment evidence received' : receiptEvidenceError ? 'Receipt lookup needs attention' : paymentCapture ? 'Waiting for verified PayPal webhook' : 'An evidence-backed purchase record', done: !!latestRunReceipt, running: !!paymentCapture && !latestRunReceipt && !receiptEvidenceError, icon: ReceiptText },
  ]
  const paymentSummary = latestRunReceipt
    ? `${formatMoney(latestRunReceipt.amount, latestRunReceipt.currency)} captured · verified receipt`
    : paymentCapture
      ? `${formatMoney(paymentCapture.amount, paymentCapture.currency)} capture response · ${receiptEvidenceError ? 'receipt lookup needs attention' : 'waiting for verified webhook receipt'}`
      : pendingPayPalOrder
        ? `Buyer approval ${pendingPayPalOrder.status.toLowerCase()} · no capture recorded`
        : shoppingReviewId
          ? 'Cart reviewed · no PayPal charge recorded'
          : latestDecision === 'APPROVE'
            ? 'Not charged · exact cart review required'
            : 'No PayPal charge recorded'

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="Primary navigation">
        <a className="brand" href="#dashboard" aria-label="IntentOS home">
          <span className="brand-mark"><Fingerprint size={21} strokeWidth={1.8} /></span>
          <span>intent<span className="brand-light">os</span></span>
        </a>

        <div className="workspace-label">WORKSPACE</div>
        <nav className="nav-list">
          <a className="nav-item active" href="#dashboard"><LayoutDashboard size={17} />Shopping Agent</a>
          <a className="nav-item" href="#intents"><ShieldCheck size={17} />Intent policies</a>
          <a className="nav-item" href="#shopping-history"><ReceiptText size={17} />Shopping history</a>
          <a className="nav-item" href="#receipts"><ReceiptText size={17} />Agent receipts</a>
          <a className="nav-item" href="#activity"><Activity size={17} />Agent activity</a>
        </nav>

        <div className="sidebar-spacer" />
        <div className="sandbox-card">
          <div className="sandbox-icon"><LockKeyhole size={16} /></div>
          <div><strong>PayPal Sandbox</strong><span>Buyer approval required</span></div>
          <span className="live-dot" aria-label="Sandbox active" />
        </div>
        <div className="profile-row">
          <div className="avatar">{auth.name?.slice(0, 1) ?? 'G'}</div>
          <div className="profile-copy"><strong>{auth.authenticated ? auth.name : 'Guest buyer'}</strong><span>{auth.authenticated ? 'PayPal account' : 'Not signed in'}</span></div>
        </div>
      </aside>

      <main id="dashboard" className="main-content">
        <header className="topbar">
          <div className="breadcrumb"><span>IntentOS</span><span className="breadcrumb-slash">/</span><strong>Shopping Agent</strong></div>
          <div className="topbar-actions">
            <div className={`backend-status ${health}`}><span className="status-dot" />API {health === 'checking' ? 'checking' : health === 'online' ? 'connected' : 'offline'}</div>
            {auth.authenticated ? <button className="auth-button" onClick={async () => { await fetch('/api/auth/logout', { method: 'POST', headers: auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {} }); setAuth({ authenticated: false, loginConfigured: auth.loginConfigured }) }}>Sign out</button> : auth.loginConfigured ? <a className="auth-button" href="/api/auth/paypal">Sign in with PayPal</a> : <span className="auth-unavailable">PayPal Login not configured</span>}
            <div className="top-avatar">{auth.authenticated && auth.name ? auth.name.slice(0, 1).toUpperCase() : 'AS'}</div>
          </div>
        </header>

        <div className="page-content">
          <section className="page-heading">
            <div>
              <div className="eyebrow"><span className="eyebrow-rule" />{todayLabel}</div>
              <h1>Your Shopping Agent</h1>
              <p className="heading-copy">You set the goal. IntentOS keeps the purchase true to it.</p>
            </div>
            <button className="secondary-button" onClick={loadExample} disabled={busy || !!pendingPayPalOrder}><Sparkles size={16} />Try a laptop request</button>
          </section>

          <ol className="agent-journey" aria-label="Purchase progress">
            {journeySteps.map((step, index) => <li key={step.title} className={step.running ? 'is-running' : step.done ? 'is-done' : ''} aria-current={step.running ? 'step' : undefined}>
              <span className="journey-icon">{step.running ? <LoaderCircle size={19} className="spin" /> : step.done ? <Check size={19} /> : <step.icon size={19} />}</span>
              <div><small>0{index + 1}</small><strong>{step.title}</strong><span>{step.detail}</span></div>
            </li>)}
          </ol>

          {activeIntent && <section className={`decision-brief ${latestDecision ? `decision-${latestDecision.toLowerCase()}` : 'decision-empty'}`} aria-labelledby="decision-brief-title">
            <div className="decision-brief-heading">
              <div>
                <span className="panel-kicker">LATEST PURCHASE ACTIVITY</span>
                <h2 id="decision-brief-title">{activeIntent ? activeIntent.goal : 'Your agent activity'}</h2>
              </div>
              <span className={`decision-badge ${latestDecision ? `badge-${latestDecision.toLowerCase()}` : ''}`}>
                {latestDecision ?? (activeIntent ? 'NOT EVALUATED' : 'NO ACTIVE INTENT')}
              </span>
            </div>
            <div className="decision-brief-grid">
              <div className="decision-brief-item">
                <span>What the agent did</span>
                <strong>{latestRunItems.length > 0 ? latestRunItems.join(', ') : latestEvent ? formatEventTitle(latestEvent.eventType) : activeIntent ? 'Intent saved; no cart evaluated yet.' : 'Create or reopen an intent to see a run.'}</strong>
                <small>{shoppingProposal?.quote?.total !== null && shoppingProposal?.quote?.total !== undefined
                  ? `Quoted total ${formatMoney(shoppingProposal.quote.total, shoppingProposal.quote.currency)}`
                  : latestEvent?.calculatedTotal !== null && latestEvent?.calculatedTotal !== undefined
                    ? `Evaluated total ${formatMoney(latestEvent.calculatedTotal, activeIntent?.currency)}`
                    : activeIntent ? `Budget ${formatMoney(activeIntent.maxAmount, activeIntent.currency)}` : 'No purchase facts yet'}</small>
              </div>
              <div className="decision-brief-item">
                <span>Was it safe?</span>
                <strong>{latestDecision === 'APPROVE' ? 'Within the recorded policy'
                  : latestDecision === 'BLOCK' ? 'Blocked before payment'
                    : latestDecision === 'HOLD' ? 'Waiting for evidence or review'
                      : latestDecision === 'MODIFY' ? 'Changes recommended'
                        : 'Not evaluated yet'}</strong>
                <small>{latestDecision ? `Decision ${latestDecision}${shoppingProposal?.revision ? ` · revision ${shoppingProposal.revision.revisionNumber}` : ''}` : 'No policy decision recorded'}</small>
              </div>
              <div className="decision-brief-item">
                <span>Why</span>
                <strong>{latestDecisionReason}</strong>
                <small>{latestEvent ? `Evidence event ${latestEvent.eventId}` : shoppingProposal?.revision ? `Run ${shoppingProposal.run.runId}` : 'Reason will appear after evaluation'}</small>
              </div>
              <div className="decision-brief-item payment-brief">
                <span>What PayPal charged</span>
                <strong>{paymentSummary}</strong>
                <small>{latestRunReceipt ? `Capture ${latestRunReceipt.paypalCaptureId}` : paymentCapture ? `Order ${paymentCapture.orderId}` : pendingPayPalOrder ? `Order ${pendingPayPalOrder.orderId}` : 'Only a verified capture receipt confirms a charge'}</small>
              </div>
            </div>
            <div className="decision-brief-links">
              <a href="#activity">Inspect decision timeline <ArrowUpRight size={13} /></a>
              <a href="#receipts">Open AgentReceipts <ArrowUpRight size={13} /></a>
            </div>
          </section>}

          <section className="metrics" aria-label="Purchase summary">
            <div className="metric-cell"><div className="metric-icon ink"><ShoppingBag size={17} /></div><div className="metric-label">Approved proposals</div><div className="metric-value">{receiptMetrics?.approvedProposals ?? 0} <span>of {receiptMetrics?.totalProposals ?? 0}</span></div></div>
            <div className="metric-cell"><div className="metric-icon coral"><ShieldCheck size={17} /></div><div className="metric-label">Violating proposals</div><div className="metric-value">{receiptMetrics?.violatingProposals ?? 0} <span>detected</span></div></div>
            <div className="metric-cell"><div className="metric-icon green"><CircleDollarSign size={17} /></div><div className="metric-label">Prevented on approved repairs</div><div className="metric-value">${(receiptMetrics?.preventedAmount ?? 0).toFixed(2)}</div></div>
            <div className="metric-cell"><div className="metric-icon blue"><FileCheck2 size={17} /></div><div className="metric-label">Final run compliance</div><div className="metric-value">{receiptMetrics ? `${(receiptMetrics.intentComplianceRate * 100).toFixed(0)}%` : '—'} <span>{receiptMetrics ? `${receiptMetrics.approvedProposals} of ${receiptMetrics.totalProposals} runs` : 'no decisions yet'}</span></div></div>
          </section>

          <div className="workspace-grid">
            <div className="primary-column">
              <section className="intent-composer" id="intents">
                <div className="section-heading">
                  <div className="section-icon"><ShoppingBag size={20} /></div>
                  <div><h2>What would you like me to buy?</h2><p>Shopping Agent <span className="agent-dot" /> {busy ? 'Working on your request' : 'Ready when you are'}</p></div>
                </div>
                <form onSubmit={(event) => { if (!draftReady || extractionNeedsClarification) { event.preventDefault(); void extractIntent() } else { void saveIntent(event) } }}>
                  <label className="request-label" htmlFor="intent-request">Your purchase request</label>
                  <textarea id="intent-request" value={request} onChange={(event) => {
                    setRequest(event.target.value)
                    setDraftReady(false)
                    setGoal('')
                    setMaxAmount('')
                    setDraftCategories([])
                    setDraftRequiredAttributes({})
                    setExtractionNeedsClarification(false)
                    setAllowRecurringPayments(false)
                    setAllowSubscriptions(false)
                    setAllowUnrequestedAddons(false)
                  }} disabled={busy || !!pendingPayPalOrder} rows={3} />
                  <div className="shopping-destination">
                    <label className="form-field"><span>Country</span><select value={shoppingCountry} disabled={busy} onChange={(event) => setShoppingCountry(event.target.value)}><option value="US">United States</option></select></label>
                    <label className="form-field"><span>State</span><select value={shoppingRegion} disabled={busy} onChange={(event) => { setShoppingRegion(event.target.value); setShoppingShipping('standard') }}><option value="CA">California</option><option value="NY">New York</option></select></label>
                    <label className="form-field"><span>Shipping method</span><select value={shoppingShipping} disabled={busy} onChange={(event) => setShoppingShipping(event.target.value)}><option value="standard">Standard</option>{shoppingRegion === 'CA' && <option value="express">Express</option>}</select></label>
                  </div>
                  <div className="agent-live-status" role="status" aria-live="polite">
                    {agentOperation !== 'idle' ? <LoaderCircle size={16} className="spin" /> : <ShieldCheck size={16} />}
                    <span>{agentOperation === 'intent' ? 'Intent Agent is translating your request into purchase boundaries.' : agentOperation === 'shopping' ? 'Shopping Agent is selecting products. IntentOS will check the proposed cart before payment.' : agentOperation === 'repair' ? 'The cart is being repaired and checked again against your intent.' : agentOperation === 'review' ? 'Checking this exact cart and recording your review.' : agentOperation === 'payment' ? 'Communicating with PayPal. Your server-side purchase checks still apply.' : draftReady ? 'Your interpretation is ready for review.' : 'Your request starts here. Payment always needs your approval.'}</span>
                  </div>
                  {draftReady && <div className="intent-review" id="intent-review">
                  <div className="interpretation-heading"><Fingerprint size={18} /><div><h3>Here is what I understood</h3><p>Intent Agent translated your request. These are the boundaries your cart will be checked against.</p></div></div>
                  {extractionNeedsClarification && <p className="clarification-note">I need a little more detail before shopping. Update your request with the answers below, then try again.</p>}
                  <div className="intent-fields">
                    <label className="form-field"><span>Goal</span><input value={goal} onChange={(event) => setGoal(event.target.value)} required={!extractionNeedsClarification} /></label>
                    <label className="form-field budget-field"><span>Maximum total <small>USD</small></span><input type="number" min="0.01" step="0.01" value={maxAmount} onChange={(event) => setMaxAmount(event.target.value)} required={!extractionNeedsClarification} /></label>
                  </div>
                  <div className="quantity-fields">
                    <label className="form-field"><span>Limit applies to item</span><input value={limitedItemName} onChange={(event) => setLimitedItemName(event.target.value)} /></label>
                    <label className="form-field"><span>Maximum quantity</span><input type="number" min="1" step="1" value={itemQuantityLimit} onChange={(event) => setItemQuantityLimit(event.target.value)} /></label>
                  </div>
                  <fieldset className="policy-toggles">
                    <legend>Payment permissions</legend>
                    <label><input type="checkbox" checked={allowRecurringPayments} onChange={(event) => setAllowRecurringPayments(event.target.checked)} /><span>Recurring payments</span></label>
                    <label><input type="checkbox" checked={allowSubscriptions} onChange={(event) => setAllowSubscriptions(event.target.checked)} /><span>Subscriptions</span></label>
                    <label><input type="checkbox" checked={allowUnrequestedAddons} onChange={(event) => setAllowUnrequestedAddons(event.target.checked)} /><span>Unrequested add-ons</span></label>
                  </fieldset>
                  {Object.keys(draftRequiredAttributes).length > 0 && <dl className="understood-attributes">{Object.entries(draftRequiredAttributes).map(([name, value]) => <div key={name}><dt>{name}</dt><dd>{value}</dd></div>)}</dl>}
                  {draftCategories.length > 0 && <p className="category-summary">Shopping for: {draftCategories.join(', ')}</p>}
                  </div>}
                  <div className="composer-footer">
                    <span className="composer-hint"><LockKeyhole size={13} />No payment without your approval</span>
                    <button className="primary-button" type="submit" disabled={!request.trim() || busy || !!pendingPayPalOrder || (!!integrations?.authenticationRequired && !auth.authenticated) || (draftReady && !extractionNeedsClarification && (!goal.trim() || !maxAmount))}>
                      {busy ? <LoaderCircle size={16} className="spin" /> : <ArrowRight size={16} />}
                      {extracting ? 'Understanding your request' : saving ? 'Shopping in progress' : draftReady && !extractionNeedsClarification ? 'Confirm intent & find my cart' : extractionNeedsClarification ? 'Check updated request' : 'Start shopping'}
                    </button>
                  </div>
                </form>
                {notice && <p className={`notice ${noticeKind}`} role={noticeKind === 'error' ? 'alert' : 'status'}>{noticeKind === 'error' ? <X size={14} /> : <Check size={14} />}{notice}</p>}
                {!auth.authenticated && integrations?.authenticationRequired && <a className="sign-in-prompt" href="/api/auth/paypal"><LockKeyhole size={15} />Sign in with PayPal to start a protected shopping request<ArrowUpRight size={15} /></a>}
                {activeIntent && <article className="saved-contract"><div className="draft-row"><div className="draft-status"><ShieldCheck size={15} /></div><div className="draft-copy"><p>{activeIntent.goal}</p><span>{activeIntent.intentId} · Intent policy</span></div><strong className="contract-budget">${activeIntent.maxAmount.toFixed(2)}</strong></div></article>}
                {paymentCapture && <div className="capture-confirmation" role="status"><strong>PayPal returned {paymentCapture.status}</strong><span>{paymentCapture.captureId} · {paymentCapture.currency} {paymentCapture.amount.toFixed(2)}</span></div>}
              </section>

              <section className="activity-section shopping-section" id="shopping-run">
                <div className="section-heading activity-heading">
                  <div><h2>{shoppingProposal ? 'Your proposed purchase' : 'Your cart'}</h2><p>{shoppingBusy ? 'Shopping Agent is working on your cart' : 'Selected by the Shopping Agent. Checked against your intent.'}</p></div>
                  <button className="text-button" onClick={startShoppingRun} disabled={!activeIntent || busy || !!pendingPayPalOrder}><Plus size={14} />New run</button>
                </div>
                <div className="simulator-disclosure">Simulated merchant catalog and quotes. Real PayPal Sandbox payment lifecycle. No physical order or retailer settlement.</div>
                {!activeIntent && <div className="cart-waiting"><ShoppingBag size={26} /><div><strong>Your cart is waiting for your goal</strong><p>Nothing selected. Nothing charged.</p></div></div>}
                {activeIntent && <>
                  <div className="shopping-run-bar">
                    <span>{shoppingRun ? `Started ${new Date(shoppingRun.createdAt).toLocaleTimeString()} · ${shoppingRegion === 'CA' ? 'California' : 'New York'} · ${shoppingShipping}` : 'No active run'}</span>
                    <button className="primary-button" onClick={proposeShopping} disabled={!shoppingRun || !shoppingCountry.trim() || !shoppingRegion.trim() || !shoppingShipping.trim() || busy || !!pendingPayPalOrder || !!paymentCapture || !!latestRunReceipt}>
                      <RefreshCw size={14} className={shoppingBusy ? 'spin' : ''} />{shoppingBusy ? 'Agent working' : shoppingProposal ? 'Find another cart' : 'Find my cart'}
                    </button>
                  </div>
                </>}
                {shoppingProposal && <div className={`shopping-result ${shoppingProposal.validation?.decision === 'APPROVE' ? 'approved' : shoppingProposal.validation?.decision === 'BLOCK' ? 'blocked' : 'review'}`}>
                  <div className="repair-result-heading"><strong>{latestDecision === 'APPROVE' ? 'This cart matches your intent' : latestDecision === 'BLOCK' ? 'IntentOS stopped this purchase' : latestDecision === 'MODIFY' ? 'This cart needs a change' : 'Let\u0027s resolve this before payment'}</strong><span>Revision {shoppingProposal.revision?.revisionNumber ?? 1}</span></div>
                  <p className="decision-explanation">{latestDecisionReason}</p>
                  {latestViolations.length > 0 && <div className="buyer-findings">{latestViolations.map((violation, index) => <article key={`${violation.type}-${index}`}><ShieldCheck size={17} /><div><strong>{formatEventTitle(violation.type)}</strong><p>{explainViolation(violation)}</p><small>Your boundary: {violation.expected || 'Recorded intent policy'}{violation.actual ? ` · Proposed: ${violation.actual}` : ''}</small></div></article>)}</div>}
                  {repairComparison && <div className="repair-comparison"><div><RefreshCw size={16} /><strong>What changed in the repair</strong></div><p>{formatMoney(repairComparison.before.total)} before → {formatMoney(repairComparison.after.total)} after</p>{repairComparison.before.items.filter(item => !repairComparison.after.items.some(next => next.name === item.name)).map(item => <p key={item.name}>Removed {item.name} from the proposed cart.</p>)}<p>{latestDecision === 'APPROVE' ? 'The new cart passed your intent checks. No payment has been made.' : 'The revised cart still needs attention. Payment remains paused.'}</p></div>}
                  <details className="technical-evidence"><summary>Agent result and recorded IDs</summary><p>{shoppingProposal.outcome}</p><small>{shoppingProposal.revision?.revisionId ?? shoppingProposal.run.runId}</small></details>
                  {shoppingProposal.quote && <>
                    <p className="simulator-disclosure">{shoppingProposal.quote.disclosure}</p>
                    {shoppingProposal.proposal && <div className="repair-items">{shoppingProposal.proposal.items.map((item) => <div key={`${item.name}-${item.category}`}><span>{item.quantity} × {item.name}</span><strong>{shoppingProposal.quote?.currency} {(item.unitPrice * item.quantity).toFixed(2)}</strong></div>)}
                      <div><span>Shipping</span><strong>{shoppingProposal.quote.currency} {shoppingProposal.quote.shipping?.toFixed(2) ?? 'Unknown'}</strong></div>
                      <div><span>Tax</span><strong>{shoppingProposal.quote.currency} {shoppingProposal.quote.tax?.toFixed(2) ?? 'Unknown'}</strong></div>
                      <div className="repair-total"><span>Quoted total</span><strong>{shoppingProposal.quote.currency} {shoppingProposal.quote.total?.toFixed(2) ?? 'HOLD'}</strong></div>
                    </div>}
                    {shoppingProposal.quote.missingEvidence.length > 0 && <p>Missing evidence: {shoppingProposal.quote.missingEvidence.join(', ')}</p>}
                  </>}
                  {shoppingProposal.revision && (shoppingProposal.validation?.decision ?? shoppingProposal.revision.validationDecision) !== 'APPROVE' && <button className="primary-button" onClick={repairShopping} disabled={busy || !!pendingPayPalOrder}><RefreshCw size={14} className={agentOperation === 'repair' ? 'spin' : ''} />{agentOperation === 'repair' ? 'Repairing and checking again' : 'Ask IntentOS to repair this cart'}</button>}
                  {shoppingProposal.revision && (shoppingProposal.validation?.decision ?? shoppingProposal.revision.validationDecision) === 'APPROVE' && !shoppingReviewId && <button className="primary-button" onClick={reviewShoppingRevision} disabled={busy}><Check size={14} />{agentOperation === 'review' ? 'Recording your review' : 'I have reviewed this cart'}</button>}
                  {shoppingReviewId && !pendingPayPalOrder && !paymentCapture && !latestRunReceipt && <div className="sandbox-checkout-note"><strong>Cart review recorded</strong><span>You reviewed this exact revision. PayPal buyer approval is still required; no payment has been made.</span><button className="primary-button checkout-button" onClick={checkoutShoppingRevision} disabled={busy || !integrations?.paypalClientId || !auth.authenticated}><WalletCards size={16} />{checkoutBusy ? 'Creating PayPal order…' : !auth.authenticated ? 'Sign in to continue' : 'Continue to PayPal Sandbox'}</button></div>}
                </div>}
                {(paymentCapture || latestRunReceipt) && <div className="payment-outcome" role="status">{latestRunReceipt ? <><ReceiptText size={22} /><div><strong>Your verified AgentReceipt is ready</strong><p>PayPal confirmed {formatMoney(latestRunReceipt.amount, latestRunReceipt.currency)}. Your intent, cart decisions, and payment evidence are recorded together.</p><button className="text-button" onClick={() => { void openReceiptDetail(latestRunReceipt); window.location.hash = 'receipts' }}>Open my receipt <ArrowUpRight size={14} /></button></div></> : receiptEvidenceError ? <><ReceiptText size={22} /><div><strong>Receipt lookup needs attention</strong><p>{receiptEvidenceError}</p><a className="text-button" href="/api/auth/paypal">Sign in with PayPal <ArrowUpRight size={14} /></a></div></> : <><LoaderCircle size={22} className="spin" /><div><strong>PayPal returned {paymentCapture?.status.toLowerCase()}</strong><p>{formatMoney(paymentCapture?.amount, paymentCapture?.currency)} capture response received. Waiting for the verified PayPal webhook before issuing your AgentReceipt.</p></div></>}</div>}
                {pendingPayPalOrder && <div className="sandbox-checkout-note pending-payment" role="status">
                  <strong>PayPal order {pendingPayPalOrder.status}</strong>
                  <span>Order {pendingPayPalOrder.orderId} · approved cart {pendingPayPalOrder.revisionId}</span>
                  <a className="primary-button checkout-button" href={pendingPayPalOrder.approvalUrl}>Open PayPal buyer approval</a>
                  <span>After approving in PayPal, return here. Capture is a separate server-side action and will not run automatically.</span>
                  <button className="secondary-button" onClick={captureBuyerApprovedOrder} disabled={checkoutBusy || !auth.authenticated}>{checkoutBusy ? 'Checking approval…' : 'Check approval and capture'}</button>
                </div>}
              </section>

              <section className="activity-section" id="activity">
                <div className="section-heading activity-heading">
                  <div><h2>How your agents reached this decision</h2><p>The recorded checks, findings, and repairs for this request</p></div>
                  <button className="text-button" onClick={explainDecision} disabled={!activeIntent || activityEvents.length === 0 || explanationLoading}><Sparkles size={14} />{explanationLoading ? 'Explaining…' : 'Explain decision'}</button>
                </div>
                {activityEvents.length === 0 ? <div className="empty-activity"><div className="empty-glyph"><Activity size={20} /></div><strong>No decisions recorded yet</strong><span>Your purchase checks will appear as the agent works.</span></div> : <div className="event-list">{activityEvents.map((event) => <article className="event-row" key={event.eventId}><div className={`event-indicator ${event.decision.toLowerCase()}`}><ShieldCheck size={14} /></div><div className="event-copy"><strong>{formatEventTitle(event.eventType)}</strong><p>{explainEvent(event)}</p><span>{formatMoney(event.calculatedTotal, activeIntent?.currency)} · {new Date(event.occurredAt).toLocaleTimeString()}</span><details><summary>Recorded evidence</summary><small>{event.message} · {event.eventId}</small></details></div></article>)}</div>}
                {modelExplanation && <p className="model-explanation" role="status"><strong>Evidence-based explanation</strong>{modelExplanation}</p>}
              </section>

              <section className="activity-section history-section" id="shopping-history">
                <div className="section-heading activity-heading"><div><h2>Shopping history</h2><p>Decision runs, including blocked and review-required proposals</p></div><button className="text-button" onClick={() => void loadShoppingHistory()} disabled={!auth.authenticated}><RefreshCw size={14} />Refresh</button></div>
                <form className="history-filters" onSubmit={(event) => { event.preventDefault(); void loadShoppingHistory() }}>
                  <label className="form-field"><span>Search goals or products</span><input value={historyQuery} onChange={(event) => setHistoryQuery(event.target.value)} /></label>
                  <label className="form-field"><span>Status</span><select value={historyStatus} onChange={(event) => setHistoryStatus(event.target.value)}><option value="">Any status</option><option value="APPROVE">Approved</option><option value="BLOCK">Blocked</option><option value="HOLD">Needs review</option><option value="MODIFY">Modify</option></select></label>
                  <label className="form-field"><span>From</span><input type="datetime-local" value={historyFrom} onChange={(event) => setHistoryFrom(event.target.value)} /></label>
                  <label className="form-field"><span>To</span><input type="datetime-local" value={historyTo} onChange={(event) => setHistoryTo(event.target.value)} /></label>
                  <button className="secondary-button" type="submit" disabled={!auth.authenticated}><Search size={14} />Filter</button>
                </form>
                {shoppingHistory?.incompleteData && <p className="history-warning">Some stored results may exceed the current backend retrieval cap.</p>}
                {!auth.authenticated && <div className="catalog-empty">Sign in with PayPal to view owner-scoped history.</div>}
                {auth.authenticated && shoppingHistory?.items.length === 0 && <div className="empty-activity"><strong>No shopping runs found</strong><span>Runs appear here whether they were captured, blocked, or held for review.</span></div>}
                {shoppingHistory?.items.map((entry) => <article className="history-row" key={entry.run.runId}>
                  <div className={`event-indicator ${entry.status.toLowerCase()}`}><Activity size={14} /></div><div className="event-copy"><strong>{entry.goal} · {entry.status}</strong><span>{entry.run.runId} · {entry.revisions.length} revision{entry.revisions.length === 1 ? '' : 's'} · {entry.productIds.join(', ') || 'No cart yet'}</span><small>{new Date(entry.run.createdAt).toLocaleString()} · intent {entry.intentId}</small></div><button className="icon-button" aria-label={`Open run ${entry.run.runId}`} onClick={() => void openShoppingRun(entry)}><ArrowUpRight size={15} /></button>
                </article>)}
                {shoppingHistory && shoppingHistory.total > shoppingHistory.items.length && <button className="text-button" onClick={() => { const next = shoppingHistory.page + 1; const params = new URLSearchParams({ page: String(next), pageSize: String(shoppingHistory.pageSize), q: historyQuery, status: historyStatus }); if (historyFrom) params.set('from', new Date(historyFrom).toISOString()); if (historyTo) params.set('to', new Date(historyTo).toISOString()); void fetch(`/api/shopping-history?${params}`).then((response) => response.json()).then((page: ShoppingHistoryPage) => setShoppingHistory((current) => current ? { ...page, items: [...current.items, ...page.items] } : page)) }}>Load more</button>}
              </section>

              <section className="activity-section receipt-section" id="receipts">
                {receiptEvidenceError && !latestRunReceipt && <p className="notice error" role="alert">{receiptEvidenceError}</p>}
                <div className="section-heading activity-heading">
                  <div><h2>AgentReceipt intelligence</h2><p>Searchable evidence, policy metrics, and {integrations?.elasticAgentBuilderConfigured ? 'Elastic Agent Builder' : 'grounded analysis'}</p></div>
                  <button className="text-button" onClick={() => { if (activeIntent) { void searchReceipts(); void loadReceiptMetrics(activeIntent.intentId) } }} disabled={!activeIntent || receiptsLoading}><RefreshCw size={14} />Refresh</button>
                </div>
                {receiptMetrics && <div className="receipt-metrics" aria-label="Intent analytics">
                  <div><span>Proposals</span><strong>{receiptMetrics.totalProposals}</strong></div>
                  <div><span>Compliance</span><strong>{(receiptMetrics.intentComplianceRate * 100).toFixed(0)}%</strong></div>
                  <div><span>Violations</span><strong>{receiptMetrics.violatingProposals}</strong></div>
                  <div><span>Repaired</span><strong>{receiptMetrics.repairedProposals}</strong></div>
                  <div><span>Prevented</span><strong>USD {receiptMetrics.preventedAmount.toFixed(2)}</strong></div>
                  <div><span>Blocked exposure · not savings</span><strong>USD {receiptMetrics.blockedExposureAmount?.toFixed(2) ?? '0.00'}</strong></div>
                  <div><span>First-pass compliance</span><strong>{((receiptMetrics.firstPassComplianceRate ?? 0) * 100).toFixed(0)}%</strong></div>
                  <div><span>Captured</span><strong>USD {receiptMetrics.capturedAmount.toFixed(2)}</strong></div>
                </div>}
                <form className="receipt-search-form" onSubmit={(event) => { event.preventDefault(); void searchReceipts() }}>
                  <label className="sr-only" htmlFor="receipt-query">Search receipts</label>
                  <input id="receipt-query" value={receiptQuery} onChange={(event) => setReceiptQuery(event.target.value)} />
                  <label className="sr-only" htmlFor="receipt-status">Filter receipts by status</label>
                  <select id="receipt-status" value={receiptStatus} onChange={(event) => setReceiptStatus(event.target.value)}>
                    <option value="">Any status</option>
                    <option value="COMPLETED">Captured</option>
                    <option value="APPROVE">Approved</option>
                    <option value="BLOCK">Blocked</option>
                    <option value="HOLD">Needs review</option>
                  </select>
                  <button className="secondary-button" type="submit" disabled={!activeIntent || receiptsLoading}><Search size={14} />{receiptsLoading ? 'Searching…' : 'Search'}</button>
                </form>
                {agentReceipts.length === 0
                  ? <div className="empty-activity"><div className="empty-glyph"><ReceiptText size={20} /></div><strong>No matching receipts</strong><span>Completed receipts appear after a verified PayPal capture webhook.</span></div>
                  : <div className="receipt-list">{agentReceipts.map((receipt) => <article className="receipt-row" key={receipt.receiptId}><div className="event-indicator approve"><ReceiptText size={14} /></div><div className="event-copy"><strong>{receipt.currency} {receipt.amount.toFixed(2)} · {receipt.captureStatus}</strong><span>{receipt.paypalCaptureId} · Order {receipt.paypalOrderId}</span><small>{receipt.proposalHistory.length} proposal snapshot{receipt.proposalHistory.length === 1 ? '' : 's'} · {receipt.repairs.length} repair event{receipt.repairs.length === 1 ? '' : 's'} · webhook {receipt.webhookEventId}</small><small>{new Date(receipt.createdAt).toLocaleString()}</small></div><button className="icon-button" aria-label={`Open receipt ${receipt.receiptId}`} onClick={() => void openReceiptDetail(receipt)}><ArrowUpRight size={15} /></button></article>)}</div>}
                {receiptDetail && <section className="receipt-detail" aria-label="AgentReceipt detail">
                  <header className="receipt-detail-header">
                    <div><span className="panel-kicker">VERIFIED AGENT RECEIPT</span><h3>{receiptDetail.receiptId}</h3><p>{receiptDetail.runId ? `Run ${receiptDetail.runId} · captured revision ${receiptDetail.revisionId ?? 'not attributed'}` : 'Legacy receipt · run attribution unavailable'}</p></div>
                    <button className="icon-button" aria-label="Close receipt detail" onClick={() => setReceiptDetail(null)}><X size={15} /></button>
                  </header>
                  <div className="receipt-facts">
                    <div><span>What you asked for</span><strong>{receiptDetail.intent?.naturalLanguageRequest ?? receiptDetail.intent?.goal ?? 'Original request unavailable'}</strong><small>{receiptDetail.intent ? `Budget ${formatMoney(receiptDetail.intent.maxAmount, receiptDetail.intent.currency)}` : `Intent ${receiptDetail.intentId}`}</small></div>
                    <div><span>Safety decision</span><strong className={`receipt-decision receipt-decision-${receiptDetail.finalDecision.toLowerCase()}`}>{receiptDetail.finalDecision}</strong><small>{receiptDetail.violations.length === 0 ? 'No violations recorded on the receipt' : `${receiptDetail.violations.length} recorded policy finding${receiptDetail.violations.length === 1 ? '' : 's'}`}</small></div>
                    <div><span>PayPal captured</span><strong>{formatMoney(receiptDetail.amount, receiptDetail.currency)}</strong><small>{receiptDetail.captureStatus} · {new Date(receiptDetail.createdAt).toLocaleString()}</small></div>
                    <div><span>Prevented on this run</span><strong>{formatMoney(receiptDetail.preventedAmount ?? 0, receiptDetail.currency)}</strong><small>Recorded same-run proposal reduction; not a separate charge</small></div>
                  </div>
                  <section className="receipt-detail-section">
                    <div className="receipt-subheading"><h4>What the agent proposed</h4><span>{receiptDetail.proposalHistory.length} snapshot{receiptDetail.proposalHistory.length === 1 ? '' : 's'}</span></div>
                    {receiptDetail.proposalHistory.length === 0 ? <p className="receipt-empty-note">Proposal details were not retained for this receipt.</p> : <div className="proposal-history-list">{receiptDetail.proposalHistory.map((proposal, index) => <article className="proposal-snapshot" key={proposal.proposalId}>
                      <header><div><strong>{index === 0 && receiptDetail.proposalHistory.length > 1 ? 'Initial proposal' : index === receiptDetail.proposalHistory.length - 1 ? 'Captured proposal' : `Proposal ${index + 1}`}</strong><span>{proposal.proposalId}</span></div><strong>{formatMoney(proposal.total, proposal.currency)}</strong></header>
                      <div className="proposal-line-list">{proposal.items.map((item, itemIndex) => <div className="proposal-line" key={`${proposal.proposalId}-${itemIndex}`}><span>{item.quantity} × {item.name}{item.recurring || item.subscription ? ' · recurring' : item.addOn ? ' · add-on' : ''}</span><strong>{formatMoney(item.unitPrice * item.quantity, proposal.currency)}</strong></div>)}
                        <div className="proposal-line"><span>Shipping</span><strong>{formatMoney(proposal.shipping, proposal.currency)}</strong></div>
                        <div className="proposal-line"><span>Tax</span><strong>{formatMoney(proposal.tax, proposal.currency)}</strong></div>
                        <div className="proposal-line proposal-line-total"><span>Proposal total</span><strong>{formatMoney(proposal.total, proposal.currency)}</strong></div>
                      </div>
                    </article>)}</div>}
                  </section>
                  <section className="receipt-detail-section">
                    <div className="receipt-subheading"><h4>Intent checks and repairs</h4><span>{receiptDetail.repairs.length} repair event{receiptDetail.repairs.length === 1 ? '' : 's'}</span></div>
                    {receiptDetail.violations.length === 0 ? <p className="receipt-empty-note">No policy violations are recorded for this receipt.</p> : <div className="receipt-violation-list">{receiptDetail.violations.map((violation, index) => <article className="receipt-violation" key={`${violation.type}-${violation.item ?? index}`}>
                      <strong>{formatEventTitle(violation.type)}{violation.item ? ` · ${violation.item}` : ''}</strong>
                      <span>Expected: {violation.expected || 'Not recorded'}</span><span>Observed: {violation.actual ?? 'Not recorded'}</span>
                    </article>)}</div>}
                    {receiptDetail.repairs.length > 0 && <div className="receipt-repair-list">{receiptDetail.repairs.map((event) => <article key={event.eventId}><strong>{formatEventTitle(event.eventType)} · {event.decision}</strong><span>{event.message}</span><small>{event.eventId} · {new Date(event.occurredAt).toLocaleString()}</small></article>)}</div>}
                  </section>
                  <section className="receipt-detail-section">
                    <div className="receipt-subheading"><h4>Decision timeline</h4><span>{receiptDetail.activityTimeline.length} events</span></div>
                    {receiptDetail.activityTimeline.length === 0 ? <p className="receipt-empty-note">No timeline events were retained.</p> : <ol className="receipt-timeline">{[...receiptDetail.activityTimeline].sort((left, right) => left.occurredAt.localeCompare(right.occurredAt)).map((event) => <li key={event.eventId}>
                      <span className={`timeline-marker timeline-marker-${event.decision.toLowerCase()}`} />
                      <div><div className="timeline-event-heading"><strong>{formatEventTitle(event.eventType)} · {event.decision}</strong><time>{new Date(event.occurredAt).toLocaleString()}</time></div>
                        <p>{event.message}</p><small>{event.eventId} · {event.revisionId ?? event.proposalId}{event.calculatedTotal === null ? '' : ` · ${formatMoney(event.calculatedTotal, receiptDetail.currency)}`}</small>
                        {event.violations.length > 0 && <small>Findings: {event.violations.map((violation) => formatEventTitle(violation.type)).join(', ')}</small>}
                      </div>
                    </li>)}</ol>}
                  </section>
                  <section className="receipt-payment-evidence">
                    <div className="receipt-subheading"><h4>PayPal Sandbox evidence</h4><span>Verified webhook</span></div>
                    <dl><div><dt>Order</dt><dd>{receiptDetail.paypalOrderId}</dd></div><div><dt>Capture</dt><dd>{receiptDetail.paypalCaptureId}</dd></div><div><dt>Order status</dt><dd>{receiptDetail.orderStatus}</dd></div><div><dt>Capture status</dt><dd>{receiptDetail.captureStatus}</dd></div><div><dt>Webhook event</dt><dd>{receiptDetail.webhookEventId}</dd></div></dl>
                    <p>Payment is to the configured PayPal Sandbox merchant. It does not confirm physical retailer fulfillment.</p>
                  </section>
                </section>}
                <form className="receipt-analyst" onSubmit={askReceiptAnalyst}>
                  <label className="form-field" htmlFor="receipt-analyst-question"><span>Ask the Receipt Analyst</span><input id="receipt-analyst-question" value={analystQuestion} onChange={(event) => setAnalystQuestion(event.target.value)} maxLength={500} /></label>
                  <button className="primary-button" type="submit" disabled={!activeIntent || !analystQuestion.trim() || analystLoading}><Sparkles size={14} />{analystLoading ? 'Reviewing evidence…' : 'Ask'}</button>
                </form>
                {analystResult && <div className="analyst-answer" role="status"><strong>Receipt Analyst · {analystResult.evidenceCount} receipts · {analystResult.eventIds.length} decision events</strong><p>{analystResult.answer}</p>{!!analystResult.toolCalls?.length && <small>Verified tool execution: {analystResult.toolCalls.join(' → ')}</small>}<small>{analystResult.retrievalMode} retrieval · {analystResult.narrationMode} · Evidence: {[...analystResult.receiptIds, ...analystResult.runIds, ...analystResult.revisionIds, ...analystResult.eventIds].join(', ') || 'none'}</small></div>}
              </section>

            </div>

            <aside className="secondary-column">
              <section className="agent-workbench" aria-label="Live agent status" aria-live="polite">
                <div className="workbench-heading"><Activity size={17} /><h2>Your agents at work</h2><span className={busy ? 'agent-dot working' : 'agent-dot'} /></div>
                <div className={`agent-worker ${agentOperation === 'intent' ? 'worker-active' : ''}`}><Fingerprint size={19} /><div><strong>Intent Agent</strong><span>{extracting ? 'Understanding your request' : draftReady ? 'Interpretation ready for you' : activeIntent ? 'Your boundaries are recorded' : 'Waiting for your request'}</span></div></div>
                <div className={`agent-worker ${agentOperation === 'shopping' ? 'worker-active' : ''}`}><ShoppingBag size={19} /><div><strong>Shopping Agent</strong><span>{agentOperation === 'shopping' ? 'Selecting from catalog candidates' : shoppingProposal ? 'Cart proposed for your review' : 'Ready to find your purchase'}</span></div></div>
                <div className={`agent-worker ${agentOperation === 'repair' ? 'worker-active' : ''}`}><ShieldCheck size={19} /><div><strong>IntentOS protection</strong><span>{agentOperation === 'repair' ? 'Repairing and revalidating' : latestDecision === 'APPROVE' ? 'Proposed cart passed the checks' : latestDecision ? 'Checkout paused for findings' : 'Checks every cart before payment'}</span></div></div>
                <div className="agent-worker"><ReceiptText size={19} /><div><strong>Receipt Analyst</strong><span>{latestRunReceipt ? 'Verified receipt ready to explain' : paymentCapture ? 'Waiting for verified payment evidence' : 'Waiting for a completed purchase'}</span></div></div>
                {latestEvent && <div className="latest-agent-message"><span>Latest recorded decision</span><p>{explainEvent(latestEvent)}</p><time>{new Date(latestEvent.occurredAt).toLocaleTimeString()}</time></div>}
              </section>
              <section className="guardrail-panel">
                <div className="panel-heading"><div><span className="panel-kicker">POLICY STATUS</span><h2>Guardrails</h2></div><ShieldCheck size={19} /></div>
                <div className="guardrail-row"><span className="guardrail-symbol"><CircleDollarSign size={16} /></span><div><strong>Maximum spend</strong><span>{activeIntent ? `$${activeIntent.maxAmount.toFixed(2)} USD` : 'Not configured'}</span></div><span className="neutral-pill">{activeIntent ? 'Set' : 'Unset'}</span></div>
                <div className="guardrail-row"><span className="guardrail-symbol coral-symbol"><WalletCards size={16} /></span><div><strong>Recurring charges</strong><span>{activeIntent ? (activeIntent.allowRecurringPayments ? 'Allowed' : 'Prohibited') : 'Not configured'}</span></div><span className="neutral-pill">{activeIntent ? 'Set' : 'Unset'}</span></div>
                <div className="guardrail-row"><span className="guardrail-symbol green-symbol"><X size={16} /></span><div><strong>Unrequested add-ons</strong><span>{activeIntent ? (activeIntent.allowUnrequestedAddons ? 'Allowed' : 'Prohibited') : 'Not configured'}</span></div><span className="neutral-pill">{activeIntent ? 'Set' : 'Unset'}</span></div>
                <a className="panel-link" href="#intents">View purchase boundaries <ArrowUpRight size={15} /></a>
              </section>

              <section className="services-panel">
                <div className="panel-heading"><div><span className="panel-kicker">CONNECTED SERVICES</span><h2>Infrastructure</h2></div></div>
                <div className="service-row"><span className={`service-indicator ${health}`} /><span>IntentOS API</span><span className="service-state">{health === 'online' ? 'Online' : health === 'checking' ? 'Checking' : 'Offline'}</span></div>
                <div className="service-row"><span className={`service-indicator ${integrations?.elasticAgentBuilderConfigured ? 'online' : 'pending'}`} /><span>Receipt Analyst</span><span className={`service-state ${integrations?.elasticAgentBuilderConfigured ? '' : 'pending-text'}`}>{integrations ? integrations.elasticAgentBuilderConfigured ? 'Elastic' : 'Bedrock / off' : 'Checking'}</span></div>
                <div className="service-row"><span className={`service-indicator ${integrations?.paypalWebhookConfigured ? 'online' : 'pending'}`} /><span>PayPal webhooks</span><span className={`service-state ${integrations?.paypalWebhookConfigured ? '' : 'pending-text'}`}>{integrations?.paypalWebhookConfigured ? 'Configured' : 'Not configured'}</span></div>
                <div className="service-row"><span className={`service-indicator ${integrations?.elasticPersistenceEnabled ? 'online' : 'pending'}`} /><span>Elastic persistence</span><span className={`service-state ${integrations?.elasticPersistenceEnabled ? '' : 'pending-text'}`}>{integrations ? integrations.elasticPersistenceEnabled ? 'Enabled' : 'In memory' : 'Checking'}</span></div>
              </section>

              <div className="boundary-note"><LockKeyhole size={15} /><p><strong>Payment boundary</strong><span>Orders will only be created after an intent policy approves the proposal.</span></p></div>
            </aside>
          </div>

          <footer className="page-footer"><span>INTENTOS · PRIVATE PREVIEW</span><span>BUILD 0.1.0 <span className="footer-separator">/</span> SANDBOX</span></footer>
        </div>
      </main>
    </div>
  )
}

export default App
