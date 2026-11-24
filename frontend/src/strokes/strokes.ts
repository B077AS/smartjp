// How kana and kanji are written, stroke by stroke. The data (stroke-data.json) is copied from KanjiVG by tools/build-strokes.mjs and checked by
// tools/verify-strokes.mjs: nothing here is drawn by hand. KanjiVG is (c) Ulrich Apel and contributors, CC BY-SA 3.0, credited under Settings, Credits.
//
// The pack is big (2 MB: 2284 characters), so it is a separate file that is only loaded when a page asks for strokes (a dynamic import), and
// then kept.

/** One stroke of a character. */
export interface Stroke {
  /** SVG path data in a 109 x 109 box (KanjiVG's own, in the order the strokes are written). */
  d: string
  /** Where its number goes in the same box: a little behind the start of the stroke. */
  badge: [number, number]
  /** Which glyph of the face it belongs to (a combination such as きゃ has two, drawn side by side). */
  glyph: number
}

/** The strokes of one thing on a card: a kana, a combination of two kana, or a kanji. */
export interface StrokeSet {
  strokes: Stroke[]
  /** How many characters are drawn side by side, each in its own 109 x 109 box. */
  glyphs: number
}

/** The side of the box one glyph is drawn in. */
export const BOX = 109

interface Pack {
  box: number
  glyphs: Record<string, { s: string[]; n: [number, number][] }>
}

let loading: Promise<Pack> | null = null

function loadPack(): Promise<Pack> {
  loading ??= import('./stroke-data.json').then((module) => module.default as unknown as Pack)
  return loading
}

/** The strokes for a face ("あ", "きゃ", "日"), or null when one of its characters has none. Throws when the file cannot be loaded. */
export async function strokesFor(face: string): Promise<StrokeSet | null> {
  const pack = await loadPack()
  const chars = [...face]
  const strokes: Stroke[] = []
  for (const [glyph, ch] of chars.entries()) {
    const entry = pack.glyphs[(ch.codePointAt(0) ?? 0).toString(16)]
    if (!entry) return null
    entry.s.forEach((d, i) => strokes.push({ d, badge: entry.n[i], glyph }))
  }
  return { strokes, glyphs: chars.length }
}
