<script setup lang="ts">
// The stroke window: the writing of one kana or kanji, animated, with the controls of a small player.
//   - play / pause, one stroke back or forward, back to the start, a slider (drag to any point), three speeds, repeat;
//   - show the numbers of the strokes, guide lines, the strokes still to come (faint), the pen dot at the tip of the stroke being drawn;
//   - zoom (scroll wheel on the drawing, the buttons, + and - on the keyboard) and move (drag the drawing); double click or "Fit" shows it all again;
//   - the steps, one frame per stroke, each a button that jumps there.
// Keyboard, with the drawing focused: space plays and pauses, the left and right arrows step, + and - zoom, 0 fits, Home goes to the start.
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { mdiChevronLeft, mdiChevronRight, mdiFitToScreenOutline, mdiMagnifyMinusOutline, mdiMagnifyPlusOutline, mdiPause, mdiPlay, mdiSkipPrevious } from '@mdi/js'
import { BOX, type StrokeSet } from '../strokes/strokes'
import Icon from './Icon.vue'
import StrokeFigure from './StrokeFigure.vue'
import StrokeSteps from './StrokeSteps.vue'
import UiCheckbox from './ui/UiCheckbox.vue'
import UiDialog from './ui/UiDialog.vue'
import UiSegmented from './ui/UiSegmented.vue'
import UiSlider from './ui/UiSlider.vue'
import type { UiOption } from './ui/types'

const props = defineProps<{ open: boolean; face: string; set: StrokeSet | null }>()
const emit = defineEmits<{ 'update:open': [open: boolean] }>()

// ---- how far the writing has got --------------------------------------------------------------------------
// progress: 0 = nothing written, 2.5 = two strokes done and the third half drawn, total = finished.
const progress = ref(0)
const playing = ref(false)
const total = computed(() => props.set?.strokes.length ?? 0)
const drawn = computed(() => Math.min(total.value, Math.floor(progress.value)))
const partial = computed(() => (drawn.value >= total.value ? 0 : progress.value - drawn.value))
const started = computed(() => (partial.value > 0 ? drawn.value + 1 : drawn.value)) // the stroke number being shown
// The stroke in the accent colour: the one being drawn, or else the one finished last.
const current = computed(() => (partial.value > 0 ? drawn.value : drawn.value > 0 ? drawn.value - 1 : null))

type Speed = 'slow' | 'normal' | 'fast'
const SPEEDS: UiOption<Speed>[] = [
  { value: 'slow', label: 'Slow' },
  { value: 'normal', label: 'Normal' },
  { value: 'fast', label: 'Fast' },
]
const STROKES_PER_SECOND: Record<Speed, number> = { slow: 0.5, normal: 1, fast: 2 }
const speed = ref<Speed>('normal')

// What the user chose to see stays chosen while the page is open.
const numbers = ref(true)
const guides = ref(true)
const ghost = ref(true)
const repeat = ref(false)

let frame = 0
let last = 0
let holdUntil = 0

function tick(now: number): void {
  if (!playing.value) return
  const dt = Math.min(0.1, (now - last) / 1000)
  last = now
  if (progress.value >= total.value) {
    // Finished: stay a moment on the whole character, then start again (repeat) or stop.
    if (!repeat.value) return pause()
    holdUntil ||= now + 900
    if (now >= holdUntil) {
      progress.value = 0
      holdUntil = 0
    }
  } else {
    progress.value = Math.min(total.value, progress.value + dt * STROKES_PER_SECOND[speed.value])
  }
  frame = requestAnimationFrame(tick)
}

function play(): void {
  if (total.value === 0) return
  if (progress.value >= total.value) progress.value = 0
  playing.value = true
  last = performance.now()
  holdUntil = 0
  cancelAnimationFrame(frame)
  frame = requestAnimationFrame(tick)
}

function pause(): void {
  playing.value = false
  cancelAnimationFrame(frame)
}

const toggle = (): void => (playing.value ? pause() : play())
const restart = (): void => {
  pause()
  progress.value = 0
}
function step(by: 1 | -1): void {
  pause()
  progress.value = by === 1 ? Math.min(total.value, Math.floor(progress.value) + 1) : Math.max(0, Math.ceil(progress.value) - 1)
}
function jump(to: number): void {
  pause()
  progress.value = to
}

// Opening starts the writing from the beginning (unless the user asked their system for less motion: then the finished character is shown).
watch(
  () => [props.open, props.set] as const,
  ([open, set]) => {
    pause()
    resetView()
    if (!open || !set) return
    const calm = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches
    progress.value = calm ? set.strokes.length : 0
    if (!calm) play()
  },
  // Not immediate: it needs the zoom state declared below, and it only matters once the window is opened or the strokes arrive.
)
onBeforeUnmount(pause)

// ---- zoom and move ----------------------------------------------------------------------------------------
const MIN_ZOOM = 1
const MAX_ZOOM = 6
const zoom = ref(1)
const center = ref({ x: 0, y: 0 })
const stage = ref<HTMLElement | null>(null)

const worldW = computed(() => BOX * (props.set?.glyphs ?? 1))
const clamp = (v: number, lo: number, hi: number): number => Math.min(hi, Math.max(lo, v))
const viewW = computed(() => worldW.value / zoom.value)
const viewH = computed(() => BOX / zoom.value)
const viewBox = computed(() => `${center.value.x - viewW.value / 2} ${center.value.y - viewH.value / 2} ${viewW.value} ${viewH.value}`)

function resetView(): void {
  zoom.value = 1
  center.value = { x: worldW.value / 2, y: BOX / 2 }
}

/** Sets the zoom so that the point (x, y) of the drawing stays where it is on the screen. */
function zoomAt(next: number, x: number, y: number): void {
  const z = clamp(next, MIN_ZOOM, MAX_ZOOM)
  const fx = (x - (center.value.x - viewW.value / 2)) / viewW.value
  const fy = (y - (center.value.y - viewH.value / 2)) / viewH.value
  const w = worldW.value / z
  const h = BOX / z
  zoom.value = z
  center.value = { x: clamp(x - fx * w + w / 2, w / 2, worldW.value - w / 2), y: clamp(y - fy * h + h / 2, h / 2, BOX - h / 2) }
}

/** The point of the drawing under a point of the screen. */
function unitsAt(clientX: number, clientY: number): { x: number; y: number } {
  const rect = stage.value?.getBoundingClientRect()
  if (!rect) return center.value
  return {
    x: center.value.x - viewW.value / 2 + ((clientX - rect.left) / rect.width) * viewW.value,
    y: center.value.y - viewH.value / 2 + ((clientY - rect.top) / rect.height) * viewH.value,
  }
}

function onWheel(event: WheelEvent): void {
  const at = unitsAt(event.clientX, event.clientY)
  zoomAt(zoom.value * Math.exp(-event.deltaY * 0.0016), at.x, at.y)
}

const zoomBy = (factor: number): void => zoomAt(zoom.value * factor, center.value.x, center.value.y)

let drag: { x: number; y: number; cx: number; cy: number } | null = null
function onPointerDown(event: PointerEvent): void {
  if (zoom.value <= MIN_ZOOM || event.button !== 0) return
  drag = { x: event.clientX, y: event.clientY, cx: center.value.x, cy: center.value.y }
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}
function onPointerMove(event: PointerEvent): void {
  const rect = stage.value?.getBoundingClientRect()
  if (!drag || !rect) return
  const w = viewW.value
  const h = viewH.value
  center.value = {
    x: clamp(drag.cx - ((event.clientX - drag.x) / rect.width) * w, w / 2, worldW.value - w / 2),
    y: clamp(drag.cy - ((event.clientY - drag.y) / rect.height) * h, h / 2, BOX - h / 2),
  }
}
const onPointerUp = (): void => {
  drag = null
}
function onDoubleClick(event: MouseEvent): void {
  if (zoom.value > MIN_ZOOM) resetView()
  else {
    const at = unitsAt(event.clientX, event.clientY)
    zoomAt(2.5, at.x, at.y)
  }
}

function onKey(event: KeyboardEvent): void {
  const keys: Record<string, () => void> = {
    ' ': toggle,
    ArrowRight: () => step(1),
    ArrowLeft: () => step(-1),
    Home: restart,
    '+': () => zoomBy(1.4),
    '=': () => zoomBy(1.4),
    '-': () => zoomBy(1 / 1.4),
    '0': resetView,
  }
  const run = keys[event.key]
  if (!run) return
  event.preventDefault()
  run()
}

const zoomPercent = computed(() => Math.round(zoom.value * 100) + '%')
const label = computed(() => {
  if (total.value === 0) return ''
  if (started.value === 0) return `${total.value} ${total.value === 1 ? 'stroke' : 'strokes'}`
  return `Stroke ${started.value} of ${total.value}`
})
const ticks = computed(() => Array.from({ length: Math.max(0, total.value - 1) }, (_, i) => i + 1))
</script>

<template>
  <UiDialog
    :open="open"
    xwide
    closable
    :title="`Stroke order: ${face}`"
    description="Watch the character being written and step through it. Zoom into the drawing with the scroll wheel or the buttons."
    @update:open="emit('update:open', $event)"
  >
    <div v-if="set" class="player">
      <div class="left">
        <div
          ref="stage"
          class="stage"
          :class="{ movable: zoom > 1 }"
          :style="{ aspectRatio: set.glyphs + ' / 1' }"
          tabindex="0"
          role="group"
          aria-label="Stroke diagram. Space plays or pauses, the arrow keys step through the strokes, plus and minus zoom, 0 shows everything."
          @keydown="onKey"
          @wheel.prevent="onWheel"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerUp"
          @dblclick="onDoubleClick"
        >
          <StrokeFigure
            :set="set"
            :drawn="drawn"
            :partial="partial"
            :current="current"
            :ghost="ghost"
            :guides="guides"
            :numbers="numbers"
            pen
            :view-box="viewBox"
            :label-scale="1 / Math.sqrt(zoom)"
          />
          <span class="zoom-tag" aria-hidden="true">{{ zoomPercent }}</span>
        </div>
        <p class="muted small hint">Scroll to zoom, drag to move, double click to zoom in or out.</p>
      </div>

      <div class="right">
        <div class="status">
          <span class="stroke-label" aria-live="polite">{{ label }}</span>
        </div>

        <div class="transport">
          <button type="button" class="tbtn" aria-label="Back to the start" title="Back to the start" @click="restart"><Icon :path="mdiSkipPrevious" :size="20" /></button>
          <button type="button" class="tbtn" aria-label="Previous stroke" title="Previous stroke" :disabled="progress <= 0" @click="step(-1)"><Icon :path="mdiChevronLeft" :size="20" /></button>
          <button type="button" class="tbtn play" :aria-label="playing ? 'Pause' : 'Play'" :title="playing ? 'Pause' : 'Play'" @click="toggle">
            <Icon :path="playing ? mdiPause : mdiPlay" :size="24" />
          </button>
          <button type="button" class="tbtn" aria-label="Next stroke" title="Next stroke" :disabled="progress >= total" @click="step(1)"><Icon :path="mdiChevronRight" :size="20" /></button>
        </div>

        <UiSlider :model-value="progress" :max="total" :step="0.05" :ticks="ticks" aria-label="Position in the writing" @update:model-value="jump" />

        <div class="group">
          <span class="group-title">Speed</span>
          <UiSegmented v-model="speed" :options="SPEEDS" aria-label="Speed" />
        </div>

        <div class="group">
          <span class="group-title">Show</span>
          <div class="checks">
            <UiCheckbox :model-value="numbers" @update:model-value="numbers = $event === true">Stroke numbers</UiCheckbox>
            <UiCheckbox :model-value="guides" @update:model-value="guides = $event === true">Guide lines</UiCheckbox>
            <UiCheckbox :model-value="ghost" @update:model-value="ghost = $event === true">Strokes still to come</UiCheckbox>
            <UiCheckbox :model-value="repeat" @update:model-value="repeat = $event === true">Repeat</UiCheckbox>
          </div>
        </div>

        <div class="group">
          <span class="group-title">Zoom</span>
          <div class="zoom">
            <button type="button" class="tbtn small" aria-label="Zoom out" title="Zoom out" :disabled="zoom <= MIN_ZOOM" @click="zoomBy(1 / 1.4)"><Icon :path="mdiMagnifyMinusOutline" :size="18" /></button>
            <span class="zoom-value">{{ zoomPercent }}</span>
            <button type="button" class="tbtn small" aria-label="Zoom in" title="Zoom in" :disabled="zoom >= MAX_ZOOM" @click="zoomBy(1.4)"><Icon :path="mdiMagnifyPlusOutline" :size="18" /></button>
            <button type="button" class="btn btn-quiet fit" :disabled="zoom === MIN_ZOOM" @click="resetView"><Icon :path="mdiFitToScreenOutline" :size="16" />Fit</button>
          </div>
        </div>

        <div class="group steps-group">
          <span class="group-title">Steps</span>
          <div class="steps-scroll hover-scroll"><StrokeSteps :set="set" pickable :active="started" @pick="jump" /></div>
        </div>
      </div>
    </div>
    <p v-else class="muted small">There are no strokes for this character.</p>
  </UiDialog>
</template>

<style scoped>
.player {
  display: grid;
  /* The drawing takes the room the Done button used to: as wide as the window's height allows, 560 px at most. */
  grid-template-columns: minmax(0, min(560px, max(320px, calc(100vh - 210px)))) minmax(0, 1fr);
  gap: 28px;
  align-items: start;
}
.stage {
  position: relative;
  width: 100%;
  overflow: hidden;
  touch-action: none;
  background: color-mix(in srgb, var(--tint) 40%, var(--surface));
  border: 1px solid var(--line);
  border-radius: 6px;
  user-select: none;
}
.stage.movable {
  cursor: grab;
}
.stage.movable:active {
  cursor: grabbing;
}
.zoom-tag {
  position: absolute;
  right: 8px;
  bottom: 6px;
  padding: 0 6px;
  font-size: 11px;
  color: var(--muted);
  background: color-mix(in srgb, var(--surface) 85%, transparent);
  border-radius: 8px;
}
.hint {
  margin: 6px 0 0;
}
.right {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}
.status {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.stroke-label {
  font-family: var(--font-display);
  font-size: 19px;
}
.transport {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tbtn {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  color: var(--ink);
  background: var(--surface);
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  cursor: pointer;
}
.tbtn:hover:not(:disabled) {
  background: var(--tint);
}
.tbtn:disabled {
  opacity: 0.4;
  cursor: default;
}
.tbtn.play {
  width: 46px;
  height: 46px;
  color: var(--accent-ink);
  background: var(--accent);
  border: 0;
  border-radius: 50%;
}
.tbtn.play:hover:not(:disabled) {
  background: var(--accent-hover);
}
.tbtn.small {
  width: 30px;
  height: 30px;
}
.group {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}
.group-title {
  font-size: 12px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--muted);
}
.checks {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, auto));
  gap: 6px 22px;
}
.zoom {
  display: flex;
  align-items: center;
  gap: 8px;
}
.zoom-value {
  min-width: 44px;
  font-size: 13px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.fit {
  margin-left: 6px;
  padding: 4px 10px;
  font-size: 12.5px;
}
.steps-group {
  align-items: stretch;
}
.steps-scroll {
  max-height: 220px;
  padding-right: 2px;
  scrollbar-gutter: stable;
}
@media (max-width: 760px) {
  .player {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
