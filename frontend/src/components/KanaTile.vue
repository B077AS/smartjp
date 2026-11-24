<script setup lang="ts">
// One kana as a toggle button: pressed = it is in the round. The user can pick any single kana this way.
import { computed } from 'vue'
import type { FontChoice } from '../flashcards/store'
import { captionOf, faceOf, isCustomCard, type StudyCard } from '../flashcards/cards'
import { stats } from '../progress/log'
import KanaGlyphs from './KanaGlyphs.vue'

const props = withDefaults(
  defineProps<{
    card: StudyCard
    selected?: boolean
    fonts?: FontChoice
    /** Draw how often the card was missed in the corner (only where the page is about misses). */
    showMisses?: boolean
  }>(),
  { selected: false, fonts: 'both', showMisses: false },
)
defineEmits<{ toggle: [] }>()

// How often this card was answered wrongly (a skip or a timeout is a miss too), from the totals the page keeps.
const misses = computed(() => {
  const entry = stats.get(props.card.id)
  return entry ? entry.seen - entry.correct : 0
})
const title = computed(() => {
  const entry = stats.get(props.card.id)
  return entry && misses.value > 0 ? 'Missed ' + misses.value + ' of ' + entry.seen + (entry.seen === 1 ? ' answer' : ' answers') : undefined
})
</script>

<template>
  <button type="button" class="tile" :aria-pressed="selected" :title="title" @click="$emit('toggle')">
    <span v-if="showMisses && misses > 0" class="misses" aria-hidden="true">{{ misses }}×</span>
    <KanaGlyphs v-if="!isCustomCard(card)" :kana="faceOf(card)" :fonts="fonts" class="art" />
    <span v-else class="plain">{{ faceOf(card) }}</span>
    <span class="romaji">{{ captionOf(card) }}</span>
  </button>
</template>

<style scoped>
.tile {
  --glyph-size: var(--tile-glyph, 28px);
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-height: 74px;
  padding: 8px 6px 6px;
  font: inherit;
  color: var(--ink);
  background: #fff;
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  cursor: pointer;
}
.tile:hover {
  background: var(--tint);
}
.tile[aria-pressed='true'] {
  background: var(--select-tint);
  border-color: var(--accent);
  box-shadow: inset 0 0 0 1px var(--accent);
}
/* A card the user wrote: ordinary text, at most two lines, not drawn in the two kana typefaces. */
.plain {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  max-width: 100%;
  overflow: hidden;
  font-size: 14px;
  line-height: 1.25;
  text-align: center;
  overflow-wrap: anywhere;
}
/* How often it was missed, only in the window of missed cards. */
.misses {
  position: absolute;
  top: 4px;
  right: 5px;
  padding: 0 5px;
  font-size: 11px;
  font-weight: 600;
  line-height: 17px;
  color: var(--wrong);
  background: color-mix(in srgb, var(--wrong) 10%, #fff);
  border-radius: 9px;
}
.romaji {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis; /* a kanji's meanings can be long */
  white-space: nowrap;
  font-size: 12px;
  color: var(--muted);
}
</style>
