import { CARDS, isCorrectKana, isCorrectReading, type Card } from '../kana/kana'
import { KANJI_CARDS, isCorrectKanjiReading, isCorrectMeaning, type KanjiCard } from '../kanji/kanji'
import type { QuestionMode } from '../progress/log'
import { clip, customCardById, isCorrectCustom, type CustomCard } from './custom'

// Kana, kanji and the cards the user wrote side by side: one list of ids for the selection, the progress log and the saved lists.

/** A kana or a kanji: the cards that ship with the app. They are fixed, so they can be indexed once. */
export type BuiltInCard = Card | KanjiCard
/** Any card a round can ask. */
export type StudyCard = BuiltInCard | CustomCard
export type { CustomCard }

export const ALL_CARDS: BuiltInCard[] = [...CARDS, ...KANJI_CARDS]
const BUILT_IN = new Map(ALL_CARDS.map((card) => [card.id, card]))

/** The card with this id, or undefined (an id from an old saved list may no longer exist, for example a custom card that was deleted). */
export const cardById = (id: string): StudyCard | undefined => BUILT_IN.get(id) ?? customCardById(id)

/** The card with this id; for ids that must exist (they came from the cards). */
export function getCard(id: string): StudyCard {
  const card = cardById(id)
  if (!card) throw new Error('Unknown card ' + id)
  return card
}

export const isKanjiCard = (card: StudyCard): card is KanjiCard => card.script === 'kanji'
export const isCustomCard = (card: StudyCard): card is CustomCard => card.script === 'custom'
export const isKanaCard = (card: StudyCard): card is Card => card.script === 'hiragana' || card.script === 'katakana'

/** What is drawn on a tile or in a list: the kana, the kanji, or the start of the text the user wrote. */
export function faceOf(card: StudyCard): string {
  if (isCustomCard(card)) return clip(card.front, 40)
  return isKanjiCard(card) ? card.kanji : card.kana
}

/** The short line under a tile and in lists: the kana's romaji, the kanji's first meanings, or the start of the answer. */
export function captionOf(card: StudyCard): string {
  if (isCustomCard(card)) return clip(card.answer, 40)
  return isKanjiCard(card) ? card.meanings.slice(0, 2).join(', ') : card.romaji
}

/**
 * Which cards a question can be asked of: typing a reading works for every card (for a card the user wrote, the answer), writing a
 * reading as kana only for kana (a kanji has several readings), typing the meaning only for kanji, and checking yourself ("flip":
 * think, show the answer, say whether you knew it) for every card, which is the way to go for a long answer.
 */
export function modeFits(mode: QuestionMode, card: StudyCard): boolean {
  if (mode === 'flip') return true
  if (isCustomCard(card)) return mode === 'reading'
  if (mode === 'kana') return isKanaCard(card)
  if (mode === 'meaning') return isKanjiCard(card)
  return true
}

/** Is the typed answer right for this card in this question? (Not asked in "flip": there the user says it.) */
export function isCorrect(card: StudyCard, mode: QuestionMode, typed: string): boolean {
  if (isCustomCard(card)) return isCorrectCustom(card, typed)
  if (isKanjiCard(card)) return mode === 'meaning' ? isCorrectMeaning(card, typed) : isCorrectKanjiReading(card, typed)
  return mode === 'kana' ? isCorrectKana(card, typed) : isCorrectReading(card, typed)
}
