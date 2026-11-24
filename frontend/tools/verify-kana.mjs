// Checks src/kana/kana.js against independent sources. Run it after ANY change to the kana table.
//
//   node tools/verify-kana.mjs [--wikipedia hepburn.txt] [--unicode UnicodeData.txt]
//
//   --wikipedia  raw wikitext of https://en.wikipedia.org/wiki/Hepburn_romanization
//                (https://en.wikipedia.org/w/index.php?title=Hepburn_romanization&action=raw): its table lists
//                hiragana, katakana and the Hepburn reading side by side.
//   --unicode    https://www.unicode.org/Public/UCD/latest/ucd/UnicodeData.txt: the character names
//                ("HIRAGANA LETTER KA") spell every kana's reading.
//
// Without the files only the checks that need no download run (counts, WanaKana). Download the files into a
// folder OUTSIDE the repo; they are not part of the project.
import { readFileSync } from 'node:fs'
import { toRomaji } from 'wanakana'
import { CARDS, GROUPS, cardsFor, isCorrectKana, isCorrectReading } from '../src/kana/kana.ts'

const args = process.argv.slice(2)
const argValue = (flag) => (args.includes(flag) ? args[args.indexOf(flag) + 1] : null)
const wikipediaFile = argValue('--wikipedia')
const unicodeFile = argValue('--unicode')

let failures = 0
const fail = (message) => {
  failures++
  console.log('  FAIL ' + message)
}
const ok = (message) => console.log('  ok   ' + message)
const hiragana = CARDS.filter((c) => c.script === 'hiragana')
const katakana = CARDS.filter((c) => c.script === 'katakana')

// ---- 1. shape -------------------------------------------------------------------------------------------------
console.log('Counts and ids')
// basic + special = the 46 kana in modern use; voiced (g z d b) = the 20 dakuten kana; handakuten = the 5 p kana.
const sizes = { basic: 44, special: 2, voiced: 20, handakuten: 5, combo: 33 }
for (const [section, expected] of Object.entries(sizes)) {
  const groupIds = GROUPS.filter((g) => g.section === section).map((g) => g.id)
  for (const script of ['hiragana', 'katakana']) {
    const n = cardsFor([script], groupIds).length
    n === expected ? ok(`${script} ${section}: ${n}`) : fail(`${script} ${section}: ${n}, expected ${expected}`)
  }
}
new Set(CARDS.map((c) => c.id)).size === CARDS.length ? ok('card ids are unique') : fail('duplicate card ids')

// ---- 2. WanaKana ----------------------------------------------------------------------------------------------
console.log('WanaKana (independent conversion tables)')
const wanaDiffs = []
for (const c of hiragana) {
  const wana = toRomaji(c.hira)
  if (wana !== c.romaji) wanaDiffs.push(`${c.hira}: table ${c.romaji}, WanaKana ${wana}`)
}
console.log('  WanaKana toRomaji differs from the table for: ' + (wanaDiffs.join(' | ') || 'nothing'))
for (const c of hiragana) {
  if (!isCorrectReading(c, c.romaji)) fail(`${c.hira}: its own reading "${c.romaji}" is not accepted`)
  if (!isCorrectKana(c, c.hira)) fail(`${c.hira}: typing the kana itself is not accepted`)
}
ok('every card accepts its own reading and its own kana')
// A reading must not be accepted for a kana with a different reading (besides the particles は=wa and へ=e).
for (const a of hiragana) {
  for (const b of hiragana) {
    if (a === b || a.romaji === b.romaji) continue
    if (isCorrectReading(a, b.romaji)) {
      const allowed = (a.hira === 'は' && b.romaji === 'wa') || (a.hira === 'へ' && b.romaji === 'e')
      if (!allowed) fail(`${a.hira} accepts "${b.romaji}", which is ${b.hira}`)
    }
  }
}
ok('no card accepts the reading of a different kana (besides the particles は=wa, へ=e)')
for (const alt of [['shi', 'し'], ['si', 'し'], ['chi', 'ち'], ['ti', 'ち'], ['tsu', 'つ'], ['tu', 'つ'], ['fu', 'ふ'], ['hu', 'ふ'], ['zi', 'じ'], ['sya', 'しゃ'], ['jya', 'じゃ'], ['tya', 'ちゃ']]) {
  const card = hiragana.find((c) => c.hira === alt[1])
  isCorrectReading(card, alt[0]) ? null : fail(`"${alt[0]}" should be accepted for ${alt[1]}`)
}
ok('alternative spellings (si ti tu hu zi sya jya tya) are accepted')
for (const [typed, hira] of [['di', 'ぢ'], ['du', 'づ'], ['ji', 'ぢ'], ['zu', 'づ'], ['wo', 'を'], ['o', 'を'], ['n', 'ん']]) {
  const card = hiragana.find((c) => c.hira === hira)
  isCorrectReading(card, typed) ? null : fail(`"${typed}" should be accepted for ${hira}`)
}
ok('di du (and ji zu), wo o, n are accepted where they must be')
// ぢ/づ and じ/ず are told apart by their own spelling.
isCorrectReading(hiragana.find((c) => c.hira === 'じ'), 'di') ? fail('じ accepts "di"') : ok('じ rejects "di"')
isCorrectReading(hiragana.find((c) => c.hira === 'ぢ'), 'zi') ? fail('ぢ accepts "zi"') : ok('ぢ rejects "zi"')

// ---- 3. Wikipedia Hepburn table -------------------------------------------------------------------------------
if (wikipediaFile) {
  console.log('Wikipedia Hepburn table (hiragana, katakana, reading)')
  const text = readFileSync(wikipediaFile, 'utf8')
  const entries = new Map()
  for (const m of text.matchAll(/([ぁ-ゖ]+) ([ァ-ヺ]+) \{\{transliteration\|ja\|hepburn\|([a-z']+)\}\}/g)) {
    if (!entries.has(m[1])) entries.set(m[1], { kata: m[2], hepburn: m[3] })
  }
  console.log(`  parsed ${entries.size} entries from the table`)
  for (const h of hiragana) {
    const e = entries.get(h.hira)
    if (!e) fail(`${h.hira} is not in the Wikipedia table`)
    else if (e.hepburn !== h.romaji) fail(`${h.hira}: table ${h.romaji}, Wikipedia ${e.hepburn}`)
  }
  for (const k of katakana) {
    const e = entries.get(k.hira)
    if (e && e.kata !== k.kana) fail(`${k.hira}: katakana ${k.kana}, Wikipedia ${e.kata}`)
  }
  ok('every hiragana card: same reading and same katakana as Wikipedia')
  const inTable = new Set(hiragana.map((c) => c.hira))
  const missing = [...entries.keys()].filter((h) => !inTable.has(h))
  console.log('  in Wikipedia but deliberately not in the table: ' + missing.join(' '))
  const expectedMissing = ['ゐ', 'ゑ', 'ぢゃ', 'ぢゅ', 'ぢょ']
  expectedMissing.sort().join() === missing.sort().join()
    ? ok('the left-out entries are exactly ゐ ゑ ぢゃ ぢゅ ぢょ')
    : fail('unexpected difference in what is left out')
}

// ---- 4. Unicode names -----------------------------------------------------------------------------------------
if (unicodeFile) {
  console.log('Unicode character names')
  const names = new Map()
  for (const line of readFileSync(unicodeFile, 'utf8').split('\n')) {
    const [code, name] = line.split(';')
    if (name) names.set(String.fromCodePoint(parseInt(code, 16)), name)
  }
  const syllable = (ch, script) => {
    const name = names.get(ch) ?? ''
    const prefix = (script === 'hiragana' ? 'HIRAGANA' : 'KATAKANA') + ' LETTER '
    return name.startsWith(prefix) ? name.slice(prefix.length).toLowerCase() : null
  }
  // Unicode spells the syllables the Nihon-shiki way. These are the ONLY places where it differs from Hepburn.
  const nihonShiki = { し: 'si', ち: 'ti', つ: 'tu', ふ: 'hu', じ: 'zi', ぢ: 'di', づ: 'du', を: 'wo' }
  const differing = []
  for (const c of hiragana.filter((x) => [...x.hira].length === 1)) {
    const unicode = syllable(c.hira, 'hiragana')
    if (unicode === null) fail(`${c.hira}: no Unicode name`)
    else if (unicode !== c.romaji) {
      differing.push(c.hira)
      if (nihonShiki[c.hira] !== unicode) fail(`${c.hira}: table ${c.romaji}, Unicode ${unicode}`)
    }
  }
  console.log('  single kana whose Unicode name differs from Hepburn: ' + differing.join(' '))
  differing.sort().join() === Object.keys(nihonShiki).sort().join()
    ? ok('only し ち つ ふ じ ぢ づ を differ, and each by its Nihon-shiki spelling (si ti tu hu zi di du wo)')
    : fail('the set of differing kana is not the expected one')
  // Katakana: same syllable name as the hiragana one.
  for (const c of katakana.filter((x) => [...x.hira].length === 1)) {
    if (syllable(c.kana, 'katakana') !== syllable(c.hira, 'hiragana')) fail(`${c.kana}: Unicode name does not match ${c.hira}`)
  }
  ok('every single katakana has the same Unicode syllable as its hiragana')
  // Combinations: a base kana plus a SMALL YA / YU / YO.
  const small = { ゃ: ['ya', 'a'], ゅ: ['yu', 'u'], ょ: ['yo', 'o'] }
  for (const c of hiragana.filter((x) => [...x.hira].length === 2)) {
    const [base, tail] = [...c.hira]
    if (names.get(tail) !== 'HIRAGANA LETTER SMALL ' + small[tail][0].toUpperCase()) fail(`${c.hira}: second character is not a small ya/yu/yo`)
    const baseCard = hiragana.find((x) => x.hira === base)
    // Rule (Hepburn): base reading minus its final "i", plus y + vowel; sh/ch/j drop the y.
    const stem = baseCard.romaji.slice(0, -1)
    const expected = /(sh|ch|j)$/.test(stem) ? stem + small[tail][1] : stem + 'y' + small[tail][1]
    if (expected !== c.romaji) fail(`${c.hira}: table ${c.romaji}, rule says ${expected}`)
  }
  ok('every combination = base kana + small ya/yu/yo, reading follows the Hepburn rule')
}

console.log(failures === 0 ? '\nALL CHECKS PASSED' : `\n${failures} CHECK(S) FAILED`)
process.exit(failures === 0 ? 0 : 1)
