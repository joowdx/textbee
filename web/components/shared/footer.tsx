import { Routes } from '@/config/routes'
import { ExternalLinks } from '@/config/external-links'
import { Activity } from 'lucide-react'
import Link from 'next/link'
import Image from 'next/image'
import { cn } from '@/lib/utils'

// A logged-in user is already converted, so the app footer stays a single slim
// bar rather than the marketing site's multi-column link farm. It borrows that
// footer's visual language (muted surface, muted-to-foreground link hovers,
// green status pill) so the two still read as one product.
const links = [
  { label: 'Quick start', href: Routes.quickstart },
  { label: 'Download app', href: Routes.downloadAndroidApp },
  { label: 'Contribute', href: Routes.contribute },
  { label: 'Community', href: ExternalLinks.discord },
  { label: 'Privacy', href: Routes.privacyPolicy },
  { label: 'Terms', href: Routes.termsOfService },
  { label: 'Refund', href: Routes.refundPolicy },
].filter((link) => link.href)

const linkClass =
  'text-sm text-muted-foreground transition-colors hover:text-foreground'

// `inset` sets the inner row's width and side padding. The default centres it
// for standalone pages; the dashboard passes its content padding so the footer
// lines up with the cards above it.
export default function Footer({
  className,
  inset = 'mx-auto max-w-7xl px-4 sm:px-6 lg:px-8',
}: {
  className?: string
  inset?: string
}) {
  return (
    <footer className={cn('border-t border-border bg-shell/60', className)}>
      {/* Left-aligned on mobile: centred links in a single column read as a
          ragged stack with no common edge to scan down. */}
      <div
        className={cn(
          'flex flex-col items-start gap-4 py-6 sm:items-center md:flex-row md:justify-between',
          inset
        )}
      >
        <div className='flex items-center gap-2'>
          <Image
            src='/images/logo.png'
            alt='textbeeqtt logo'
            width={20}
            height={20}
            className='h-5 w-5 rounded-full'
          />
          <span className='text-sm text-muted-foreground'>
            © {new Date().getFullYear()} textbeeqtt
          </span>
        </div>

        {/* Stacked on mobile: wrapped inline links produced a ragged two-line
            block that was hard to scan and gave small tap targets. */}
        <nav
          aria-label='Footer'
          className='flex w-full flex-col items-start gap-3 sm:w-auto sm:flex-row sm:flex-wrap sm:items-center sm:justify-center sm:gap-x-5 sm:gap-y-2'
        >
          {links.map((link) => (
            <Link
              key={link.label}
              href={link.href}
              target='_blank'
              rel='noopener noreferrer'
              className={linkClass}
            >
              {link.label}
            </Link>
          ))}
          {Routes.statusPage ? (
            <Link
              href={Routes.statusPage}
              target='_blank'
              rel='nofollow noopener noreferrer'
              className='inline-flex items-center gap-1.5 rounded-full border bg-muted/70 px-2.5 py-1 text-sm font-medium text-success transition-colors hover:bg-muted'
            >
              <Activity className='h-3.5 w-3.5' />
              Status
            </Link>
          ) : null}
        </nav>
      </div>
    </footer>
  )
}
