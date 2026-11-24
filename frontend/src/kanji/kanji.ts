// The kanji the flashcards teach. The data (kanji-data.json) is copied from KANJIDIC2 by tools/build-kanji.mjs and
// checked by tools/verify-kanji.mjs: nothing here is typed by hand. This file only turns it into cards and decides what
// counts as a correct answer. KANJIDIC2 is (c) EDRDG, licensed CC BY-SA 4.0: it is credited under Settings > Credits.

import { toHiragana, toKana, toRomaji } from 'wanakana'
import pack from './kanji-data.json'

/**
 * One reading prepared for the screen: the kana in parts (the dictionary's "." marks where the okurigana starts) and its
 * romaji. The dictionary's "-" (a form that only occurs before or after something, みず- next to みず) is not shown: the
 * forms that look the same without it are merged into one (see views()). Romaji comes from WanaKana, the same library that reads what the user types, so a
 * romaji shown here is one that is accepted as the answer. WanaKana leaves out a final small tsu (ジッ shows as ji).
 */
export interface ReadingView {
  stem: string
  /** The part after the dot: the okurigana of a kun reading, or ''. */
  okurigana: string
  romaji: string
}

/** One flashcard: one kanji. */
export interface KanjiCard {
  id: string
  script: 'kanji'
  kanji: string
  grade: number
  strokes: number
  /** English meanings, in the dictionary's order. */
  meanings: string[]
  /** On readings in katakana, kun readings in hiragana, as the dictionary writes them: "-" = only as a prefix or suffix, "." = where the okurigana starts. */
  on: string[]
  kun: string[]
  /** The same readings prepared for the screen. */
  onView: ReadingView[]
  kunView: ReadingView[]
  /** Every reading that counts as right, as hiragana. */
  readings: string[]
}

export interface KanjiGrade {
  grade: number
  label: string
  hint: string
}

export const KANJI_SOURCE = pack.source

// KANJIDIC2 (see RESEARCH.md): grades 1 to 6 are the kanji taught in elementary school, in that school year; grade 8 is the
// rest of the 2136 everyday-use kanji (taught in secondary school). Grades 9 and 10 (name kanji) are not in the pack.
const gradeHint = (grade: number): string =>
  grade === 8 ? 'The rest of the 2136 everyday-use kanji, taught in secondary school.' : `Taught in school year ${grade} in Japan.`

export const KANJI_GRADES: KanjiGrade[] = pack.grades.map((grade) => ({
  grade,
  label: 'Grade ' + grade,
  hint: gradeHint(grade),
}))

// The dictionary also lists the name of the radical the kanji is ("one radical (no.1)"): that is not a meaning to type.
const isRadicalNote = (meaning: string): boolean => /\bradical \(no\./.test(meaning)

/** A reading as hiragana: the dictionary's "-" and "." marks removed, katakana turned into hiragana. */
const plain = (reading: string): string => toHiragana(reading.replace(/-/g, '').replace('.', ''))

// Romaji cannot tell づ from ず or ぢ from じ (Hepburn: zu, ji), so they count as the same when answers are compared.
const canon = (hiragana: string): string => hiragana.replace(/ぢ/g, 'じ').replace(/づ/g, 'ず')

/** The readings of one kind as shown: without the "-" marks, and a reading that then equals an earlier one is left out. */
function views(readings: string[]): ReadingView[] {
  const shown = new Map<string, ReadingView>()
  for (const reading of readings) {
    const [stem, okurigana = ''] = reading.replace(/^-|-$/g, '').split('.')
    const key = stem + '.' + okurigana
    if (!shown.has(key)) shown.set(key, { stem, okurigana, romaji: toRomaji(stem + okurigana) })
  }
  return [...shown.values()]
}

function readingsOf(on: string[], kun: string[]): string[] {
  const all = new Set<string>()
  const add = (hiragana: string) => {
    all.add(canon(hiragana))
    // A reading that ends in a small tsu (ジッ) cannot be typed in romaji: the reading without it counts too.
    if (hiragana.endsWith('っ')) all.add(canon(hiragana.slice(0, -1)))
  }
  for (const reading of on) add(plain(reading))
  for (const reading of kun) {
    add(plain(reading)) // はたら.く -> はたらく
    add(toHiragana(reading.replace(/-/g, '').split('.')[0])) // and the stem alone: はたら
  }
  all.delete('')
  return [...all]
}

export const KANJI_CARDS: KanjiCard[] = pack.kanji.map((k) => ({
  id: 'kanji:' + (k.c.codePointAt(0) ?? 0).toString(16),
  script: 'kanji',
  kanji: k.c,
  grade: k.grade,
  strokes: k.strokes,
  meanings: k.meanings.filter((m) => !isRadicalNote(m)),
  on: k.on,
  kun: k.kun,
  onView: views(k.on),
  kunView: views(k.kun),
  readings: readingsOf(k.on, k.kun),
}))

const BY_GRADE = new Map<number, KanjiCard[]>(KANJI_GRADES.map((g) => [g.grade, KANJI_CARDS.filter((card) => card.grade === g.grade)]))

/** The kanji of one grade, in the pack's order (fewest strokes first). */
export const kanjiOfGrade = (grade: number): KanjiCard[] => BY_GRADE.get(grade) ?? []

/** "What is the reading?" Typed romaji (or kana) is turned into hiragana and compared with every on and kun reading of the kanji. */
export function isCorrectKanjiReading(card: KanjiCard, typed: string): boolean {
  const text = canon(toHiragana(toKana(typed.trim().toLowerCase())))
  return text !== '' && card.readings.includes(text)
}

// Meanings are compared without case, brackets and a leading "to", "the" or "a".
const plainMeaning = (text: string): string =>
  text
    .toLowerCase()
    .replace(/\(.*?\)/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/^(to|the|a|an) /, '')

/** "What does it mean?" Any one of the dictionary's English meanings is right. */
export function isCorrectMeaning(card: KanjiCard, typed: string): boolean {
  const text = plainMeaning(typed)
  return text !== '' && card.meanings.some((meaning) => plainMeaning(meaning) === text)
}
