// Checks src/strokes/stroke-data.json (run it after every change to the pack):
//   node tools/verify-strokes.mjs <kanjivg-YYYYMMDD.xml.gz>
//  1. The pack is exactly what tools/build-strokes.mjs makes from the KanjiVG file (no hand edits, not stale).
//  2. Every kana of the flashcard table (each part of a combination) and every kanji of the kanji pack has strokes, and nothing else is in it.
//  3. Every stroke is a well-formed SVG path inside the 109 x 109 box, and every stroke has its number position.
//  4. INDEPENDENT CHECK for the kanji: the number of strokes equals the stroke count KANJIDIC2 gives (the kanji pack), for all 2136.
//     There is no second source for the kana: their stroke counts are only reported, not checked (see RESEARCH.md).
import fs from 'node:fs'
import path from 'node:path'
import zlib from 'node:zlib'
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { CARDS } from '../src/kana/kana.ts'
import { SIZE, SOURCE_OUT, buildPack, keyOf, neededCharacters, parseKanjiVg } from './build-strokes.mjs'

const file = process.argv.slice(2).find((a) => !a.startsWith('--'))
if (!file) {
  console.error('usage: node tools/verify-strokes.mjs <kanjivg-YYYYMMDD.xml.gz>')
  process.exit(2)
}
const here = path.dirname(fileURLToPath(import.meta.url))
const pack = JSON.parse(fs.readFileSync(path.join(here, '..', 'src', 'strokes', 'stroke-data.json'), 'utf8'))
const kanjiPack = JSON.parse(fs.readFileSync(path.join(here, '..', 'src', 'kanji', 'kanji-data.json'), 'utf8'))
const raw = fs.readFileSync(file)
const xml = (file.endsWith('.gz') ? zlib.gunzipSync(raw) : raw).toString('utf8')
let checks = 0
const ok = (cond, message) => {
  assert.ok(cond, message)
  checks++
}

// 1
const needed = neededCharacters(CARDS, kanjiPack)
assert.deepEqual(pack, buildPack(parseKanjiVg(xml), needed, pack.source.release), 'the pack differs from what build-strokes.mjs makes from this file')
checks++

assert.deepEqual(JSON.parse(fs.readFileSync(SOURCE_OUT, 'utf8')), pack.source, 'stroke-source.json differs from the pack')
checks++

// 2
const keys = new Set(needed.map(keyOf))
ok(Object.keys(pack.glyphs).length === keys.size, 'the pack has other characters than the app needs')
for (const key of keys) ok(pack.glyphs[key], 'no strokes for U+' + key)

// 3: a path made only of the commands KanjiVG uses and numbers, starting with a move (a first "m" is an absolute move too)
const pathRe = /^[Mm][-\d.,\sMmCcSsLlHhVvQqTtZz]+$/
for (const [key, glyph] of Object.entries(pack.glyphs)) {
  ok(glyph.s.length >= 1 && glyph.s.length === glyph.n.length, 'strokes and numbers differ for U+' + key)
  for (const d of glyph.s) ok(pathRe.test(d), 'odd path in U+' + key + ': ' + d.slice(0, 50))
  for (const [x, y] of glyph.n) ok(x >= 0 && x <= SIZE && y >= 0 && y <= SIZE, 'number outside the box in U+' + key)
}

// 4: 2127 of the 2136 agree. These nine differ by exactly one stroke between the two sources (checked 2026-11-24 against KanjiVG r20260714 and
// KANJIDIC2 3.6.2); it is not known which source is right, so the page shows the number of strokes it DRAWS. A tenth difference, or one of these
// changing, fails the check, so a new KanjiVG release cannot change a drawing unnoticed.
const KNOWN = { '牙': [5, 4], '葛': [11, 12], '僅': [12, 13], '遜': [13, 14], '遡': [13, 14], '餅': [14, 15], '餌': [14, 15], '賭': [15, 16], '謎': [16, 17] }
const wrong = kanjiPack.kanji.filter((k) => pack.glyphs[keyOf(k.c)].s.length !== k.strokes)
for (const k of wrong) {
  const drawn = pack.glyphs[keyOf(k.c)].s.length
  ok(KNOWN[k.c] && KNOWN[k.c][0] === k.strokes && KNOWN[k.c][1] === drawn, `new stroke count difference: ${k.c} KANJIDIC2 ${k.strokes}, KanjiVG ${drawn}`)
}
ok(wrong.length === Object.keys(KNOWN).length, 'a known stroke count difference is gone: update KNOWN')
console.log(`stroke counts: ${kanjiPack.kanji.length - wrong.length} of ${kanjiPack.kanji.length} kanji agree with KANJIDIC2, ${wrong.length} are the known exceptions`)

const kanaKeys = [...new Set(CARDS.flatMap((c) => [...c.kana]))].map((ch) => [ch, pack.glyphs[keyOf(ch)].s.length])
console.log('kana strokes (reported, no second source):', kanaKeys.map(([ch, n]) => ch + n).join(' '))
console.log(`ok: ${checks} checks, ${Object.keys(pack.glyphs).length} characters (${kanjiPack.kanji.length} kanji), KanjiVG ${pack.source.release}`)
