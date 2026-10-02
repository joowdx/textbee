import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Github, MessageSquare } from 'lucide-react'
import Link from 'next/link'
import { ExternalLinks } from '@/config/external-links'

export default function CommunityLinks() {
  if (!ExternalLinks.github && !ExternalLinks.discord) return null

  return (
    <div className='grid gap-4 md:grid-cols-2'>
      {ExternalLinks.github ? (
        <Card>
          <CardHeader>
            <CardTitle>GitHub</CardTitle>
          </CardHeader>
          <CardContent>
            <p className='text-sm text-muted-foreground mb-4'>
              Check out our source code and contribute to the project.
            </p>
            <Button asChild className='w-full'>
              <Link href={ExternalLinks.github} prefetch={false} target='_blank'>
                <Github className='mr-2 h-4 w-4' />
                View Source
              </Link>
            </Button>
          </CardContent>
        </Card>
      ) : null}

      {ExternalLinks.discord ? (
        <Card>
          <CardHeader>
            <CardTitle>Discord</CardTitle>
          </CardHeader>
          <CardContent>
            <p className='text-sm text-muted-foreground mb-4'>
              Join our community for support and updates.
            </p>
            <Button asChild className='w-full' variant='outline'>
              <Link href={ExternalLinks.discord} prefetch={false} target='_blank'>
                <MessageSquare className='mr-2 h-4 w-4' />
                Join Discord
              </Link>
            </Button>
          </CardContent>
        </Card>
      ) : null}
    </div>
  )
}
