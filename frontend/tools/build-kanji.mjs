// Builds src/kanji/kanji-data.json (the kanji the flashcards teach) from KANJIDIC2, in the JSON form made by
// jmdict-simplified (https://github.com/scriptin/jmdict-simplified/releases, file kanjidic2-en-<version>.json.zip).
// Nothing in the pack is typed by hand: every kanji, reading and meaning is copied from that file. Only these fields are
// kept: grade, stroke count, English meanings, on and kun readings. The SKIP codes (CC BY-NC-SA) and the old JLPT field
// are NOT copied on purpose (see RESEARCH.md).
//
//   node tools/build-kanji.mjs <path to kanjidic2-en-x.y.z.json> [--grades 1,2]
//
// Download and unzip the file outside the repo. Then run `npm run verify:kanji -- <same path>`.
// Never normalise the characters (NFKC): some kanji are compatibility code points and would turn into another one.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export const OUT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'src', 'kanji', 'kanji-data.json')

/** The pack for these grades, made from the parsed KANJIDIC2 file. Used by the verifier too, so both agree on the rules. */
export function buildPack(dict, grades) {
  const kanji = dict.characters
    .filter((c) => grades.includes(c.misc.grade))
    .map((c) => {
      const readings = c.readingMeaning?.groups?.flatMap((g) => g.readings) ?? []
      const meanings = c.readingMeaning?.groups?.flatMap((g) => g.meanings).filter((m) => m.lang === 'en').map((m) => m.value) ?? []
      return {
        c: c.literal,
        grade: c.misc.grade,
        strokes: c.misc.strokeCounts[0],
        meanings,
        on: readings.filter((r) => r.type === 'ja_on').map((r) => r.value),
        kun: readings.filter((r) => r.type === 'ja_kun').map((r) => r.value),
      }
    })
    // Few strokes first, so the simple shapes come first; the code point only makes the order the same every time.
    .sort((a, b) => a.grade - b.grade || a.strokes - b.strokes || a.c.codePointAt(0) - b.c.codePointAt(0))
  return {
    source: { name: 'KANJIDIC2', version: dict.version, dictDate: dict.dictDate, databaseVersion: dict.databaseVersion, via: 'jmdict-simplified' },
    grades,
    kanji,
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2)
  const at = args.indexOf('--grades')
  const file = args.filter((a, i) => !a.startsWith('--') && i !== at + 1 || (at < 0 && !a.startsWith('--')))[0]
  if (!file) {
    console.error('usage: node tools/build-kanji.mjs <kanjidic2-en.json> [--grades 1,2]')
    process.exit(2)
  }
  const gradeArg = at >= 0 ? args[at + 1] : '1'
  const grades = gradeArg.split(',').map(Number)
  const pack = buildPack(JSON.parse(fs.readFileSync(file, 'utf8')), grades)
  fs.writeFileSync(OUT, JSON.stringify(pack, null, 1) + '\n')
  console.log(`wrote ${pack.kanji.length} kanji of grade ${grades.join(',')} from KANJIDIC2 ${pack.source.version} (${pack.source.dictDate}) to ${OUT}`)
}
