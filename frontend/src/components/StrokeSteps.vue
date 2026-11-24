<script setup lang="ts">
// One frame per stroke: the strokes written so far in ink, the one just written in the accent colour, the rest faint. With `pickable` a frame is a
// button that says which step the user wants to see (the player in the stroke window uses it), and `active` marks the step being shown.
import { computed } from 'vue'
import type { StrokeSet } from '../strokes/strokes'
import StrokeFigure from './StrokeFigure.vue'

const props = defineProps<{ set: StrokeSet; pickable?: boolean; active?: number | null; columns?: number }>()
const emit = defineEmits<{ pick: [step: number] }>()

// Bigger frames while there are few strokes, smaller for a kanji with many; a combination is drawn twice as wide.
const cols = computed(() => props.columns ?? (props.set.strokes.length <= 3 ? 3 : props.set.strokes.length <= 8 ? 4 : 6))
const wide = computed(() => props.set.glyphs > 1)
</script>

<template>
  <ol class="steps" :style="{ '--cols': wide ? Math.max(1, Math.floor(cols / 2)) : cols }">
    <li v-for="(_, i) in set.strokes" :key="i">
      <component
        :is="pickable ? 'button' : 'div'"
        :type="pickable ? 'button' : undefined"
        class="step"
        :class="{ on: active === i + 1, wide }"
        :aria-label="pickable ? `Show stroke ${i + 1}` : undefined"
        :aria-current="pickable && active === i + 1 ? 'step' : undefined"
        @click="pickable && emit('pick', i + 1)"
      >
        <span class="frame" :style="{ aspectRatio: set.glyphs + ' / 1' }"><StrokeFigure :set="set" :drawn="i + 1" :current="i" guides /></span>
        <span class="cap">{{ i + 1 }}</span>
      </component>
    </li>
  </ol>
</template>

<style scoped>
.steps {
  display: grid;
  grid-template-columns: repeat(var(--cols), minmax(0, 1fr));
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.step {
  display: block;
  width: 100%;
  padding: 0;
  font: inherit;
  text-align: center;
  background: none;
  border: 0;
}
.frame {
  display: block;
  padding: 4px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 4px;
}
.cap {
  display: block;
  margin-top: 3px;
  font-size: 12px;
  color: var(--muted);
}
button.step {
  cursor: pointer;
}
button.step:hover .frame {
  border-color: var(--line-strong);
}
.step.on .frame {
  border-color: var(--accent);
  box-shadow: inset 0 0 0 1px var(--accent);
}
.step.on .cap {
  color: var(--accent);
  font-weight: 600;
}
</style>
