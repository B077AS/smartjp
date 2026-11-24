// Columns with the class `hover-scroll` (the sidebar, the settings screen's right column) draw their scrollbar thumb only while the
// pointer is over them or something inside has the keyboard focus. That is the class `hot` on the column.
// It is done with a class, not with CSS :hover, because Chromium (and so the desktop window's web view) does not redraw a custom
// scrollbar when only the :hover state of its owner changes; a class change on the owner is picked up (checked in headless Edge).

const COLUMN = '.hover-scroll'

const columnOf = (target: EventTarget | null): Element | null => (target instanceof Element ? target.closest(COLUMN) : null)

export function installHoverScroll(): void {
  document.addEventListener('mouseover', (event) => columnOf(event.target)?.classList.add('hot'), true)
  document.addEventListener(
    'mouseout',
    (event) => {
      const column = columnOf(event.target)
      // Moving to something else inside the column is not leaving it; a column with the keyboard focus stays hot.
      if (column && !(event.relatedTarget instanceof Node && column.contains(event.relatedTarget)) && !column.matches(':focus-within')) column.classList.remove('hot')
    },
    true,
  )
  document.addEventListener('focusin', (event) => columnOf(event.target)?.classList.add('hot'))
  document.addEventListener('focusout', (event) => {
    const column = columnOf(event.target)
    if (column && !column.matches(':hover')) column.classList.remove('hot')
  })
}
