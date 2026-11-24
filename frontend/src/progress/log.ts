// Progress is an append-only log of answers (decision in CLAUDE.md): every answer, right or wrong, is one event that is stored in
// the database (through api/client.ts, like the saved lists) and never edited. "What do I know" and "what do I miss" are worked out
// from the events. The page does not hold the whole log: it asks Java once for the totals per card, keeps them up to date itself as
// answers are given, and sends each new answer to be stored.
//
// Because every event has a unique id made here, two devices can later merge by simply taking the union and dropping duplicates.

import { reactive } from 'vue'
import { api } from '../api/client'
import { notifyError } from '../state/toasts'

export type QuestionMode = 'reading' | 'kana' | 'meaning' | 'flip'

export interface AnswerEvent {
  /** Unique (a retry, or a sync later, must not count an answer twice). */
  id: string
  cardId: string
  correct: boolean
  /** How long the card was on screen before the answer, in milliseconds. */
  ms: number
  /** 'reading': shown kana or kanji, typed romaji. 'kana': shown romaji, typed kana. 'meaning': shown kanji, typed English meaning. 'flip': shown the card, then the answer, and the user said whether they knew it. */
  mode: QuestionMode
}

export function newId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') return crypto.randomUUID()
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

export interface CardStats {
  /** How often the card was answered (a skip and a timeout count: they are misses). */
  seen: number
  /** How often it was answered correctly. */
  correct: number
}

/** For each card that was ever answered: how often and how often correctly. Reactive, so what the page shows follows the answers. */
export const stats = reactive(new Map<string, CardStats>())

/** Reads the totals from the database. Throws when the storage cannot be reached. */
export async function loadStats(): Promise<void> {
  const rows = await api.answers.stats()
  stats.clear()
  for (const row of rows) stats.set(row.cardId, { seen: row.seen, correct: row.correct })
}

export const missesOf = (cardId: string): number => {
  const entry = stats.get(cardId)
  return entry ? entry.seen - entry.correct : 0
}

/** The cards answered wrongly at least once, the one missed most often first; with the same number of misses, the one missed more often in proportion. */
export function mostMissed(): { cardId: string; seen: number; misses: number }[] {
  const rows = [...stats].map(([cardId, s]) => ({ cardId, seen: s.seen, misses: s.seen - s.correct })).filter((row) => row.misses > 0)
  return rows.sort((a, b) => b.misses - a.misses || b.misses / b.seen - a.misses / a.seen || a.cardId.localeCompare(b.cardId))
}

/**
 * Logs an answer: the totals change at once, and the answer is sent to the database in the background (a round never waits for it).
 * When it cannot be stored a notification says so (the same message replaces the old one, so a dead storage is not a flood).
 */
export function recordAnswer(answer: Pick<AnswerEvent, 'cardId' | 'correct' | 'ms' | 'mode'>): void {
  const entry = stats.get(answer.cardId) ?? { seen: 0, correct: 0 }
  stats.set(answer.cardId, { seen: entry.seen + 1, correct: entry.correct + (answer.correct ? 1 : 0) })

  const event: AnswerEvent = { id: newId(), ...answer }
  api.answers
    .record({ id: event.id, cardId: event.cardId, correct: event.correct, ms: event.ms, mode: event.mode })
    .then((reply) => {
      if (reply.result !== 'recorded' && reply.result !== 'duplicate') throw new Error(reply.result)
    })
    .catch(() => notifyError("Couldn't save your answers", 'The storage cannot be reached, so what you answer now may not be remembered.'))
}
