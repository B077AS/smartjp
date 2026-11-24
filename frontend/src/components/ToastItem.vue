<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { mdiAlertCircle, mdiCheckCircle, mdiClose, mdiInformation } from '@mdi/js'
import Icon from './Icon.vue'
import { dismissToast, lifetimeMs, type Toast } from '../state/toasts'

const props = defineProps<{ toast: Toast }>()

// Hovering (or keyboard focus inside) pauses the countdown; leaving resumes it with the time that was left,
// not a fresh start. The shrinking line is a CSS animation paused at the same moment, so the two stay in step.
const total = lifetimeMs(props.toast)
let left = total
let startedAt = 0
let timer: ReturnType<typeof setTimeout> | undefined
const paused = ref(false)
let hovering = false
let focused = false

function run() {
  startedAt = Date.now()
  timer = setTimeout(() => dismissToast(props.toast.id), left)
  paused.value = false
}
function pause() {
  if (timer === undefined) return
  clearTimeout(timer)
  timer = undefined
  left = Math.max(0, left - (Date.now() - startedAt))
  paused.value = true
}
function sync() {
  if (hovering || focused) pause()
  else if (timer === undefined) run()
}
function setHover(on: boolean) {
  hovering = on
  sync()
}
function setFocus(on: boolean) {
  focused = on
  sync()
}

onMounted(run)
onBeforeUnmount(() => clearTimeout(timer))

const ICON = { success: mdiCheckCircle, error: mdiAlertCircle, info: mdiInformation }

function runAction() {
  props.toast.action?.run()
  dismissToast(props.toast.id)
}
</script>

<template>
  <div
    class="toast"
    :class="[`toast-${toast.kind}`, { paused }]"
    :role="toast.kind === 'error' ? 'alert' : 'status'"
    @mouseenter="setHover(true)"
    @mouseleave="setHover(false)"
    @focusin="setFocus(true)"
    @focusout="setFocus(false)"
  >
    <Icon class="lead" :path="ICON[toast.kind]" :size="20" />
    <div class="body">
      <strong>{{ toast.title }}</strong>
      <span v-if="toast.text">{{ toast.text }}</span>
      <button v-if="toast.action" type="button" class="link-button act" @click="runAction">{{ toast.action.label }}</button>
    </div>
    <button type="button" class="close" aria-label="Close notification" @click="dismissToast(toast.id)">
      <Icon :path="mdiClose" :size="16" />
    </button>
    <div class="bar" :style="{ animationDuration: total + 'ms' }" aria-hidden="true"></div>
  </div>
</template>

<style scoped>
.toast {
  --c: var(--muted);
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 10px 14px 12px;
  background: var(--surface);
  border: 1px solid var(--line-strong);
  border-radius: 10px;
  box-shadow: 0 6px 20px rgba(31, 27, 22, 0.16), 0 1px 3px rgba(31, 27, 22, 0.12);
  font-size: 14px;
  line-height: 1.4;
  /* the layer lets clicks through; the toast itself takes them (also while a dialog has switched the page off) */
  pointer-events: auto;
}
.toast-success {
  --c: var(--good);
}
.toast-error {
  --c: var(--wrong); /* an error is red whatever the accent is */
}
/* not .icon: the shared Icon component puts that class on every icon, the close button's too */
.lead {
  margin-top: 1px;
  color: var(--c);
}
.body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.body span {
  color: var(--muted);
  overflow-wrap: anywhere;
}
.act {
  margin-top: 4px;
  font-size: 14px;
}
.close {
  flex: none;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  padding: 0;
  color: var(--muted);
  background: none;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
}
.close:hover {
  color: var(--ink);
  background: var(--tint);
}
.bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  background: var(--c);
  opacity: 0.55;
  transform-origin: left;
  animation: shrink linear forwards;
}
.paused .bar {
  animation-play-state: paused;
}
@keyframes shrink {
  from {
    transform: scaleX(1);
  }
  to {
    transform: scaleX(0);
  }
}
</style>
