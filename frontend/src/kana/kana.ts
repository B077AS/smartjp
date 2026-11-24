// The kana the flashcards teach: 46 basic (44 + ん and を as "special"), 25 voiced (20 dakuten + 5 handakuten) and 33 combinations, in hiragana and
// katakana. Readings are modified Hepburn. Every entry was checked against three independent sources
// (see tools/verify-kana.mjs and RESEARCH.md): the Hepburn table on Wikipedia, WanaKana, and the Unicode character
// names. Do not add or change a kana here without running `npm run verify:kana`.
//
// Left out on purpose: ゐ ゑ (not used in modern Japanese), ぢゃ ぢゅ ぢょ (same sound as じゃ じゅ じょ), ー, ヴ and the
// extended katakana (ファ ティ ...): their list and status were not verified.

import { toHiragana, toKana } from 'wanakana'

export type ScriptId = 'hiragana' | 'katakana'
export type SectionId = 'basic' | 'special' | 'voiced' | 'handakuten' | 'combo'

export interface Script {
  id: ScriptId
  label: string
  sample: string
}
export interface Section {
  id: SectionId
  label: string
  hint: string
}
/** A row of the table, e.g. the K row. `hira` is its kana in hiragana, in table order. */
export interface Group {
  id: string
  section: SectionId
  title: string
  hira: string[]
}
/** One flashcard: one kana in one script. */
export interface Card {
  id: string
  script: ScriptId
  kana: string
  /** The hiragana form, whatever the script: what answers are compared with. */
  hira: string
  /** Modified Hepburn. */
  romaji: string
  group: string
  /** Typed readings accepted besides what kana comparison finds. */
  accept: string[]
  note: string | null
}

// [hiragana, hepburn]. Rows are the usual textbook rows.
type Row = [hira: string, romaji: string][]
const ROWS: Record<SectionId, Record<string, Row>> = {
  basic: {
    a: [['あ', 'a'], ['い', 'i'], ['う', 'u'], ['え', 'e'], ['お', 'o']],
    k: [['か', 'ka'], ['き', 'ki'], ['く', 'ku'], ['け', 'ke'], ['こ', 'ko']],
    s: [['さ', 'sa'], ['し', 'shi'], ['す', 'su'], ['せ', 'se'], ['そ', 'so']],
    t: [['た', 'ta'], ['ち', 'chi'], ['つ', 'tsu'], ['て', 'te'], ['と', 'to']],
    n: [['な', 'na'], ['に', 'ni'], ['ぬ', 'nu'], ['ね', 'ne'], ['の', 'no']],
    h: [['は', 'ha'], ['ひ', 'hi'], ['ふ', 'fu'], ['へ', 'he'], ['ほ', 'ho']],
    m: [['ま', 'ma'], ['み', 'mi'], ['む', 'mu'], ['め', 'me'], ['も', 'mo']],
    y: [['や', 'ya'], ['ゆ', 'yu'], ['よ', 'yo']],
    r: [['ら', 'ra'], ['り', 'ri'], ['る', 'ru'], ['れ', 're'], ['ろ', 'ro']],
    w: [['わ', 'wa']],
  },
  special: {
    sp: [['を', 'o'], ['ん', 'n']],
  },
  voiced: {
    g: [['が', 'ga'], ['ぎ', 'gi'], ['ぐ', 'gu'], ['げ', 'ge'], ['ご', 'go']],
    z: [['ざ', 'za'], ['じ', 'ji'], ['ず', 'zu'], ['ぜ', 'ze'], ['ぞ', 'zo']],
    d: [['だ', 'da'], ['ぢ', 'ji'], ['づ', 'zu'], ['で', 'de'], ['ど', 'do']],
    b: [['ば', 'ba'], ['び', 'bi'], ['ぶ', 'bu'], ['べ', 'be'], ['ぼ', 'bo']],
  },
  handakuten: {
    p: [['ぱ', 'pa'], ['ぴ', 'pi'], ['ぷ', 'pu'], ['ぺ', 'pe'], ['ぽ', 'po']],
  },
  combo: {
    ky: [['きゃ', 'kya'], ['きゅ', 'kyu'], ['きょ', 'kyo']],
    sh: [['しゃ', 'sha'], ['しゅ', 'shu'], ['しょ', 'sho']],
    ch: [['ちゃ', 'cha'], ['ちゅ', 'chu'], ['ちょ', 'cho']],
    ny: [['にゃ', 'nya'], ['にゅ', 'nyu'], ['にょ', 'nyo']],
    hy: [['ひゃ', 'hya'], ['ひゅ', 'hyu'], ['ひょ', 'hyo']],
    my: [['みゃ', 'mya'], ['みゅ', 'myu'], ['みょ', 'myo']],
    ry: [['りゃ', 'rya'], ['りゅ', 'ryu'], ['りょ', 'ryo']],
    gy: [['ぎゃ', 'gya'], ['ぎゅ', 'gyu'], ['ぎょ', 'gyo']],
    j: [['じゃ', 'ja'], ['じゅ', 'ju'], ['じょ', 'jo']],
    by: [['びゃ', 'bya'], ['びゅ', 'byu'], ['びょ', 'byo']],
    py: [['ぴゃ', 'pya'], ['ぴゅ', 'pyu'], ['ぴょ', 'pyo']],
  },
}

// The hints only state facts checked in RESEARCH.md / tools/verify-kana.mjs: ん is outside the regular table and を
// is used as a particle; the 20 dakuten kana are the g z d b rows; the 5 handakuten kana are the p row; a
// combination is a base kana plus a small ya / yu / yo.
export const SECTIONS: Section[] = [
  { id: 'basic', label: 'Basic', hint: 'The regular table: the vowels and the rows built on them.' },
  { id: 'special', label: 'Special', hint: 'ん is outside the regular table, and を is only used as a particle.' },
  { id: 'voiced', label: 'Voiced', hint: 'Basic kana with two small marks (dakuten): the g, z, d and b rows.' },
  { id: 'handakuten', label: 'P-sounds', hint: 'Five kana with a small circle (handakuten).' },
  { id: 'combo', label: 'Combinations', hint: 'A kana ending in i plus a small ya, yu or yo, read as one syllable.' },
]

// What a row is called in the app: the letter its readings start with. Plain letters, no Japanese terms.
const TITLES: Record<string, string> = { a: 'Vowels', sp: 'を and ん' }
const groupTitle = (id: string, sectionId: SectionId): string => TITLES[id] ?? (sectionId === 'combo' ? id.toUpperCase() : id.toUpperCase() + ' row')

// Readings the typed-romaji check cannot get from WanaKana alone: the kana that share a reading with another one,
// and the particles. (WanaKana turns "ji" into じ, never ぢ, and "o" into お, never を.)
const ACCEPT: Record<string, string[]> = { ぢ: ['ji'], づ: ['zu'], は: ['wa'], へ: ['e'], を: ['o'] }

// Shown after an answer. Particle readings: は wa, へ e, を o (Wikipedia, Hepburn romanization).
const NOTES: Record<string, string> = {
  は: 'Written ha. As a particle it is read wa.',
  へ: 'Written he. As a particle it is read e.',
  を: 'Read o (some romanisations write wo). It is used as a particle.',
  ぢ: 'Romanised the same as じ (ji).',
  づ: 'Romanised the same as ず (zu).',
}

// Katakana sits exactly 0x60 above hiragana in Unicode; tools/verify-kana.mjs checks every result against the
// Wikipedia table and the Unicode names.
function toKatakanaChars(hira: string): string {
  return [...hira].map((c) => String.fromCodePoint((c.codePointAt(0) ?? 0) + 0x60)).join('')
}

function idOf(script: ScriptId, kana: string): string {
  return script + ':' + [...kana].map((c) => (c.codePointAt(0) ?? 0).toString(16)).join('-')
}

export const SCRIPTS: Script[] = [
  { id: 'hiragana', label: 'Hiragana', sample: 'あ' },
  { id: 'katakana', label: 'Katakana', sample: 'ア' },
]

export const GROUPS: Group[] = []
export const CARDS: Card[] = []

for (const section of SECTIONS) {
  for (const [groupId, row] of Object.entries(ROWS[section.id])) {
    GROUPS.push({ id: groupId, section: section.id, title: groupTitle(groupId, section.id), hira: row.map(([h]) => h) })
    for (const script of ['hiragana', 'katakana'] as const) {
      for (const [hira, romaji] of row) {
        const kana = script === 'hiragana' ? hira : toKatakanaChars(hira)
        CARDS.push({
          id: idOf(script, kana),
          script,
          kana,
          hira,
          romaji,
          group: groupId,
          accept: [romaji, ...(ACCEPT[hira] ?? [])],
          note: NOTES[hira] ?? null,
        })
      }
    }
  }
}

const BY_ID = new Map(CARDS.map((card) => [card.id, card]))
/** The card with this id, or undefined (an id from old saved data may no longer exist). */
export const cardById = (id: string): Card | undefined => BY_ID.get(id)
/** The card with this id; for ids that must exist (they came from this table). */
export function getCard(id: string): Card {
  const card = BY_ID.get(id)
  if (!card) throw new Error('Unknown card ' + id)
  return card
}

/** The kana of a group written in the given script, for labels: "か き く け こ". */
export function groupLabel(group: Group, script: ScriptId): string {
  return group.hira.map((h) => (script === 'katakana' ? toKatakanaChars(h) : h)).join(' ')
}

/** The cards of one row in one script, in table order. */
export function cardsOfGroup(groupId: string, script: ScriptId): Card[] {
  return CARDS.filter((card) => card.group === groupId && card.script === script)
}

export function cardsFor(scripts: ScriptId[], groupIds: string[]): Card[] {
  const scriptSet = new Set(scripts)
  const groupSet = new Set(groupIds)
  return CARDS.filter((card) => scriptSet.has(card.script) && groupSet.has(card.group))
}

/**
 * "What is the reading?" The typed romaji is turned into kana and compared as kana, so every legal spelling passes
 * (shi / si, chi / ti, tsu / tu, fu / hu, ...). The explicit list covers what kana comparison cannot tell apart.
 */
export function isCorrectReading(card: Card, typed: string): boolean {
  const text = typed.trim().toLowerCase()
  if (!text) return false
  if (card.accept.includes(text)) return true
  return toHiragana(toKana(text)) === card.hira
}

// For "which kana is this reading?": every kana with the same reading is right (お and を are both "o").
const SAME_READING = new Map<string, Set<string>>()
for (const card of CARDS) {
  if (card.script !== 'hiragana') continue
  const sameReading = SAME_READING.get(card.romaji) ?? new Set<string>()
  sameReading.add(card.hira)
  SAME_READING.set(card.romaji, sameReading)
}

/** Other kana of the same script with the same reading (お and を are both "o"), to show after a wrong answer. */
export function sameReadingKana(card: Card): string[] {
  return CARDS.filter((c) => c.script === card.script && c.romaji === card.romaji && c.id !== card.id).map((c) => c.kana)
}

export function isCorrectKana(card: Card, typedKana: string): boolean {
  const text = toHiragana(toKana(typedKana.trim()))
  return text !== '' && (SAME_READING.get(card.romaji)?.has(text) ?? false)
}
