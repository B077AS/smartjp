import { toRomaji } from 'wanakana'

// Example words for a kana or a kanji. The data (words-data.json) is copied from JMdict by tools/build-words.mjs and checked by tools/verify-words.mjs;
// the rules for which words are picked are written at the top of the build script (common words only, no ranking by how common they are).
// JMdict is (c) EDRDG, CC BY-SA 4.0, credited under Settings, Credits. The file is loaded only when a page asks for words (a dynamic import).

/** One example word as the page shows it. */
export interface ExampleWord {
  /** How it is written: kanji and kana, or kana alone. */
  written: string
  /** The readings, as kana, with their romaji. Empty for a word written in kana alone (the written form is the reading). */
  readings: { kana: string; romaji: string }[]
  /** Romaji of the reading when the word is written in kana (the written form itself). */
  romaji: string
  /** The dictionary's first meanings. */
  meanings: string
}

interface Pack {
  words: { id: string; w: string; r?: string[]; g: string[] }[]
  index: Record<string, number[]>
}

let loading: Promise<Pack> | null = null

function loadPack(): Promise<Pack> {
  loading ??= import('./words-data.json').then((module) => module.default as unknown as Pack)
  return loading
}

/** The example words of one face ("あ", "きゃ", "日"); an empty list when there is none. Throws when the file cannot be loaded. */
export async function wordsFor(face: string): Promise<ExampleWord[]> {
  const pack = await loadPack()
  return (pack.index[face] ?? []).map((i) => {
    const word = pack.words[i]
    return {
      written: word.w,
      readings: (word.r ?? []).map((kana) => ({ kana, romaji: toRomaji(kana) })),
      romaji: word.r ? '' : toRomaji(word.w),
      meanings: word.g.join(', '),
    }
  })
}
