<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { mdiArrowLeft, mdiArrowRight, mdiCheck, mdiClockOutline, mdiClose, mdiEyeOutline, mdiFlagCheckered, mdiHome, mdiRestart, mdiSkipNext } from '@mdi/js'
import Icon from '../components/Icon.vue'
import KanjiReading from '../components/KanjiReading.vue'
import { toKana, toKatakana } from 'wanakana'
import KanaGlyphs from '../components/KanaGlyphs.vue'
import { sameReadingKana } from '../kana/kana'
import { faceOf, getCard, isCorrect, isCustomCard, isKanaCard, isKanjiCard, type StudyCard } from '../flashcards/cards'
import { clip } from '../flashcards/custom'
import { recordAnswer } from '../progress/log'
import { buildDeck, missedCards, session, settings, shuffled, startSession } from '../flashcards/store'

const router = useRouter()

type AnswerKind = 'submit' | 'skip' | 'timeout'

// asking -> feedback -> (next card) ... -> done. In the check-myself question ("flip") there is a step between: asking -> revealed (the
// answer is shown, the user says whether they knew it) -> next card, with no feedback step; a skip or a timeout still goes through feedback.
const phase = ref<'asking' | 'revealed' | 'feedback' | 'done'>('asking')
const typed = ref('')
const last = ref<{ correct: boolean; kind: AnswerKind; typed: string } | null>(null) // how the card just answered went
const remainingMs = ref(0)
const input = ref<HTMLInputElement | null>(null)
const nextButton = ref<HTMLButtonElement | null>(null)
const revealButton = ref<HTMLButtonElement | null>(null)
const knewButton = ref<HTMLButtonElement | null>(null)
let timer: ReturnType<typeof setInterval> | null = null
let shownAt = 0
let thinkMs = 0 // how long the card was on screen before the answer was shown (flip)

const total = computed(() => session.deck.length)
const card = computed(() => session.deck[session.index])
const kanaMode = computed(() => session.config.mode === 'kana')
const meaningMode = computed(() => session.config.mode === 'meaning')
const flipMode = computed(() => session.config.mode === 'flip')
/** The kanji on screen, or null when the card is something else. */
const kanji = computed(() => (card.value && isKanjiCard(card.value) ? card.value : null))
/** The kana on screen, or null when the card is something else. */
const kana = computed(() => (card.value && isKanaCard(card.value) ? card.value : null))
/** The card the user wrote that is on screen, or null. */
const custom = computed(() => (card.value && isCustomCard(card.value) ? card.value : null))
const limitMs = computed(() => session.config.seconds * 1000)
const isLast = computed(() => session.index + 1 >= total.value)
const barWidth = computed(() => (limitMs.value ? Math.max(0, (remainingMs.value / limitMs.value) * 100) : 100))
const secondsLeft = computed(() => Math.ceil(remainingMs.value / 1000))

const inputLabel = computed(() => {
  if (custom.value) return 'Type the answer'
  if (meaningMode.value) return 'Type the meaning in English'
  if (!kanaMode.value) return 'Type the reading in romaji'
  return card.value.script === 'katakana' ? 'Type it in katakana' : 'Type it in hiragana'
})

function stopTimer(): void {
  if (timer !== null) clearInterval(timer)
  timer = null
}

function begin(): void {
  phase.value = 'asking'
  typed.value = ''
  last.value = null
  shownAt = performance.now()
  stopTimer()
  if (limitMs.value > 0) {
    remainingMs.value = limitMs.value
    timer = setInterval(() => {
      remainingMs.value = Math.max(0, limitMs.value - (performance.now() - shownAt))
      if (remainingMs.value === 0) answer('timeout')
    }, 100)
  }
  nextTick(() => (flipMode.value ? revealButton.value : input.value)?.focus())
}

// While typing kana, romaji turns into kana as it is typed (a lone "n" waits for the next letter).
function onInput(event: Event): void {
  const target = event.target as HTMLInputElement
  const value = target.value
  if (!kanaMode.value) {
    typed.value = value
    return
  }
  const converted = toKana(value, { IMEMode: card.value.script === 'katakana' ? 'toKatakana' : true })
  typed.value = converted
  target.value = converted
}

// Finishes what is typed: a trailing "n" becomes ん / ン.
function finalKana(value: string): string {
  const kana = toKana(value)
  return card.value.script === 'katakana' ? toKatakana(kana) : kana
}

function answer(kind: AnswerKind): void {
  if (phase.value !== 'asking') return
  stopTimer()
  const ms = Math.round(performance.now() - shownAt)
  const text = kanaMode.value ? finalKana(typed.value.trim()) : typed.value.trim()
  // In flip there is nothing to check: the answer is only given by grade(), so a skip or a timeout is a miss.
  const correct = kind === 'submit' && !flipMode.value && isCorrect(card.value, session.config.mode, text)
  finish(kind, correct, text, ms)
}

function finish(kind: AnswerKind, correct: boolean, text: string, ms: number): void {
  last.value = { correct, kind, typed: text }
  session.results.push({ cardId: card.value.id, correct, ms, typed: text, timedOut: kind === 'timeout' })
  recordAnswer({ cardId: card.value.id, correct, ms, mode: session.config.mode })
  phase.value = 'feedback'
  nextTick(() => nextButton.value?.focus())
}

// Check myself: show the answer (the timer stops, the user is now judging themselves) ...
function reveal(): void {
  if (phase.value !== 'asking') return
  stopTimer()
  thinkMs = Math.round(performance.now() - shownAt)
  last.value = { correct: true, kind: 'submit', typed: '' } // only so the answer panel has something to show; the card is not graded yet
  phase.value = 'revealed'
  nextTick(() => knewButton.value?.focus())
}

// ... then the user says whether they knew it, and the next card comes straight away.
function grade(correct: boolean): void {
  if (phase.value !== 'revealed') return
  finish('submit', correct, '', thinkMs)
  next()
}

function onSubmit(): void {
  if (phase.value === 'feedback') return next()
  if (!typed.value.trim()) {
    input.value?.focus() // nothing typed: not counted, use Skip to give up on a card
    return
  }
  answer('submit')
}

function next(): void {
  if (isLast.value) {
    phase.value = 'done'
    return
  }
  session.index++
  begin()
}

// ---- how the round is going (the line above the card) -----------------------------------------------------
const correctCount = computed(() => session.results.filter((r) => r.correct).length)
const wrongCount = computed(() => session.results.length - correctCount.value)

// One cell per card of the round: green or red once answered, outlined for the card on screen.
function cellClass(i: number): string {
  const result = session.results[i]
  if (result) return result.correct ? 'ok' : 'bad'
  return i === session.index ? 'now' : ''
}
const cellGap = computed(() => (total.value > 60 ? '1px' : total.value > 30 ? '2px' : '4px')) // 208 cards still fit

// ---- end of the round ------------------------------------------------------------------------------------
const missed = computed(() => (phase.value === 'done' ? missedCards() : []))

/** The right answer of a card as one or two short lines (a kanji in a reading question lists its first readings with their romaji). */
function answerLines(card: StudyCard): string[] {
  if (isCustomCard(card)) return [clip(card.answer, 120)]
  if (isKanjiCard(card)) {
    const readings = [...card.onView, ...card.kunView]
      .slice(0, 3)
      .map((v) => `${v.stem}${v.okurigana} (${v.romaji})`)
      .join(', ')
    const meanings = card.meanings.slice(0, 3).join(', ')
    if (meaningMode.value) return [meanings]
    return flipMode.value ? [readings, meanings] : [readings]
  }
  return [kanaMode.value ? card.kana : card.romaji]
}

/** What went wrong with a missed card: what was typed, or why nothing was. */
function mistakeOf(r: { typed: string; timedOut: boolean }): { text: string; typed: boolean } {
  if (r.typed) return { text: r.typed, typed: true }
  if (r.timedOut) return { text: 'Time is up', typed: false }
  return { text: flipMode.value ? 'Missed it' : 'Skipped', typed: false }
}

// One row per missed answer, in the order they happened.
const review = computed(() =>
  phase.value !== 'done'
    ? []
    : session.results
        .filter((r) => !r.correct)
        .map((r) => {
          const card = getCard(r.cardId)
          return { id: r.cardId, card, face: faceOf(card), plain: isCustomCard(card), answer: answerLines(card), mistake: mistakeOf(r) }
        }),
)

function practiseMissed(): void {
  startSession(shuffled(missedCards()), session.config)
  begin()
}

function another(): void {
  startSession(buildDeck(), settings)
  begin()
}

const verdict = computed(() => {
  if (phase.value === 'revealed') return 'Answer'
  if (!last.value || last.value.correct) return 'Correct'
  const words: Record<AnswerKind, string> = { timeout: 'Time is up', skip: 'Skipped', submit: 'Not quite' }
  return words[last.value.kind]
})
const verdictIcon = computed(() => {
  if (phase.value === 'revealed') return mdiEyeOutline
  if (!last.value || last.value.correct) return mdiCheck
  return last.value.kind === 'timeout' ? mdiClockOutline : last.value.kind === 'skip' ? mdiSkipNext : mdiClose
})
/** A card's text is as big as it can be while it still fits: a word is large, a sentence smaller, a paragraph small (and scrolls). */
function customSize(text: string): string {
  const n = [...text].length
  return (n <= 6 ? 64 : n <= 14 ? 44 : n <= 40 ? 30 : n <= 120 ? 22 : 18) + 'px'
}
const otherKana = computed(() => (kanaMode.value && kana.value ? sameReadingKana(kana.value) : []))
// For a kanji the answer shows all three parts (On, Kun, Meaning); the ones the question asked for stay in full ink, the others are dimmed.
const meaningsText = computed(() => kanji.value?.meanings.join(', ') ?? '')

onMounted(() => {
  // A page reload forgets the round: go back to the settings.
  if (!session.deck.length) router.replace({ name: 'home' })
  else begin()
})
onBeforeUnmount(stopTimer)
</script>

<template>
  <div v-if="session.deck.length" class="play">
    <div v-if="phase !== 'done'" class="stage">
      <!-- Above the card: the way out, the count, and one cell per card (green right, red wrong). -->
      <div class="rail">
        <div class="rail-top">
          <RouterLink :to="{ name: 'home' }" class="leave"><Icon :path="mdiArrowLeft" :size="16" />Stop and change settings</RouterLink>
          <span class="muted counts">
            <b>{{ correctCount }}</b> right<span class="vbar" aria-hidden="true"></span><b>{{ wrongCount }}</b> wrong<span class="vbar" aria-hidden="true"></span>{{ session.results.length }} of {{ total }} answered
          </span>
        </div>
        <div class="seg" :style="{ '--n': total, '--gap': cellGap }" aria-hidden="true">
          <i v-for="n in total" :key="n" :class="cellClass(n - 1)"></i>
        </div>
      </div>

      <section class="card flash" aria-label="Flashcard">
        <div class="meta muted">
          <span>Card {{ session.index + 1 }} of {{ total }}</span>
          <span v-if="limitMs">{{ secondsLeft }} s</span>
        </div>
        <div v-if="limitMs" class="bar" aria-hidden="true"><div class="fill" :style="{ width: barWidth + '%' }"></div></div>
        <div v-else class="bar plain" aria-hidden="true"></div>

        <div v-if="custom" class="glyph custom-face" aria-label="Card">
          <span class="custom-text" :style="{ fontSize: customSize(custom.front) }">{{ custom.front }}</span>
        </div>
        <div v-else-if="!kanaMode" class="glyph" :class="session.config.fonts">
          <KanaGlyphs :kana="faceOf(card)" :fonts="session.config.fonts" labels />
        </div>
        <div v-else class="glyph reading" aria-label="Reading to write as kana">{{ kana?.romaji }}</div>

        <!-- Check myself: no typing. Show the answer, then say whether you knew it (a skip or a timeout ends in Next, like any card). -->
        <div v-if="flipMode" class="answer">
          <span class="muted flip-label">{{ phase === 'asking' ? 'Think of the answer, then show it.' : phase === 'revealed' ? 'Did you know it?' : 'Next card.' }}</span>
          <div class="row">
            <button v-if="phase === 'asking'" ref="revealButton" type="button" class="btn btn-primary" @click="reveal"><Icon :path="mdiEyeOutline" :size="18" />Show the answer</button>
            <template v-else-if="phase === 'revealed'">
              <button type="button" class="btn btn-quiet" @click="grade(false)"><Icon :path="mdiClose" :size="18" />Missed it</button>
              <button ref="knewButton" type="button" class="btn btn-primary" @click="grade(true)"><Icon :path="mdiCheck" :size="18" />I knew it</button>
            </template>
            <button v-else ref="nextButton" type="button" class="btn btn-primary" @click="next">{{ isLast ? 'Finish' : 'Next' }}<Icon :path="isLast ? mdiFlagCheckered : mdiArrowRight" :size="18" /></button>
          </div>
        </div>
        <form v-else class="answer" @submit.prevent="onSubmit">
          <label for="answer" class="muted">{{ inputLabel }}</label>
          <div class="row">
            <input
              id="answer"
              ref="input"
              :value="typed"
              :class="{ kana: kanaMode }"
              :disabled="phase === 'feedback'"
              :lang="kanaMode ? 'ja' : 'en'"
              autocomplete="off"
              autocapitalize="off"
              autocorrect="off"
              spellcheck="false"
              @input="onInput"
            />
            <button v-if="phase === 'asking'" type="submit" class="btn btn-primary"><Icon :path="mdiCheck" :size="18" />Check</button>
            <button v-else ref="nextButton" type="submit" class="btn btn-primary">{{ isLast ? 'Finish' : 'Next' }}<Icon :path="isLast ? mdiFlagCheckered : mdiArrowRight" :size="18" /></button>
          </div>
        </form>

        <!-- The row keeps its height once the card is answered (the button is only hidden), so the card never changes size. -->
        <div class="foot">
          <button v-show="phase === 'asking'" type="button" class="link-button" @click="answer('skip')"><Icon :path="mdiSkipNext" :size="16" />Skip this card</button>
        </div>
      </section>

      <!-- Under the card, in its own place: the card stays exactly where it is whatever the answer was. -->
      <div class="under" aria-live="polite">
        <div v-if="(phase === 'feedback' || phase === 'revealed') && last" class="verdict" :class="phase === 'revealed' ? 'show' : last.correct ? 'ok' : 'bad'">
          <div class="tag"><Icon :path="verdictIcon" :size="16" />{{ verdict }}</div>
          <div class="facts">
            <template v-if="kanji">
              <div v-if="!last.correct && last.typed" class="typed">You typed: <strong>{{ last.typed }}</strong></div>
              <div class="kparts">
                <span class="kglyph kana" lang="ja">{{ kanji.kanji }}</span>
                <div class="kpart" :class="{ asked: flipMode || !meaningMode }">
                  <span class="klabel">On</span>
                  <span v-if="kanji.onView.length" class="kitems"><KanjiReading v-for="(view, i) in kanji.onView" :key="i" :view="view" /></span>
                  <span v-else class="none">none</span>
                </div>
                <div class="kpart" :class="{ asked: flipMode || !meaningMode }">
                  <span class="klabel">Kun</span>
                  <span v-if="kanji.kunView.length" class="kitems"><KanjiReading v-for="(view, i) in kanji.kunView" :key="i" :view="view" /></span>
                  <span v-else class="none">none</span>
                </div>
                <div class="kpart" :class="{ asked: flipMode || meaningMode }">
                  <span class="klabel">Meaning</span>
                  <span class="kmeaning">{{ meaningsText }}</span>
                </div>
              </div>
            </template>
            <template v-else-if="custom">
              <div v-if="!last.correct && last.typed" class="typed">You typed: <strong>{{ last.typed }}</strong></div>
              <div class="custom-answer"><span class="klabel">Answer</span>{{ custom.answer }}</div>
            </template>
            <template v-else-if="kana">
            <div v-if="last.correct">
              <template v-if="kanaMode"><strong>{{ kana.romaji }}</strong> is written <span class="kana" lang="ja">{{ kana.kana }}</span>.</template>
              <template v-else><span class="kana" lang="ja">{{ kana.kana }}</span> is read <strong>{{ kana.romaji }}</strong>.</template>
            </div>
            <div v-else>
              The answer is
              <strong :class="{ kana: kanaMode }" :lang="kanaMode ? 'ja' : 'en'">{{ kanaMode ? kana.kana : kana.romaji }}</strong>
              <template v-if="otherKana.length">
                (also <span class="kana" lang="ja">{{ otherKana.join(' ') }}</span>)</template
              >.
              <template v-if="last.typed">
                You typed: <strong :lang="kanaMode ? 'ja' : 'en'">{{ last.typed }}</strong></template
              >
            </div>
            <div v-if="kana.note" class="note">{{ kana.note }}</div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <section v-else class="card done stack" aria-label="Round finished">
      <div class="done-head">
        <h1>{{ missed.length ? 'Round finished' : 'Perfect round' }}</h1>
        <span v-if="missed.length" class="muted counts">
          <b>{{ correctCount }}</b> right<span class="vbar" aria-hidden="true"></span><b>{{ wrongCount }}</b> wrong
        </span>
        <span v-else class="muted counts"><b>{{ correctCount }}</b> of {{ total }} right</span>
      </div>

      <!-- Same cells as above the card during the round: one per card, green right, red wrong. -->
      <div v-if="missed.length" class="seg" :style="{ '--n': total, '--gap': cellGap }" aria-hidden="true">
        <i v-for="n in total" :key="n" :class="cellClass(n - 1)"></i>
      </div>

      <div v-if="missed.length" class="review" role="table" aria-label="Missed cards">
        <div class="review-row review-head" role="row">
          <span role="columnheader">Card</span><span role="columnheader">Answer</span><span role="columnheader">You typed</span>
        </div>
        <div v-for="row in review" :key="row.id" class="review-row" role="row">
          <span class="rv-face copyable" :class="row.plain ? 'plain' : 'kana'" lang="ja" role="cell">{{ row.face }}</span>
          <span class="rv-answer copyable" role="cell"><span v-for="(line, i) in row.answer" :key="i" class="rv-line">{{ line }}</span></span>
          <span class="rv-typed" :class="row.mistake.typed ? 'copyable' : 'none'" role="cell">{{ row.mistake.text }}</span>
        </div>
      </div>
      <div v-else class="perfect">
        <span class="tick"><Icon :path="mdiCheck" :size="20" /></span>
        <div>
          <strong>Every card was right</strong>
          <span>{{ total }} of {{ total }}, nothing to practise.</span>
        </div>
      </div>

      <div class="buttons">
        <button v-if="missed.length" type="button" class="btn btn-primary" @click="practiseMissed">
          <Icon :path="mdiRestart" :size="18" />Practise the {{ missed.length }} missed
        </button>
        <button type="button" class="btn" :class="missed.length ? 'btn-quiet' : 'btn-primary'" @click="another"><Icon :path="mdiRestart" :size="18" />Another round</button>
        <RouterLink :to="{ name: 'home' }" class="btn btn-quiet"><Icon :path="mdiHome" :size="18" />Home</RouterLink>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* The card is centred in the page, horizontally and vertically (62px = the content area's top and bottom padding). The stage is
   three rows: the line above (sits at the bottom of the first row), the card, the answer under it (sits at the top of the third
   row). The two outer rows are equal, so the card stays exactly in the middle whatever they hold. */
.play {
  min-height: calc(100vh - 62px);
  display: flex;
  align-items: center;
  justify-content: center;
}
.stage {
  width: 100%;
  max-width: 660px;
  align-self: stretch;
  display: grid;
  grid-template-rows: 1fr auto 1fr;
  justify-items: stretch;
}
.flash,
.done {
  width: 100%;
  max-width: 660px;
  padding: 20px 30px 22px;
}
.rail {
  align-self: end;
  padding-bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.rail-top {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: baseline;
  gap: 4px 16px;
  font-size: 13px;
}
.counts b {
  color: var(--ink);
  font-weight: 600;
}
.leave {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--muted);
}
.leave:hover {
  color: var(--ink);
  text-decoration: underline;
}
.seg {
  display: grid;
  grid-template-columns: repeat(var(--n), 1fr);
  gap: var(--gap);
}
.seg i {
  height: 8px;
  border-radius: 3px;
  background: var(--tint);
}
.seg i.ok {
  background: var(--good);
}
.seg i.bad {
  background: var(--wrong);
}
.seg i.now {
  background: var(--surface);
  box-shadow: inset 0 0 0 2px var(--ink);
}
.flash {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}
.meta {
  width: 100%;
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}
.bar {
  width: 100%;
  height: 3px;
  background: var(--tint);
}
.bar .fill {
  height: 100%;
  background: var(--accent);
  transition: width 0.1s linear;
}
.glyph {
  --glyph-size: 150px; /* two typefaces side by side */
  min-height: 230px;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1.1;
}
.glyph.mincho,
.glyph.gothic {
  --glyph-size: 190px;
}
.glyph.reading {
  font-family: var(--font-display);
  font-size: 112px;
}
/* A card the user wrote: the box keeps the size of the other cards, long text scrolls inside it. margin:auto (not align-items) centres
   the text and still lets the top of a long text be reached. */
.glyph.custom-face {
  width: 100%;
  height: 230px;
  min-height: 0;
  overflow-y: auto;
  line-height: 1.3;
}
.custom-text {
  margin: auto;
  max-width: 100%;
  font-family: 'Inter', var(--font-gothic), system-ui, sans-serif;
  text-align: center;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.answer {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.answer label,
.flip-label {
  font-size: 13px;
}
.row {
  min-height: 38px; /* the height of the text field, so the card is the same size whichever question is asked */
}
.row .btn {
  flex: 1;
}
.row input + .btn {
  flex: none;
}
.row {
  display: flex;
  gap: 10px;
}
.row input {
  flex: 1;
  min-width: 0;
  padding: 6px 12px;
  font: inherit;
  font-size: 16px;
  color: var(--ink);
  background: #fff;
  border: 1px solid var(--line-strong);
  border-radius: 4px;
}
.row input:disabled {
  background: var(--tint);
}
.foot {
  width: 100%;
  display: flex;
  align-items: center;
  font-size: 13px;
  height: 20px;
}
.under {
  align-self: start;
  padding-top: 12px;
}
/* The answer under the card: a solid label (green or red) next to the facts. Compact on purpose. */
.verdict {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  overflow: hidden;
  font-size: 14px;
  border: 1px solid;
  border-radius: 6px;
  animation: rise 0.2s ease-out;
}
.verdict.ok {
  border-color: var(--good);
}
.verdict.bad {
  border-color: var(--wrong);
}
.verdict.show {
  border-color: var(--ink);
}
.verdict .tag {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  color: #fff;
  font-weight: 600;
  white-space: nowrap;
}
.verdict.ok .tag {
  background: var(--good);
}
.verdict.bad .tag {
  background: var(--wrong);
}
.verdict.show .tag {
  background: var(--ink);
}
.verdict .facts {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 1px;
  padding: 6px 14px;
  background: var(--surface);
}
/* A kanji's answer: the kanji, then On, Kun and Meaning side by side, each part cut off by a vertical line. */
.verdict .typed {
  margin-bottom: 4px;
  font-size: 13px;
  color: var(--muted);
}
.verdict .typed strong {
  color: var(--ink);
}
.kparts {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  gap: 6px 0;
}
.kglyph {
  align-self: center;
  padding-right: 14px;
  font-size: 30px;
  line-height: 1;
}
.kpart {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  padding: 0 14px;
  border-left: 1px solid var(--line-strong);
}
.klabel {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--muted);
}
.kitems {
  display: flex;
  flex-wrap: wrap;
  gap: 0 14px;
}
/* The part the question asked for stays in full ink; the other parts are dimmed. */
.kpart:not(.asked) .kitems,
.kpart:not(.asked) .kmeaning {
  color: var(--muted);
}
.none {
  color: var(--muted);
}
/* The answer of a card the user wrote: as written (line breaks kept), and a long one scrolls so the page does not grow. */
.custom-answer {
  max-height: 9.5em;
  overflow-y: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.4;
}
.custom-answer .klabel {
  display: block;
}
.verdict .note {
  font-size: 13px;
  color: var(--muted);
}
@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .verdict {
    animation: none;
  }
}
.done-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 12px;
}
.done-head h1 {
  margin: 0;
}
/* The table scrolls inside itself when a round had many misses, so the title and the buttons stay in place. */
.review {
  border: 1px solid var(--line);
  border-radius: 8px;
  overflow-y: auto;
  max-height: min(340px, 42vh);
}
.review-row {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr) minmax(0, 1fr);
  align-items: center;
  gap: 0 12px;
  padding: 8px 14px;
  font-size: 14px;
  border-top: 1px solid var(--line);
}
.review-row:first-child {
  border-top: 0;
}
.review-head {
  position: sticky;
  top: 0;
  background: var(--paper);
  padding-block: 8px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--muted);
}
.rv-face {
  font-size: 28px;
  line-height: 1.1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.rv-face.plain {
  font-size: 15px;
  line-height: 1.3;
}
.rv-answer,
.rv-typed {
  min-width: 0;
  overflow-wrap: anywhere;
}
.rv-answer {
  font-weight: 600;
}
.rv-line + .rv-line {
  display: block;
  font-weight: 400;
  color: var(--muted);
}
.rv-line {
  display: block;
}
.rv-typed {
  color: var(--wrong);
  font-weight: 600;
}
.rv-typed.none {
  color: var(--muted);
  font-weight: 400;
}
.perfect {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--good) 12%, var(--surface));
}
.perfect .tick {
  flex: none;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--good);
  color: #fff;
}
.perfect strong {
  display: block;
}
.perfect span:not(.tick) {
  font-size: 13px;
  color: var(--muted);
}
.buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
@media (max-width: 520px) {
  .flash,
  .done {
    padding: 20px 18px 24px;
  }
  .glyph {
    --glyph-size: 96px;
    min-height: 170px;
  }
  .glyph.mincho,
  .glyph.gothic {
    --glyph-size: 130px;
  }
  .glyph.reading {
    font-size: 84px;
  }
  .glyph.custom-face {
    height: 170px;
  }
}
</style>
