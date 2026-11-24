<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { mdiBookOpenVariantOutline, mdiCardsOutline, mdiCogOutline } from '@mdi/js'
import Icon from '../components/Icon.vue'
import SettingsDialog from '../components/SettingsDialog.vue'
import Wordmark from '../components/Wordmark.vue'

const settingsOpen = ref(false)
// The round belongs to Flashcards. (RouterLink's own active class would mark Flashcards on every page: its route has an empty path.)
const route = useRoute()
const inFlashcards = computed(() => route.name === 'home' || route.name === 'play')
const inCharacters = computed(() => route.name === 'characters')
</script>

<template>
  <div class="shell">
    <nav class="sidebar hover-scroll" aria-label="Main">
      <Wordmark />
      <div class="links">
        <RouterLink :to="{ name: 'home' }" class="link" :class="{ active: inFlashcards }"><Icon :path="mdiCardsOutline" :size="18" />Flashcards</RouterLink>
        <RouterLink :to="{ name: 'characters' }" class="link" :class="{ active: inCharacters }"><Icon :path="mdiBookOpenVariantOutline" :size="18" />Characters</RouterLink>
      </div>
      <div class="account">
        <button type="button" class="settings-link" @click="settingsOpen = true"><Icon :path="mdiCogOutline" :size="18" />Settings</button>
      </div>
    </nav>
    <main class="content">
      <RouterView />
    </main>
    <SettingsDialog v-model:open="settingsOpen" />
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 208px minmax(0, 1fr);
}
/* Stays in view while the page scrolls. On a window too short for it, it scrolls by itself (.hover-scroll: the bar shows on hover). */
.sidebar {
  position: sticky;
  top: 0;
  align-self: start;
  height: 100vh;
  scrollbar-gutter: stable;
  padding: 22px 2px 22px 14px; /* the right side is the room kept for the scrollbar (12px), so the content has 14px on both sides */
  border-right: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  gap: 22px;
}
.links {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 4px;
  font-size: 14px;
}
.link.active {
  background: var(--tint);
  font-weight: 600;
}
.account {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.settings-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  font: inherit;
  font-size: 14px;
  color: var(--ink);
  text-align: left;
  background: none;
  border: 0;
  border-radius: 4px;
  cursor: pointer;
}
.settings-link:hover {
  background: var(--tint);
}
.content {
  min-width: 0;
  padding: 22px clamp(16px, 2.5vw, 40px) 40px;
  /* The height a page can fill without the window scrolling: the window minus the padding above and below. */
  --page-height: calc(100vh - 62px);
}

/* Narrow window: the sidebar becomes a strip on top and scrolls away with the page. */
@media (max-width: 720px) {
  .shell {
    grid-template-columns: minmax(0, 1fr);
  }
  .sidebar {
    position: static;
    height: auto;
    padding: 22px 14px;
    scrollbar-gutter: auto;
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }
  .account {
    margin-top: 0;
  }
  .content {
    padding: 24px 16px 40px;
  }
}
</style>
