const POLAR_CUSTOMER_PORTAL_REQUEST_BASE =
  'https://polar.sh/textbee/portal/request'

export function polarCustomerPortalRequestUrl(
  email?: string | null
): string {
  const trimmed = email?.trim()
  if (!trimmed) return POLAR_CUSTOMER_PORTAL_REQUEST_BASE
  return `${POLAR_CUSTOMER_PORTAL_REQUEST_BASE}?email=${encodeURIComponent(trimmed)}`
}

export function smsPermissionGuideUrl(source: string): string {
  const guide = process.env.NEXT_PUBLIC_SMS_PERMISSION_GUIDE_URL
  if (!guide) return ''
  return `${guide}?utm_source=${source}&utm_medium=app&utm_campaign=sms_permission`
}

export const ExternalLinks = {
  patreon: '',
  github: `https://github.com/${process.env.NEXT_PUBLIC_RELEASES_REPO || 'joowdx/textbee'}`,
  discord: process.env.NEXT_PUBLIC_COMMUNITY_URL || '',
  polar: '',
  twitter: '',
  linkedin: '',
}
