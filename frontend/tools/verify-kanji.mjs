// Checks src/kanji/kanji-data.json (run it after every change to the pack):
//   node tools/verify-kanji.mjs <kanjidic2-en.json> [--kanjiapi]
//  1. The pack is exactly what tools/build-kanji.mjs makes from the KANJIDIC2 file (no hand edits, not stale).
//  2. Every entry is one character, the same code point KANJIDIC2 lists (never normalised), with a meaning, readings
//     in the right script (on = katakana, kun = hiragana with the "." and "-" marks) and a stroke count.
//  3. With --kanjiapi (needs the internet): the same kanji, grade, strokes, meanings and readings as kanjiapi.dev.
//     NOTE: kanjiapi.dev is built from KANJIDIC2 too, so this checks our processing, not the dictionary itself.
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import assert from 'node:assert/strict'
import { toHiragana, toKana, toRomaji } from 'wanakana'
import { buildPack } from './build-kanji.mjs'

const args = process.argv.slice(2)
const file = args.find((a) => !a.startsWith('--'))
if (!file) {
  console.error('usage: node tools/verify-kanji.mjs <kanjidic2-en.json> [--kanjiapi]')
  process.exit(2)
}
const here = path.dirname(fileURLToPath(import.meta.url))
const pack = JSON.parse(fs.readFileSync(path.join(here, '..', 'src', 'kanji', 'kanji-data.json'), 'utf8'))
const dict = JSON.parse(fs.readFileSync(file, 'utf8'))
let checks = 0
const ok = (cond, message) => {
  assert.ok(cond, message)
  checks++
}

// 1
assert.deepEqual(pack, buildPack(dict, pack.grades), 'the pack differs from what build-kanji.mjs makes from this file')
checks++
ok(pack.source.version === dict.version && pack.source.dictDate === dict.dictDate, 'the pack was made from another version of the file')

// 2
const bySource = new Map(dict.characters.map((c) => [c.literal, c]))
// "-" marks a reading that only occurs as a suffix or prefix; "." splits a kun reading from its okurigana. Also "-\u3044.\u308b".
const katakana = /^-?[\u30a1-\u30fa\u30fc]+-?$/u
const hiraganaKun = /^-?[\u3041-\u3096]+([.][\u3041-\u3096]+)?-?$/u
// KANJIDIC2 also lists two katakana loanword readings as "kun" (\u5fd7 \u30b7\u30ea\u30f3\u30b0 "shilling", \u7c89 \u30c7\u30b7\u30e1\u30fc\u30c8\u30eb "decimeter"): kept as the dictionary has them.
const KATAKANA_KUN = new Set(['\u30b7\u30ea\u30f3\u30b0', '\u30c7\u30b7\u30e1\u30fc\u30c8\u30eb'])
const seen = new Set()
for (const k of pack.kanji) {
  ok([...k.c].length === 1, `${k.c}: not a single character`)
  ok(k.c === k.c.normalize('NFKC'), `${k.c}: changes under normalisation (compatibility code point)`)
  ok(!seen.has(k.c), `${k.c}: twice`)
  seen.add(k.c)
  const source = bySource.get(k.c)
  ok(source, `${k.c}: not in KANJIDIC2`)
  const ucs = source.codepoints.find((p) => p.type === 'ucs').value
  ok(k.c.codePointAt(0).toString(16) === ucs.toLowerCase(), `${k.c}: code point differs from KANJIDIC2 (${ucs})`)
  ok(pack.grades.includes(k.grade) && k.grade === source.misc.grade, `${k.c}: wrong grade`)
  ok(Number.isInteger(k.strokes) && k.strokes > 0, `${k.c}: stroke count`)
  ok(k.meanings.length > 0 && k.meanings.every((m) => m.trim() === m && m !== ''), `${k.c}: meanings`)
  ok(k.on.length + k.kun.length > 0, `${k.c}: no reading at all`)
  for (const r of k.on) ok(katakana.test(r), `${k.c}: on reading ${r} is not katakana`)
  for (const r of k.kun) ok(hiraganaKun.test(r) || KATAKANA_KUN.has(r), `${k.c}: kun reading ${r} is not hiragana`)
}
// The romaji the app shows next to a reading (WanaKana's toRomaji) must be typeable: turned back into kana it must give the
// same reading. This mirrors src/kanji/kanji.ts: づ = ず and ぢ = じ (romaji cannot tell them apart), and a final small tsu
// is dropped by WanaKana (ジッ shows as "ji"), so the reading without it is what must come back.
const canon = (kana) => kana.replace(/ぢ/g, 'じ').replace(/づ/g, 'ず')
let readings = 0
let dropsTsu = 0
for (const k of pack.kanji) {
  for (const reading of [...k.on, ...k.kun]) {
    const kana = toHiragana(reading.replace(/-/g, '').replace('.', ''))
    const romaji = toRomaji(kana)
    ok(/^[a-z']+$/.test(romaji), `${k.c}: romaji of ${reading} is "${romaji}"`)
    const wanted = kana.endsWith('っ') ? kana.slice(0, -1) : kana
    if (wanted !== kana) dropsTsu++
    ok(canon(toHiragana(toKana(romaji))) === canon(wanted), `${k.c}: ${reading} shows as ${romaji}, which types as ${toKana(romaji)}`)
    readings++
  }
}
console.log(`romaji: ${readings} readings round-trip through WanaKana (${dropsTsu} end in a small tsu that the romaji leaves out)`)

// Everything KANJIDIC2 has for these grades is in the pack.
const expected = dict.characters.filter((c) => pack.grades.includes(c.misc.grade)).length
ok(pack.kanji.length === expected, `pack has ${pack.kanji.length} kanji, KANJIDIC2 has ${expected} for grades ${pack.grades}`)

// 3
if (args.includes('--kanjiapi')) {
  const report = []
  const checksBefore = checks
  // Up to 3 tries per request (a hobby service drops some), and 12 requests at a time.
  const get = async (url) => {
    for (let attempt = 1; ; attempt++) {
      try {
        return await (await fetch(url, { signal: AbortSignal.timeout(20000) })).json()
      } catch (error) {
        if (attempt === 3) throw error
      }
    }
  }
  // kanjiapi.dev is built from an OLDER KANJIDIC2, so differences are expected and are only REPORTED, never failed:
  // the 2020 school curriculum moved kanji between grades 4 to 8 (KANJIDIC2 3.6.2: 202 / 193 / 191 in grades 4 / 5 / 6),
  // and for 塡 頰 𠮟 剝 kanjiapi.dev has the common forms 填 頬 叱 剥. Everything else must agree.
  const same = (a, b) => JSON.stringify([...a].sort()) === JSON.stringify([...b].sort())
  for (const grade of pack.grades) {
    const list = await get(`https://kanjiapi.dev/v1/kanji/grade-${grade}`)
    const mine = pack.kanji.filter((k) => k.grade === grade).map((k) => k.c)
    const onlyApi = list.filter((c) => !mine.includes(c))
    const onlyMine = mine.filter((c) => !list.includes(c))
    if (onlyApi.length + onlyMine.length > 0) {
      report.push(`grade ${grade}: ${mine.length} here, ${list.length} at kanjiapi.dev; only here: ${onlyMine.join('') || '-'}; only there: ${onlyApi.join('') || '-'}`)
    }
  }
  const checkAgainstApi = async (k) => {
    const response = await fetch(`https://kanjiapi.dev/v1/kanji/${encodeURIComponent(k.c)}`, { signal: AbortSignal.timeout(20000) })
    if (response.status === 404) return report.push(`${k.c}: not at kanjiapi.dev`)
    const api = await response.json()
    const different = []
    if (api.grade !== k.grade) different.push(`grade ${k.grade} / ${api.grade}`)
    if (api.stroke_count !== k.strokes) different.push('strokes')
    if (!same(api.meanings, k.meanings)) different.push('meanings')
    if (!same(api.on_readings, k.on)) different.push('on readings')
    if (!same(api.kun_readings, k.kun)) different.push('kun readings')
    if (different.length) report.push(`${k.c}: ${different.join(', ')}`)
    else checks++
  }
  for (let i = 0; i < pack.kanji.length; i += 12) await Promise.all(pack.kanji.slice(i, i + 12).map(checkAgainstApi))
  console.log(`kanjiapi.dev: ${checks - checksBefore} kanji agree in every field; ${report.length} differences (not failures):`)
  for (const line of report) console.log('  ' + line)
}

console.log(`OK: ${pack.kanji.length} kanji (grades ${pack.grades.join(',')}), KANJIDIC2 ${pack.source.version}, ${checks} checks${args.includes('--kanjiapi') ? ', incl. kanjiapi.dev' : ''}`)
