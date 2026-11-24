<script setup lang="ts">
// One on or kun reading of a kanji: the kana, then its romaji. The okurigana of a kun reading (the dictionary's "."
// mark) is set off by a thin vertical line, never by a dot. The dictionary's "-" (prefix or suffix form) is not shown.
import type { ReadingView } from '../kanji/kanji'

defineProps<{ view: ReadingView }>()
</script>

<template>
  <span class="reading">
    <span class="kana" lang="ja">{{ view.stem }}<span v-if="view.okurigana" class="okuri">{{ view.okurigana }}</span></span>
    <span class="rom">{{ view.romaji }}</span>
  </span>
</template>

<style scoped>
.reading {
  display: inline-flex;
  align-items: baseline;
  gap: 5px;
  white-space: nowrap;
}
.okuri {
  margin-left: 2px;
  padding-left: 3px;
  border-left: 1px solid var(--line-strong);
}
.rom {
  font-size: 13px;
  font-weight: 400;
  color: var(--muted);
}
</style>
