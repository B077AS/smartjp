<script setup lang="ts">
// The Characters page: look up any kana or kanji. Left: a search box and the chart of the kana (the usual table, rows by consonant and columns by
// vowel) or the kanji of a grade. Right: the entry of the chosen character: how it is drawn in two typefaces, its strokes (a frame per stroke, and
// a window with a player to watch, zoom and step through), its readings and meanings (kanji), and example words.
// Nothing here is typed by hand: kana from the verified table, kanji from KANJIDIC2, strokes from KanjiVG, words from JMdict (see RESEARCH.md).
import { computed, nextTick, ref, shallowRef, watch } from 'vue'
import { TabsList, TabsRoot, TabsTrigger } from 'reka-ui'
import { mdiCheck, mdiChevronLeft, mdiChevronRight, mdiClose, mdiMagnify, mdiPlay } from '@mdi/js'
import Icon from '../components/Icon.vue'
import KanaGlyphs from '../components/KanaGlyphs.vue'
import KanjiReading from '../components/KanjiReading.vue'
import StrokeDialog from '../components/StrokeDialog.vue'
import StrokeSteps from '../components/StrokeSteps.vue'
import UiSegmented from '../components/ui/UiSegmented.vue'
import type { UiOption } from '../components/ui/types'
import { view } from '../characters/view'
import { CARDS, GROUPS, SCRIPTS, SECTIONS, cardsOfGroup, type Card, type Group, type SectionId } from '../kana/kana'
import { KANJI_GRADES, kanjiOfGrade, type KanjiCard } from '../kanji/kanji'
import { cardById, captionOf, faceOf, isCustomCard, isKanjiCard, type BuiltInCard } from '../flashcards/cards'
import { searchCards } from '../flashcards/search'
import { isSelected, toggleCard } from '../flashcards/store'
import { strokesFor, type StrokeSet } from '../strokes/strokes'
import { wordsFor, type ExampleWord } from '../words/words'

// ---- what is shown on the left ---------------------------------------------------------------------------------
const GRADE_OPTIONS: UiOption<string>[] = KANJI_GRADES.map((g) => ({ value: String(g.grade), label: String(g.grade) }))
const grade = computed({
  get: () => String(view.grade),
  set: (value: string) => (view.grade = Number(value)),
})

// The kana are laid out like on the flashcards page: a card per section (Basic, Special, Voiced, P-sounds, Combinations), a titled block per row.
const sectionOf = (card: Card): SectionId => GROUPS.find((g) => g.id === card.group)?.section ?? 'basic'
const groupsOf = (sectionId: SectionId): Group[] => GROUPS.filter((g) => g.section === sectionId)
const kanjiShown = computed(() => kanjiOfGrade(view.grade))

// ---- search ---------------------------------------------------------------------------------------------------
const MAX_RESULTS = 300
const searching = computed(() => view.query.trim() !== '')
const matches = computed(() => searchCards(view.query))
const shown = computed(() => matches.value.slice(0, MAX_RESULTS))

// ---- the chosen character -----------------------------------------------------------------------------------
const card = computed<BuiltInCard>(() => {
  const found = cardById(view.selectedId)
  return found && !isCustomCard(found) ? found : (cardById('hiragana:3042') as BuiltInCard)
})
const kanji = computed<KanjiCard | null>(() => (isKanjiCard(card.value) ? card.value : null))
const kana = computed<Card | null>(() => (isKanjiCard(card.value) ? null : card.value))
const face = computed(() => faceOf(card.value))

const choose = (next: BuiltInCard): void => {
  view.selectedId = next.id
}

// The tab, section and grade follow the chosen character, so it is always found in the list when it is picked from a search or with the arrows.
watch(
  () => view.selectedId,
  () => {
    if (searching.value) return
    const c = card.value
    if (isKanjiCard(c)) {
      view.tab = 'kanji'
      view.grade = c.grade
    } else {
      view.tab = c.script
    }
  },
  { immediate: true },
)

// The arrows go through the results while a search is shown, otherwise through the character's own script (or its grade).
const siblings = computed<BuiltInCard[]>(() => {
  if (searching.value && shown.value.some((c) => c.id === card.value.id)) return shown.value
  const c = card.value
  return isKanjiCard(c) ? kanjiOfGrade(c.grade) : CARDS.filter((k) => k.script === c.script)
})
const at = computed(() => siblings.value.findIndex((c) => c.id === card.value.id))
function move(by: 1 | -1): void {
  const next = siblings.value[at.value + by]
  if (!next) return
  choose(next)
  nextTick(() => document.querySelector('.chars [aria-current="true"]')?.scrollIntoView({ block: 'nearest' }))
}

// The two parts of the line above the entry ("Hiragana", "Basic"), set apart by a vertical line in the template.
const heading = computed<[string, string]>(() => {
  const c = card.value
  if (isKanjiCard(c)) return ['Kanji', `Grade ${c.grade}`]
  return [SCRIPTS.find((s) => s.id === c.script)?.label ?? '', SECTIONS.find((s) => s.id === sectionOf(c))?.label ?? '']
})

// ---- strokes and words, loaded for the chosen character ----------------------------------------------------------
type Load = 'loading' | 'ready' | 'missing' | 'error'
const strokes = shallowRef<StrokeSet | null>(null)
const strokesState = ref<Load>('loading')
const words = shallowRef<ExampleWord[]>([])
const wordsState = ref<Load>('loading')
let sequence = 0

watch(
  face,
  async (now) => {
    const mine = ++sequence
    strokes.value = null
    words.value = []
    strokesState.value = 'loading'
    wordsState.value = 'loading'
    const [s, w] = await Promise.allSettled([strokesFor(now), wordsFor(now)])
    if (mine !== sequence) return // another character was chosen meanwhile
    if (s.status === 'fulfilled') {
      strokes.value = s.value
      strokesState.value = s.value ? 'ready' : 'missing'
    } else strokesState.value = 'error'
    if (w.status === 'fulfilled') {
      words.value = w.value
      wordsState.value = w.value.length > 0 ? 'ready' : 'missing'
    } else wordsState.value = 'error'
  },
  { immediate: true },
)

const strokeOpen = ref(false)
const inSelection = computed(() => isSelected(card.value.id))
const meaningsLine = computed(() => (kanji.value ? kanji.value.meanings.slice(0, 3).join(', ') : ''))
const moreMeanings = computed(() => (kanji.value ? kanji.value.meanings.slice(3).join(', ') : ''))
const strokeCount = computed(() => strokes.value?.strokes.length ?? 0)
</script>

<template>
  <div class="page">
    <header class="top">
      <h1>Characters</h1>
      <p class="muted">Look up any kana or kanji: how it is written, how it sounds and words that use it.</p>
    </header>

    <div class="layout">
      <!-- ============ the characters ============ -->
      <section class="main chars" aria-label="Characters">
        <div class="bar">
          <div class="searchbar">
            <Icon :path="mdiMagnify" :size="18" class="lead" />
            <input
              v-model="view.query"
              type="text"
              role="searchbox"
              class="search"
              placeholder="Search a character, a reading or a meaning"
              aria-label="Search characters"
              autocomplete="off"
              spellcheck="false"
              @keydown.escape="view.query = ''"
            />
            <button v-if="view.query !== ''" type="button" class="clear-search" aria-label="Clear the search" @click="view.query = ''">
              <Icon :path="mdiClose" :size="16" />
            </button>
          </div>
          <span v-if="!searching && view.tab === 'kanji'" class="grades">
            <span class="muted">Grade</span>
            <UiSegmented v-model="grade" :options="GRADE_OPTIONS" aria-label="Kanji grade" />
          </span>
        </div>

        <section v-if="searching" class="card body results" aria-label="Search results">
          <p v-if="matches.length > 0" class="muted small count" aria-live="polite">
            {{ matches.length }} {{ matches.length === 1 ? 'character matches' : 'characters match' }}<template v-if="matches.length > shown.length">, the first {{ shown.length }} are shown</template>
          </p>
          <div class="scroller">
            <div v-if="matches.length > 0" class="kgrid">
              <button v-for="c in shown" :key="c.id" type="button" class="ctile" :aria-current="c.id === card.id ? 'true' : undefined" @click="choose(c)">
                <span class="g" lang="ja">{{ faceOf(c) }}</span>
                <span class="r">{{ captionOf(c) }}</span>
              </button>
            </div>
            <p v-else class="muted small">Nothing matches. Try the character itself, a reading as kana or romaji (shi), or an English meaning of a kanji (water).</p>
          </div>
        </section>

        <template v-else>
          <TabsRoot v-model="view.tab" class="tabs">
            <TabsList class="ui-tabs-list" aria-label="Script">
              <TabsTrigger v-for="script in SCRIPTS" :key="script.id" :value="script.id" class="ui-tab">
                <span class="kana tabkana" lang="ja">{{ script.sample }}</span>
                <span class="lbl" :data-text="script.label">{{ script.label }}</span>
              </TabsTrigger>
              <TabsTrigger value="kanji" class="ui-tab">
                <span class="kana tabkana" lang="ja">字</span>
                <span class="lbl" data-text="Kanji">Kanji</span>
              </TabsTrigger>
            </TabsList>
          </TabsRoot>

          <!-- Like the flashcards page: one card per section, a titled block per row, the tiles in the order of the row. -->
          <div v-if="view.tab !== 'kanji'" class="tab-scroll">
            <section v-for="section in SECTIONS" :key="section.id" class="card section" :aria-labelledby="`h-${view.tab}-${section.id}`">
              <header class="section-head">
                <h2 :id="`h-${view.tab}-${section.id}`">{{ section.label }}</h2>
                <p class="muted small">{{ section.hint }}</p>
              </header>
              <div class="groups" :class="{ combo: section.id === 'combo' }">
                <div v-for="group in groupsOf(section.id)" :key="group.id" class="group">
                  <div class="group-head"><strong>{{ group.title }}</strong></div>
                  <div class="tiles" :style="{ '--cols': section.id === 'combo' ? 3 : 5 }">
                    <button
                      v-for="cell in cardsOfGroup(group.id, view.tab)"
                      :key="cell.id"
                      type="button"
                      class="ctile"
                      :class="{ combo: section.id === 'combo' }"
                      :aria-current="cell.id === card.id ? 'true' : undefined"
                      :aria-label="`${cell.kana} ${cell.romaji}`"
                      @click="choose(cell)"
                    >
                      <span class="g" lang="ja">{{ cell.kana }}</span>
                      <span class="r">{{ cell.romaji }}</span>
                    </button>
                  </div>
                </div>
              </div>
            </section>
          </div>

          <div v-else class="body-scroll">
            <section class="card body" aria-label="Kanji">
              <p class="muted small count">{{ kanjiShown.length }} kanji of grade {{ view.grade }}, the ones with the fewest strokes first.</p>
              <div class="scroller">
                <div class="kgrid">
                  <button v-for="c in kanjiShown" :key="c.id" type="button" class="ctile" :aria-current="c.id === card.id ? 'true' : undefined" @click="choose(c)">
                    <span class="g" lang="ja">{{ c.kanji }}</span>
                    <span class="r">{{ captionOf(c) }}</span>
                  </button>
                </div>
              </div>
            </section>
          </div>
        </template>
      </section>

      <!-- ============ the entry of the chosen character ============ -->
      <div class="detail-col">
        <aside class="card detail" aria-label="Entry">
          <div class="d-head">
            <span class="muted small">{{ heading[0] }}<span class="vbar" aria-hidden="true"></span>{{ heading[1] }}</span>
            <span class="nav">
              <button type="button" class="nav-btn" aria-label="Previous character" :disabled="at <= 0" @click="move(-1)"><Icon :path="mdiChevronLeft" :size="18" /></button>
              <button type="button" class="nav-btn" aria-label="Next character" :disabled="at < 0 || at >= siblings.length - 1" @click="move(1)"><Icon :path="mdiChevronRight" :size="18" /></button>
            </span>
          </div>

          <div class="d-scroll">
          <div class="hero">
            <div class="bigglyph" :class="{ two: [...face].length > 1 }" lang="ja"><span class="g">{{ face }}</span></div>
            <div class="hero-text">
              <template v-if="kana">
                <div class="romaji copyable">{{ kana.romaji }}</div>
                <KanaGlyphs :kana="kana.kana" fonts="both" labels class="faces" />
              </template>
              <template v-else-if="kanji">
                <div class="meaning copyable">{{ meaningsLine }}</div>
                <div v-if="moreMeanings" class="muted small copyable">{{ moreMeanings }}</div>
                <div class="chips">
                  <span class="chip">Grade {{ kanji.grade }}</span>
                  <span v-if="strokeCount" class="chip">{{ strokeCount }} {{ strokeCount === 1 ? 'stroke' : 'strokes' }}</span>
                </div>
              </template>
            </div>
          </div>
          <p v-if="kana?.note" class="muted small note">{{ kana.note }}</p>

          <div v-if="kanji" class="readings">
            <div class="reading-col">
              <div class="lab">On</div>
              <div class="reading-list"><KanjiReading v-for="r in kanji.onView" :key="r.stem + r.okurigana" :view="r" /><span v-if="kanji.onView.length === 0" class="muted small">none</span></div>
            </div>
            <div class="reading-col">
              <div class="lab">Kun</div>
              <div class="reading-list"><KanjiReading v-for="r in kanji.kunView" :key="r.stem + r.okurigana" :view="r" /><span v-if="kanji.kunView.length === 0" class="muted small">none</span></div>
            </div>
          </div>

          <section class="sec" aria-label="Stroke order">
            <div class="sec-head">
              <h2>Stroke order</h2>
              <span v-if="strokeCount" class="muted small grow">{{ strokeCount }} {{ strokeCount === 1 ? 'stroke' : 'strokes' }}</span>
              <span v-else class="grow"></span>
              <button v-if="strokes" type="button" class="btn btn-quiet small-btn" @click="strokeOpen = true"><Icon :path="mdiPlay" :size="16" />Play and zoom</button>
            </div>
            <StrokeSteps v-if="strokes" :set="strokes" />
            <p v-else-if="strokesState === 'loading'" class="muted small">Loading the strokes…</p>
            <p v-else-if="strokesState === 'missing'" class="muted small">There are no strokes for this character.</p>
            <p v-else class="muted small">The stroke data could not be loaded.</p>
            <p v-if="strokes" class="muted small hint">Orange is the stroke just written, grey is still to come.</p>
          </section>

          <section class="sec words" aria-label="Example words">
            <div class="sec-head">
              <h2>Words with <span class="ja" lang="ja">{{ face }}</span></h2>
              <span class="muted small grow">common words</span>
            </div>
            <ul v-if="words.length > 0" class="ex-list">
              <li v-for="w in words" :key="w.written + w.readings.map((r) => r.kana).join()" class="ex">
                <span class="ex-w copyable ja" lang="ja">{{ w.written }}</span>
                <span class="ex-body">
                  <span v-if="w.readings.length > 0" class="ex-read">
                    <template v-for="(r, i) in w.readings" :key="r.kana">
                      <span v-if="i > 0" class="vbar" aria-hidden="true"></span><span class="copyable ja" lang="ja">{{ r.kana }}</span> <span class="muted small">{{ r.romaji }}</span>
                    </template>
                  </span>
                  <span v-else class="muted small">{{ w.romaji }}</span>
                  <span class="ex-mean copyable">{{ w.meanings }}</span>
                </span>
              </li>
            </ul>
            <p v-else-if="wordsState === 'loading'" class="muted small">Loading the words…</p>
            <p v-else-if="wordsState === 'missing'" class="muted small">No common word was found for this character.</p>
            <p v-else class="muted small">The words could not be loaded.</p>
          </section>
          </div>

          <div class="foot">
            <button type="button" class="btn" :class="inSelection ? 'btn-quiet' : 'btn-primary'" @click="toggleCard(card.id)">
              <Icon :path="mdiCheck" :size="18" />{{ inSelection ? 'In your flashcard selection (remove)' : 'Add to my flashcard selection' }}
            </button>
          </div>
        </aside>
      </div>
    </div>

    <StrokeDialog v-model:open="strokeOpen" :face="face" :set="strokes" />
  </div>
</template>

<style scoped>
.top {
  margin-bottom: 14px;
}
.top p {
  margin: 2px 0 0;
  font-size: 14px;
}
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 400px;
  gap: 24px;
  align-items: start;
}
/* Wide and tall enough: the page itself does not scroll. The search bar and the tabs stay where they are; the characters scroll, and so
   does the middle of the entry on the right when it is taller than the window. */
@media (min-width: 1101px) and (min-height: 700px) {
  .page {
    display: flex;
    flex-direction: column;
    height: var(--page-height, auto);
  }
  .layout {
    flex: 1;
    min-height: 0;
    grid-template-rows: minmax(0, 1fr);
    align-items: stretch;
  }
  .main {
    display: flex;
    flex-direction: column;
    min-height: 0;
  }
  .tabs {
    flex: none;
  }
  /* The card (chart, kanji, search results) keeps its own header, the count or the column heads, and only what is under it scrolls
     (.scroller), with the bar at the card's edge. */
  .body-scroll,
  .results {
    flex: 0 1 auto;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
  .body-scroll > .body {
    flex: 0 1 auto;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
  .scroller {
    min-height: 0;
    overflow-y: auto;
    margin: 0 -16px -16px;
    padding: 0 16px 16px;
  }
  /* The kana: the tabs stay, the sections under them scroll (like the flashcards page). */
  .tab-scroll {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding-right: 4px; /* a little air between the tiles and the scrollbar */
  }
  /* The entry is a card as tall as the page. Its top (where it is, and the arrows) and its bottom (the button) stay; everything between
     them is ONE scrolling area (.d-scroll), with its bar at the card's right edge, so nothing can ever hang out of the card. */
  .detail-col {
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
  .detail {
    flex: 1 1 auto;
    min-height: 0;
  }
  .d-scroll {
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: auto;
    margin: 0 -18px;
    padding: 0 18px;
  }
}
.small {
  font-size: 13px;
}
.bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 16px;
  margin-bottom: 12px;
}
.grades {
  display: flex;
  align-items: center;
  gap: 8px;
}
.searchbar {
  position: relative;
  flex: 1 1 280px;
  max-width: 420px;
}
.search {
  width: 100%;
  height: 32px;
  padding: 0 32px 0 32px;
  font: inherit;
  font-size: 13px;
  color: var(--ink);
  background: #fff;
  border: 1px solid var(--line-strong);
  border-radius: 4px;
}
.searchbar .lead {
  position: absolute;
  left: 9px;
  top: 7px;
  color: var(--muted);
  pointer-events: none;
}
.clear-search {
  position: absolute;
  right: 4px;
  top: 4px;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  padding: 0; /* the browser's own button padding pushed the icon to the right of its box */
  color: var(--muted);
  background: none;
  border: 0;
  border-radius: 4px;
  cursor: pointer;
}
.clear-search:hover {
  color: var(--ink);
  background: var(--tint);
}
.tabkana {
  font-size: 18px;
  line-height: 1;
  color: var(--ink);
}
.body-scroll {
  padding: 12px 4px 4px 0;
}
.body {
  padding: 14px 16px 16px;
}
.results {
  margin-top: 0;
}
.count {
  margin: 0 0 8px;
}

/* the kana: the same sections, rows and tiles as the flashcards page (without the checkboxes, a tile opens the entry) */
.tab-scroll {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 12px;
}
.section {
  padding: 14px 18px 8px;
}
.section-head {
  margin-bottom: 8px;
}
.section-head h2 {
  font-size: 18px;
}
.section-head p {
  margin: 2px 0 0;
}
/* A row is a titled block of its own. Wide screens put two rows side by side. */
.groups {
  display: grid;
  /* 440 px is what the flashcards need for five two-typeface tiles; the same width here keeps both pages alike. */
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 440px), 1fr));
  gap: 0 24px;
}
.groups.combo {
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 340px), 1fr));
}
.group {
  padding: 10px 0 12px;
  border-top: 1px solid var(--line);
}
.group-head {
  margin-bottom: 8px;
}
.tiles {
  display: grid;
  grid-template-columns: repeat(var(--cols, 5), minmax(0, 1fr));
  gap: 6px;
}
.tiles .ctile {
  min-height: 74px;
}
.tiles .ctile .g {
  font-size: 28px;
}
.tiles .ctile.combo .g {
  font-size: 22px;
}

/* a character: one button, the chosen one is marked with the accent */
.ctile {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  min-height: 56px;
  padding: 8px 6px 6px;
  font: inherit;
  color: var(--ink);
  background: #fff;
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  cursor: pointer;
}
.ctile:hover {
  background: var(--tint);
}
.ctile[aria-current='true'] {
  background: var(--select-tint);
  border-color: var(--accent);
  box-shadow: inset 0 0 0 1px var(--accent);
}
.ctile .g {
  font-family: var(--font-mincho);
  font-size: 28px;
  line-height: 1.15;
}
.ctile .r {
  max-width: 100%;
  overflow: hidden;
  font-size: 12px;
  color: var(--muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kgrid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(78px, 1fr));
  gap: 6px;
}
.kgrid .ctile {
  min-height: 70px;
}
.kgrid .ctile .g {
  font-size: 28px;
}

/* the entry */
.detail-col {
  min-width: 0;
}
/* Hero, readings, stroke order and words. Never a sideways scrollbar: whatever is long has to wrap (see the rules below). */
.d-scroll {
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-x: hidden;
}
.detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px 18px 18px;
}
.d-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.nav {
  display: flex;
  gap: 6px;
}
.nav-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  color: var(--ink);
  background: var(--surface);
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  cursor: pointer;
}
.nav-btn:hover:not(:disabled) {
  background: var(--tint);
}
.nav-btn:disabled {
  opacity: 0.4;
  cursor: default;
}
.hero {
  display: flex;
  align-items: center;
  gap: 16px;
}
.bigglyph {
  display: grid;
  flex: none;
  place-items: center;
  width: 132px;
  height: 132px;
  background: var(--tint);
  border-radius: 6px;
}
.bigglyph .g {
  font-family: var(--font-mincho);
  font-size: 96px;
  line-height: 1;
}
.bigglyph.two .g {
  font-size: 58px;
}
.hero-text {
  min-width: 0;
}
.romaji {
  font-family: var(--font-display);
  font-size: 36px;
  line-height: 1.1;
}
.faces {
  --glyph-size: 30px;
  margin-top: 10px;
  justify-content: flex-start;
}
.meaning {
  font-family: var(--font-display);
  font-size: 25px;
  line-height: 1.2;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.chip {
  padding: 1px 9px;
  font-size: 12.5px;
  background: var(--tint);
  border-radius: 10px;
}
.note {
  margin: -6px 0 0;
}
.readings {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
.reading-col {
  min-width: 0;
  padding: 0 14px;
  border-left: 1px solid var(--line);
}
.reading-col:first-child {
  padding-left: 0;
  border-left: 0;
}
.lab {
  margin-bottom: 4px;
  font-size: 11.5px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--muted);
}
.reading-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 18px;
}
/* KanjiReading never wraps (right for the answer under a card); here a long reading has to wrap inside its column. */
.reading-list :deep(.reading) {
  flex-wrap: wrap;
  white-space: normal;
  overflow-wrap: anywhere;
}
.sec h2 {
  margin: 0;
  font-size: 17px;
}
.sec-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.grow {
  flex: 1;
}
.small-btn {
  padding: 4px 10px;
  font-size: 12.5px;
}
.hint {
  margin: 8px 0 0;
}
.ja {
  font-family: var(--font-gothic);
}
.ex-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.ex {
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  padding: 9px 0;
  border-top: 1px solid var(--line);
}
.ex-w {
  font-size: 20px;
  line-height: 1.2;
  overflow-wrap: anywhere;
}
.ex-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.ex-read,
.ex-mean,
.meaning,
.romaji {
  overflow-wrap: anywhere;
}
.ex-read {
  font-size: 14px;
}
.ex-mean {
  font-size: 14px;
}
.foot {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

@media (max-width: 1100px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .detail-col {
    order: -1; /* the entry first on a narrow window */
  }
}
</style>
