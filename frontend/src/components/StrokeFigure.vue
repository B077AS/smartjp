<script setup lang="ts">
// Draws the strokes of a face (a kana, two kana of a combination side by side, or a kanji) as one SVG. It only draws: how far the writing has
// got is decided by whoever uses it (the step strip draws a frame per stroke, the player in the stroke window animates).
//   drawn   strokes that are finished; partial   how much (0 to 1) of the next one is drawn; current   the stroke shown in the accent colour.
// Finished strokes are ink, the current one the accent, strokes still to come faint grey (when `ghost`).
import { computed, watchPostEffect, ref } from 'vue'
import { BOX, type StrokeSet } from '../strokes/strokes'

const props = withDefaults(
  defineProps<{
    set: StrokeSet
    drawn: number
    partial?: number
    current?: number | null
    ghost?: boolean
    guides?: boolean
    numbers?: boolean
    /** A dot at the tip of the stroke that is being drawn (the pen). */
    pen?: boolean
    /** Part of the drawing to show, "x y width height" in box units (zoom and pan); the whole drawing when not given. */
    viewBox?: string
    /** Makes the number labels and the pen smaller or larger (1 / the zoom, so they do not grow with it). */
    labelScale?: number
  }>(),
  { partial: 0, current: null, ghost: true, guides: false, numbers: false, pen: false, labelScale: 1 },
)

const width = computed(() => BOX * props.set.glyphs)
const box = computed(() => props.viewBox ?? `0 0 ${width.value} ${BOX}`)
const shift = (glyph: number): string | undefined => (glyph === 0 ? undefined : `translate(${glyph * BOX} 0)`)

type Look = 'done' | 'current' | 'partial' | 'ghost' | 'none'
function look(i: number): Look {
  if (i < props.drawn) return i === props.current ? 'current' : 'done'
  if (i === props.drawn && props.partial > 0) return 'partial'
  return props.ghost ? 'ghost' : 'none'
}
const visible = computed(() => props.set.strokes.map((_, i) => ({ i, look: look(i) })).filter((s) => s.look !== 'none'))

// The numbers of the strokes that have been started.
const labelled = computed(() => (props.numbers ? props.set.strokes.slice(0, props.drawn + (props.partial > 0 ? 1 : 0)).map((s, i) => ({ i, s })) : []))

// The pen: where the stroke being drawn has got to. Measured on the real path, so it follows the curve.
const paths: (SVGPathElement | null)[] = []
const tip = ref<{ x: number; y: number } | null>(null)
watchPostEffect(() => {
  const el = props.pen && props.partial > 0 ? paths[props.drawn] : null
  if (!el) {
    tip.value = null
    return
  }
  const at = el.getPointAtLength(el.getTotalLength() * props.partial)
  tip.value = { x: at.x + (props.set.strokes[props.drawn]?.glyph ?? 0) * BOX, y: at.y }
})
</script>

<template>
  <svg class="figure" :viewBox="box" preserveAspectRatio="xMidYMid meet" aria-hidden="true">
    <g v-if="guides" class="guides">
      <template v-for="g in set.glyphs" :key="g">
        <rect :x="(g - 1) * BOX + 1" y="1" :width="BOX - 2" :height="BOX - 2" />
        <path :d="`M${(g - 1) * BOX + BOX / 2} 1V${BOX - 1}M${(g - 1) * BOX + 1} ${BOX / 2}H${g * BOX - 1}`" />
      </template>
    </g>
    <template v-for="s in visible" :key="s.i">
      <path
        :ref="(el) => (paths[s.i] = el as SVGPathElement | null)"
        :d="set.strokes[s.i].d"
        :transform="shift(set.strokes[s.i].glyph)"
        :class="['stroke', s.look]"
        :pathLength="s.look === 'partial' ? 1 : undefined"
        :stroke-dasharray="s.look === 'partial' ? '1 1' : undefined"
        :stroke-dashoffset="s.look === 'partial' ? 1 - partial : undefined"
      />
    </template>
    <g v-for="l in labelled" :key="'n' + l.i" class="badge" :transform="`translate(${l.s.badge[0] + l.s.glyph * BOX} ${l.s.badge[1]}) scale(${labelScale})`">
      <circle r="4.6" />
      <text y="0.5">{{ l.i + 1 }}</text>
    </g>
    <circle v-if="tip" class="pen" :cx="tip.x" :cy="tip.y" :r="3.4 * labelScale" />
  </svg>
</template>

<style scoped>
.figure {
  display: block;
  width: 100%;
  height: 100%;
}
.stroke {
  fill: none;
  stroke-width: 4.2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.done {
  stroke: var(--ink);
}
.current,
.partial {
  stroke: var(--accent);
}
.ghost {
  stroke: color-mix(in srgb, var(--ink) 13%, transparent);
}
/* constant thickness however far it is zoomed (non-scaling-stroke: the width and the dashes are in screen pixels) */
.guides rect,
.guides path {
  fill: none;
  stroke: var(--line-strong);
  stroke-width: 1;
  stroke-dasharray: 4 4;
  vector-effect: non-scaling-stroke;
}
.guides rect {
  stroke-dasharray: none;
}
.badge circle {
  fill: var(--accent);
}
.badge text {
  font: 700 6.4px var(--font-ui);
  fill: var(--accent-ink);
  text-anchor: middle;
  dominant-baseline: middle;
}
.pen {
  fill: var(--accent);
  stroke: var(--surface);
  stroke-width: 1.2;
}
</style>
