/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: 'standalone',

  async redirects() {
    return [
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
        destination: 'https://dl.textbee.dev',
        permanent: false,
      },
      // The invite itself lives behind textbee.dev/discord, so it can rotate
      // in one place.
      {
        source: '/discord',
        destination: 'https://textbee.dev/discord',
        permanent: false,
      },
    ]
  },
}



module.exports = nextConfig;
