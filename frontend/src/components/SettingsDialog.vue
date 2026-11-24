<script setup lang="ts">
// The Settings window: a menu on the left, the section on the right: Appearance, Sync, Credits and About (the credits
// the data licences ask for: KANJIDIC2 wants its attribution on a separate screen of the app; the pages carry no credit lines of their own). All sections are drawn
// on top of each other in one grid cell and only the chosen one is shown, so the window is always as tall as the tallest
// section (at any width) and never jumps when the section changes.
// Reka UI Dialog, styled in ui.css (global, because the dialog is drawn in a portal).
import { DialogClose, DialogContent, DialogDescription, DialogOverlay, DialogPortal, DialogRoot, DialogTitle } from 'reka-ui'
import { computed, ref, watch } from 'vue'
import { mdiCheck, mdiClose, mdiInformationOutline, mdiOpenInNew, mdiPaletteOutline, mdiScriptTextOutline, mdiSync } from '@mdi/js'
import { api, inDesktopApp, type AppInfoSummary } from '../api/client'
import Icon from './Icon.vue'
import { keepOpenForMenu } from './ui/menuGuard'
import SyncDesktop from './SyncDesktop.vue'
import SyncServer from './SyncServer.vue'
import { KANJI_SOURCE } from '../kanji/kanji'
// Only who and which version (the packs themselves are big and load when the Characters page needs them).
import STROKE_SOURCE from '../strokes/stroke-source.json'
import WORDS_SOURCE from '../words/words-source.json'
import { ACCENTS, accentByKey, setAccent, theme } from '../state/theme'

const chosen = computed(() => accentByKey(theme.accent))
const section = ref<'appearance' | 'sync' | 'credits' | 'about'>('appearance')
// The desktop app runs the sync (it connects to a server); the website is the server and only shows its key and what it holds.
const desktop = inDesktopApp()

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [open: boolean] }>()

// About: what this running copy is. Asked of Java the first time the window opens (a failure just leaves the line out).
const REPO = 'https://github.com/B077AS/smartjp'
const STACK = ['Java 25', 'Spring Boot', 'Jolt', 'Vue 3', 'SQLite', 'Reka UI', 'Material Design Icons']
const info = ref<AppInfoSummary | null>(null)
watch(
  () => props.open,
  (isOpen) => {
    if (isOpen && info.value === null) {
      api.app
        .info()
        .then((reply) => (info.value = reply))
        .catch(() => undefined)
    }
  },
  { immediate: true },
)
</script>

<template>
  <DialogRoot :open="open" @update:open="emit('update:open', $event)">
    <DialogPortal>
      <DialogOverlay class="ui-overlay" />
      <DialogContent class="ui-dialog settings-dialog" @interact-outside="keepOpenForMenu">
        <nav class="settings-menu" aria-label="Settings sections">
          <DialogTitle class="settings-title">Settings</DialogTitle>
          <button type="button" :class="{ on: section === 'appearance' }" :aria-current="section === 'appearance' ? 'page' : undefined" @click="section = 'appearance'">
            <Icon :path="mdiPaletteOutline" :size="18" />Appearance
          </button>
          <button type="button" :class="{ on: section === 'sync' }" :aria-current="section === 'sync' ? 'page' : undefined" @click="section = 'sync'">
            <Icon :path="mdiSync" :size="18" />Sync
          </button>
          <button type="button" :class="{ on: section === 'credits' }" :aria-current="section === 'credits' ? 'page' : undefined" @click="section = 'credits'">
            <Icon :path="mdiScriptTextOutline" :size="18" />Credits
          </button>
          <button type="button" :class="{ on: section === 'about' }" :aria-current="section === 'about' ? 'page' : undefined" @click="section = 'about'">
            <Icon :path="mdiInformationOutline" :size="18" />About
          </button>
        </nav>
        <div class="settings-body">
          <DialogClose class="settings-close" aria-label="Close settings"><Icon :path="mdiClose" :size="18" /></DialogClose>
          <DialogDescription class="sr-only">Change how the app looks, sync it with your server, and see where its data and typefaces come from.</DialogDescription>
          <div class="settings-sections">
            <section class="settings-section" :class="{ shown: section === 'appearance' }" :inert="section !== 'appearance'" aria-labelledby="appearance-heading">
              <header class="settings-head">
                <h2 id="appearance-heading">Appearance</h2>
                <p class="settings-sub">The colour of buttons, selected kana, tabs and links.</p>
              </header>
              <div class="settings-scroll">
              <h3 class="settings-group">Accent colour</h3>
              <div class="accent-grid" role="group" aria-label="Accent colour">
                <button
                  v-for="a in ACCENTS"
                  :key="a.key"
                  type="button"
                  class="accent-card"
                  :class="{ on: a.key === theme.accent }"
                  :aria-label="a.name"
                  :aria-pressed="a.key === theme.accent"
                  @click="setAccent(a.key)"
                >
                  <span class="accent-dot" :style="{ background: a.color, color: a.on }">
                    <Icon v-if="a.key === theme.accent" :path="mdiCheck" :size="18" />
                  </span>
                  <span class="accent-text"><b>{{ a.name }}</b><span>{{ a.color }}</span></span>
                </button>
              </div>
              <h3 class="settings-group preview-title">Preview: {{ chosen.name }}</h3>
              <div class="accent-preview" aria-hidden="true">
                <div class="pv-row">
                  <span class="pv-btn">Start round</span>
                  <span class="pv-btn pv-quiet">Clear</span>
                  <span class="pv-link">Select all</span>
                </div>
                <div class="pv-row">
                  <span class="pv-tab">Hiragana</span>
                  <span class="pv-tab pv-off">Katakana</span>
                  <span class="pv-tab pv-off">Kanji</span>
                </div>
                <div class="pv-row">
                  <span class="pv-tile">あ</span>
                  <span class="pv-tile pv-off">い</span>
                  <span class="pv-tile">う</span>
                  <span class="pv-tile pv-off">え</span>
                  <span class="pv-tile pv-off">お</span>
                </div>
              </div>
              </div>
            </section>
            <section class="settings-section" :class="{ shown: section === 'sync' }" :inert="section !== 'sync'" aria-labelledby="sync-heading">
              <header class="settings-head">
                <h2 id="sync-heading">Sync</h2>
                <p class="settings-sub">{{ desktop ? 'Keep this app and your own SmartJP website in step.' : 'This website is the server your desktop app syncs with.' }}</p>
              </header>
              <div class="settings-scroll">
                <SyncDesktop v-if="desktop" :active="section === 'sync'" />
                <SyncServer v-else :active="section === 'sync'" />
              </div>
            </section>
            <section class="settings-section" :class="{ shown: section === 'credits' }" :inert="section !== 'credits'" aria-labelledby="credits-heading">
              <header class="settings-head">
                <h2 id="credits-heading">Credits</h2>
                <p class="settings-sub">Where the data and the typefaces come from, and the licences they are used under.</p>
              </header>
              <div class="settings-scroll">
              <div class="settings-cols">
              <div>
              <h3 class="settings-group">Kanji</h3>
              <p class="about">
                The kanji, their grades, readings and meanings come from <strong>KANJIDIC2</strong> (version {{ KANJI_SOURCE.version }}, {{ KANJI_SOURCE.dictDate }}), a
                file of the Electronic Dictionary Research and Development Group (EDRDG), used under the Creative Commons
                Attribution-ShareAlike 4.0 licence (creativecommons.org/licenses/by-sa/4.0). It reached the app through the jmdict-simplified project. More about the
                EDRDG licence: edrdg.org/edrdg/licence.html
              </p>
              </div>
              <div>
              <h3 class="settings-group">Example words</h3>
              <p class="about">
                The example words on the Characters page, and their meanings, come from <strong>JMdict</strong> (version {{ WORDS_SOURCE.version }}, {{ WORDS_SOURCE.dictDate }}), the common words
                only. It is also a file of the EDRDG, under the Creative Commons Attribution-ShareAlike 4.0 licence (creativecommons.org/licenses/by-sa/4.0), and reached the app through the jmdict-simplified
                project. More about the EDRDG licence: edrdg.org/edrdg/licence.html
              </p>
              </div>
              <div>
              <h3 class="settings-group">Stroke order</h3>
              <p class="about">
                How each character is written, stroke by stroke, comes from <strong>KanjiVG</strong> (release {{ STROKE_SOURCE.release }}), by Ulrich Apel and contributors, under the
                Creative Commons Attribution-ShareAlike 3.0 licence (creativecommons.org/licenses/by-sa/3.0). More: kanjivg.tagaini.net
              </p>
              </div>
              <div>
              <h3 class="settings-group">Typefaces</h3>
              <p class="about">
                The kana and kanji are drawn in <strong>Hina Mincho</strong> (The Hina Mincho Project Authors) and <strong>Noto Sans JP</strong> (Google); <strong>Inter</strong> and
                <strong>Newsreader</strong> (The Inter and Newsreader Project Authors) draw the rest of the text. All four are under the SIL Open Font Licence 1.1. Each
                typeface's copyright notice and the licence text are shipped with the app, in its licenses folder.
              </p>
              </div>
              </div>
              </div>
            </section>
            <section class="settings-section" :class="{ shown: section === 'about' }" :inert="section !== 'about'" aria-labelledby="about-heading">
              <header class="settings-head">
                <h2 id="about-heading">About</h2>
                <p class="settings-sub">What this copy of SmartJP is.</p>
              </header>
              <div class="settings-scroll">
                <div class="about-card">
                  <img class="about-logo" src="/logo.svg" alt="" width="96" height="96" />
                  <div class="about-name">SmartJP</div>
                  <div class="about-version">Version {{ info?.version ?? '...' }}</div>
                  <p class="about-text">
                    Learn Japanese, kana first. Flashcards for hiragana, katakana and the 2136 jouyou kanji, a character reference with stroke order and example
                    words, and your own cards. One application that is both a website you host yourself and a desktop app.
                  </p>
                  <div class="about-chips"><span v-for="s in STACK" :key="s" class="about-chip">{{ s }}</span></div>
                  <div class="about-links">
                    <button type="button" class="btn" @click="api.app.openLink(REPO)"><Icon :path="mdiOpenInNew" :size="16" />Source on GitHub</button>
                    <button type="button" class="btn btn-quiet" @click="api.app.openLink(REPO + '/issues')"><Icon :path="mdiOpenInNew" :size="16" />Report a problem</button>
                  </div>
                  <div v-if="info" class="about-system">
                    {{ info.mode === 'desktop' ? 'Desktop app' : 'Website' }}<span class="vbar" aria-hidden="true"></span>{{ info.os }}<span class="vbar" aria-hidden="true"></span>Java {{ info.java }}
                  </div>
                </div>
              </div>
            </section>
          </div>
        </div>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
