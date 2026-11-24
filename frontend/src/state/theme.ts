import { reactive } from 'vue'
import { api } from '../api/client'
import { notifyError } from './toasts'

// The look is "Snow": near-white paper and ink (tokens in styles/base.css). The one thing the user changes is the accent,
// in Settings > Appearance. It colours the main buttons, selected kana, tabs, links and the timer line. Right and wrong
// answers (green and red) never follow the accent.
// "on" is the text colour that stays readable on a button of that colour (white for every accent offered so far).
export interface Accent {
  key: string
  name: string
  color: string
  on: string
}

export const ACCENTS: Accent[] = [
  { key: 'burnt-orange', name: 'Burnt orange', color: '#c2410c', on: '#ffffff' },
  { key: 'indigo', name: 'Indigo', color: '#34508c', on: '#ffffff' },
  { key: 'teal', name: 'Teal', color: '#1f6f78', on: '#ffffff' },
  { key: 'plum', name: 'Plum', color: '#7a3b69', on: '#ffffff' },
  { key: 'crimson', name: 'Crimson', color: '#a4232f', on: '#ffffff' },
  { key: 'graphite', name: 'Graphite', color: '#2a2a28', on: '#ffffff' },
  { key: 'pine', name: 'Pine', color: '#2f6b4d', on: '#ffffff' },
]
export const DEFAULT_ACCENT = ACCENTS[0].key

// The chosen accent is kept in the database of this installation (Java: AppearanceService), so it is still there after a restart. It is a
// taste of the device and is never synced: the desktop app and its server can have different colours.

export const theme = reactive({ accent: DEFAULT_ACCENT })

export function accentByKey(key: string): Accent {
  return ACCENTS.find((a) => a.key === key) ?? ACCENTS[0]
}

/** Puts the chosen accent on the page as CSS variables (they override the defaults in base.css). */
export function applyTheme(): void {
  const accent = accentByKey(theme.accent)
  const root = document.documentElement
  root.style.setProperty('--accent', accent.color)
  root.style.setProperty('--accent-ink', accent.on)
}

// Set once the user picks a colour: a slow read of the stored one must not undo a choice made in the meantime.
let chosenHere = false

/** Applies the colour at once and keeps it in the background (a click never waits for the database). */
export function setAccent(key: string): void {
  chosenHere = true
  theme.accent = accentByKey(key).key
  applyTheme()
  api.appearance
    .save({ accent: theme.accent })
    .then((reply) => {
      if (reply.result !== 'saved') notifyError("Couldn't keep the colour", 'It will be back to the default after a restart.')
    })
    .catch(() => notifyError("Couldn't keep the colour", 'The storage cannot be reached. It will be back to the default after a restart.'))
}

/** Reads the colour kept in the database (if the user ever chose one) and puts it on the page. Throws when the storage cannot be reached. */
export async function loadAppearance(): Promise<void> {
  const stored = (await api.appearance.get()).accent
  if (!stored || chosenHere) return
  theme.accent = accentByKey(stored).key // a key this version does not know gives the default
  applyTheme()
}
