import { signLink } from './email-render'

const base = (value: string | undefined, fallback: string) =>
  (value || fallback).replace(/\/+$/, '')

export const apiPublicUrl = () =>
  base(process.env.API_PUBLIC_URL, 'http://localhost:3001')

/** The web app; FRONTEND_URL when APP_PUBLIC_URL is unset. */
export const appPublicUrl = () =>
  base(process.env.APP_PUBLIC_URL || process.env.FRONTEND_URL, 'http://localhost:3000')

export const logoUrl = () =>
  process.env.APP_LOGO_URL || `${appPublicUrl()}/images/logo.png`

/** Optional links and addresses. Empty hides what would show them. */
export const docsUrl = () => process.env.DOCS_URL || ''
export const communityUrl = () => process.env.COMMUNITY_URL || ''
export const pricingUrl = () => process.env.PRICING_URL || ''
export const supportEmail = () =>
  process.env.SUPPORT_EMAIL || process.env.MAIL_REPLY_TO || ''

/** "contact billing@…" when BILLING_EMAIL is set, else "contact support". */
export const contactBilling = () =>
  process.env.BILLING_EMAIL ? `contact ${process.env.BILLING_EMAIL}` : 'contact support'

export const emailLinkSecret = () => process.env.EMAIL_LINK_SECRET || ''

export const billingUrl = () => `${appPublicUrl()}/dashboard/account/billing`

export const upgradeUrl = () =>
  `${appPublicUrl()}/checkout/pro?billingInterval=monthly`

export const scaleUpgradeUrl = () =>
  `${appPublicUrl()}/checkout/scale?billingInterval=monthly`

/** Signed one-click link; without a secret it points at the account page. */
export const unsubscribeUrl = (userId: string): string => {
  const secret = emailLinkSecret()
  if (!secret) return `${appPublicUrl()}/dashboard/account`
  const token = signLink(secret, 'unsubscribe', userId, 0)
  return `${apiPublicUrl()}/api/v1/email/unsubscribe?t=${token}`
}
