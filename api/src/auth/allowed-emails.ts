import { HttpException, HttpStatus } from '@nestjs/common'

// This instance serves its owner only. ALLOWED_EMAILS (comma-separated, any
// case) names who may use it. Unset, nobody can create an account and
// existing accounts sign in as before; set, only the listed addresses can
// register, sign in with Google or sign in with a password.
const allowedEmails = (): string[] =>
  (process.env.ALLOWED_EMAILS ?? '')
    .split(',')
    .map((value) => value.trim().toLowerCase())
    .filter(Boolean)

const isListed = (email: unknown) =>
  typeof email === 'string' &&
  allowedEmails().includes(email.trim().toLowerCase())

const refuse = () => {
  throw new HttpException(
    { error: 'This account is not allowed here' },
    HttpStatus.FORBIDDEN,
  )
}

/** Refuses a new account for an address that is not listed. */
export const assertMayCreateAccount = (email: unknown) => {
  if (!isListed(email)) refuse()
}

/** Refuses a sign-in for an unlisted address once a list is set. */
export const assertMaySignIn = (email: unknown) => {
  if (allowedEmails().length && !isListed(email)) refuse()
}
