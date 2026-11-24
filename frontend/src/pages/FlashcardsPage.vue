<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { CollapsibleContent, CollapsibleRoot, CollapsibleTrigger, TabsContent, TabsList, TabsRoot, TabsTrigger } from 'reka-ui'
import { mdiCardTextOutline, mdiCheckAll, mdiChevronRight, mdiClose, mdiContentSaveOutline, mdiEyeOutline, mdiMagnify, mdiPencilOutline, mdiPlay, mdiPlus, mdiTrashCanOutline } from '@mdi/js'
import Icon from '../components/Icon.vue'
import KanaGlyphs from '../components/KanaGlyphs.vue'
import KanaTile from '../components/KanaTile.vue'
import UiCheckbox from '../components/ui/UiCheckbox.vue'
import UiDialog from '../components/ui/UiDialog.vue'
import UiSegmented from '../components/ui/UiSegmented.vue'
import UiSelect from '../components/ui/UiSelect.vue'
import type { UiOption } from '../components/ui/types'
import { GROUPS, SCRIPTS, SECTIONS, cardsOfGroup, type Group, type ScriptId, type SectionId } from '../kana/kana'
import { KANJI_GRADES, kanjiOfGrade, type KanjiGrade } from '../kanji/kanji'
import { cardById, faceOf, getCard, modeFits, type BuiltInCard, type StudyCard } from '../flashcards/cards'
import { searchCards } from '../flashcards/search'
import { MAX_TEXT, customCards, removeCustomCard, saveCustomCard, type CustomCard } from '../flashcards/custom'
import { mostMissed, stats, type QuestionMode } from '../progress/log'
import { notifyError, notifySuccess } from '../state/toasts'
import { MAX_NAME, cleanName, lists, listNamed, removeList, saveList, type CardList } from '../flashcards/lists'
import { OPTIONS, buildDeck, replaceSelection, setCards, settings, startSession, toggleCard, type FontChoice } from '../flashcards/store'

const router = useRouter()
const tab = ref<ScriptId | 'kanji' | 'custom'>('hiragana')

const FONT_OPTIONS: UiOption<FontChoice>[] = [
  { value: 'both', label: 'Both' },
  { value: 'mincho', label: 'Mincho' },
  { value: 'gothic', label: 'Gothic' },
]
const COUNT_OPTIONS: UiOption<number>[] = OPTIONS.COUNTS.map((n) => ({ value: n, label: n === 0 ? 'All selected cards' : n + ' cards' }))
const SECONDS_OPTIONS: UiOption<number>[] = OPTIONS.SECONDS.map((n) => ({ value: n, label: n === 0 ? 'No time limit' : n + ' seconds' }))
// The questions offered follow the open tab: typing a meaning only makes sense for kanji, writing the kana from a reading only for
// kana, and your own cards can be answered by typing the answer or by checking yourself. Leaving a tab with a question chosen that
// the new tab does not list falls back to typing (the select would otherwise show an option it does not list).
const modeOptions = computed<UiOption<QuestionMode>[]>(() => {
  const flip: UiOption<QuestionMode> = { value: 'flip', label: 'Show the card, I check myself' }
  if (tab.value === 'custom') return [{ value: 'reading', label: 'Show the card, I type the answer' }, flip]
  const options: UiOption<QuestionMode>[] = [
    { value: 'reading', label: 'Show the card, I type the reading' },
    { value: 'kana', label: 'Show the reading, I type the kana' },
  ]
  if (tab.value === 'kanji') options.push({ value: 'meaning', label: 'Show the kanji, I type the meaning' })
  return [...options, flip]
})
watch(
  modeOptions,
  (options) => {
    if (!options.some((option) => option.value === settings.mode)) settings.mode = 'reading'
  },
  { immediate: true },
)

// ---- the selection -----------------------------------------------------------------------------------------
const selectedSet = computed(() => new Set(settings.selected))
const total = computed(() => selectedCards.value.length)
// The selected cards that exist: for the moment after a custom card is deleted, when its id may still be in the selection for one render.
const selectedCards = computed(() => settings.selected.map(cardById).filter((card) => card !== undefined))
const countIn = (script: ScriptId | 'kanji' | 'custom'): number => selectedCards.value.filter((card) => card.script === script).length
// The question decides which of the selected cards can be asked: writing a reading as kana only works for kana, typing a meaning only for kanji.
const playable = computed(() => selectedCards.value.filter((card) => modeFits(settings.mode, card)).length)
const roundSize = computed(() => (settings.count > 0 ? Math.min(settings.count, playable.value) : playable.value))

const groupsOf = (sectionId: SectionId): Group[] => GROUPS.filter((g) => g.section === sectionId)
const idsOf = (cards: { id: string }[]): string[] => cards.map((c) => c.id)
const sectionCards = (sectionId: SectionId, script: ScriptId): BuiltInCard[] => groupsOf(sectionId).flatMap((g) => cardsOfGroup(g.id, script))

function groupState(group: Group, script: ScriptId): boolean | 'indeterminate' {
  const ids = idsOf(cardsOfGroup(group.id, script))
  const chosen = ids.filter((id) => selectedSet.value.has(id)).length
  return chosen === 0 ? false : chosen === ids.length ? true : 'indeterminate'
}

const setGroup = (group: Group, script: ScriptId, on: boolean | 'indeterminate') => setCards(idsOf(cardsOfGroup(group.id, script)), on === true)
const setSection = (sectionId: SectionId, script: ScriptId, on: boolean) => setCards(idsOf(sectionCards(sectionId, script)), on)
const setScript = (script: ScriptId, on: boolean) => setCards(idsOf(SECTIONS.flatMap((s) => sectionCards(s.id, script))), on)

const isKnown = (card: { id: string }): boolean => (stats.get(card.id)?.correct ?? 0) > 0
const knownIn = (cards: { id: string }[]): string => cards.filter(isKnown).length + '/' + cards.length

// Which grades are unfolded (kept only while the page is open). Grade 1 starts open; a grade only draws its tiles while open (grade 8 has 1110).
const openGrades = ref<number[]>([1])
const setGradeOpen = (grade: number, open: boolean) => {
  openGrades.value = open ? [...openGrades.value, grade] : openGrades.value.filter((g) => g !== grade)
}
const setGrade = (grade: KanjiGrade, on: boolean) => setCards(idsOf(kanjiOfGrade(grade.grade)), on)
const setAllKanji = (on: boolean) => setCards(idsOf(KANJI_GRADES.flatMap((g) => kanjiOfGrade(g.grade))), on)

// ---- search ------------------------------------------------------------------------------------------------
// While something is typed, the tabs give way to one list of every matching card (kana and kanji together). Selecting there
// works on the cards that are on screen; a very wide search (one letter) is cut off so the page stays quick.
const MAX_RESULTS = 300
const query = ref('')
const searching = computed(() => query.value.trim() !== '')
const matches = computed(() => searchCards(query.value))
const shown = computed(() => matches.value.slice(0, MAX_RESULTS))

// "15 of 71": how much of a section is in the round.
const chosenIn = (cards: { id: string }[]): number => cards.filter((c) => selectedSet.value.has(c.id)).length

function start() {
  startSession(buildDeck(), settings)
  router.push({ name: 'play' })
}

// ---- most missed ------------------------------------------------------------------------------------------
// Every answer is logged (progress/log.ts); a card answered wrongly (a skip and a timeout count) is a missed card, and the one missed
// most often comes first. The shortcuts here take the first N of the cards that fit the chosen question.
const MISSED_OPTIONS: UiOption<number>[] = [10, 20, 50, 0].map((n) => ({ value: n, label: n === 0 ? 'All missed cards' : n + ' cards' }))
const missedCount = ref(10)
const missedOpen = ref(false)
// Cards that no longer exist (a deleted custom card) have history but are not offered.
const missedAll = computed(() => mostMissed().map((row) => cardById(row.cardId)).filter((card): card is StudyCard => card !== undefined))
const missedFit = computed(() => missedAll.value.filter((card) => modeFits(settings.mode, card)))
const missedPick = computed(() => (missedCount.value > 0 ? missedFit.value.slice(0, missedCount.value) : missedFit.value))
const missedLine = computed(() => {
  const all = missedAll.value.length
  const fit = missedFit.value.length
  if (all === 0) return 'Nothing missed yet. Cards you get wrong will show up here.'
  if (fit === 0) return 'None of the ' + cardsText(all) + ' you missed fits the chosen question.'
  return fit < all ? fit + ' of the ' + cardsText(all) + ' you missed fit the chosen question.' : 'You have missed ' + cardsText(all) + ' at least once.'
})
/** Adds the most missed cards to the selection and leaves the rest of it alone. */
const selectMissed = () => setCards(idsOf(missedPick.value), true)
/** Selects only the most missed cards and starts a round at once, with the settings of the Round panel. */
function practiseMissed() {
  replaceSelection(idsOf(missedPick.value))
  start()
}

// ---- my lists ----------------------------------------------------------------------------------------------
// A list holds ids only, so it can name a card that is gone (a custom card that was deleted): such ids are ignored everywhere.
const liveCards = (list: CardList): string[] => list.cards.filter((id) => cardById(id))
const cardsText = (n: number): string => n + (n === 1 ? ' card' : ' cards')

/** The tick of a list: all of its cards selected = true, some = 'indeterminate', none = false. */
const listState = (list: CardList): boolean | 'indeterminate' => {
  const live = liveCards(list)
  const chosen = live.filter((id) => selectedSet.value.has(id)).length
  return chosen === 0 ? false : chosen === live.length ? true : 'indeterminate'
}
/** Ticking adds the list's cards to the selection, unticking takes them out; the rest of the selection is left alone. */
const setList = (list: CardList, on: boolean | 'indeterminate') => setCards(liveCards(list), on === true)
const listLine = (list: CardList): string => {
  const n = liveCards(list).length
  return listState(list) === 'indeterminate' ? `${cardsText(n)}, ${liveCards(list).filter((id) => selectedSet.value.has(id)).length} selected` : cardsText(n)
}

// The eye button: a window with the cards of one list, to select or deselect them one by one (the selection changes at once).
const inspected = ref<CardList | null>(null)
const inspectOpen = computed({
  get: () => inspected.value !== null,
  set: (open) => {
    if (!open) inspected.value = null
  },
})
const inspectedCards = computed<StudyCard[]>(() => (inspected.value ? liveCards(inspected.value).map(getCard) : []))
function deleteInspected() {
  const list = inspected.value
  inspected.value = null
  if (list) toDelete.value = list
}

const saveOpen = ref(false)
const listName = ref('')
const willReplace = computed(() => listName.value.trim() !== '' && listNamed(listName.value) !== null)

function openSave() {
  listName.value = ''
  saveOpen.value = true
}

const saving = ref(false)

async function confirmSave() {
  if (saving.value) return
  saving.value = true
  try {
    const result = await saveList(listName.value, settings.selected)
    if (result === 'created' || result === 'replaced') {
      saveOpen.value = false
      replaceSelection([]) // the new list would otherwise show up ticked: nothing stays selected unless the user selects it
      notifySuccess(result === 'created' ? 'List saved' : 'List replaced', `"${cleanName(listName.value)}"`)
      return
    }
    notifyError("Couldn't save the list", {
      empty_name: 'Give the list a name.',
      no_cards: 'Select at least one card first.',
      bad_cards: 'Some of the selected cards are not valid.',
      too_many: 'You have too many lists: delete one first.',
    }[result])
  } catch {
    notifyError("Couldn't save the list", 'The storage cannot be reached. Try again.')
  } finally {
    saving.value = false
  }
}

const toDelete = ref<CardList | null>(null)
const deleteOpen = computed({
  get: () => toDelete.value !== null,
  set: (open) => {
    if (!open) toDelete.value = null
  },
})
async function confirmDelete() {
  const list = toDelete.value
  toDelete.value = null
  if (!list) return
  try {
    await removeList(list.id)
    notifySuccess('List deleted', `"${list.name}"`)
  } catch {
    notifyError("Couldn't delete the list", 'The storage cannot be reached. Try again.')
  }
}

// ---- the cards I wrote -------------------------------------------------------------------------------------
// Any text on the front, any text as the answer. They are listed one per row (not as tiles) because they can be long.
const allCustom = (on: boolean) => setCards(idsOf(customCards), on)

// What is selected, by kind (only the kinds that have something, so the line stays short).
const breakdown = computed(() =>
  [
    { n: countIn('hiragana'), label: 'hiragana' },
    { n: countIn('katakana'), label: 'katakana' },
    { n: countIn('kanji'), label: 'kanji' },
    { n: countIn('custom'), label: 'custom' },
  ].filter((part) => part.n > 0),
)

const cardOpen = ref(false)
const editing = ref<CustomCard | null>(null) // null = a new card
const front = ref('')
const answer = ref('')
const savingCard = ref(false)
const tooLong = computed(() => [...front.value].length > MAX_TEXT || [...answer.value].length > MAX_TEXT)
const canSaveCard = computed(() => front.value.trim() !== '' && answer.value.trim() !== '' && !tooLong.value && !savingCard.value)

function openCard(card: CustomCard | null) {
  editing.value = card
  front.value = card?.front ?? ''
  answer.value = card?.answer ?? ''
  cardOpen.value = true
}

/** Saves the card. "Save and add another" keeps the window open with empty fields. A new card is NOT selected (the user chose that). */
async function confirmCard(another: boolean) {
  if (!canSaveCard.value) return
  savingCard.value = true
  try {
    const wasNew = editing.value === null
    const result = await saveCustomCard(editing.value?.id ?? null, front.value, answer.value)
    if (result === 'created' || result === 'updated') {
      notifySuccess(wasNew ? 'Card added' : 'Card saved')
      if (another && wasNew) {
        front.value = ''
        answer.value = ''
        nextTick(() => document.getElementById('card-front')?.focus())
      } else {
        cardOpen.value = false
      }
      return
    }
    notifyError("Couldn't save the card", {
      empty_front: 'Write something on the front.',
      empty_answer: 'Write the answer.',
      too_long: `The front and the answer can each be ${MAX_TEXT} characters at most.`,
      too_many: 'You have too many cards: delete some first.',
      not_found: 'This card no longer exists.',
    }[result])
  } catch {
    notifyError("Couldn't save the card", 'The storage cannot be reached. Try again.')
  } finally {
    savingCard.value = false
  }
}

const cardToDelete = ref<CustomCard | null>(null)
const cardDeleteOpen = computed({
  get: () => cardToDelete.value !== null,
  set: (open) => {
    if (!open) cardToDelete.value = null
  },
})
async function confirmCardDelete() {
  const card = cardToDelete.value
  cardToDelete.value = null
  if (!card) return
  try {
    await removeCustomCard(card.id)
    setCards([card.id], false) // it must not stay in the selection
    notifySuccess('Card deleted')
  } catch {
    notifyError("Couldn't delete the card", 'The storage cannot be reached. Try again.')
  }
}
</script>

<template>
  <div class="page">
    <header class="top">
      <h1>Flashcards</h1>
      <p class="muted">Choose the kana or kanji for your round: a whole row, or single cards one by one.</p>
    </header>

    <div class="layout">
      <!-- ============ what to practise ============ -->
      <section class="main" aria-label="Cards">
        <div class="fontbar">
          <div class="searchbar">
            <Icon :path="mdiMagnify" :size="18" class="lead" />
            <input
              id="card-search"
              v-model="query"
              type="text"
              role="searchbox"
              class="search"
              placeholder="Search a character, a reading or a meaning"
              aria-label="Search cards"
              autocomplete="off"
              spellcheck="false"
              @keydown.escape="query = ''"
            />
            <button v-if="query !== ''" type="button" class="clear-search" aria-label="Clear the search" @click="query = ''">
              <Icon :path="mdiClose" :size="16" />
            </button>
          </div>
          <span class="fonts">
            <span class="muted">Typefaces</span>
            <UiSegmented v-model="settings.fonts" :options="FONT_OPTIONS" aria-label="Typefaces shown" />
          </span>
        </div>

        <section v-if="searching" class="card section results" aria-label="Search results">
          <header class="section-head">
            <div>
              <h2>Search results</h2>
              <p v-if="matches.length > 0" class="muted small" aria-live="polite">
                {{ matches.length }} {{ matches.length === 1 ? 'card matches' : 'cards match' }}<template v-if="matches.length > shown.length">, the first {{ shown.length }} are shown</template>
              </p>
            </div>
            <span v-if="matches.length > 0" class="muted small actions">
              {{ chosenIn(shown) }} of {{ shown.length }}
              <span class="vbar" aria-hidden="true"></span>
              <button type="button" class="link-button" @click="setCards(idsOf(shown), true)">All</button>
              <span class="vbar" aria-hidden="true"></span>
              <button type="button" class="link-button" @click="setCards(idsOf(shown), false)">None</button>
            </span>
          </header>
          <div class="results-scroll">
            <div v-if="matches.length > 0" class="tiles result-tiles">
              <KanaTile
                v-for="card in shown"
                :key="card.id"
                :card="card"
                :selected="selectedSet.has(card.id)"
                :fonts="settings.fonts"
                :style="{ '--tile-glyph': '28px' }"
                @toggle="toggleCard(card.id)"
              />
            </div>
            <p v-else class="muted small nothing">
              Nothing matches. Try the character itself, a reading as kana or romaji (shi), or an English meaning of a kanji (water).
            </p>
          </div>
        </section>

        <TabsRoot v-else v-model="tab" class="tabs-root">
          <TabsList class="ui-tabs-list" aria-label="Script">
            <TabsTrigger v-for="script in SCRIPTS" :key="script.id" :value="script.id" class="ui-tab">
              <span class="kana tabkana" lang="ja">{{ script.sample }}</span>
              <span class="lbl" :data-text="script.label">{{ script.label }}</span>
              <span class="count">{{ countIn(script.id) }}<span class="word"> selected</span></span>
            </TabsTrigger>
            <TabsTrigger value="kanji" class="ui-tab">
              <span class="kana tabkana" lang="ja">字</span>
              <span class="lbl" data-text="Kanji">Kanji</span>
              <span class="count">{{ countIn('kanji') }}<span class="word"> selected</span></span>
            </TabsTrigger>
            <TabsTrigger value="custom" class="ui-tab">
              <Icon :path="mdiCardTextOutline" :size="18" class="tabicon" />
              <span class="lbl" data-text="My cards">My cards</span>
              <span class="count">{{ countIn('custom') }}<span class="word"> selected</span></span>
            </TabsTrigger>
          </TabsList>

          <TabsContent v-for="script in SCRIPTS" :key="script.id" :value="script.id" class="tabbody">
            <div class="script-actions muted small">
              <button type="button" class="link-button" @click="setScript(script.id, true)">Select all {{ script.label }}</button>
              <span class="vbar" aria-hidden="true"></span>
              <button type="button" class="link-button" @click="setScript(script.id, false)">Clear {{ script.label }}</button>
            </div>

            <div class="tab-scroll">
            <section v-for="section in SECTIONS" :key="section.id" class="card section" :aria-labelledby="`h-${script.id}-${section.id}`">
              <header class="section-head">
                <div>
                  <h2 :id="`h-${script.id}-${section.id}`">{{ section.label }}</h2>
                  <p class="muted small">{{ section.hint }}</p>
                </div>
                <span class="muted small actions">
                  {{ chosenIn(sectionCards(section.id, script.id)) }} of {{ sectionCards(section.id, script.id).length }}
                  <span class="vbar" aria-hidden="true"></span>
                  <button type="button" class="link-button" @click="setSection(section.id, script.id, true)">All</button>
                  <span class="vbar" aria-hidden="true"></span>
                  <button type="button" class="link-button" @click="setSection(section.id, script.id, false)">None</button>
                </span>
              </header>

              <div class="groups" :class="{ combo: section.id === 'combo' }">
                <div v-for="group in groupsOf(section.id)" :key="group.id" class="group">
                  <div class="group-head">
                    <UiCheckbox :model-value="groupState(group, script.id)" @update:model-value="setGroup(group, script.id, $event)">
                      <strong>{{ group.title }}</strong>
                    </UiCheckbox>
                    <span class="muted small" :title="'Answered correctly at least once'">{{ knownIn(cardsOfGroup(group.id, script.id)) }} known</span>
                  </div>
                  <div class="tiles" :style="{ '--cols': section.id === 'combo' ? 3 : 5 }">
                    <KanaTile
                      v-for="card in cardsOfGroup(group.id, script.id)"
                      :key="card.id"
                      :card="card"
                      :selected="selectedSet.has(card.id)"
                      :fonts="settings.fonts"
                      :style="{ '--tile-glyph': section.id === 'combo' ? '22px' : '28px' }"
                      @toggle="toggleCard(card.id)"
                    />
                  </div>
                </div>
              </div>
            </section>
            </div>
          </TabsContent>

          <TabsContent value="kanji" class="tabbody">
            <div class="script-actions muted small">
              <button type="button" class="link-button" @click="setAllKanji(true)">Select all kanji</button>
              <span class="vbar" aria-hidden="true"></span>
              <button type="button" class="link-button" @click="setAllKanji(false)">Clear kanji</button>
            </div>

            <div class="tab-scroll">
            <CollapsibleRoot
              v-for="grade in KANJI_GRADES"
              :key="grade.grade"
              as="section"
              class="card section"
              :open="openGrades.includes(grade.grade)"
              @update:open="setGradeOpen(grade.grade, $event)"
            >
              <header class="section-head fold-head">
                <div>
                  <h2>
                    <CollapsibleTrigger class="fold">
                      <Icon :path="mdiChevronRight" :size="20" class="chev" />{{ grade.label }}
                    </CollapsibleTrigger>
                  </h2>
                  <p class="muted small">{{ grade.hint }} <span :title="'Answered correctly at least once'">{{ knownIn(kanjiOfGrade(grade.grade)) }} known</span></p>
                </div>
                <span class="muted small actions">
                  {{ chosenIn(kanjiOfGrade(grade.grade)) }} of {{ kanjiOfGrade(grade.grade).length }}
                  <span class="vbar" aria-hidden="true"></span>
                  <button type="button" class="link-button" @click="setGrade(grade, true)">All</button>
                  <span class="vbar" aria-hidden="true"></span>
                  <button type="button" class="link-button" @click="setGrade(grade, false)">None</button>
                </span>
              </header>
              <CollapsibleContent>
                <div class="tiles kanji-tiles">
                  <KanaTile
                    v-for="card in kanjiOfGrade(grade.grade)"
                    :key="card.id"
                    :card="card"
                    :selected="selectedSet.has(card.id)"
                    :fonts="settings.fonts"
                    :style="{ '--tile-glyph': '28px' }"
                    @toggle="toggleCard(card.id)"
                  />
                </div>
              </CollapsibleContent>
            </CollapsibleRoot>
            </div>
          </TabsContent>

          <TabsContent value="custom" class="tabbody">
            <!-- One Add button, never two: under the message while there are no cards, above the list once there is one. Quiet, not the primary button. -->
            <div v-if="customCards.length" class="script-actions custom-actions">
              <button type="button" class="btn btn-quiet small-btn" @click="openCard(null)"><Icon :path="mdiPlus" :size="16" />Add a card</button>
              <span class="muted small actions">
                {{ chosenIn(customCards) }} of {{ customCards.length }}
                <span class="vbar" aria-hidden="true"></span>
                <button type="button" class="link-button" @click="allCustom(true)">All</button>
                <span class="vbar" aria-hidden="true"></span>
                <button type="button" class="link-button" @click="allCustom(false)">None</button>
              </span>
            </div>

            <div class="tab-scroll">
            <div v-if="customCards.length === 0" class="custom-empty">
              <h2>Write your own cards</h2>
              <p>Put a word, a phrase or a sentence on the front and the answer you want to give. For long answers choose “Show the card, I check myself”.</p>
              <button type="button" class="btn btn-quiet small-btn" @click="openCard(null)"><Icon :path="mdiPlus" :size="16" />Add a card</button>
            </div>

            <!-- Tiles like the kana and kanji ones, twice as wide: the front big, the answer as the small line under it. Edit and delete appear
                 on hover and on keyboard focus (always, on a touch screen). -->
            <ul v-else class="custom-tiles">
              <li v-for="card in customCards" :key="card.id" class="custom-tile">
                <button type="button" class="custom-use" :aria-pressed="selectedSet.has(card.id)" :title="card.front + ' → ' + card.answer" @click="toggleCard(card.id)">
                  <span class="custom-front">{{ card.front }}</span>
                  <span class="custom-answer">{{ card.answer }}</span>
                </button>
                <span class="custom-acts">
                  <button type="button" class="custom-act" :aria-label="`Edit the card ${faceOf(card)}`" @click="openCard(card)"><Icon :path="mdiPencilOutline" :size="16" /></button>
                  <button type="button" class="custom-act" :aria-label="`Delete the card ${faceOf(card)}`" @click="cardToDelete = card"><Icon :path="mdiTrashCanOutline" :size="16" /></button>
                </span>
              </li>
            </ul>
            </div>
          </TabsContent>
        </TabsRoot>
      </section>

      <!-- ============ the round and my lists ============ -->
      <aside class="side hover-scroll" aria-label="Round and lists">
        <!-- Every line has its own slot that is always there (hidden when it has nothing to say), so the panel is the same height
             whatever is selected and the panels under it never move. -->
        <section class="card panel start">
          <p class="selected-line"><strong>{{ total }}</strong> {{ total === 1 ? 'card' : 'cards' }} selected</p>
          <p class="slot muted small">
            <template v-if="breakdown.length">
              <template v-for="(part, i) in breakdown" :key="part.label"><span v-if="i > 0" class="vbar" aria-hidden="true"></span>{{ part.n }} {{ part.label }}</template>
            </template>
            <template v-else>Select cards on the left to begin.</template>
          </p>
          <p class="slot muted small" :class="{ off: !(total > 0 && playable < total) }">{{ playable }} of them fit the question.</p>
          <button type="button" class="btn btn-primary" :disabled="playable === 0" @click="start">
            <Icon :path="mdiPlay" :size="18" />Start round<span class="vbar" aria-hidden="true"></span>{{ roundSize }} {{ roundSize === 1 ? 'card' : 'cards' }}
          </button>
          <button type="button" class="link-button clear" :disabled="total === 0" @click="replaceSelection([])">Clear the whole selection</button>
        </section>

        <section class="card panel" aria-labelledby="h-round">
          <h2 id="h-round">Round</h2>
          <div class="fields">
            <div class="field">
              <label for="count">Cards in a round</label>
              <UiSelect id="count" v-model="settings.count" :options="COUNT_OPTIONS" />
            </div>
            <div class="field">
              <label for="seconds">Time per card</label>
              <UiSelect id="seconds" v-model="settings.seconds" :options="SECONDS_OPTIONS" />
            </div>
            <div class="field">
              <label for="mode">Question</label>
              <UiSelect id="mode" v-model="settings.mode" :options="modeOptions" />
            </div>
          </div>
        </section>

        <section class="card panel" aria-labelledby="h-missed">
          <div class="panel-head">
            <h2 id="h-missed">Most missed</h2>
            <button type="button" class="list-act" aria-label="See the cards you missed" title="See the cards you missed" :disabled="missedAll.length === 0" @click="missedOpen = true">
              <Icon :path="mdiEyeOutline" :size="18" />
            </button>
          </div>
          <!-- The text keeps room for two lines whatever it says, so the panels under it never move. -->
          <p class="missed-line muted small">{{ missedLine }}</p>
          <div class="field">
            <label for="missed-count">Take</label>
            <UiSelect id="missed-count" v-model="missedCount" :options="MISSED_OPTIONS" />
          </div>
          <div class="missed-actions">
            <button type="button" class="btn btn-quiet" :disabled="missedPick.length === 0" title="Add them to the selection" @click="selectMissed"><Icon :path="mdiCheckAll" :size="18" />Select</button>
            <button type="button" class="btn btn-primary" :disabled="missedPick.length === 0" title="Practise only these, starting now" @click="practiseMissed"><Icon :path="mdiPlay" :size="18" />Practise</button>
          </div>
        </section>

        <section class="card panel lists-panel" aria-labelledby="h-lists">
          <div class="panel-head">
            <h2 id="h-lists">My lists</h2>
            <button type="button" class="btn btn-quiet small-btn" :disabled="total === 0" @click="openSave"><Icon :path="mdiContentSaveOutline" :size="16" />Save selection…</button>
          </div>
          <p v-if="lists.length === 0" class="muted small">
            Select the cards you want, then save them here to practise exactly those again.
          </p>
          <ul v-else class="lists">
            <li v-for="list in lists" :key="list.id" class="list" :class="{ on: listState(list) === true }">
              <!-- The tick adds or removes the whole list; only the name and the number of cards are shown, never the cards. -->
              <UiCheckbox class="list-check" :model-value="listState(list)" @update:model-value="setList(list, $event)">
                <span class="list-text">
                  <span class="list-name">{{ list.name }}</span>
                  <span class="muted small">{{ listLine(list) }}</span>
                </span>
              </UiCheckbox>
              <button type="button" class="list-act" :aria-label="`See the cards in the list ${list.name}`" title="See the cards" @click="inspected = list">
                <Icon :path="mdiEyeOutline" :size="18" />
              </button>
              <button type="button" class="list-act" :aria-label="`Delete the list ${list.name}`" title="Delete the list" @click="toDelete = list">
                <Icon :path="mdiTrashCanOutline" :size="18" />
              </button>
            </li>
          </ul>
        </section>
      </aside>
    </div>

    <UiDialog v-model:open="saveOpen" title="Save as a list" :description="`The ${total} selected cards are saved under this name.`">
      <form id="save-form" class="stack" @submit.prevent="confirmSave">
        <div class="field">
          <label for="list-name">Name</label>
          <input id="list-name" v-model="listName" type="text" :maxlength="MAX_NAME" autocomplete="off" />
        </div>
        <p v-if="willReplace" class="muted small">A list with this name exists. Saving replaces it.</p>
      </form>
      <template #footer>
        <button type="button" class="btn btn-quiet" @click="saveOpen = false">Cancel</button>
        <button type="submit" form="save-form" class="btn btn-primary" :disabled="saving"><Icon :path="mdiContentSaveOutline" :size="18" />{{ willReplace ? 'Replace list' : 'Save list' }}</button>
      </template>
    </UiDialog>

    <UiDialog
      v-model:open="cardOpen"
      wide
      :title="editing ? 'Edit the card' : 'Add a card'"
      description="The front is what you are shown. The answer is what you give for it. Both can be a word, a phrase or a few lines."
    >
      <form id="card-form" class="stack" @submit.prevent="confirmCard(false)">
        <div class="field">
          <label for="card-front">Front</label>
          <textarea id="card-front" v-model="front" rows="3" autocomplete="off" spellcheck="false" @keydown.ctrl.enter.prevent="confirmCard(false)"></textarea>
          <span class="hint" :class="{ over: [...front].length > MAX_TEXT }">{{ [...front].length }} / {{ MAX_TEXT }}</span>
        </div>
        <div class="field">
          <label for="card-answer">Answer</label>
          <textarea id="card-answer" v-model="answer" rows="3" autocomplete="off" spellcheck="false" @keydown.ctrl.enter.prevent="confirmCard(false)"></textarea>
          <span class="hint" :class="{ over: [...answer].length > MAX_TEXT }">{{ [...answer].length }} / {{ MAX_TEXT }}</span>
        </div>
        <p class="muted small">
          When you type the answer in a round, it has to match this text: capital letters, spaces and punctuation at the ends do not matter,
          a different word does. For long answers use “Show the card, I check myself”, where you judge your own answer.
        </p>
      </form>
      <template #footer>
        <button type="button" class="btn btn-quiet" @click="cardOpen = false">Cancel</button>
        <button v-if="!editing" type="button" class="btn btn-quiet" :disabled="!canSaveCard" @click="confirmCard(true)"><Icon :path="mdiPlus" :size="18" />Save and add another</button>
        <button type="submit" form="card-form" class="btn btn-primary" :disabled="!canSaveCard"><Icon :path="mdiContentSaveOutline" :size="18" />Save card</button>
      </template>
    </UiDialog>

    <UiDialog
      v-model:open="cardDeleteOpen"
      title="Delete this card?"
      :description="`“${cardToDelete ? faceOf(cardToDelete) : ''}” will be removed for good. Lists that contain it simply skip it.`"
    >
      <template #footer>
        <button type="button" class="btn btn-quiet" @click="cardToDelete = null">Cancel</button>
        <button type="button" class="btn btn-primary" @click="confirmCardDelete"><Icon :path="mdiTrashCanOutline" :size="18" />Delete card</button>
      </template>
    </UiDialog>

    <UiDialog
      v-model:open="inspectOpen"
      wide
      :title="inspected?.name ?? ''"
      :description="inspectedCards.length ? `${cardsText(inspectedCards.length)} in this list. Choose which ones to practise.` : 'This list has no cards left.'"
    >
      <template v-if="inspectedCards.length">
        <div class="inspect-bar">
          <span class="muted small">{{ chosenIn(inspectedCards) }} of {{ inspectedCards.length }} selected</span>
          <span class="muted small actions">
            <button type="button" class="link-button" @click="setCards(idsOf(inspectedCards), true)">All</button>
            <span class="vbar" aria-hidden="true"></span>
            <button type="button" class="link-button" @click="setCards(idsOf(inspectedCards), false)">None</button>
          </span>
        </div>
        <div class="tiles inspect-tiles">
          <KanaTile
            v-for="card in inspectedCards"
            :key="card.id"
            :card="card"
            :selected="selectedSet.has(card.id)"
            :fonts="settings.fonts"
            :style="{ '--tile-glyph': '26px' }"
            @toggle="toggleCard(card.id)"
          />
        </div>
      </template>
      <p v-else class="muted small">The cards it held were deleted. You can delete the list too.</p>
      <template #footer>
        <button type="button" class="btn btn-quiet danger-btn" @click="deleteInspected"><Icon :path="mdiTrashCanOutline" :size="16" />Delete list</button>
        <button type="button" class="btn btn-primary" @click="inspected = null">Done</button>
      </template>
    </UiDialog>

    <UiDialog
      v-model:open="missedOpen"
      wide
      title="Most missed"
      description="The cards you answered wrongly, the most missed first. The number is how often. Choose which ones to practise."
    >
      <div class="inspect-bar">
        <span class="muted small">{{ chosenIn(missedAll) }} of {{ missedAll.length }} selected</span>
        <span class="muted small actions">
          <button type="button" class="link-button" @click="setCards(idsOf(missedAll), true)">All</button>
          <span class="vbar" aria-hidden="true"></span>
          <button type="button" class="link-button" @click="setCards(idsOf(missedAll), false)">None</button>
        </span>
      </div>
      <div class="tiles inspect-tiles">
        <KanaTile
          v-for="card in missedAll"
          :key="card.id"
          :card="card"
          :selected="selectedSet.has(card.id)"
          :fonts="settings.fonts"
          show-misses
          :style="{ '--tile-glyph': '26px' }"
          @toggle="toggleCard(card.id)"
        />
      </div>
      <template #footer>
        <button type="button" class="btn btn-primary" @click="missedOpen = false">Done</button>
      </template>
    </UiDialog>

    <UiDialog v-model:open="deleteOpen" title="Delete this list?" :description="`“${toDelete?.name ?? ''}” will be removed. The cards themselves are not affected.`">
      <template #footer>
        <button type="button" class="btn btn-quiet" @click="toDelete = null">Cancel</button>
        <button type="button" class="btn btn-primary" @click="confirmDelete"><Icon :path="mdiTrashCanOutline" :size="18" />Delete list</button>
      </template>
    </UiDialog>
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
/* Wide: kana on the left, round and lists on the right. */
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 310px;
  gap: 24px;
  align-items: stretch;
}
/* Wide and tall enough: the page itself does not scroll. The title, the search bar, the tabs and the right column stay
   where they are, and only the list of characters scrolls (the .tabbody of the open tab, or the search results).
   --page-height is what AppLayout leaves for the page. On a narrow or short window everything flows and the page scrolls. */
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
  }
  .main {
    display: flex;
    flex-direction: column;
    min-height: 0;
  }
  .tabs-root {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
  }
  /* The bar with Select all / Clear (.script-actions) belongs to the tab and stays put; only the list under it (.tab-scroll) scrolls. */
  .tabbody {
    flex: 1;
    min-height: 0;
  }
  .tab-scroll {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding-right: 4px; /* a little air between the tiles and the scrollbar */
  }
  /* The same for the search results: the title and All / None stay, the tiles scroll, and the bar sits at the card's edge. */
  .results {
    flex: 0 1 auto;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
  .results-scroll {
    min-height: 0;
    overflow-y: auto;
    margin: 0 -18px -14px;
    padding: 0 18px 14px;
  }
  /* The right column is as tall as the page leaves room for. When its panels are taller (a short window), it scrolls by itself
     (.hover-scroll shows the bar only on hover). The scrollbar keeps 12px that hang out into the space on the right, so the panels
     keep their width. */
  .layout > .side {
    align-self: stretch;
    min-height: 0;
    scrollbar-gutter: stable;
    margin-right: -12px;
  }
  .layout > .side > * {
    flex: none;
  }
  /* A long list of saved lists scrolls inside its own panel, so Start round and the settings above it never scroll away. The panel
     only shrinks (it does not grow to fill the column), down to a few rows; on a window too short even for that the column scrolls. */
  .layout > .side > .lists-panel {
    flex: 0 1 auto;
    display: flex;
    flex-direction: column;
  }
  .layout > .side > .lists-panel:has(.lists) {
    min-height: 150px;
  }
  .lists-panel .lists {
    min-height: 0;
    overflow-y: auto;
  }
}
.small {
  font-size: 13px;
}
.fontbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 16px;
  margin-bottom: 12px;
}
.fonts {
  display: flex;
  align-items: center;
  gap: 8px 12px;
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
.results {
  padding-bottom: 14px;
}
.tiles.result-tiles {
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
}
.nothing {
  margin: 0;
}
.tabkana {
  font-size: 18px;
  line-height: 1;
  color: var(--ink);
}
.tabbody {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 10px;
}
/* Display:flex above beats the browser's own rule for the hidden attribute, so the panels of the tabs that are not
   chosen stayed in the layout (10px of padding each) and pushed the open one down by 10px per tab before it. */
.tabbody[hidden] {
  display: none;
}
.tab-scroll {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.script-actions {
  display: flex;
  gap: 8px;
}
.section {
  padding: 14px 18px 8px;
}
.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}
.section-head h2 {
  font-size: 18px;
}
.section-head p {
  margin: 2px 0 0;
}
.actions {
  white-space: nowrap;
  padding-top: 4px;
}
/* A row is a titled block of its own. Wide screens put two combination rows side by side. */
.groups {
  display: grid;
  /* 440 px is what five two-typeface tiles need; on a wide window two rows sit side by side. */
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.tiles.kanji-tiles {
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
}
/* A grade folds up: the title button (chevron + name) is stretched over the whole header, so the header
   itself is the click target (no hover background); only the All / None links stay clickable on their own. */
.fold-head {
  position: relative;
  margin-bottom: 0;
}
.fold {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  font: inherit;
  /* A tight line box, so the icon is centred on the letters and not on the font's tall ascender space. */
  line-height: 1;
  color: inherit;
  background: none;
  border: 0;
  cursor: pointer;
}
.fold::after {
  content: '';
  position: absolute;
  inset: 0;
}
.fold-head .actions {
  pointer-events: none;
}
.fold-head .actions .link-button {
  position: relative;
  pointer-events: auto;
}
.fold .chev {
  color: var(--muted);
  transition: transform 0.12s;
}
.fold[data-state='open'] .chev {
  transform: rotate(90deg);
}
[data-state='open'] > .fold-head {
  margin-bottom: 8px;
}
@media (prefers-reduced-motion: reduce) {
  .fold .chev {
    transition: none;
  }
}
.tiles {
  display: grid;
  grid-template-columns: repeat(var(--cols, 5), minmax(0, 1fr));
  gap: 6px;
}

/* ---- the cards I wrote: one per row, because the text can be long ---- */
.custom-actions {
  align-items: center;
  justify-content: space-between;
}
/* No box: the message is part of the page background, centred, so an empty tab does not look like a card with nothing in it. */
.custom-empty {
  flex: 1; /* the tab body is a column that fills the page: the message sits in the middle of what is left under the tabs */
  min-height: 300px; /* ... and on a short or narrow window, where the page scrolls, it still gets some air */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 24px 16px 56px; /* a little more below than above, so it looks centred to the eye */
  text-align: center;
  color: var(--muted);
}
.custom-empty h2 {
  margin-top: 4px;
  font-size: 18px;
  color: var(--ink);
}
.custom-empty p {
  margin: 0;
  max-width: 46ch;
  font-size: 13px;
}
.custom-empty .small-btn {
  margin-top: 10px;
}
/* The tiles: twice as wide as a kanji tile (96 px), taller to fit a short sentence. Long text is cut after three lines (the front) and
   two lines (the answer); it is whole in the edit window and in the round. */
.custom-tiles {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(192px, 1fr));
  gap: 6px;
}
.custom-tile {
  position: relative;
  display: flex;
}
.custom-use {
  flex: 1;
  min-width: 0;
  min-height: 96px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px 14px;
  font: inherit;
  color: var(--ink);
  text-align: center;
  background: #fff;
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  cursor: pointer;
}
.custom-use:hover {
  background: var(--tint);
}
.custom-use[aria-pressed='true'] {
  background: var(--select-tint);
  border-color: var(--accent);
  box-shadow: inset 0 0 0 1px var(--accent);
}
.custom-front,
.custom-answer {
  max-width: 100%;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-line;
  overflow-wrap: anywhere;
}
.custom-front {
  -webkit-line-clamp: 3;
  line-clamp: 3;
  font-size: 16px;
  line-height: 1.35;
}
.custom-answer {
  -webkit-line-clamp: 2;
  line-clamp: 2;
  font-size: 12px;
  line-height: 1.35;
  color: var(--muted);
}
.custom-acts {
  position: absolute;
  top: 3px;
  right: 3px;
  display: flex;
  gap: 1px;
  padding: 1px;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 4px;
  opacity: 0;
}
.custom-tile:hover .custom-acts,
.custom-tile:focus-within .custom-acts {
  opacity: 1;
}
@media (hover: none) {
  .custom-acts {
    opacity: 1;
  }
}
.custom-act {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  color: var(--muted);
  background: none;
  border: 0;
  border-radius: 3px;
  cursor: pointer;
}
.custom-act:hover {
  color: var(--accent);
  background: var(--tint);
}
.field .hint.over {
  color: var(--wrong);
}

/* ---- right column ---- */
.side {
  align-self: start;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0 2px 2px 0;
}
.panel {
  padding: 14px 16px 16px;
}
.panel h2 {
  font-size: 17px;
  margin-bottom: 10px;
}
.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}
.panel-head h2 {
  margin-bottom: 0;
}
.small-btn {
  padding: 4px 10px;
  font-size: 12.5px;
}
.start {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.slot {
  min-height: 20px; /* one line of small text, taken whether or not there is something to say */
}
.off {
  visibility: hidden; /* keeps its place and cannot be clicked or tabbed to */
}
.start p {
  margin: 0;
}
.selected-line {
  font-size: 15px;
}
.clear {
  align-self: flex-start;
  font-size: 13px;
}
.clear:disabled {
  color: var(--muted);
  text-decoration: none;
  opacity: 0.55;
  cursor: default;
}
.fields {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.lists {
  list-style: none;
  margin: 0 -16px -6px;
  padding: 0;
  display: flex;
  flex-direction: column;
}
.list {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 7px 8px 7px 16px;
  border-top: 1px solid var(--line);
}
.list.on {
  background: var(--select-tint);
}
.list-check {
  flex: 1;
  min-width: 0;
}
.list-check :deep(.ui-check-label) {
  flex: 1;
  min-width: 0;
}
.list-text {
  display: flex;
  flex-direction: column;
}
.list-name {
  font-weight: 600;
  overflow-wrap: anywhere;
}
.list-act {
  flex: none;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  color: var(--muted);
  background: none;
  border: 0;
  border-radius: 4px;
  cursor: pointer;
}
.list-act:disabled {
  opacity: 0.4;
  cursor: default;
  pointer-events: none;
}
.missed-line {
  min-height: 40px; /* two lines of small text, taken whether or not there is that much to say */
  margin: 0 0 10px;
}
.missed-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.missed-actions .btn {
  flex: 1;
  justify-content: center;
}
.list-act:hover {
  color: var(--accent);
  background: var(--tint);
}
/* The window with the cards of a list. A long list scrolls inside it. */
.inspect-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.tiles.inspect-tiles {
  grid-template-columns: repeat(auto-fill, minmax(118px, 1fr));
  max-height: min(46vh, 420px);
  overflow-y: auto;
  padding-right: 4px;
}
.danger-btn {
  margin-right: auto;
  color: var(--wrong);
  border-color: color-mix(in srgb, var(--wrong) 40%, var(--line-strong));
}

@media (max-width: 1100px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .side {
    order: -1; /* the start button first on a narrow window */
  }
}
</style>
