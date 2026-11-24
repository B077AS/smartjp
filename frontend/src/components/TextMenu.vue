<script setup lang="ts">
// The right-click menu of the desktop window, drawn by the page in the app's own look (a Jolt window has no menu of its own; the
// website needs none, a browser shows its own). Two places get it:
//   - a text field: Cut, Copy, Paste and Select all (Cut and Copy greyed out while nothing is highlighted);
//   - highlighted text anywhere else (the characters, the texts of the user's own cards): Copy. Only when the click is on the highlight.
// Anywhere else nothing opens. Cut and Copy go through the web view (document.execCommand); Paste reads the clipboard in Java
// (api.clipboard.readText), because a web view does not let a page read it (Ctrl+V is not affected).
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { DropdownMenuContent, DropdownMenuItem, DropdownMenuPortal, DropdownMenuRoot, DropdownMenuSeparator, DropdownMenuTrigger } from 'reka-ui'
import { mdiContentCopy, mdiContentCut, mdiContentPaste, mdiSelectAll } from '@mdi/js'
import Icon from './Icon.vue'
import { api } from '../api/client'
import { notifyError } from '../state/toasts'

type Field = HTMLInputElement | HTMLTextAreaElement

const open = ref(false)
const anchor = reactive({ x: 0, y: 0 })
const can = reactive({ field: false, cut: false, copy: false, paste: false })
let target: Field | null = null

const TEXT_INPUTS = new Set(['text', 'email', 'search', 'password', 'url', 'tel', 'number'])

/** The text field a right click landed in, or null for anything else. */
function fieldAt(el: EventTarget | null): Field | null {
  if (!(el instanceof HTMLElement)) return null
  const field = el.closest<HTMLElement>('input, textarea')
  if (!field) return null
  if (field instanceof HTMLInputElement && !TEXT_INPUTS.has(field.type)) return null
  return field as Field
}

const hasSelection = (field: Field): boolean => field.selectionStart !== field.selectionEnd

/** Is the click on text that is highlighted? (A highlight elsewhere on the page does not make a menu for this click.) */
function onHighlight(x: number, y: number): boolean {
  const selection = window.getSelection()
  if (!selection || selection.isCollapsed || selection.rangeCount === 0 || selection.toString() === '') return false
  for (let i = 0; i < selection.rangeCount; i++) {
    for (const r of selection.getRangeAt(i).getClientRects()) {
      if (x >= r.left - 1 && x <= r.right + 1 && y >= r.top - 1 && y <= r.bottom + 1) return true
    }
  }
  return false
}

function onContextMenu(event: MouseEvent): void {
  const field = fieldAt(event.target)
  if (field) {
    const selected = hasSelection(field)
    const secret = field instanceof HTMLInputElement && field.type === 'password' // a password is never copied out
    can.field = true
    can.copy = selected && !secret
    can.cut = selected && !secret && !field.readOnly && !field.disabled
    can.paste = !field.readOnly && !field.disabled
    target = field
  } else if (onHighlight(event.clientX, event.clientY)) {
    can.field = false
    can.copy = true
    can.cut = can.paste = false
    target = null
  } else {
    return // nothing to offer: no menu
  }
  event.preventDefault()
  anchor.x = event.clientX
  anchor.y = event.clientY
  open.value = true
}

async function run(action: 'cut' | 'copy' | 'paste' | 'selectAll'): Promise<void> {
  const field = target
  // The menu is still closing when this runs and hands the focus back to the page when it is done; wait for that, or it would
  // undo the highlight or the focus the action needs.
  await nextTick()
  await new Promise((resolve) => setTimeout(resolve, 0))
  field?.focus({ preventScroll: true })
  try {
    if (action === 'cut' || action === 'copy') {
      document.execCommand(action)
    } else if (action === 'selectAll') {
      field?.select()
    } else {
      const text = await api.clipboard.readText()
      if (text === '') return
      field?.focus({ preventScroll: true })
      document.execCommand('insertText', false, text) // fires the input event, so a v-model sees it
    }
  } catch {
    notifyError("Couldn't paste", 'The clipboard could not be read. Use Ctrl+V instead.')
  }
}

// A press anywhere outside the menu closes it. Reka does this by itself on the page, but inside a modal dialog (Settings) the dialog
// keeps the pointer and the focus for itself, and the menu stayed open when the dialog's own content was clicked. Done here, in the
// capture phase, so no layer above or below can swallow the press.
function onPointerDown(event: PointerEvent): void {
  if (!open.value) return
  if (event.target instanceof Element && event.target.closest('.ui-menu')) return
  open.value = false
}

onMounted(() => {
  document.addEventListener('contextmenu', onContextMenu, true)
  document.addEventListener('pointerdown', onPointerDown, true)
})
onBeforeUnmount(() => {
  document.removeEventListener('contextmenu', onContextMenu, true)
  document.removeEventListener('pointerdown', onPointerDown, true)
})
</script>

<template>
  <!-- not modal: no scroll lock and no padding on the page while it is open, so nothing jumps -->
  <DropdownMenuRoot v-model:open="open" :modal="false">
    <DropdownMenuTrigger as-child>
      <span class="anchor" :style="{ left: anchor.x + 'px', top: anchor.y + 'px' }" />
    </DropdownMenuTrigger>
    <DropdownMenuPortal>
      <!-- The focus goes back to the field through run(), not to the invisible anchor. -->
      <DropdownMenuContent class="ui-menu" side="bottom" align="start" :side-offset="2" @close-auto-focus.prevent>
        <template v-if="can.field">
          <DropdownMenuItem class="ui-menu-item" :disabled="!can.cut" @select="run('cut')"><Icon :path="mdiContentCut" />Cut<span class="ui-menu-hint">Ctrl+X</span></DropdownMenuItem>
        </template>
        <DropdownMenuItem class="ui-menu-item" :disabled="!can.copy" @select="run('copy')"><Icon :path="mdiContentCopy" />Copy<span class="ui-menu-hint">Ctrl+C</span></DropdownMenuItem>
        <template v-if="can.field">
          <DropdownMenuItem class="ui-menu-item" :disabled="!can.paste" @select="run('paste')"><Icon :path="mdiContentPaste" />Paste<span class="ui-menu-hint">Ctrl+V</span></DropdownMenuItem>
          <DropdownMenuSeparator class="ui-menu-sep" />
          <DropdownMenuItem class="ui-menu-item" @select="run('selectAll')"><Icon :path="mdiSelectAll" />Select all<span class="ui-menu-hint">Ctrl+A</span></DropdownMenuItem>
        </template>
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>

<style scoped>
.anchor {
  position: fixed;
  width: 1px;
  height: 1px;
  pointer-events: none;
}
</style>
