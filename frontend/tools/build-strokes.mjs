// Builds src/strokes/stroke-data.json (how each kana and kanji is written, stroke by stroke) from KanjiVG
// (https://kanjivg.tagaini.net, release files on https://github.com/KanjiVG/kanjivg/releases, the "kanjivg-<date>.xml.gz" asset).
// Nothing in the pack is drawn or typed by hand: every stroke path is copied from that file, in the file's own order.
//
//   node tools/build-strokes.mjs <path to kanjivg-YYYYMMDD.xml.gz> [--release r20260714]
//
// Download the file outside the repo. Then run `npm run verify:strokes -- <same path>`.
//
// What is kept for each character: the strokes (the SVG path data, as written in KanjiVG, 109 x 109 box) and, for each stroke, where to put its
// number. KanjiVG's own number positions are in a part of the file this release does not have, so the position is worked out here: a point a little
// behind the start of the stroke, on the line the stroke starts along. It only places a label; it changes no stroke.
//
// KanjiVG is (c) Ulrich Apel and contributors, licensed CC BY-SA 3.0: credited under Settings, About. Keep this file apart from the
// EDRDG data (KANJIDIC2, JMdict, CC BY-SA 4.0).
import fs from 'node:fs'
import path from 'node:path'
import zlib from 'node:zlib'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
export const OUT = path.join(here, '..', 'src', 'strokes', 'stroke-data.json')
// The credits (About screen) only need who and which release, not the 2 MB of strokes: a small file next to the pack says it.
export const SOURCE_OUT = path.join(here, '..', 'src', 'strokes', 'stroke-source.json')
export const SIZE = 109

/** The key of a character in the pack: its code point in hex, without padding ("3042", "65e5", "20b9f"), like the card ids. */
export const keyOf = (ch) => ch.codePointAt(0).toString(16)

/** Parses the KanjiVG file into code point (hex, no padding) -> the stroke paths in order. */
export function parseKanjiVg(xml) {
  const out = new Map()
  for (const m of xml.matchAll(/<kanji id="kvg:kanji_([0-9a-f]+)">([\s\S]*?)<\/kanji>/g)) {
    const strokes = [...m[2].matchAll(/<path id="kvg:[0-9a-f]+-s(\d+)"[^>]*?\sd="([^"]+)"/g)].map((p) => [Number(p[1]), p[2]])
    strokes.sort((a, b) => a[0] - b[0])
    // The numbers must be 1, 2, 3 ... with nothing missing, or the order would not be the order they are written in.
    if (strokes.some(([n], i) => n !== i + 1)) throw new Error('strokes of ' + m[1] + ' are not numbered 1..n')
    out.set(String(parseInt(m[1], 16).toString(16)), strokes.map(([, d]) => d))
  }
  return out
}

const round1 = (n) => Math.round(n * 10) / 10

/** Where the number of a stroke goes: 6.5 units behind its start, on the line the stroke begins along. */
export function badgeOf(d) {
  const m = /^[Mm]\s*(-?[\d.]+)[,\s]\s*(-?[\d.]+)\s*([a-zA-Z])\s*(-?[\d.]+)(?:[,\s]\s*(-?[\d.]+))?/.exec(d)
  if (!m) throw new Error('cannot read the start of a stroke: ' + d.slice(0, 40))
  const x0 = Number(m[1])
  const y0 = Number(m[2])
  const cmd = m[3]
  const a = Number(m[4])
  const b = m[5] === undefined ? 0 : Number(m[5])
  let dx = 0
  let dy = 0
  if ('cCsSlLqQtT'.includes(cmd)) {
    dx = cmd === cmd.toLowerCase() ? a : a - x0
    dy = cmd === cmd.toLowerCase() ? b : b - y0
  } else if (cmd === 'h') dx = a
  else if (cmd === 'H') dx = a - x0
  else if (cmd === 'v') dy = a
  else if (cmd === 'V') dy = a - y0
  let len = Math.hypot(dx, dy)
  if (len < 0.5) {
    dx = -1
    dy = -1
    len = Math.SQRT2
  }
  const clamp = (v) => Math.min(SIZE - 4.5, Math.max(4.5, v))
  return [round1(clamp(x0 - (dx / len) * 6.5)), round1(clamp(y0 - (dy / len) * 6.5))]
}

/** The characters the app needs strokes for: every kana of the flashcard table (each part of a combination) and every kanji of the pack. */
export function neededCharacters(kanaCards, kanjiPack) {
  const set = new Set()
  for (const card of kanaCards) for (const ch of card.kana) set.add(ch)
  for (const k of kanjiPack.kanji) set.add(k.c)
  return [...set].sort((a, b) => a.codePointAt(0) - b.codePointAt(0))
}

export function buildPack(strokesByKey, needed, release) {
  const glyphs = {}
  for (const ch of needed) {
    const key = keyOf(ch)
    const strokes = strokesByKey.get(key)
    if (!strokes) throw new Error('KanjiVG has no strokes for ' + ch + ' (U+' + key + ')')
    glyphs[key] = { s: strokes, n: strokes.map(badgeOf) }
  }
  return {
    source: { name: 'KanjiVG', release, licence: 'CC BY-SA 3.0', url: 'https://kanjivg.tagaini.net' },
    box: SIZE,
    glyphs,
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2)
  const at = args.indexOf('--release')
  const file = args.find((a, i) => !a.startsWith('--') && i !== at + 1)
  if (!file) {
    console.error('usage: node tools/build-strokes.mjs <kanjivg-YYYYMMDD.xml.gz> [--release r20260714]')
    process.exit(2)
  }
  const release = at >= 0 ? args[at + 1] : path.basename(file)
  const raw = fs.readFileSync(file)
  const xml = (file.endsWith('.gz') ? zlib.gunzipSync(raw) : raw).toString('utf8')
  const { CARDS } = await import('../src/kana/kana.ts')
  const kanjiPack = JSON.parse(fs.readFileSync(path.join(here, '..', 'src', 'kanji', 'kanji-data.json'), 'utf8'))
  const pack = buildPack(parseKanjiVg(xml), neededCharacters(CARDS, kanjiPack), release)
  fs.mkdirSync(path.dirname(OUT), { recursive: true })
  fs.writeFileSync(OUT, JSON.stringify(pack) + '\n')
  fs.writeFileSync(SOURCE_OUT, JSON.stringify(pack.source, null, 1) + '\n')
  console.log(`wrote strokes for ${Object.keys(pack.glyphs).length} characters (KanjiVG ${release}) to ${OUT} (${(fs.statSync(OUT).size / 1024).toFixed(0)} KB)`)
}
