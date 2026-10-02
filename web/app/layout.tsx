import { PropsWithChildren } from 'react'
import '@/styles/main.css'
import { Metadata } from 'next'
import { Geist_Mono, Inter } from 'next/font/google'
import ThemeProvider from './theme-provider'
import Analytics from '@/components/shared/analytics'
import AttributionCapture from '@/components/shared/attribution-capture'

const inter = Inter({
  subsets: ['latin'],
  variable: '--font-inter',
  display: 'swap',
})

const geistMono = Geist_Mono({
  subsets: ['latin'],
  variable: '--font-geist-mono',
  display: 'swap',
})

export const metadata: Metadata = {
  title: 'textbee - sms gateway - dashboard',

  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000'),
}

export default async function RootLayout({ children }: PropsWithChildren) {
  return (
    <html lang='en' suppressHydrationWarning className={`${inter.variable} ${geistMono.variable}`}>
      {/* No <main> here: the (app) layout renders its own, and nesting
          <main> inside <main> is invalid HTML. */}
      <body className='font-sans antialiased'>
        <ThemeProvider
          attribute='class'
          defaultTheme='system'
          enableSystem
          disableTransitionOnChange
        >
          {children}
        </ThemeProvider>
        <Analytics />
        <AttributionCapture />
      </body>
    </html>
  )
}
