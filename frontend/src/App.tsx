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

type RepairResult = {
  status: 'APPROVED' | 'NEEDS_USER_REVIEW'
  attempts: number
  proposal: {
    proposalId: string
    intentId: string
    currency: string
    total: number
    shipping: number
    tax: number
    offerEvidence?: OfferEvidence[]
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
  explanation: string
  modelAdvice?: string | null
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
  paypalCatalogConfigured: boolean
  channel3Configured: boolean
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
  webhookEventId: string
  createdAt: string
  proposalHistory: RepairResult['proposal'][]
  violations: Array<{ type: string; item: string | null; expected: string; actual: string | null }>
  repairs: DecisionEvent[]
  activityTimeline: DecisionEvent[]
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
}

type ReceiptAnalystResult = { answer: string; receiptIds: string[]; evidenceCount: number }

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

type CatalogProduct = {
  id: string
  name: string
  description?: string
}

type OfferEvidence = {
  source: string
  productId: string
  productName: string
  category: string
  retailer: string
  offerUrl: string
  price: number
  currency: string
  availability: string
  attributes: Record<string, string>
  retrievedAt: string
}

type Channel3Offer = {
  productId: string
  productName: string
  description: string
  category: string
  attributes: Record<string, string>
  retailer: string
  offerUrl: string
  price: number
  currency: string
  availability: string
  condition: string
  retrievedAt: string
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
  calculatedTotal: number
  violations: Array<{ type: string; item: string | null; expected: string; actual: string | null }>
  recommendations: string[]
}

type DecisionEvent = {
  eventId: string
  proposalId: string
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
  const [health, setHealth] = useState<'checking' | 'online' | 'offline'>('checking')
  const [auth, setAuth] = useState<AuthSession>({ authenticated: false, loginConfigured: false })
  const [integrations, setIntegrations] = useState<IntegrationStatus | null>(null)
  const [catalogProducts, setCatalogProducts] = useState<CatalogProduct[]>([])
  const [catalogLoading, setCatalogLoading] = useState(false)
  const [catalogMessage, setCatalogMessage] = useState('')
  const [channel3Offers, setChannel3Offers] = useState<Channel3Offer[]>([])
  const [channel3Loading, setChannel3Loading] = useState(false)
  const [channel3Message, setChannel3Message] = useState('')
  const [selectedOffer, setSelectedOffer] = useState<Channel3Offer | null>(null)
  const [offerTax, setOfferTax] = useState('')
  const [offerShipping, setOfferShipping] = useState('')
  const [selectedOfferProposal, setSelectedOfferProposal] = useState<RepairResult['proposal'] | null>(null)
  const [offerPolicyCheck, setOfferPolicyCheck] = useState<PolicyCheck | null>(null)
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
  const [saving, setSaving] = useState(false)
  const [repairing, setRepairing] = useState(false)
  const [repairResult, setRepairResult] = useState<RepairResult | null>(null)
  const [initialPolicyCheck, setInitialPolicyCheck] = useState<PolicyCheck | null>(null)
  const [finalPolicyCheck, setFinalPolicyCheck] = useState<PolicyCheck | null>(null)
  const [activityEvents, setActivityEvents] = useState<DecisionEvent[]>([])
  const [checkoutBusy, setCheckoutBusy] = useState(false)
  const [paymentCapture, setPaymentCapture] = useState<PayPalCapture | null>(null)
  const [agentReceipts, setAgentReceipts] = useState<AgentReceipt[]>([])
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
      void loadReceiptMetrics(createdIntent.intentId)
      setRepairResult(null)
      setChannel3Offers([])
      setChannel3Message('')
      setSelectedOffer(null)
      setSelectedOfferProposal(null)
      setOfferPolicyCheck(null)
      setOfferTax('')
      setOfferShipping('')
      setInitialPolicyCheck(null)
      setFinalPolicyCheck(null)
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

  async function searchOffers() {
    if (!activeIntent) return
    setChannel3Loading(true)
    setChannel3Message('')
    setChannel3Offers([])
    setSelectedOffer(null)
    setSelectedOfferProposal(null)
    setOfferPolicyCheck(null)
    try {
      const response = await fetch(`/api/intents/${activeIntent.intentId}/offers`)
      if (!response.ok) throw new Error(response.status === 401
        ? 'Sign in with PayPal to search product offers.'
        : response.status === 503
          ? 'Channel3 offer search is not configured on the backend.'
          : 'Channel3 offer search failed.')
      const offers = await response.json() as Channel3Offer[]
      setChannel3Offers(offers)
      setChannel3Message(offers.length
        ? `${offers.length} in-stock offer${offers.length === 1 ? '' : 's'} returned. Prices can change; the selected offer will be checked again before checkout.`
        : 'No matching in-stock offers were found.')
    } catch (error) {
      setChannel3Message(error instanceof Error ? error.message : 'Could not search Channel3 offers.')
    } finally {
      setChannel3Loading(false)
    }
  }

  function chooseOffer(offer: Channel3Offer) {
    setSelectedOffer(offer)
    setOfferTax('')
    setOfferShipping('')
    setSelectedOfferProposal(null)
    setOfferPolicyCheck(null)
    setPaymentCapture(null)
  }

  async function validateSelectedOffer() {
    if (!activeIntent || !selectedOffer) return
    if (offerTax.trim() === '' || offerShipping.trim() === '') {
      setNotice('Enter the confirmed tax and shipping amounts. Use 0 only when you have verified there is no charge.')
      setNoticeKind('error')
      return
    }
    const tax = Number(offerTax)
    const shipping = Number(offerShipping)
    if (!Number.isFinite(tax) || !Number.isFinite(shipping) || tax < 0 || shipping < 0) {
      setNotice('Tax and shipping must be confirmed amounts of zero or more.')
      setNoticeKind('error')
      return
    }
    const proposal: RepairResult['proposal'] = {
      proposalId: `PROP-${crypto.randomUUID()}`,
      intentId: activeIntent.intentId,
      currency: selectedOffer.currency,
      items: [{
        name: selectedOffer.productName,
        category: selectedOffer.category,
        quantity: 1,
        unitPrice: selectedOffer.price,
        recurring: false,
        subscription: false,
        addOn: false,
        merchant: selectedOffer.retailer,
        attributes: selectedOffer.attributes,
      }],
      shipping,
      tax,
      total: selectedOffer.price + tax + shipping,
      offerEvidence: [{
        source: 'channel3',
        productId: selectedOffer.productId,
        productName: selectedOffer.productName,
        category: selectedOffer.category,
        retailer: selectedOffer.retailer,
        offerUrl: selectedOffer.offerUrl,
        price: selectedOffer.price,
        currency: selectedOffer.currency,
        availability: selectedOffer.availability,
        attributes: selectedOffer.attributes,
        retrievedAt: selectedOffer.retrievedAt,
      }],
    }
    setSelectedOfferProposal(proposal)
    setOfferPolicyCheck(null)
    setNotice('')
    const headers = { 'Content-Type': 'application/json', ...(auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {}) }
    try {
      const response = await fetch(`/api/intents/${activeIntent.intentId}/validation`, {
        method: 'POST', headers, body: JSON.stringify(proposal),
      })
      if (!response.ok) throw new Error('Could not validate this offer against the intent.')
      const result = await response.json() as PolicyCheck
      setOfferPolicyCheck(result)
      await refreshIntentActivity(activeIntent.intentId)
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Offer validation failed.')
      setNoticeKind('error')
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

  async function startCheckout() {
    if (!activeIntent || !selectedOfferProposal || offerPolicyCheck?.decision !== 'APPROVE' || !integrations?.paypalClientId) return
    setCheckoutBusy(true)
    setPaymentCapture(null)
    setNotice('')
    const headers = { 'Content-Type': 'application/json', ...(auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {}) }
    try {
      const orderResponse = await fetch(`/api/intents/${activeIntent.intentId}/orders`, {
        method: 'POST', headers, body: JSON.stringify(selectedOfferProposal),
      })
      if (!orderResponse.ok) throw new Error(orderResponse.status === 409
        ? 'The offer changed or the proposal no longer passes policy validation. Search again, review the current price, and revalidate.'
        : 'PayPal order creation failed.')
      const order = await orderResponse.json() as PayPalOrder
      await loadPayPalV6(integrations.paypalClientId)
      if (!window.paypal) throw new Error('PayPal checkout SDK is unavailable.')
      const paypal = await window.paypal.createInstance({
        clientId: integrations.paypalClientId,
        components: ['paypal-payments'],
        pageType: 'checkout',
      })
      const session = paypal.createPayPalOneTimePaymentSession({
        onApprove: async (approval) => {
          if (approval.orderId !== order.orderId) throw new Error('PayPal approved a different order than the validated checkout.')
          const captureResponse = await fetch(`/api/intents/${activeIntent.intentId}/orders/${order.orderId}/capture`, {
            method: 'POST', headers,
          })
          if (!captureResponse.ok) throw new Error('Server-side PayPal capture failed after buyer approval.')
          setPaymentCapture(await captureResponse.json() as PayPalCapture)
          await refreshIntentActivity(activeIntent.intentId)
          await searchReceipts()
          await loadReceiptMetrics(activeIntent.intentId)
          setNotice('Payment capture returned. The verified PayPal webhook will finalize the AgentReceipt.')
          setNoticeKind('success')
        },
        onCancel: () => {
          setNotice('PayPal checkout was cancelled. No capture was requested.')
          setNoticeKind('error')
        },
        onError: () => {
          setNotice('PayPal checkout could not be completed. No client-side capture was attempted.')
          setNoticeKind('error')
        },
      })
      await session.start({ presentationMode: 'auto', orderId: order.orderId })
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Could not start PayPal checkout.')
      setNoticeKind('error')
    } finally {
      setCheckoutBusy(false)
    }
  }

  async function runRepair() {
    if (!activeIntent) return
    setRepairing(true)
    setNotice('')
    setRepairResult(null)
    setInitialPolicyCheck(null)
    setFinalPolicyCheck(null)
    try {
      const proposal = {
        proposalId: `PROP-${crypto.randomUUID()}`,
        intentId: activeIntent.intentId,
        currency: activeIntent.currency,
        items: [
          { name: 'Development laptop', category: 'laptop', quantity: 1, unitPrice: 899, attributes: { ram: '32GB' } },
          { name: 'Extended warranty', category: 'laptop', quantity: 1, unitPrice: 199, addOn: true },
          { name: 'Antivirus subscription', category: 'laptop', quantity: 1, unitPrice: 79, recurring: true, subscription: true, addOn: true },
        ],
        shipping: 0,
        tax: 72,
        total: 1249,
      }
      const headers = { 'Content-Type': 'application/json', ...(auth.csrfToken ? { 'X-CSRF-Token': auth.csrfToken } : {}) }
      const initialResponse = await fetch(`/api/intents/${activeIntent.intentId}/validation`, {
        method: 'POST',
        headers,
        body: JSON.stringify(proposal),
      })
      if (!initialResponse.ok) throw new Error('The policy check failed. Check the API and try again.')
      const initialCheck = await initialResponse.json() as PolicyCheck
      setInitialPolicyCheck(initialCheck)
      await refreshIntentActivity(activeIntent.intentId)
      if (initialCheck.decision === 'HOLD') {
        setNotice('This intent needs clarification before its proposal can be repaired.')
        setNoticeKind('error')
        return
      }

      const response = await fetch(`/api/intents/${activeIntent.intentId}/repair`, {
        method: 'POST',
        headers,
        body: JSON.stringify(proposal),
      })
      if (!response.ok) throw new Error('The repair request failed. Check the API and try again.')
      const result = await response.json() as RepairResult
      setRepairResult(result)

      const finalResponse = await fetch(`/api/intents/${activeIntent.intentId}/validation`, {
        method: 'POST',
        headers,
        body: JSON.stringify(result.proposal),
      })
      if (!finalResponse.ok) throw new Error('The repaired proposal could not be revalidated.')
      setFinalPolicyCheck(await finalResponse.json() as PolicyCheck)
      await refreshIntentActivity(activeIntent.intentId)
      const eventsResponse = await fetch(`/api/intents/${activeIntent.intentId}/events`)
      if (eventsResponse.ok) setActivityEvents(await eventsResponse.json() as DecisionEvent[])
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Could not run proposal repair.')
      setNoticeKind('error')
    } finally {
      setRepairing(false)
    }
  }

  async function loadCatalog() {
    setCatalogLoading(true)
    setCatalogMessage('')
    try {
      const response = await fetch('/api/catalog/products?page=1&pageSize=20')
      if (!response.ok) throw new Error(response.status === 401 ? 'Sign in with PayPal to browse the catalog.' : 'PayPal Catalog request failed. Check Sandbox access.')
      const result = await response.json()
      setCatalogProducts(Array.isArray(result.products) ? result.products : [])
      setCatalogMessage(Array.isArray(result.products) && result.products.length
        ? `${result.products.length} product records loaded.`
        : 'No products are listed in this PayPal catalog yet.')
    } catch (error) {
      setCatalogMessage(error instanceof Error ? error.message : 'Could not load the catalog.')
    } finally {
      setCatalogLoading(false)
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
          <a className="nav-item" href="#receipts"><ReceiptText size={17} />Agent receipts</a>
          <a className="nav-item" href="#activity"><Activity size={17} />Agent activity</a>
          <a className="nav-item" href="#offers"><Search size={17} />Retailer offers</a>
          <a className="nav-item" href="#catalog"><ShoppingBag size={17} />PayPal catalog</a>
        </nav>

        <div className="sidebar-spacer" />
        <div className="sandbox-card">
          <div className="sandbox-icon"><LockKeyhole size={16} /></div>
          <div><strong>Sandbox mode</strong><span>Payments are simulated</span></div>
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
              <div className="eyebrow"><span className="eyebrow-rule" />THURSDAY, OCTOBER 8, 2026</div>
              <h1>Purchase control</h1>
              <p className="heading-copy">Your agents, operating within your intent.</p>
            </div>
            <button className="secondary-button" onClick={loadExample}><Sparkles size={16} />Load demo request</button>
          </section>

          <section className="metrics" aria-label="Purchase summary">
            <div className="metric-cell"><div className="metric-icon ink"><ShoppingBag size={17} /></div><div className="metric-label">Approved proposals</div><div className="metric-value">{receiptMetrics?.approvedProposals ?? 0} <span>of {receiptMetrics?.totalProposals ?? 0}</span></div></div>
            <div className="metric-cell"><div className="metric-icon coral"><ShieldCheck size={17} /></div><div className="metric-label">Violating proposals</div><div className="metric-value">{receiptMetrics?.violatingProposals ?? 0} <span>detected</span></div></div>
            <div className="metric-cell"><div className="metric-icon green"><CircleDollarSign size={17} /></div><div className="metric-label">Spend prevented</div><div className="metric-value">${(receiptMetrics?.preventedAmount ?? 0).toFixed(2)}</div></div>
            <div className="metric-cell"><div className="metric-icon blue"><FileCheck2 size={17} /></div><div className="metric-label">Intent compliance</div><div className="metric-value">{receiptMetrics ? `${(receiptMetrics.intentComplianceRate * 100).toFixed(0)}%` : '—'} <span>{receiptMetrics ? `${receiptMetrics.totalProposals} proposals` : 'no decisions yet'}</span></div></div>
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
                {activeIntent && <article className="saved-contract"><div className="draft-row"><div className="draft-status"><ShieldCheck size={15} /></div><div className="draft-copy"><p>{activeIntent.goal}</p><span>{activeIntent.intentId} · In-memory contract</span></div><strong className="contract-budget">${activeIntent.maxAmount.toFixed(2)}</strong></div><button className="repair-button" onClick={runRepair} disabled={repairing}><ShieldCheck size={14} />{repairing ? 'Checking and repairing…' : 'Run sample proposal repair'}</button></article>}
                {initialPolicyCheck && <div className={`validation-result ${initialPolicyCheck.decision === 'APPROVE' ? 'approved' : initialPolicyCheck.decision === 'HOLD' ? 'review' : 'blocked'}`} role="status"><div className="repair-result-heading"><strong>Initial validation · {initialPolicyCheck.decision}</strong><span>Calculated ${initialPolicyCheck.calculatedTotal.toFixed(2)}</span></div>{initialPolicyCheck.violations.length > 0 ? <ul>{initialPolicyCheck.violations.map((violation, index) => <li key={`${violation.type}-${violation.item ?? index}`}><strong>{violation.type.replaceAll('_', ' ')}</strong>{violation.item ? ` · ${violation.item}` : ''}<span>Expected {violation.expected}; got {violation.actual ?? 'missing'}</span></li>)}</ul> : <p>No policy violations.</p>}</div>}
                {repairResult && <div className={`repair-result ${repairResult.status === 'APPROVED' ? 'approved' : 'review'}`} role="status"><div className="repair-result-heading"><strong>{repairResult.status === 'APPROVED' ? 'Proposal repaired' : 'User review required'}</strong><span>{repairResult.attempts} repair {repairResult.attempts === 1 ? 'pass' : 'passes'}</span></div><p>{repairResult.explanation}</p>{repairResult.modelAdvice && <p className="model-advice"><strong>Model suggestion · advisory</strong><span>{repairResult.modelAdvice}</span></p>}<div className="repair-items">{repairResult.proposal.items.map((item) => <div key={item.name}><span>{item.quantity} × {item.name}</span><strong>${(item.quantity * item.unitPrice).toFixed(2)}</strong></div>)}<div className="repair-total"><span>Recalculated total</span><strong>${repairResult.proposal.total.toFixed(2)}</strong></div></div><small>Sample proposal only. No Channel3 retailer checkout or PayPal payment was created.</small></div>}
                {finalPolicyCheck && <div className={`validation-result ${finalPolicyCheck.decision === 'APPROVE' ? 'approved' : finalPolicyCheck.decision === 'HOLD' ? 'review' : 'blocked'}`} role="status"><div className="repair-result-heading"><strong>Revalidation · {finalPolicyCheck.decision}</strong><span>Calculated ${finalPolicyCheck.calculatedTotal.toFixed(2)}</span></div>{finalPolicyCheck.violations.length > 0 ? <ul>{finalPolicyCheck.violations.map((violation, index) => <li key={`${violation.type}-${violation.item ?? index}`}><strong>{violation.type.replaceAll('_', ' ')}</strong>{violation.item ? ` · ${violation.item}` : ''}<span>Expected {violation.expected}; got {violation.actual ?? 'missing'}</span></li>)}</ul> : <p>No policy violations.</p>}</div>}
                {paymentCapture && <div className="capture-confirmation" role="status"><strong>PayPal returned {paymentCapture.status}</strong><span>{paymentCapture.captureId} · {paymentCapture.currency} {paymentCapture.amount.toFixed(2)}</span></div>}
              </section>

              <section className="activity-section" id="activity">
                <div className="section-heading activity-heading">
                  <div><h2>Recent activity</h2><p>Decision events across your agents</p></div>
                  <button className="text-button" onClick={explainDecision} disabled={!activeIntent || activityEvents.length === 0 || explanationLoading}><Sparkles size={14} />{explanationLoading ? 'Explaining…' : 'Explain decision'}</button>
                </div>
                {activityEvents.length === 0 ? <div className="empty-activity"><div className="empty-glyph"><Activity size={20} /></div><strong>No agent activity yet</strong><span>Validated proposals and repair decisions will appear here.</span></div> : <div className="event-list">{activityEvents.map((event) => <article className="event-row" key={event.eventId}><div className={`event-indicator ${event.decision.toLowerCase()}`}><ShieldCheck size={14} /></div><div className="event-copy"><strong>{event.eventType.replaceAll('_', ' ')} · {event.decision}</strong><span>{event.proposalId} · ${event.calculatedTotal.toFixed(2)} · {new Date(event.occurredAt).toLocaleTimeString()}</span><small>{event.message}</small>{event.violations.length > 0 && <small>{event.violations.map((violation) => violation.type.replaceAll('_', ' ')).join(' · ')}</small>}</div></article>)}</div>}
                {modelExplanation && <p className="model-explanation" role="status"><strong>Evidence-based explanation</strong>{modelExplanation}</p>}
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
                  : <div className="receipt-list">{agentReceipts.map((receipt) => <article className="receipt-row" key={receipt.receiptId}><div className="event-indicator approve"><ReceiptText size={14} /></div><div className="event-copy"><strong>{receipt.currency} {receipt.amount.toFixed(2)} · {receipt.captureStatus}</strong><span>{receipt.paypalCaptureId} · Order {receipt.paypalOrderId}</span><small>{receipt.proposalHistory.length} proposal snapshot{receipt.proposalHistory.length === 1 ? '' : 's'} · {receipt.repairs.length} repair event{receipt.repairs.length === 1 ? '' : 's'} · webhook {receipt.webhookEventId}</small><small>{new Date(receipt.createdAt).toLocaleString()}</small></div></article>)}</div>}
                <form className="receipt-analyst" onSubmit={askReceiptAnalyst}>
                  <label className="form-field" htmlFor="receipt-analyst-question"><span>Ask the Receipt Analyst</span><input id="receipt-analyst-question" value={analystQuestion} onChange={(event) => setAnalystQuestion(event.target.value)} placeholder="Why was this purchase approved?" maxLength={500} /></label>
                  <button className="primary-button" type="submit" disabled={!activeIntent || !analystQuestion.trim() || analystLoading}><Sparkles size={14} />{analystLoading ? 'Reviewing evidence…' : 'Ask'}</button>
                </form>
                {analystResult && <div className="analyst-answer" role="status"><strong>Receipt Analyst · {analystResult.evidenceCount} receipt{analystResult.evidenceCount === 1 ? '' : 's'} retrieved</strong><p>{analystResult.answer}</p><small>Evidence: {analystResult.receiptIds.join(', ')}</small></div>}
              </section>

              <section className="activity-section catalog-section" id="offers">
                <div className="section-heading activity-heading">
                  <div><h2>Retailer offers</h2><p>Channel3 results for the active intent · offers are rechecked before checkout</p></div>
                  <button className="text-button" onClick={searchOffers} disabled={!activeIntent || !integrations?.channel3Configured || channel3Loading} title={!integrations?.channel3Configured ? 'Configure CHANNEL3_API_KEY on the backend' : 'Search current retailer offers'}><Search size={14} />{channel3Loading ? 'Searching' : 'Search offers'}</button>
                </div>
                {!integrations?.channel3Configured && <div className="catalog-empty">Channel3 offer search is not configured on the backend.</div>}
                {channel3Message && <div className="catalog-empty" role="status">{channel3Message}</div>}
                {channel3Offers.length > 0 && <div className="offer-list">{channel3Offers.map((offer) => <article className={`offer-row ${selectedOffer?.offerUrl === offer.offerUrl ? 'selected' : ''}`} key={`${offer.productId}-${offer.offerUrl}`}>
                  <div className="offer-copy"><strong>{offer.productName}</strong><span>{offer.retailer} · {offer.availability} · {offer.condition}</span><small>{offer.description || offer.category || 'Product details as provided by Channel3'}</small><a href={offer.offerUrl} target="_blank" rel="noreferrer">View retailer offer <ArrowUpRight size={12} /></a><small>Offer retrieved {new Date(offer.retrievedAt).toLocaleString()}</small></div>
                  <div className="offer-price"><strong>{offer.currency} {offer.price.toFixed(2)}</strong><button className={selectedOffer?.offerUrl === offer.offerUrl ? 'secondary-button' : 'repair-button'} onClick={() => chooseOffer(offer)}>{selectedOffer?.offerUrl === offer.offerUrl ? 'Selected' : 'Select offer'}</button></div>
                </article>)}</div>}
                {selectedOffer && <div className="offer-checkout-panel">
                  <div className="offer-checkout-heading"><strong>Selected offer</strong><span>{selectedOffer.productName} · {selectedOffer.retailer} · {selectedOffer.currency} {selectedOffer.price.toFixed(2)}</span></div>
                  <p>Enter confirmed checkout costs from the retailer. Missing tax or shipping is not assumed to be zero.</p>
                  <div className="offer-cost-fields">
                    <label className="form-field"><span>Confirmed tax ({selectedOffer.currency})</span><input aria-label="Confirmed tax" type="number" min="0" step="0.01" value={offerTax} onChange={(event) => { setOfferTax(event.target.value); setSelectedOfferProposal(null); setOfferPolicyCheck(null) }} placeholder="Required" /></label>
                    <label className="form-field"><span>Confirmed shipping ({selectedOffer.currency})</span><input aria-label="Confirmed shipping" type="number" min="0" step="0.01" value={offerShipping} onChange={(event) => { setOfferShipping(event.target.value); setSelectedOfferProposal(null); setOfferPolicyCheck(null) }} placeholder="Required" /></label>
                  </div>
                  <button className="secondary-button" onClick={validateSelectedOffer} disabled={!offerTax.trim() || !offerShipping.trim()}>Validate selected offer</button>
                  {selectedOfferProposal && <div className="offer-total"><span>Server-calculated proposal total</span><strong>{selectedOffer.currency} {selectedOfferProposal.total.toFixed(2)}</strong></div>}
                  {offerPolicyCheck && <div className={`validation-result ${offerPolicyCheck.decision === 'APPROVE' ? 'approved' : offerPolicyCheck.decision === 'HOLD' ? 'review' : 'blocked'}`} role="status"><div className="repair-result-heading"><strong>Offer policy · {offerPolicyCheck.decision}</strong><span>Calculated {selectedOffer.currency} {offerPolicyCheck.calculatedTotal.toFixed(2)}</span></div>{offerPolicyCheck.violations.length ? <ul>{offerPolicyCheck.violations.map((violation, index) => <li key={`${violation.type}-${violation.item ?? index}`}><strong>{violation.type.replaceAll('_', ' ')}</strong><span>Expected {violation.expected}; got {violation.actual ?? 'missing'}</span></li>)}</ul> : <p>No policy violations.</p>}</div>}
                  {offerPolicyCheck?.decision === 'APPROVE' && <div className="sandbox-checkout-note"><strong>PayPal Sandbox demonstration</strong><span>This payment goes to the configured Sandbox merchant; it does not pay the Channel3 retailer.</span><button className="primary-button checkout-button" onClick={startCheckout} disabled={checkoutBusy || !integrations?.paypalClientId || !auth.authenticated}>{checkoutBusy ? 'Opening PayPal…' : !auth.authenticated ? 'Sign in to continue' : 'Continue to Sandbox checkout'}</button></div>}
                </div>}
              </section>

              <section className="activity-section catalog-section" id="catalog">
                <div className="section-heading activity-heading">
                  <div><h2>PayPal Catalog</h2><p>Read-only product records from your Sandbox app</p></div>
                  <button className="text-button" onClick={loadCatalog} disabled={!integrations?.paypalCatalogConfigured || catalogLoading} title={!integrations?.paypalCatalogConfigured ? 'Configure PayPal Sandbox credentials first' : 'Refresh product records'}><RefreshCw size={14} />{catalogLoading ? 'Loading' : 'Refresh'}</button>
                </div>
                {!integrations?.paypalCatalogConfigured && <div className="catalog-empty">Add PayPal Sandbox app credentials to `.env` to browse catalog metadata.</div>}
                {integrations?.paypalCatalogConfigured && catalogMessage && <div className="catalog-empty" role="status">{catalogMessage}</div>}
                {catalogProducts.length > 0 && <div className="catalog-list">{catalogProducts.map((product) => {
                  const isLaptop = /laptop|notebook/i.test(product.name)
                  return <article className="catalog-product" key={product.id}>
                    <div><strong>{product.name}</strong><p>{product.description || 'No description provided.'}</p><span>Product ID · {product.id}</span></div>
                    <small>{isLaptop ? <>DEMO ESTIMATE<br />$899.00 USD<br />Not an offer</> : <>Metadata only<br />No offer price</>}</small>
                  </article>
                })}</div>}
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
                <div className="service-row"><span className={`service-indicator ${integrations?.channel3Configured ? 'online' : 'pending'}`} /><span>Channel3 offers</span><span className={`service-state ${integrations?.channel3Configured ? '' : 'pending-text'}`}>{integrations ? integrations.channel3Configured ? 'Configured' : 'Not configured' : 'Checking'}</span></div>
                <div className="service-row"><span className={`service-indicator ${integrations?.elasticAgentBuilderConfigured ? 'online' : 'pending'}`} /><span>Receipt Analyst</span><span className={`service-state ${integrations?.elasticAgentBuilderConfigured ? '' : 'pending-text'}`}>{integrations ? integrations.elasticAgentBuilderConfigured ? 'Elastic' : 'Bedrock / off' : 'Checking'}</span></div>
                <div className="service-row"><span className={`service-indicator ${integrations?.paypalCatalogConfigured ? 'online' : 'pending'}`} /><span>PayPal Catalog</span><span className={`service-state ${integrations?.paypalCatalogConfigured ? '' : 'pending-text'}`}>{integrations ? integrations.paypalCatalogConfigured ? 'Configured' : 'Not configured' : 'Checking'}</span></div>
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
