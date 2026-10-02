export const Routes = {
  landingPage: process.env.NEXT_PUBLIC_SITE_URL || '/',
  contribute: '',
  useCases: '',
  quickstart: process.env.NEXT_PUBLIC_DOCS_URL || '',
  login: '/login',
  register: '/register',
  logout: '/logout',
  resetPassword: '/reset-password',
  verifyEmail: '/verify-email',

  dashboard: '/dashboard',

  downloadAndroidApp: `https://github.com/${process.env.NEXT_PUBLIC_RELEASES_REPO || 'joowdx/textbee'}/releases/latest`,
  privacyPolicy: process.env.NEXT_PUBLIC_PRIVACY_URL || '',
  refundPolicy: process.env.NEXT_PUBLIC_REFUND_URL || '',
  termsOfService: process.env.NEXT_PUBLIC_TERMS_URL || '',
  statusPage: process.env.NEXT_PUBLIC_STATUS_URL || '',
  pricing: process.env.NEXT_PUBLIC_PRICING_URL || '',
}
