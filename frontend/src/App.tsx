import { useEffect, useState, type FormEvent } from 'react'
import {
  Activity,
  ArrowUpRight,
  Check,
  CircleDollarSign,
  FileCheck2,
  Fingerprint,
  LayoutDashboard,
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
type AgentReceipt = {
  receiptId: string
  intentId: string
  amount: number
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

type ReceiptAnalystResult = { answer: string; receiptIds: string[]; evidenceCount: number; eventIds: string[]; runIds: string[]; revisionIds: string[]; retrievalMode: string; narrationMode: string }

type PayPalV6Session = {
  start(options: { presentationMode: 'auto'; orderId: string }): Promise<unknown>
}

type PayPalV6Instance = {
  createPayPalOneTimePaymentSession(options: {
    onApprove(data: { orderId: string }): Promise<void>
    onCancel(): void
    onError(error: unknown): void
  }): PayPalV6Session
}

declare global {
  interface Window {
    paypal?: {
      createInstance(options: {
        clientId: string
        components: ['paypal-payments']
        pageType: 'checkout'
      }): Promise<PayPalV6Instance>
    }
  }
}

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
  calculatedTotal: number
  violations: Array<{ type: string; item: string | null; expected: string; actual: string | null }>
  message: string
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
  const [shoppingCountry, setShoppingCountry] = useState('')
  const [shoppingRegion, setShoppingRegion] = useState('')
  const [shoppingShipping, setShoppingShipping] = useState('')
  const [shoppingHistory, setShoppingHistory] = useState<ShoppingHistoryPage | null>(null)
  const [historyQuery, setHistoryQuery] = useState('')
  const [historyStatus, setHistoryStatus] = useState('')
  const [historyFrom, setHistoryFrom] = useState('')
  const [historyTo, setHistoryTo] = useState('')
  const [saving, setSaving] = useState(false)
  const [activityEvents, setActivityEvents] = useState<DecisionEvent[]>([])
  const [checkoutBusy, setCheckoutBusy] = useState(false)
  const [paymentCapture, setPaymentCapture] = useState<PayPalCapture | null>(null)
  const [agentReceipts, setAgentReceipts] = useState<AgentReceipt[]>([])
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

  async function saveIntent(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
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
      const runResponse = await fetch(`/api/intents/${createdIntent.intentId}/shopping-runs`, {
        method: 'POST',
        headers: auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {},
      })
      if (!runResponse.ok) throw new Error('Intent saved, but a shopping run could not be started.')
      const createdRun = await runResponse.json() as ShoppingProposalResult['run']
      setShoppingRun(createdRun)
      setShoppingProposal(null)
      setShoppingReviewId('')
      void loadReceiptMetrics(createdIntent.intentId)
      setActivityEvents([])
      setAgentReceipts([])
      setReceiptMetrics(null)
      setAnalystResult(null)
      setPaymentCapture(null)
      setNotice(`Contract ${createdIntent.intentId} saved. Deterministic policy validation is ready.`)
      setNoticeKind('success')
      setRequest('')
      setGoal('')
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Could not save the intent.')
      setNoticeKind('error')
    } finally {
      setSaving(false)
    }
  }

  async function extractIntent() {
    if (!request.trim()) return
    setExtracting(true)
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
      setNotice(draft.needsClarification
        ? `Please clarify: ${draft.clarificationQuestions.join(' ')}`
        : 'Draft extracted. Review the fields and permissions before creating the contract.')
      setNoticeKind(draft.needsClarification ? 'error' : 'success')
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Could not extract the intent draft.')
      setNoticeKind('error')
    } finally {
      setExtracting(false)
    }
  }

  function loadExample() {
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
      const restoredRun = { ...entry.run, intentId: entry.intentId }
      setShoppingRun(restoredRun)
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
    setShoppingBusy(true)
    setShoppingReviewId('')
    try {
      const response = await fetch(`/api/intents/${activeIntent.intentId}/shopping-runs/${shoppingRun.runId}/propose`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {}) },
        body: JSON.stringify({ country: shoppingCountry.trim(), region: shoppingRegion.trim(), shippingMethod: shoppingShipping.trim() }),
      })
      if (!response.ok) throw new Error(response.status === 503 ? 'Nova shopping is unavailable; no proposal or payment was created.' : 'Shopping proposal failed closed.')
      setShoppingProposal(await response.json() as ShoppingProposalResult)
      await refreshIntentActivity(activeIntent.intentId)
      void loadShoppingHistory()
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Shopping proposal failed.')
      setNoticeKind('error')
    } finally {
      setShoppingBusy(false)
    }
  }

  async function repairShopping() {
    if (!activeIntent || !shoppingRun || !shoppingProposal?.revision) return
    setShoppingBusy(true)
    setShoppingReviewId('')
    try {
      const response = await fetch(`/api/intents/${activeIntent.intentId}/shopping-runs/${shoppingRun.runId}/revisions/${shoppingProposal.revision.revisionId}/repair`, {
        method: 'POST', headers: auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {},
      })
      if (!response.ok) throw new Error(response.status === 409 ? 'No further safe repair is available. Review the cart manually.' : 'Repair could not be completed.')
      setShoppingProposal(await response.json() as ShoppingProposalResult)
      await refreshIntentActivity(activeIntent.intentId)
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Repair failed.')
      setNoticeKind('error')
    } finally {
      setShoppingBusy(false)
    }
  }

  async function reviewShoppingRevision() {
    if (!activeIntent || !shoppingRun || !shoppingProposal?.revision) return
    setShoppingBusy(true)
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
    }
  }

  async function checkoutShoppingRevision() {
    if (!activeIntent || !shoppingRun || !shoppingProposal?.revision || !shoppingReviewId || !integrations?.paypalClientId) return
    setCheckoutBusy(true)
    const headers = { 'Content-Type': 'application/json', ...(auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {}) }
    try {
      const orderResponse = await fetch(`/api/intents/${activeIntent.intentId}/shopping-runs/${shoppingRun.runId}/revisions/${shoppingProposal.revision.revisionId}/orders`, { method: 'POST', headers })
      if (!orderResponse.ok) throw new Error('The reviewed quote or current intent policy changed. Requote and review the current cart.')
      const order = await orderResponse.json() as PayPalOrder
      await loadPayPalV6(integrations.paypalClientId)
      if (!window.paypal) throw new Error('PayPal checkout SDK is unavailable.')
      const paypal = await window.paypal.createInstance({ clientId: integrations.paypalClientId, components: ['paypal-payments'], pageType: 'checkout' })
      const session = paypal.createPayPalOneTimePaymentSession({
        onApprove: async (approval) => {
          if (approval.orderId !== order.orderId) throw new Error('PayPal approved a different order.')
          const captureResponse = await fetch(`/api/intents/${activeIntent.intentId}/orders/${order.orderId}/capture`, { method: 'POST', headers })
          if (!captureResponse.ok) throw new Error('Server-side capture was rejected by current authorization checks.')
          setPaymentCapture(await captureResponse.json() as PayPalCapture)
          await refreshIntentActivity(activeIntent.intentId)
          await loadReceiptMetrics(activeIntent.intentId)
          await loadShoppingHistory()
          setNotice('PayPal capture returned. The verified webhook will finalize the receipt.')
          setNoticeKind('success')
        },
        onCancel: () => { setNotice('PayPal checkout was cancelled. No capture was requested.'); setNoticeKind('error') },
        onError: () => { setNotice('PayPal checkout failed. No client-side capture was attempted.'); setNoticeKind('error') },
      })
      await session.start({ presentationMode: 'auto', orderId: order.orderId })
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Could not start PayPal checkout.')
      setNoticeKind('error')
    } finally {
      setCheckoutBusy(false)
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

  function loadPayPalV6(clientId: string) {
    if (window.paypal) return Promise.resolve()
    return new Promise<void>((resolve, reject) => {
      const existing = document.querySelector<HTMLScriptElement>('#paypal-v6-sdk')
      if (existing) {
        existing.addEventListener('load', () => resolve(), { once: true })
        existing.addEventListener('error', () => reject(new Error('PayPal checkout SDK could not be loaded.')), { once: true })
        return
      }
      const script = document.createElement('script')
      script.id = 'paypal-v6-sdk'
      script.src = 'https://www.sandbox.paypal.com/web-sdk/v6/core'
      script.async = true
      script.onload = () => window.paypal ? resolve() : reject(new Error('PayPal checkout SDK did not initialize.'))
      script.onerror = () => reject(new Error('PayPal checkout SDK could not be loaded.'))
      script.dataset.clientId = clientId
      document.head.append(script)
    })
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

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="Primary navigation">
        <a className="brand" href="#dashboard" aria-label="IntentOS home">
          <span className="brand-mark"><Fingerprint size={21} strokeWidth={1.8} /></span>
          <span>intent<span className="brand-light">os</span></span>
        </a>

        <div className="workspace-label">WORKSPACE</div>
        <nav className="nav-list">
          <a className="nav-item active" href="#dashboard"><LayoutDashboard size={17} />Overview</a>
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
          <div className="avatar">AS</div>
          <div className="profile-copy"><strong>{auth.authenticated ? auth.name : 'Alex Smith'}</strong><span>{auth.authenticated ? 'PayPal account' : 'Personal workspace'}</span></div>
          <button className="icon-button profile-menu" aria-label="Open account menu"><Plus size={16} /></button>
        </div>
      </aside>

      <main id="dashboard" className="main-content">
        <header className="topbar">
          <div className="breadcrumb"><span>Workspace</span><span className="breadcrumb-slash">/</span><strong>Overview</strong></div>
          <div className="topbar-actions">
            <div className={`backend-status ${health}`}><span className="status-dot" />API {health === 'checking' ? 'checking' : health === 'online' ? 'connected' : 'offline'}</div>
            {auth.authenticated ? <button className="auth-button" onClick={async () => { await fetch('/api/auth/logout', { method: 'POST', headers: auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {} }); setAuth({ authenticated: false, loginConfigured: auth.loginConfigured }) }}>Sign out</button> : auth.loginConfigured ? <a className="auth-button" href="/api/auth/paypal">Sign in with PayPal</a> : <span className="auth-unavailable">PayPal Login not configured</span>}
            <button className="icon-button search-button" aria-label="Search"><Search size={17} /></button>
            <div className="top-avatar">{auth.authenticated && auth.name ? auth.name.slice(0, 1).toUpperCase() : 'AS'}</div>
          </div>
        </header>

        <div className="page-content">
          <section className="page-heading">
            <div>
              <div className="eyebrow"><span className="eyebrow-rule" />{todayLabel}</div>
              <h1>Purchase control</h1>
              <p className="heading-copy">Your agents, operating within your intent.</p>
            </div>
            <button className="secondary-button" onClick={loadExample}><Sparkles size={16} />Load demo request</button>
          </section>

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
                  <div className="section-icon"><Fingerprint size={18} /></div>
                  <div><h2>New intent contract</h2><p>Set the goal and payment boundaries explicitly.</p></div>
                  <span className="draft-label">DRAFT</span>
                </div>
                <form onSubmit={saveIntent}>
                  <label className="sr-only" htmlFor="intent-request">Describe the purchase you want your agent to make</label>
                  <textarea id="intent-request" value={request} onChange={(event) => {
                    setRequest(event.target.value)
                    setGoal('')
                    setMaxAmount('')
                    setDraftCategories([])
                    setDraftRequiredAttributes({})
                    setExtractionNeedsClarification(false)
                    setAllowRecurringPayments(false)
                    setAllowSubscriptions(false)
                    setAllowUnrequestedAddons(false)
                  }} placeholder={'For example: “Find a development laptop under $1,000. No subscriptions.”'} rows={3} />
                  <div className="intent-fields">
                    <label className="form-field"><span>Goal</span><input value={goal} onChange={(event) => setGoal(event.target.value)} placeholder="Development laptop" required /></label>
                    <label className="form-field budget-field"><span>Maximum total <small>USD</small></span><input type="number" min="0.01" step="0.01" value={maxAmount} onChange={(event) => setMaxAmount(event.target.value)} placeholder="1,000.00" required /></label>
                  </div>
                  <div className="quantity-fields">
                    <label className="form-field"><span>Limit applies to item</span><input value={limitedItemName} onChange={(event) => setLimitedItemName(event.target.value)} placeholder="Development laptop" /></label>
                    <label className="form-field"><span>Maximum quantity</span><input type="number" min="1" step="1" value={itemQuantityLimit} onChange={(event) => setItemQuantityLimit(event.target.value)} placeholder="1" /></label>
                  </div>
                  <fieldset className="policy-toggles">
                    <legend>Payment permissions</legend>
                    <label><input type="checkbox" checked={allowRecurringPayments} onChange={(event) => setAllowRecurringPayments(event.target.checked)} /><span>Recurring payments</span></label>
                    <label><input type="checkbox" checked={allowSubscriptions} onChange={(event) => setAllowSubscriptions(event.target.checked)} /><span>Subscriptions</span></label>
                    <label><input type="checkbox" checked={allowUnrequestedAddons} onChange={(event) => setAllowUnrequestedAddons(event.target.checked)} /><span>Unrequested add-ons</span></label>
                  </fieldset>
                  <div className="composer-footer">
                    <span className="composer-hint"><LockKeyhole size={13} />Stored in server memory for this session</span>
                    <button className="secondary-button" type="button" onClick={extractIntent} disabled={!request.trim() || extracting}><Sparkles size={15} />{extracting ? 'Drafting…' : 'Draft from request'}</button>
                    <button className="primary-button" type="submit" disabled={!request.trim() || !goal.trim() || !maxAmount || saving || extractionNeedsClarification}><Plus size={16} />{saving ? 'Saving contract…' : 'Create intent contract'}</button>
                  </div>
                </form>
                {notice && <p className={`notice ${noticeKind}`} role="status"><Check size={14} />{notice}</p>}
                {activeIntent && <article className="saved-contract"><div className="draft-row"><div className="draft-status"><ShieldCheck size={15} /></div><div className="draft-copy"><p>{activeIntent.goal}</p><span>{activeIntent.intentId} · Intent policy</span></div><strong className="contract-budget">${activeIntent.maxAmount.toFixed(2)}</strong></div></article>}
                {paymentCapture && <div className="capture-confirmation" role="status"><strong>PayPal returned {paymentCapture.status}</strong><span>{paymentCapture.captureId} · {paymentCapture.currency} {paymentCapture.amount.toFixed(2)}</span></div>}
              </section>

              <section className="activity-section shopping-section" id="shopping-run">
                <div className="section-heading activity-heading">
                  <div><h2>Nova shopping run</h2><p>Candidate selection from the simulated merchant catalog</p></div>
                  <button className="text-button" onClick={startShoppingRun} disabled={!activeIntent || shoppingBusy}><Plus size={14} />New run</button>
                </div>
                <div className="simulator-disclosure">Simulated merchant catalog and quotes. Real PayPal Sandbox payment lifecycle. No physical order or retailer settlement.</div>
                {!activeIntent && <div className="catalog-empty">Create or reopen an intent to start a shopping run.</div>}
                {activeIntent && <>
                  <div className="shopping-destination">
                    <label className="form-field"><span>Country</span><input value={shoppingCountry} onChange={(event) => setShoppingCountry(event.target.value.toUpperCase())} maxLength={2} placeholder="US" /></label>
                    <label className="form-field"><span>Region</span><input value={shoppingRegion} onChange={(event) => setShoppingRegion(event.target.value.toUpperCase())} maxLength={8} placeholder="CA" /></label>
                    <label className="form-field"><span>Shipping method</span><input value={shoppingShipping} onChange={(event) => setShoppingShipping(event.target.value)} placeholder="standard" /></label>
                  </div>
                  <div className="shopping-run-bar">
                    <span>{shoppingRun ? `${shoppingRun.runId} · ${new Date(shoppingRun.createdAt).toLocaleString()}` : 'No active run'}</span>
                    <button className="primary-button" onClick={proposeShopping} disabled={!shoppingRun || !shoppingCountry.trim() || !shoppingRegion.trim() || !shoppingShipping.trim() || shoppingBusy}>
                      <Sparkles size={14} />{shoppingBusy ? 'Searching and evaluating…' : 'Find and evaluate cart'}
                    </button>
                  </div>
                </>}
                {shoppingProposal && <div className={`shopping-result ${shoppingProposal.validation?.decision === 'APPROVE' ? 'approved' : shoppingProposal.validation?.decision === 'BLOCK' ? 'blocked' : 'review'}`}>
                  <div className="repair-result-heading"><strong>{shoppingProposal.validation?.decision ?? shoppingProposal.revision?.validationDecision ?? shoppingProposal.quote?.status ?? 'No selection'}</strong><span>{shoppingProposal.revision?.revisionId ?? shoppingProposal.run.runId}</span></div>
                  <p>{shoppingProposal.outcome}</p>
                  {shoppingProposal.quote && <>
                    <p className="simulator-disclosure">{shoppingProposal.quote.disclosure}</p>
                    {shoppingProposal.proposal && <div className="repair-items">{shoppingProposal.proposal.items.map((item) => <div key={`${item.name}-${item.category}`}><span>{item.quantity} × {item.name}</span><strong>{shoppingProposal.quote?.currency} {(item.unitPrice * item.quantity).toFixed(2)}</strong></div>)}
                      <div><span>Shipping</span><strong>{shoppingProposal.quote.currency} {shoppingProposal.quote.shipping?.toFixed(2) ?? 'Unknown'}</strong></div>
                      <div><span>Tax</span><strong>{shoppingProposal.quote.currency} {shoppingProposal.quote.tax?.toFixed(2) ?? 'Unknown'}</strong></div>
                      <div className="repair-total"><span>Quoted total</span><strong>{shoppingProposal.quote.currency} {shoppingProposal.quote.total?.toFixed(2) ?? 'HOLD'}</strong></div>
                    </div>}
                    {shoppingProposal.quote.missingEvidence.length > 0 && <p>Missing evidence: {shoppingProposal.quote.missingEvidence.join(', ')}</p>}
                  </>}
                  {shoppingProposal.revision?.violationTypes.length ? <p>Policy findings: {shoppingProposal.revision.violationTypes.join(', ')}</p> : null}
                  {shoppingProposal.revision && shoppingProposal.validation?.decision !== 'APPROVE' && <button className="secondary-button" onClick={repairShopping} disabled={shoppingBusy}><ShieldCheck size={14} />{shoppingBusy ? 'Repairing…' : 'Attempt safe repair'}</button>}
                  {shoppingProposal.revision && shoppingProposal.validation?.decision === 'APPROVE' && !shoppingReviewId && <button className="secondary-button" onClick={reviewShoppingRevision} disabled={shoppingBusy}><Check size={14} />Review this exact cart</button>}
                  {shoppingReviewId && <div className="sandbox-checkout-note"><strong>Cart review recorded</strong><span>Review {shoppingReviewId} is bound to {shoppingProposal.revision?.revisionId} and its quote.</span><button className="primary-button checkout-button" onClick={checkoutShoppingRevision} disabled={checkoutBusy || !integrations?.paypalClientId || !auth.authenticated}>{checkoutBusy ? 'Opening PayPal…' : !auth.authenticated ? 'Sign in to continue' : 'Continue to PayPal approval'}</button></div>}
                </div>}
              </section>

              <section className="activity-section" id="activity">
                <div className="section-heading activity-heading">
                  <div><h2>Recent activity</h2><p>Decision events across your agents</p></div>
                  <button className="text-button" onClick={explainDecision} disabled={!activeIntent || activityEvents.length === 0 || explanationLoading}><Sparkles size={14} />{explanationLoading ? 'Explaining…' : 'Explain decision'}</button>
                </div>
                {activityEvents.length === 0 ? <div className="empty-activity"><div className="empty-glyph"><Activity size={20} /></div><strong>No agent activity yet</strong><span>Validated proposals and repair decisions will appear here.</span></div> : <div className="event-list">{activityEvents.map((event) => <article className="event-row" key={event.eventId}><div className={`event-indicator ${event.decision.toLowerCase()}`}><ShieldCheck size={14} /></div><div className="event-copy"><strong>{event.eventType.replaceAll('_', ' ')} · {event.decision}</strong><span>{event.proposalId} · ${event.calculatedTotal.toFixed(2)} · {new Date(event.occurredAt).toLocaleTimeString()}</span><small>{event.message}</small>{event.violations.length > 0 && <small>{event.violations.map((violation) => violation.type.replaceAll('_', ' ')).join(' · ')}</small>}</div></article>)}</div>}
                {modelExplanation && <p className="model-explanation" role="status"><strong>Evidence-based explanation</strong>{modelExplanation}</p>}
              </section>

              <section className="activity-section history-section" id="shopping-history">
                <div className="section-heading activity-heading"><div><h2>Shopping history</h2><p>Decision runs, including blocked and review-required proposals</p></div><button className="text-button" onClick={() => void loadShoppingHistory()} disabled={!auth.authenticated}><RefreshCw size={14} />Refresh</button></div>
                <form className="history-filters" onSubmit={(event) => { event.preventDefault(); void loadShoppingHistory() }}>
                  <label className="form-field"><span>Search</span><input value={historyQuery} onChange={(event) => setHistoryQuery(event.target.value)} placeholder="Goal or product ID" /></label>
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
                  <input id="receipt-query" value={receiptQuery} onChange={(event) => setReceiptQuery(event.target.value)} placeholder="Search product, violation, or PayPal ID" />
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
                {receiptDetail && <div className="receipt-detail" aria-label="AgentReceipt detail"><div className="repair-result-heading"><strong>{receiptDetail.receiptId} · {receiptDetail.finalDecision}</strong><button className="icon-button" aria-label="Close receipt detail" onClick={() => setReceiptDetail(null)}><X size={14} /></button></div><p>{receiptDetail.runId ? `Run ${receiptDetail.runId} · revision ${receiptDetail.revisionId ?? 'legacy attribution unavailable'}` : 'Legacy receipt · run attribution unavailable'}</p><div className="repair-items">{receiptDetail.proposalHistory.map((proposal) => <div key={proposal.proposalId}><strong>{proposal.proposalId} · {proposal.currency} {proposal.total.toFixed(2)}</strong>{proposal.items.map((item, index) => <span key={`${item.name}-${index}`}>{item.quantity} × {item.name} · {proposal.currency} {item.unitPrice.toFixed(2)} each</span>)}</div>)}<div className="repair-total"><span>Captured by PayPal Sandbox</span><strong>{receiptDetail.currency} {receiptDetail.amount.toFixed(2)}</strong></div></div>{receiptDetail.violations.length > 0 && <p>Recorded violations: {receiptDetail.violations.map((violation) => `${violation.type}${violation.item ? ` (${violation.item})` : ''}`).join(', ')}</p>}<div className="receipt-timeline">{receiptDetail.activityTimeline.map((event) => <article key={event.eventId}><strong>{event.eventType.replaceAll('_', ' ')} · {event.decision}</strong><span>{event.revisionId ?? event.proposalId}{event.calculatedTotal === null ? '' : ` · ${receiptDetail.currency} ${event.calculatedTotal.toFixed(2)}`}</span><small>{event.message}</small></article>)}</div></div>}
                <form className="receipt-analyst" onSubmit={askReceiptAnalyst}>
                  <label className="form-field" htmlFor="receipt-analyst-question"><span>Ask the Receipt Analyst</span><input id="receipt-analyst-question" value={analystQuestion} onChange={(event) => setAnalystQuestion(event.target.value)} placeholder="Why was this purchase approved?" maxLength={500} /></label>
                  <button className="primary-button" type="submit" disabled={!activeIntent || !analystQuestion.trim() || analystLoading}><Sparkles size={14} />{analystLoading ? 'Reviewing evidence…' : 'Ask'}</button>
                </form>
                {analystResult && <div className="analyst-answer" role="status"><strong>Receipt Analyst · {analystResult.evidenceCount} receipts · {analystResult.eventIds.length} decision events</strong><p>{analystResult.answer}</p><small>{analystResult.retrievalMode} retrieval · {analystResult.narrationMode} · Evidence: {[...analystResult.receiptIds, ...analystResult.runIds, ...analystResult.revisionIds, ...analystResult.eventIds].join(', ') || 'none'}</small></div>}
              </section>

            </div>

            <aside className="secondary-column">
              <section className="guardrail-panel">
                <div className="panel-heading"><div><span className="panel-kicker">POLICY STATUS</span><h2>Guardrails</h2></div><ShieldCheck size={19} /></div>
                <div className="guardrail-row"><span className="guardrail-symbol"><CircleDollarSign size={16} /></span><div><strong>Maximum spend</strong><span>{activeIntent ? `$${activeIntent.maxAmount.toFixed(2)} USD` : 'Not configured'}</span></div><span className="neutral-pill">{activeIntent ? 'Set' : 'Unset'}</span></div>
                <div className="guardrail-row"><span className="guardrail-symbol coral-symbol"><WalletCards size={16} /></span><div><strong>Recurring charges</strong><span>{activeIntent ? (activeIntent.allowRecurringPayments ? 'Allowed' : 'Prohibited') : 'Not configured'}</span></div><span className="neutral-pill">{activeIntent ? 'Set' : 'Unset'}</span></div>
                <div className="guardrail-row"><span className="guardrail-symbol green-symbol"><X size={16} /></span><div><strong>Unrequested add-ons</strong><span>{activeIntent ? (activeIntent.allowUnrequestedAddons ? 'Allowed' : 'Prohibited') : 'Not configured'}</span></div><span className="neutral-pill">{activeIntent ? 'Set' : 'Unset'}</span></div>
                <button className="panel-link" disabled>Manage intent policies <ArrowUpRight size={15} /></button>
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
