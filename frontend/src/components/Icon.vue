<script setup lang="ts">
// Takes an SVG path from @mdi/js (Material Design Icons), e.g. <Icon :path="mdiCheck" />. Only the icons that are
// imported end up in the bundle. Icons are decoration: the button or text next to them
// carries the meaning (give an icon-only button an aria-label).
withDefaults(defineProps<{ path: string; size?: number }>(), { size: 20 })
</script>

<template>
  <svg class="icon" :width="size" :height="size" viewBox="0 0 24 24" aria-hidden="true">
    <path :d="path" fill="currentColor" />
  </svg>
</template>

<style scoped>
.icon {
  flex: none;
}
</style>
