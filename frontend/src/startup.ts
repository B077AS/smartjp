import { ApiError } from './api/client'
import { loadCustomCards } from './flashcards/custom'
import { loadLists } from './flashcards/lists'
import { loadStats } from './progress/log'
import { loadAppearance } from './state/theme'
import { notifyError } from './state/toasts'

// Everything the page reads from the database (through the Java side) when it starts, and again after the website was unlocked or a sync
// changed what is stored. A website that still wants its key is not an error to report: the unlock screen is showing.
const failed = (title: string) => (error: unknown) => {
  if (error instanceof ApiError && error.code === 'locked') return
  notifyError(title, 'The storage cannot be reached. Reload the page to try again.')
}

export async function loadEverything(): Promise<void> {
  await Promise.all([
    loadLists().catch(failed("Couldn't load your lists")),
    loadStats().catch(failed("Couldn't load your progress")),
    loadCustomCards().catch(failed("Couldn't load your own cards")),
    loadAppearance().catch(failed("Couldn't load your colour")),
  ])
}
