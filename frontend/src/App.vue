<script setup lang="ts">
import { inDesktopApp } from './api/client'
import TextMenu from './components/TextMenu.vue'
import ToastLayer from './components/ToastLayer.vue'
import UnlockPage from './components/UnlockPage.vue'
import { access } from './state/access'

// The right-click menu is drawn by the page in the desktop window only (a Jolt window has none of its own); a browser shows its own.
const desktop = inDesktopApp()
</script>

<template>
  <!-- A website that wants its access key shows only the unlock screen until it has it. -->
  <UnlockPage v-if="access.locked" />
  <RouterView v-else />
  <ToastLayer />
  <TextMenu v-if="desktop" />
</template>
