import { toHiragana, toKana } from 'wanakana'
import { ALL_CARDS, isKanjiCard, type BuiltInCard } from './cards'

// The search box on the flashcards page. It finds a card from what the user has at hand: the character itself (kana or kanji,
// one or several pasted together), a reading typed as kana or as romaji, or an English meaning (kanji). Nothing here is
// a list of Japanese facts: it only looks inside the card data that already exists (kana table, KANJIDIC2 pack).

interface Entry {
  card: BuiltInCard
  face: string
  /** Hiragana forms to compare a kana query with: a kana's own, a kanji's every reading. */
  hira: string[]
  /** Romaji forms to compare a latin query with. */
  romaji: string[]
  /** Lower-case English meanings without brackets (kanji only). */
  meanings: string[]
}

// Romaji cannot tell づ from ず or ぢ from じ, so a search treats them as the same (like checking an answer does).
const canon = (hiragana: string): string => hiragana.replace(/ぢ/g, 'じ').replace(/づ/g, 'ず')

function entryOf(card: BuiltInCard): Entry {
  if (isKanjiCard(card)) {
    return {
      card,
      face: card.kanji,
      hira: card.readings.map(canon),
      romaji: [...card.onView, ...card.kunView].map((r) => r.romaji.toLowerCase()),
      meanings: card.meanings.map((m) => m.toLowerCase().replace(/\(.*?\)/g, ' ').replace(/\s+/g, ' ').trim()).filter(Boolean),
    }
  }
  return { card, face: card.kana, hira: [canon(card.hira)], romaji: [card.romaji, ...card.accept].map((r) => r.toLowerCase()), meanings: [] }
}

// Built on the first search, then kept: about 2400 small entries.
let index: Entry[] | null = null
const entries = (): Entry[] => (index ??= ALL_CARDS.map(entryOf))

const HAS_KANA = /[぀-ヿ]/
const LATIN = /^[a-z' -]+$/
const ONLY_HIRAGANA = /^[ぁ-ゖー]+$/

/** 0 = exactly what was typed, 1 = starts with it; undefined = no match. Lower is listed first. */
function rank(entry: Entry, query: string, kanaQuery: string | null, latinAsKana: string | null): number | undefined {
  const kanjiCard = isKanjiCard(entry.card)

  // The character itself, or several pasted together: 日本 finds both.
  if ([...query].includes(entry.face)) return 0

  // Typed as kana (katakana counts as hiragana).
  if (kanaQuery) {
    if (entry.hira.includes(kanaQuery)) return 0
    if (entry.hira.some((h) => h.startsWith(kanaQuery))) return 1
    return undefined
  }

  if (!LATIN.test(query)) return undefined
  // Romaji. A kana card answers to one letter (k finds the k row); a kanji has many readings and meanings, so it needs two letters.
  if (entry.romaji.includes(query)) return 0
  if ((!kanjiCard || query.length >= 2) && entry.romaji.some((r) => r.startsWith(query))) return 1
  // Romaji typed the way another spelling goes (si, wo): compare as kana. Kana cards only, a kanji has too many readings for a short query.
  if (!kanjiCard && latinAsKana && entry.hira.some((h) => h.startsWith(latinAsKana))) return 1
  // The English meaning, from the start of a word (water finds the kanji for it and not "backwater").
  if (kanjiCard && query.length >= 2) {
    if (entry.meanings.includes(query)) return 0
    if (entry.meanings.some((m) => (' ' + m).includes(' ' + query))) return 1
  }
  return undefined
}

/** The cards that match what is typed in the search box, best matches first, otherwise in the usual order. Empty for an empty query. */
export function searchCards(raw: string): BuiltInCard[] {
  const query = raw.trim().toLowerCase()
  if (query === '') return []
  const kanaQuery = HAS_KANA.test(query) ? canon(toHiragana(query)) : null
  const converted = LATIN.test(query) ? toKana(query) : ''
  const latinAsKana = ONLY_HIRAGANA.test(converted) ? canon(converted) : null

  const hits: { card: BuiltInCard; rank: number; order: number }[] = []
  entries().forEach((entry, order) => {
    const r = rank(entry, query, kanaQuery, latinAsKana)
    if (r !== undefined) hits.push({ card: entry.card, rank: r, order })
  })
  return hits.sort((a, b) => a.rank - b.rank || a.order - b.order).map((hit) => hit.card)
}
