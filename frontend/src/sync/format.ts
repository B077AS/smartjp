import type { SyncCounts } from '../api/client'

// Words for the Sync screen: when something happened, and what a sync moved. Only wording; the numbers come from Java.

const plural = (n: number, one: string, many = one + 's'): string => `${n.toLocaleString()} ${n === 1 ? one : many}`

/** "Today 18:20", "Yesterday 21:05", or the date for anything older; "never" when there is no time. */
export function formatWhen(iso: string | null | undefined, now: Date = new Date()): string {
  if (!iso) return 'Never'
  const then = new Date(iso)
  if (Number.isNaN(then.getTime())) return 'Never'
  const time = then.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  const dayStart = (d: Date): number => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const daysAgo = Math.round((dayStart(now) - dayStart(then)) / 86_400_000)
  if (daysAgo === 0) return `Today ${time}`
  if (daysAgo === 1) return `Yesterday ${time}`
  return `${then.toLocaleDateString([], { day: 'numeric', month: 'short', year: 'numeric' })}, ${time}`
}

/** "320 answers, 1 list, 3 cards", leaving out what is zero; "nothing" when everything is. */
export function describeCounts(c: SyncCounts | null | undefined): string {
  if (!c) return 'nothing'
  const parts: string[] = []
  if (c.answers > 0) parts.push(plural(c.answers, 'answer'))
  if (c.lists > 0) parts.push(plural(c.lists, 'list'))
  if (c.cards > 0) parts.push(plural(c.cards, 'card'))
  if (c.removed > 0) parts.push(`${plural(c.removed, 'deleted item')} removed`)
  return parts.length > 0 ? parts.join(', ') : 'nothing'
}

export const isEmptyCounts = (c: SyncCounts | null | undefined): boolean => describeCounts(c) === 'nothing'

/** What one side holds, in the order of the screen: "980 answers, 7 lists, 10 cards". */
export function describeHeld(answers: number, lists: number, cards: number): string {
  return [plural(answers, 'answer'), plural(lists, 'list'), plural(cards, 'card')].join(', ')
}
