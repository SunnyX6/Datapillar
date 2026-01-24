import type { Ticket, UserProfile } from './types'

export function normalizeTags(raw: string): string[] {
  if (!raw.trim()) {
    return []
  }
  const tags = raw
    .split(/[,，]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
  return Array.from(new Set(tags))
}

export function buildTicketTitle(typeLabel: string, target: string): string {
  const trimmedTarget = target.trim()
  if (!trimmedTarget) {
    return typeLabel
  }
  return `${typeLabel}: ${trimmedTarget}`
}

export function isTicketMentioned(ticket: Ticket, currentUser: UserProfile): boolean {
  const needles = [`@${currentUser.name}`, `@${currentUser.avatar}`, '@me', '@我']
  return ticket.timeline.some((event) => {
    if (!event.comment) {
      return false
    }
    return needles.some((needle) => event.comment.includes(needle))
  })
}
