// Checks src/words/words-data.json (run it after every change to the pack):
//   node tools/verify-words.mjs <jmdict-eng-common.json>
//  1. The pack is exactly what tools/build-words.mjs makes from the JMdict file (no hand edits, not stale).
//  2. Every word is really in the file: the id exists, the written form is one of the entry's own forms, every reading is one of its kana forms
//     (and applies to that form), every meaning is one of the entry's own glosses.
//  3. Every word listed for a character fits it: a kanji's word contains the kanji, a kana's word has a reading that starts with it.
//  4. At most five per character, no word twice for one character, every index points at a word, every kanji of a word is a kanji the app teaches.
// It also REPORTS which characters have no word at all.
import fs from 'node:fs'
import path from 'node:path'
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { CARDS } from '../src/kana/kana.ts'
import { PER_CHARACTER, SOURCE_OUT, buildPack } from './build-words.mjs'

const file = process.argv.slice(2).find((a) => !a.startsWith('--'))
if (!file) {
  console.error('usage: node tools/verify-words.mjs <jmdict-eng-common.json>')
  process.exit(2)
}
const here = path.dirname(fileURLToPath(import.meta.url))
const pack = JSON.parse(fs.readFileSync(path.join(here, '..', 'src', 'words', 'words-data.json'), 'utf8'))
const kanjiPack = JSON.parse(fs.readFileSync(path.join(here, '..', 'src', 'kanji', 'kanji-data.json'), 'utf8'))
const dict = JSON.parse(fs.readFileSync(file, 'utf8'))
let checks = 0
const ok = (cond, message) => {
  assert.ok(cond, message)
  checks++
}

// 1
assert.deepEqual(pack, buildPack(dict, kanjiPack, CARDS), 'the pack differs from what build-words.mjs makes from this file')
checks++
ok(pack.source.version === dict.version && pack.source.dictDate === dict.dictDate, 'the pack was made from another version of the file')

assert.deepEqual(JSON.parse(fs.readFileSync(SOURCE_OUT, 'utf8')), pack.source, 'words-source.json differs from the pack')
checks++

// 2
const byId = new Map(dict.words.map((w) => [w.id, w]))
const IDEOGRAPH = /[㐀-䶿一-鿿豈-﫿\u{20000}-\u{2fa1f}]/u
const taught = new Set(kanjiPack.kanji.map((k) => k.c))
for (const w of pack.words) {
  const entry = byId.get(w.id)
  ok(entry, 'no such JMdict id ' + w.id)
  const kanjiForms = entry.kanji.map((k) => k.text)
  const kanaForms = entry.kana.map((k) => k.text)
  ok(kanjiForms.includes(w.w) || kanaForms.includes(w.w), `${w.id} ${w.w}: not a form of the entry`)
  const readings = w.r ?? [w.w]
  for (const r of readings) {
    const kana = entry.kana.find((k) => k.text === r)
    ok(kana, `${w.id} ${w.w}: reading ${r} is not a kana form of the entry`)
    ok(kana.common, `${w.id} ${w.w}: reading ${r} is not flagged common`)
    if (kanjiForms.includes(w.w)) ok(kana.appliesToKanji.includes('*') || kana.appliesToKanji.includes(w.w), `${w.id} ${w.w}: reading ${r} does not apply to it`)
  }
  const glosses = new Set(entry.sense.flatMap((s) => s.gloss.map((g) => g.text)))
  ok(w.g.length >= 1 && w.g.length <= 3, `${w.id}: wrong number of meanings`)
  for (const g of w.g) ok(glosses.has(g), `${w.id} ${w.w}: meaning "${g}" is not one of the entry's`)
  for (const ch of w.w) if (IDEOGRAPH.test(ch)) ok(taught.has(ch), `${w.id} ${w.w}: ${ch} is not a kanji the app teaches`)
}

// 3 and 4
const kanaStrings = new Set(CARDS.map((c) => c.kana))
for (const [key, list] of Object.entries(pack.index)) {
  ok(taught.has(key) || kanaStrings.has(key), 'a key that is no character of the app: ' + key)
  ok(list.length >= 1 && list.length <= PER_CHARACTER, `${key}: ${list.length} words`)
  ok(new Set(list).size === list.length, `${key}: a word twice`)
  for (const i of list) {
    const w = pack.words[i]
    ok(w, `${key}: no word at index ${i}`)
    if (taught.has(key)) ok(w.w.includes(key), `${key}: ${w.w} does not contain it`)
    else ok((w.r ?? [w.w]).some((r) => r.startsWith(key)), `${key}: ${w.w} has no reading that starts with it`)
  }
}

const without = [...taught, ...kanaStrings].filter((c) => !pack.index[c])
console.log(`characters with no word (${without.length}): ${without.join(' ')}`)
console.log(`ok: ${checks} checks, ${pack.words.length} words, ${Object.keys(pack.index).length} characters, JMdict ${pack.source.version} (${pack.source.dictDate})`)
