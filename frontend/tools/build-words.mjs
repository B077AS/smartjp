// Builds src/words/words-data.json (example words for every kana and every kanji of the app) from JMdict, in the JSON form made by
// jmdict-simplified (https://github.com/scriptin/jmdict-simplified/releases, file jmdict-eng-common-<version>.json.tgz: only the words
// flagged "common"). Every word, reading and meaning is copied from that file; nothing is written by hand. JMdict is (c) EDRDG,
// CC BY-SA 4.0: credited under Settings, About.
//
//   node tools/build-words.mjs <path to jmdict-eng-common-x.y.z.json>
//
// Download and unpack the file outside the repo. Then run `npm run verify:words -- <same path>`.
//
// THE RULES (mechanical on purpose: the app's author cannot judge Japanese, so a person is never asked to pick words).
// A word is a candidate when:
//   - it is in the "common" file (some form of it is flagged common);
//   - its FIRST meaning has none of the tags vulgar, rude (X), derogatory, obscure, archaic, obsolete, rare, slang, net slang, sensitive, familiar or jocular;
//   - it has a written form: the first common kanji form that applies to that meaning, or the kana alone when the first meaning is tagged
//     "usually written using kana alone" (uk) or the word has no common kanji form;
//   - it has at least one common reading that applies to that form;
//   - the same written form with the same readings is listed once for a character (JMdict has separate entries for the same spelling);
//   - every kanji in the written form is one the app teaches (the 2136 everyday kanji), so each can be looked up on the Characters page.
// For a KANJI the words that contain it are ranked by: the highest grade among their kanji (simpler first), the length of the written
// form (shorter first), the JMdict id (only to make the order the same every time). The first FIVE are kept.
// For a KANA (hiragana or katakana, and a combination such as きゃ) the words with a common reading that STARTS with it, at least two kana
// long (three for katakana: that leaves out the short abbreviations and keeps the loanwords), are ranked by: written in kana alone first (the learner can read all of it), the length of the reading (shorter first), the JMdict
// id. The first FIVE are kept. A kana or kanji with no such word simply has none.
//
// The ranking is NOT by how common a word is: the file has no frequency (only a yes/no "common" flag). Say "common words", never "the
// most common words".
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
export const OUT = path.join(here, '..', 'src', 'words', 'words-data.json')
// The credits (About screen) only need who and which version, not the 800 KB of words: a small file next to the pack says it.
export const SOURCE_OUT = path.join(here, '..', 'src', 'words', 'words-source.json')
export const PER_CHARACTER = 5
const isKatakana = (s) => /^[\u30a0-\u30ff]/.test(s)
/** The first entries of a ranked list, a written form with the same readings only once. */
export const firstDistinct = (list, keyOf) => {
  const seen = new Set()
  const out = []
  for (const item of list) {
    const key = keyOf(item)
    if (seen.has(key)) continue
    seen.add(key)
    out.push(item)
    if (out.length === PER_CHARACTER) break
  }
  return out
}
const sameWord = (c) => c.w + '|' + c.readings.join('/')
const BAD = new Set(['vulg', 'X', 'derog', 'obsc', 'arch', 'obs', 'rare', 'sl', 'net-sl', 'sens', 'fam', 'joc'])
const IDEOGRAPH = /[㐀-䶿一-鿿豈-﫿\u{20000}-\u{2fa1f}]/u
// A written form may hold only kanji, hiragana, katakana, the long-vowel mark and the repeat mark: no digits, no Latin letters.
const PLAIN = /^[\u3040-\u309f\u30a0-\u30ff\u3005\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\u{20000}-\u{2fa1f}]+$/u
const applies = (list, form) => list.includes('*') || list.includes(form)

/** One JMdict word as the pack keeps it, or null when it is not a candidate. */
export function candidateOf(word, gradeOf) {
  const sense = word.sense[0]
  if (!sense || sense.misc.some((m) => BAD.has(m))) return null
  const glosses = sense.gloss.map((g) => g.text)
  if (glosses.length === 0) return null

  const commonKanji = word.kanji.filter((k) => k.common && applies(sense.appliesToKanji, k.text))
  const commonKana = word.kana.filter((k) => k.common)
  let written
  let readings
  let kanaOnly
  if (commonKanji.length > 0 && !sense.misc.includes('uk')) {
    written = commonKanji[0].text
    readings = commonKana.filter((k) => applies(k.appliesToKanji, written)).map((k) => k.text)
    kanaOnly = false
  } else {
    const kana = commonKana.filter((k) => applies(sense.appliesToKana, k.text))
    if (kana.length === 0) return null
    written = kana[0].text
    readings = [written]
    kanaOnly = true
  }
  if (readings.length === 0 || !PLAIN.test(written)) return null

  // Every kanji of the word must be one the app teaches; the largest grade says how hard the word is.
  let grade = 0
  for (const ch of written) {
    if (!IDEOGRAPH.test(ch)) continue
    const g = gradeOf.get(ch)
    if (g === undefined) return null
    grade = Math.max(grade, g)
  }
  return { id: word.id, w: written, r: kanaOnly ? undefined : readings, g: glosses.slice(0, 3), readings, grade, kanaOnly }
}

const numeric = (id) => Number(id)

export function buildPack(dict, kanjiPack, kanaCards) {
  const gradeOf = new Map(kanjiPack.kanji.map((k) => [k.c, k.grade]))
  const candidates = dict.words.map((w) => candidateOf(w, gradeOf)).filter(Boolean)

  const chosen = new Map() // key -> candidates
  // kanji
  for (const k of kanjiPack.kanji) {
    const list = candidates.filter((c) => !c.kanaOnly && c.w.includes(k.c))
    list.sort((a, b) => a.grade - b.grade || [...a.w].length - [...b.w].length || numeric(a.id) - numeric(b.id))
    chosen.set(k.c, firstDistinct(list, sameWord))
  }
  // kana: the strings of the flashcards (a single kana or a combination)
  for (const kana of new Set(kanaCards.map((c) => c.kana))) {
    const list = candidates
      .map((c) => ({ c, reading: c.readings.find((r) => r.startsWith(kana) && [...r].length >= (isKatakana(kana) ? 3 : 2)) }))
      .filter((x) => x.reading !== undefined)
    list.sort((a, b) => Number(b.c.kanaOnly) - Number(a.c.kanaOnly) || [...a.reading].length - [...b.reading].length || numeric(a.c.id) - numeric(b.c.id))
    chosen.set(kana, firstDistinct(list, (x) => sameWord(x.c)).map((x) => x.c))
  }

  const words = []
  const at = new Map()
  const index = {}
  for (const [key, list] of chosen) {
    if (list.length === 0) continue
    index[key] = list.map((c) => {
      if (!at.has(c.id)) {
        at.set(c.id, words.length)
        words.push(c.kanaOnly ? { id: c.id, w: c.w, g: c.g } : { id: c.id, w: c.w, r: c.r, g: c.g })
      }
      return at.get(c.id)
    })
  }
  return {
    source: { name: 'JMdict', version: dict.version, dictDate: dict.dictDate, via: 'jmdict-simplified (common words only)', licence: 'CC BY-SA 4.0' },
    perCharacter: PER_CHARACTER,
    words,
    index,
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const file = process.argv.slice(2).find((a) => !a.startsWith('--'))
  if (!file) {
    console.error('usage: node tools/build-words.mjs <jmdict-eng-common.json>')
    process.exit(2)
  }
  const { CARDS } = await import('../src/kana/kana.ts')
  const kanjiPack = JSON.parse(fs.readFileSync(path.join(here, '..', 'src', 'kanji', 'kanji-data.json'), 'utf8'))
  const dict = JSON.parse(fs.readFileSync(file, 'utf8'))
  const pack = buildPack(dict, kanjiPack, CARDS)
  fs.mkdirSync(path.dirname(OUT), { recursive: true })
  fs.writeFileSync(OUT, JSON.stringify(pack) + '\n')
  fs.writeFileSync(SOURCE_OUT, JSON.stringify(pack.source, null, 1) + '\n')
  const keys = Object.keys(pack.index).length
  const total = new Set([...kanjiPack.kanji.map((k) => k.c), ...CARDS.map((c) => c.kana)]).size
  console.log(`wrote ${pack.words.length} words for ${keys} of ${total} characters (JMdict ${pack.source.version}, ${pack.source.dictDate}) to ${OUT} (${(fs.statSync(OUT).size / 1024).toFixed(0)} KB)`)
}
