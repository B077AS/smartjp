<script setup lang="ts">
import { toasts } from '../state/toasts'
import ToastItem from './ToastItem.vue'
</script>

<template>
  <!-- Fixed over everything (above Reka dialogs, z-index 70/71), never part of the page flow. -->
  <TransitionGroup tag="div" name="toast" class="toast-layer" role="region" aria-label="Notifications">
    <ToastItem v-for="t in toasts" :key="t.id" :toast="t" />
  </TransitionGroup>
</template>

<style scoped>
.toast-layer {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 100;
  width: min(340px, calc(100vw - 32px));
  display: flex;
  flex-direction: column;
  gap: 10px;
  pointer-events: none;
}
.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 0.25s ease,
    transform 0.25s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(24px);
}
/* A leaving toast stops taking space at once, so the others slide up instead of waiting for it. */
.toast-leave-active {
  position: absolute;
  width: 100%;
}
.toast-move {
  transition: transform 0.25s ease;
}
@media (prefers-reduced-motion: reduce) {
  .toast-enter-active,
  .toast-leave-active,
  .toast-move {
    transition: none;
  }
}
</style>
