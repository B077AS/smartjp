// The desktop window's right-click menu (TextMenu.vue) is drawn in its own portal, outside any dialog. A dialog counts a press or a focus
// on it as "outside" and closes itself, so picking Copy or Paste in a text field of a dialog closed the dialog. A dialog passes its
// outside events through this, and the ones that land on the menu are cancelled.
type OutsideEvent = { target: EventTarget | null; detail?: { originalEvent?: Event }; preventDefault: () => void }

export function keepOpenForMenu(event: OutsideEvent): void {
  const target = event.detail?.originalEvent?.target ?? event.target
  if (target instanceof Element && target.closest('.ui-menu')) event.preventDefault()
}
