import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'
import { inDesktopApp } from '../api/client'

import AppLayout from '../layouts/AppLayout.vue'
import CharactersPage from '../pages/CharactersPage.vue'
import FlashcardsPage from '../pages/FlashcardsPage.vue'
import PlayPage from '../pages/PlayPage.vue'

// There are no accounts and no sign-in: whoever opens the app uses it.
// The server keeps no list of pages: the client application answers every address that is not a file with index.html
// (WebConfig), so a new route here needs nothing on the Java side.
// In the desktop window the pages come from Jolt, which has no such fallback, so the address uses a #.
const router = createRouter({
  history: inDesktopApp() ? createWebHashHistory() : createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', name: 'home', component: FlashcardsPage }, // settings of a round
        { path: 'play', name: 'play', component: PlayPage },
        { path: 'characters', name: 'characters', component: CharactersPage }, // look up any kana or kanji
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

export default router
