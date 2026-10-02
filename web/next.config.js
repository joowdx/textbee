/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: 'standalone',

  async redirects() {
    const redirects = [
      {
        source: '/',
        destination: '/dashboard',
        permanent: true,
      },
      // Sign-ups are closed on this instance; the API refuses addresses not on
      // ALLOWED_EMAILS, and the page sends people to sign in instead.
      {
        source: '/register',
        destination: '/login',
        permanent: false,
      },
      {
        source: '/android',
        destination: `https://github.com/${process.env.NEXT_PUBLIC_RELEASES_REPO || 'joowdx/textbee'}/releases/latest`,
        permanent: false,
      },
    ]

    // Included only when NEXT_PUBLIC_COMMUNITY_URL is set, so the invite can
    // rotate in one place.
    if (process.env.NEXT_PUBLIC_COMMUNITY_URL) {
      redirects.push({
        source: '/discord',
        destination: process.env.NEXT_PUBLIC_COMMUNITY_URL,
        permanent: false,
      })
    }

    return redirects
  },
}



module.exports = nextConfig;
