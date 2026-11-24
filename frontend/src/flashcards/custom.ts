import { reactive } from 'vue'
import { api, type CustomCardSummary } from '../api/client'

// The cards the user writes: any text on the front and any text as the answer (a word, a phrase, a sentence). They are kept in
// the SQLite database through api/client.ts, like the saved lists; this file holds what the page shows. The rules (what is empty,
// how long, how many) are in Java (CustomCardsService): the page asks and shows the answer.
// In the selection, in saved lists and in the progress log a card is called "custom:<id>", like "hira:ka" for a kana.

export const CUSTOM_PREFIX = 'custom:'

/** The longest front and answer; the same limit as CustomCardsService.MAX_TEXT (the page uses it only to stop typing early). */
export const MAX_TEXT = 500

/** One card the user wrote, shaped like the kana and kanji cards (an id and a script) so the rest of the page can treat them alike. */
export interface CustomCard {
  id: string
  script: 'custom'
  front: string
  answer: string
  savedAt: string
}

export const customCards = reactive<CustomCard[]>([])

const toCard = (saved: CustomCardSummary): CustomCard => ({
  id: CUSTOM_PREFIX + saved.id,
  script: 'custom',
  front: saved.front,
  answer: saved.answer,
  savedAt: saved.savedAt,
})
const databaseId = (cardId: string): string => cardId.slice(CUSTOM_PREFIX.length)

/** Reads every custom card. Throws when the storage cannot be reached. */
export async function loadCustomCards(): Promise<void> {
  customCards.splice(0, customCards.length, ...(await api.customCards.all()).map(toCard))
}

export type CustomSaveResult = 'created' | 'updated' | 'empty_front' | 'empty_answer' | 'too_long' | 'too_many' | 'not_found'

/** Writes a card (a new one when `cardId` is null) and says what came of it. Throws when the storage cannot be reached. */
export async function saveCustomCard(cardId: string | null, front: string, answer: string): Promise<CustomSaveResult> {
  const reply = await api.customCards.save({ id: cardId === null ? null : databaseId(cardId), front, answer })
  if (reply.card) {
    const saved = toCard(reply.card)
    const index = customCards.findIndex((c) => c.id === saved.id)
    if (index >= 0) customCards[index] = saved
    else customCards.push(saved)
  }
  return reply.result as CustomSaveResult
}

export async function removeCustomCard(cardId: string): Promise<void> {
  await api.customCards.remove(databaseId(cardId))
  const index = customCards.findIndex((c) => c.id === cardId)
  if (index >= 0) customCards.splice(index, 1)
}

export const customCardById = (id: string): CustomCard | undefined => customCards.find((c) => c.id === id)

// ---- checking a typed answer ---------------------------------------------------------------------------------

/**
 * A text as it is compared: width and compatibility forms unified (full-width letters, half-width katakana), lower case, every run
 * of spaces and line breaks one space, and punctuation or quotes at either end dropped. "Tokyo." and "  tokyo " are the same answer;
 * a typo or a missing word is not.
 */
export function normaliseAnswer(text: string): string {
  const compact = text.normalize('NFKC').toLowerCase().replace(/\s+/g, ' ').trim()
  return compact.replace(/^[\s.,;:!?'"`()[\]{}¿¡。、！？「」『』]+|[\s.,;:!?'"`()[\]{}。、！？「」『』]+$/g, '')
}

export function isCorrectCustom(card: CustomCard, typed: string): boolean {
  const wanted = normaliseAnswer(card.answer)
  return wanted !== '' && normaliseAnswer(typed) === wanted
}

/** A text on one line, cut after `max` characters: for tiles, lists and the end-of-round summary. */
export function clip(text: string, max: number): string {
  const line = text.replace(/\s+/g, ' ').trim()
  const chars = [...line]
  return chars.length <= max ? line : chars.slice(0, max).join('').trimEnd() + '…'
}
