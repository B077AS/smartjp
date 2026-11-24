import { reactive } from 'vue'
import { api, type CardList } from '../api/client'

// The user's own flashcard lists: a name and a set of individual cards. They are kept in the SQLite database next to the
// application (the website's or the desktop app's own), through api/client.ts; this file only holds what the page shows.
// The rules (names, how many lists, "the same name replaces") are in Java (ListsService): the page asks and shows the answer.
// A list holds card ids only, so a list never goes stale when the kana table is corrected.

export type { CardList }

/** The longest name; the same limit as ListsService.MAX_NAME (the page uses it only to stop typing early). */
export const MAX_NAME = 40

export const lists = reactive<CardList[]>([])

/** The name as Java will clean it: runs of whitespace become one space, ends trimmed. */
export const cleanName = (name: string): string => name.replace(/\s+/g, ' ').trim().slice(0, MAX_NAME)
const sameName = (a: string, b: string): boolean => a.toLocaleLowerCase() === b.toLocaleLowerCase()

export function listNamed(name: string): CardList | null {
  return lists.find((l) => sameName(l.name, cleanName(name))) ?? null
}

/** Reads every saved list. Throws when the storage cannot be reached. */
export async function loadLists(): Promise<void> {
  lists.splice(0, lists.length, ...(await api.lists.all()))
}

export type SaveResult = 'created' | 'replaced' | 'empty_name' | 'no_cards' | 'bad_cards' | 'too_many'

/** Saves the cards under a name (a list with the same name is replaced) and says what came of it. Throws when the storage cannot be reached. */
export async function saveList(name: string, cardIds: string[]): Promise<SaveResult> {
  const reply = await api.lists.save({ name, cards: cardIds })
  const saved = reply.list
  if (saved) {
    const index = lists.findIndex((l) => l.id === saved.id)
    if (index >= 0) lists[index] = saved
    else lists.push(saved)
  }
  return reply.result as SaveResult
}

export async function removeList(id: string): Promise<void> {
  await api.lists.remove(id)
  const index = lists.findIndex((l) => l.id === id)
  if (index >= 0) lists.splice(index, 1)
}
