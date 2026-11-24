// The page behind an open dialog must not scroll. Reka's own lock is switched off (see base.css), so this one does it:
// while a dialog's overlay (.ui-overlay) is in the page, <html> gets the class "scroll-locked" (overflow hidden).
// Taking the scrollbar away widens the page by its width, so the width is measured first and kept as padding: nothing
// jumps sideways, and the overlay, which then spans the whole window, dims the strip too.

const CLASS = 'scroll-locked'

function sync(): void {
  const html = document.documentElement
  const wanted = document.querySelector('.ui-overlay') !== null
  if (wanted === html.classList.contains(CLASS)) return
  if (wanted) {
    html.style.setProperty('--scrollbar-width', window.innerWidth - html.clientWidth + 'px')
    html.classList.add(CLASS)
  } else {
    html.classList.remove(CLASS)
    html.style.removeProperty('--scrollbar-width')
  }
}

/** Starts watching. Reka draws dialogs in a portal at the end of <body>, so only the children of <body> are watched. */
export function installScrollLock(): void {
  new MutationObserver(sync).observe(document.body, { childList: true })
}
