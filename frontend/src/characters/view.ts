import { reactive } from 'vue'

// Where the user is on the Characters page: kept here (not inside the page) so leaving for the flashcards and coming back finds the same character,
// tab and search. Not saved anywhere: a reload starts again at あ.

export type Tab = 'hiragana' | 'katakana' | 'kanji'

export const view = reactive({
  tab: 'hiragana' as Tab,
  grade: 1,
  /** The card id of the character whose entry is open. */
  selectedId: 'hiragana:3042',
  query: '',
})
