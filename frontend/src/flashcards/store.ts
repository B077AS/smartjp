import { reactive } from 'vue'
import { ALL_CARDS, cardById, getCard, modeFits, type StudyCard } from './cards'
import { customCards } from './custom'
import type { QuestionMode } from '../progress/log'

// What the user picked on the settings screen (NOT saved yet: it starts from the defaults on every page load; the answers are saved, see progress/log.ts)
// and the round that is being played.
// The selection is a list of individual card ids: a row, a section or a saved list is only a shortcut that
// selects several cards at once.

export type FontChoice = 'both' | 'mincho' | 'gothic'

export interface Settings {
  /** Card ids. */
  selected: string[]
  /** Cards in a round; 0 = every selected card. */
  count: number
  /** Seconds per card; 0 = no time limit. */
  seconds: number
  mode: QuestionMode
  fonts: FontChoice
}

const COUNTS = [10, 20, 50, 0]
const SECONDS = [0, 5, 10, 20]

// Nothing is selected at the start: the user picks what to practise. A round asks every selected card (count 0) unless the user limits it.
const DEFAULTS: Settings = { selected: [], count: 0, seconds: 0, mode: 'reading', fonts: 'gothic' }

export const OPTIONS = { COUNTS, SECONDS }

export const settings = reactive<Settings>({ ...DEFAULTS, selected: [...DEFAULTS.selected] })

// ---- the selection ---------------------------------------------------------------------------------------

export const isSelected = (id: string): boolean => settings.selected.includes(id)

/** Select or unselect these cards, leaving the rest of the selection alone. */
export function setCards(ids: string[], on: boolean): void {
  const wanted = new Set(ids)
  const rest = settings.selected.filter((id) => !wanted.has(id))
  settings.selected = on ? [...rest, ...ids] : rest
}

export function toggleCard(id: string): void {
  setCards([id], !isSelected(id))
}

export function replaceSelection(ids: string[]): void {
  settings.selected = [...new Set(ids)].filter((id) => cardById(id))
}

export function selectedCards(): StudyCard[] {
  // In table order (the cards the user wrote last, in the order they were written), not in the order the user clicked.
  const chosen = new Set(settings.selected)
  return [...ALL_CARDS, ...customCards].filter((c) => chosen.has(c.id))
}

/** The selected cards the chosen question can be asked of (a kanji cannot be written from its reading, for one). */
export function playableCards(): StudyCard[] {
  return selectedCards().filter((c) => modeFits(settings.mode, c))
}

// ---- rounds ----------------------------------------------------------------------------------------------

export function shuffled<T>(list: readonly T[]): T[] {
  const copy = [...list]
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[copy[i], copy[j]] = [copy[j], copy[i]]
  }
  return copy
}

/** The playable cards in random order, cut to the chosen round size. */
export function buildDeck(): StudyCard[] {
  const pool = shuffled(playableCards())
  return settings.count > 0 ? pool.slice(0, settings.count) : pool
}

export interface RoundResult {
  cardId: string
  correct: boolean
  ms: number
  typed: string
  timedOut: boolean
}

export interface RoundConfig {
  mode: QuestionMode
  seconds: number
  fonts: FontChoice
}

export const session = reactive<{ deck: StudyCard[]; index: number; results: RoundResult[]; config: RoundConfig }>({
  deck: [],
  index: 0,
  results: [],
  // Frozen at the start, so settings changed later do not affect a running round.
  config: { mode: 'reading', seconds: 0, fonts: 'gothic' },
})

export function startSession(cards: StudyCard[], config: RoundConfig): void {
  session.deck = cards
  session.index = 0
  session.results = []
  session.config = { mode: config.mode, seconds: config.seconds, fonts: config.fonts }
}

export function missedCards(): StudyCard[] {
  return session.results.filter((r) => !r.correct).map((r) => getCard(r.cardId))
}
