import { reactive } from 'vue'

// Notifications that float over the page (top right). One list, shared by every page and layout, so a message never
// pushes content around. ToastLayer.vue draws them and owns the timers (so hovering can pause one).
export type ToastKind = 'success' | 'error' | 'info'

export interface Toast {
  id: number
  kind: ToastKind
  title: string
  text: string
  // Optional button, for example "Send a new link". Clicking it runs the function and closes the toast.
  action?: { label: string; run: () => void }
}

export const toasts = reactive<Toast[]>([])

const MAX_VISIBLE = 4
let nextId = 1

export function dismissToast(id: number): void {
  const at = toasts.findIndex((t) => t.id === id)
  if (at >= 0) toasts.splice(at, 1)
}

export function notify(kind: ToastKind, title: string, text = '', action?: Toast['action']): void {
  // The same message again (a second wrong password, say) replaces the old one instead of piling up.
  for (const old of toasts.filter((t) => t.kind === kind && t.title === title && t.text === text)) dismissToast(old.id)
  toasts.push({ id: nextId++, kind, title, text, action })
  while (toasts.length > MAX_VISIBLE) toasts.shift()
}

export const notifySuccess = (title: string, text = ''): void => notify('success', title, text)
export const notifyError = (title: string, text = ''): void => notify('error', title, text)
export const notifyInfo = (title: string, text = '', action?: Toast['action']): void => notify('info', title, text, action)

// How long a toast stays when nobody hovers it. An error is read more slowly, and an action needs time to be found.
export function lifetimeMs(t: Toast): number {
  return t.kind === 'error' || t.action ? 8000 : 4000
}
