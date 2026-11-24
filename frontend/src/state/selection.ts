// Text highlighted with the mouse (only the characters and texts that can be copied, see base.css) goes away again.
// A browser keeps an existing highlight when the click lands on something that cannot be highlighted, so without this
// there would be no way to get rid of it. A click anywhere clears it (the browser then starts a new one if the click was on
// text), and so does Escape. Not cleared: a click in a text field (it has its own selection) or in the right-click menu (its Copy
// needs the highlight), a right click (the user is
// about to copy), and a click on the page's own scrollbar.

export function installSelectionClear(): void {
  document.addEventListener('mousedown', (event) => {
    if (event.button !== 0) return
    const target = event.target
    if (!(target instanceof Element) || target === document.documentElement) return
    if (target.closest('input, textarea, [role="menu"]')) return // the menu's Copy needs the highlight to still be there
    window.getSelection()?.removeAllRanges()
  })

  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Escape') return
    const target = event.target
    if (target instanceof Element && target.closest('input, textarea')) return
    window.getSelection()?.removeAllRanges()
  })
}
