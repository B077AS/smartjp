<script setup lang="ts">
// Our own dialog (Reka UI Dialog): replaces the browser's prompt() and confirm(). The body goes in the default
// slot, the buttons in the "footer" slot. Escape and a click outside close it.
import { DialogClose, DialogContent, DialogDescription, DialogOverlay, DialogPortal, DialogRoot, DialogTitle } from 'reka-ui'
import { mdiClose } from '@mdi/js'
import Icon from '../Icon.vue'
import { keepOpenForMenu } from './menuGuard'

defineProps<{
  open: boolean
  title: string
  /** Read out by screen readers. */
  description: string
  /** A wider window, for a form with longer text fields. */
  wide?: boolean
  /** Wider still, for the stroke window. */
  xwide?: boolean
  /** An X in the top right corner, for a window that has no buttons of its own at the bottom. */
  closable?: boolean
}>()
const emit = defineEmits<{ 'update:open': [open: boolean] }>()
</script>

<template>
  <DialogRoot :open="open" @update:open="emit('update:open', $event)">
    <DialogPortal>
      <DialogOverlay class="ui-overlay" />
      <DialogContent class="ui-dialog" :class="{ wide, xwide, closable }" @interact-outside="keepOpenForMenu">
        <DialogClose v-if="closable" class="ui-dialog-close" aria-label="Close"><Icon :path="mdiClose" :size="18" /></DialogClose>
        <DialogTitle class="ui-dialog-title">{{ title }}</DialogTitle>
        <DialogDescription class="ui-dialog-desc">{{ description }}</DialogDescription>
        <!-- A wide dialog is as tall as the window allows: title and buttons stay, only this part scrolls. -->
        <div class="ui-dialog-body"><slot /></div>
        <div v-if="$slots.footer" class="ui-dialog-foot"><slot name="footer" /></div>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
