<script setup lang="ts">
// One kana drawn in one or both typefaces: Hina Mincho (print-like, with brush shapes) and Noto Sans JP (plain
// gothic). The size comes from
// --glyph-size on the parent. The second drawing is hidden from screen readers (it would be read twice).
import { computed } from 'vue'
import type { FontChoice } from '../flashcards/store'

const props = withDefaults(
  defineProps<{
    kana: string
    fonts?: FontChoice
    /** Small captions "Mincho" / "Gothic" under each drawing. */
    labels?: boolean
  }>(),
  { fonts: 'both', labels: false },
)

const faces = computed(() => {
  const all = [
    { id: 'mincho', label: 'Mincho' },
    { id: 'gothic', label: 'Gothic' },
  ]
  return props.fonts === 'both' ? all : all.filter((f) => f.id === props.fonts)
})
</script>

<template>
  <span class="glyphs">
    <span v-for="(face, i) in faces" :key="face.id" class="face" :aria-hidden="i > 0 ? 'true' : undefined">
      <span :class="['g', 'g-' + face.id]" lang="ja">{{ kana }}</span>
      <span v-if="labels" class="cap">{{ face.label }}</span>
    </span>
  </span>
</template>

<style scoped>
.glyphs {
  display: inline-flex;
  align-items: flex-start;
  justify-content: center;
  gap: calc(var(--glyph-size, 32px) * 0.45);
}
.face {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.g {
  font-size: var(--glyph-size, 32px);
  line-height: 1.15;
  white-space: nowrap;
}
.g-mincho {
  font-family: var(--font-mincho);
}
.g-gothic {
  font-family: var(--font-gothic);
}
.cap {
  font-family: var(--font-ui);
  font-size: 11px;
  color: var(--muted);
}
</style>
