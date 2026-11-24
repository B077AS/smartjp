<script setup lang="ts">
// Settings, Sync, in the desktop app: keep this app and your own SmartJP website (the "server") in step. Two states:
//   - not connected: the server's address and its access key, entered once (the key is kept in the system keystore by Java);
//   - connected: both sides side by side and one choice, Merge (the default, loses nothing), "Replace this desktop with the server" or
//     "Replace the server with this desktop" (the two overwrite, so they ask first and the overwritten side saves a backup).
// All the rules and the work are in Java (SyncDesktopService); this file only asks and shows what came of it.
import { computed, onMounted, ref, watch } from 'vue'
import { RadioGroupIndicator, RadioGroupItem, RadioGroupRoot } from 'reka-ui'
import { mdiCheck, mdiSync } from '@mdi/js'
import { api, type RunSyncResponse, type SyncSummary } from '../api/client'
import { describeCounts, describeHeld, formatWhen, isEmptyCounts } from '../sync/format'
import { loadEverything } from '../startup'
import { notifyError, notifySuccess } from '../state/toasts'
import Icon from './Icon.vue'
import UiDialog from './ui/UiDialog.vue'

// `active`: this section of the Settings window is the one showing. Looking at it again looks at the server again, so a key that was renewed
// or a server that came back is noticed without closing the window.
const props = defineProps<{ active?: boolean }>()

type Action = 'merge' | 'pull' | 'push'
type Reach = 'loading' | 'ok' | 'unreachable' | 'bad_key' | 'failed'

const phase = ref<'loading' | 'disconnected' | 'connected'>('loading')
const address = ref<string | null>(null)
const reach = ref<Reach>('loading')
const local = ref<SyncSummary | null>(null)
const remote = ref<SyncSummary | null>(null)
const action = ref<Action>('merge')
const busy = ref(false)
const confirmOpen = ref(false)
const addressInput = ref('')
const keyInput = ref('')

const OPTIONS: { value: Action; title: string; text: string; tag: string; tone: 'hot' | 'bad' }[] = [
  { value: 'merge', title: 'Merge', text: 'Add what each side is missing. Nothing is lost on either side.', tag: 'Recommended', tone: 'hot' },
  { value: 'pull', title: 'Replace this desktop with the server', text: 'This desktop becomes a copy of the server.', tag: 'Overwrites', tone: 'bad' },
  { value: 'push', title: 'Replace the server with this desktop', text: 'The server becomes a copy of this desktop. Also how you start again from zero.', tag: 'Overwrites', tone: 'bad' },
]

const BUTTON: Record<Action, string> = { merge: 'Merge now', pull: 'Replace this desktop', push: 'Replace the server' }

const shown = (n: number | undefined): string => (n === undefined ? '—' : n.toLocaleString())
const moreThan = (a: number | undefined, b: number | undefined): boolean => a !== undefined && b !== undefined && a > b
const status = computed(() => (reach.value === 'ok' ? { text: 'Connected', tone: 'ok' } : reach.value === 'loading' ? { text: 'Connected', tone: 'ok' } : reach.value === 'bad_key' ? { text: 'Key not accepted', tone: 'bad' } : { text: "Can't reach the server", tone: 'bad' }))

// ---- what is there now ----------------------------------------------------------------------------------------
async function refreshCompare(): Promise<void> {
  reach.value = 'loading'
  try {
    const reply = await api.sync.compare()
    if (reply.result === 'ok') {
      local.value = reply.local ?? null
      remote.value = reply.remote ?? null
      reach.value = 'ok'
    } else if (reply.result === 'not_connected') {
      phase.value = 'disconnected'
    } else {
      local.value = reply.local ?? null // this desktop is known even when the server is not
      remote.value = null
      reach.value = reply.result === 'unreachable' || reply.result === 'bad_key' ? reply.result : 'failed'
    }
  } catch {
    reach.value = 'failed'
  }
}

async function refreshStatus(): Promise<void> {
  try {
    const current = await api.sync.status()
    address.value = current.address ?? null
    phase.value = current.connected ? 'connected' : 'disconnected'
    if (current.connected) await refreshCompare()
  } catch {
    phase.value = 'disconnected'
  }
}
onMounted(refreshStatus)
watch(
  () => props.active,
  (shownNow) => {
    if (shownNow && phase.value === 'connected' && !busy.value) void refreshCompare()
  },
)

// ---- connecting -------------------------------------------------------------------------------------------------
async function connect(): Promise<void> {
  if (busy.value || addressInput.value.trim() === '' || keyInput.value.trim() === '') return
  busy.value = true
  try {
    const reply = await api.sync.connect({ address: addressInput.value, key: keyInput.value })
    switch (reply.result) {
      case 'connected':
        keyInput.value = ''
        address.value = reply.status.address ?? null
        phase.value = 'connected'
        notifySuccess('Connected to your server', 'Choose what to do with the two copies.')
        await refreshCompare()
        break
      case 'bad_address':
        notifyError("That address doesn't look right", 'Use something like https://smartjp.example.org')
        break
      case 'bad_key':
        notifyError("The server doesn't accept that key", "Copy it again from the server's Settings, Sync, or from its log.")
        break
      case 'unreachable':
        notifyError("Couldn't reach the server", 'Check the address, and that the server is running.')
        break
      case 'no_keystore':
        notifyError("Couldn't keep the key safely", "This computer has no system keystore (Windows Credential Manager or the Linux Secret Service) to keep it in.")
        break
      default:
        notifyError("That address didn't answer like a SmartJP server", 'Check that it is the address of your SmartJP website.')
    }
  } catch {
    notifyError("Couldn't connect", 'Something went wrong inside the app. Try again.')
  } finally {
    busy.value = false
  }
}

async function disconnect(): Promise<void> {
  try {
    await api.sync.disconnect()
  } catch {
    notifyError("Couldn't disconnect", 'Something went wrong inside the app. Try again.')
    return
  }
  phase.value = 'disconnected'
  address.value = null
  local.value = null
  remote.value = null
  action.value = 'merge'
}

// ---- syncing ---------------------------------------------------------------------------------------------------
function start(): void {
  if (busy.value) return
  if (action.value === 'merge') void run()
  else confirmOpen.value = true // an overwrite always asks first
}

function report(reply: RunSyncResponse, chosen: Action): void {
  switch (reply.result) {
    case 'done':
      if (chosen === 'merge') {
        notifySuccess(
          'Merged with your server',
          isEmptyCounts(reply.sent) && isEmptyCounts(reply.received)
            ? 'Both sides already had the same data.'
            : `Sent ${describeCounts(reply.sent)}. Received ${describeCounts(reply.received)}.`,
        )
      } else if (chosen === 'pull') {
        notifySuccess('This desktop now matches the server', `Your old data was saved as ${reply.backup} in this app's data folder.`)
      } else {
        notifySuccess('The server now matches this desktop', `The server saved its old data as ${reply.backup} in its data folder.`)
      }
      break
    case 'unreachable':
      notifyError("Couldn't reach the server", 'Check the address, and that the server is running, then try again.')
      break
    case 'bad_key':
      notifyError("The server doesn't accept the key", 'It may have been renewed. Disconnect and connect again with the new key.')
      break
    default:
      notifyError("The sync didn't finish", 'The server did not answer as expected. Nothing was overwritten. You can try again.')
  }
}

async function run(): Promise<void> {
  const chosen = action.value
  confirmOpen.value = false
  busy.value = true
  try {
    report(await api.sync.run({ action: chosen }), chosen)
    await loadEverything() // the lists, cards and totals the page shows may have just changed
    await refreshCompare()
  } catch {
    notifyError("The sync didn't finish", 'Something went wrong inside the app. Try again.')
  } finally {
    busy.value = false
  }
}

const confirmTitle = computed(() => (action.value === 'pull' ? 'Replace this desktop with the server?' : 'Replace the server with this desktop?'))
const confirmText = computed(() => {
  const l = local.value
  const r = remote.value
  if (!l || !r) return 'Everything on the side that is replaced is overwritten.'
  return action.value === 'pull'
    ? `Everything on this desktop is replaced by what is on the server: ${describeHeld(r.answers, r.lists, r.customCards)}. What exists only on this desktop is gone from it.`
    : `Everything on the server is replaced by what is on this desktop: ${describeHeld(l.answers, l.lists, l.customCards)}. What exists only on the server is gone from it.`
})
const confirmBackup = computed(() =>
  action.value === 'pull' ? "First, this desktop is saved to a backup file in this app's data folder." : "First, the server saves its own data to a backup file in its data folder.",
)
</script>

<template>
  <div class="sync">
    <p v-if="phase === 'loading'" class="muted small">Looking for your server…</p>

    <!-- ===== not connected: the address and the key, once ===== -->
    <form v-else-if="phase === 'disconnected'" class="stack" @submit.prevent="connect">
      <div class="row"><span class="sync-tag">Not connected</span></div>
      <p class="muted small">Keep this desktop app and your own SmartJP website in step. Nothing is sent anywhere else.</p>
      <div class="field">
        <label for="sync-address">Server address</label>
        <input id="sync-address" v-model="addressInput" type="text" placeholder="https://smartjp.example.org" autocomplete="off" spellcheck="false" />
      </div>
      <div class="field">
        <label for="sync-key">Access key</label>
        <input id="sync-key" v-model="keyInput" type="password" autocomplete="off" spellcheck="false" />
        <span class="hint">On the server: Settings, Sync, "Access key". It is also printed in the server's log when it starts. You only enter it once; this computer keeps it in its own keystore.</span>
      </div>
      <div class="row">
        <button type="submit" class="btn btn-primary" :disabled="busy || addressInput.trim() === '' || keyInput.trim() === ''">
          <Icon :path="mdiCheck" :size="18" />{{ busy ? 'Connecting…' : 'Connect' }}
        </button>
        <span class="muted small">Checks the address and the key, then shows both sides.</span>
      </div>
    </form>

    <!-- ===== connected ===== -->
    <template v-else>
      <div class="row between">
        <span class="row">
          <span class="sync-tag" :class="status.tone">{{ status.text }}</span>
          <span class="muted small addr">{{ address }}</span>
        </span>
        <span class="row">
          <button v-if="reach !== 'ok' && reach !== 'loading'" type="button" class="link-button small" @click="refreshCompare">Try again</button>
          <button type="button" class="link-button small" @click="disconnect">Disconnect</button>
        </span>
      </div>

      <div class="connected">
      <div class="sides">
        <div class="side">
          <h3>This desktop</h3>
          <span class="muted small">Saved on this computer</span>
          <dl class="facts">
            <dt>Answers</dt><dd :class="{ more: moreThan(local?.answers, remote?.answers) }">{{ shown(local?.answers) }}</dd>
            <dt>Last answer</dt><dd>{{ local ? formatWhen(local.lastAnswerAt) : shown(undefined) }}</dd>
            <dt>Saved lists</dt><dd :class="{ more: moreThan(local?.lists, remote?.lists) }">{{ shown(local?.lists) }}</dd>
            <dt>My cards</dt><dd :class="{ more: moreThan(local?.customCards, remote?.customCards) }">{{ shown(local?.customCards) }}</dd>
          </dl>
        </div>
        <div class="side">
          <h3>Server</h3>
          <span class="muted small addr">{{ address }}</span>
          <dl class="facts">
            <dt>Answers</dt><dd :class="{ more: moreThan(remote?.answers, local?.answers) }">{{ shown(remote?.answers) }}</dd>
            <dt>Last answer</dt><dd>{{ remote ? formatWhen(remote.lastAnswerAt) : shown(undefined) }}</dd>
            <dt>Saved lists</dt><dd :class="{ more: moreThan(remote?.lists, local?.lists) }">{{ shown(remote?.lists) }}</dd>
            <dt>My cards</dt><dd :class="{ more: moreThan(remote?.customCards, local?.customCards) }">{{ shown(remote?.customCards) }}</dd>
          </dl>
        </div>
      </div>

      <div class="actions">
      <RadioGroupRoot v-model="action" class="choices" aria-label="What to do" orientation="vertical">
        <RadioGroupItem v-for="o in OPTIONS" :key="o.value" :value="o.value" class="choice">
          <span class="mark"><RadioGroupIndicator class="dot" /></span>
          <span class="text"><b>{{ o.title }}</b><span class="d">{{ o.text }}</span></span>
          <span class="sync-tag" :class="o.tone">{{ o.tag }}</span>
        </RadioGroupItem>
      </RadioGroupRoot>

      <p v-if="action !== 'merge'" class="sync-note warn">The side that is overwritten is saved first as a backup file, so this can be undone by hand.</p>

      <div class="row between">
        <span class="muted small">Last sync: {{ formatWhen(local?.lastSyncAt) }}</span>
        <button type="button" class="btn" :class="action === 'merge' ? 'btn-primary' : 'btn-danger'" :disabled="busy" @click="start">
          <Icon :path="mdiSync" :size="18" />{{ busy ? 'Syncing…' : BUTTON[action] }}
        </button>
      </div>
      </div>
      </div>
    </template>

    <UiDialog v-model:open="confirmOpen" :title="confirmTitle" :description="confirmText">
      <p class="sync-note">{{ confirmBackup }}</p>
      <template #footer>
        <button type="button" class="btn btn-quiet" @click="confirmOpen = false">Cancel</button>
        <button type="button" class="btn btn-danger" @click="run">{{ BUTTON[action] }}</button>
      </template>
    </UiDialog>
  </div>
</template>

<style scoped>
.sync {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
/* The address and key form does not need the whole width of a wide window. */
.sync > form {
  max-width: 560px;
}
.row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.row.between {
  justify-content: space-between;
}
.addr {
  overflow-wrap: anywhere;
}
.small {
  font-size: 13px;
}
/* On a wide window the two sides sit on the left, one above the other, and the choices on the right. */
.connected {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
}
.actions {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}
.sides {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
  align-content: start;
}
@media (min-width: 1000px) {
  .connected {
    grid-template-columns: minmax(0, 5fr) minmax(0, 6fr);
    gap: 22px;
  }
  .sides {
    grid-template-columns: minmax(0, 1fr);
  }
}
.side {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 6px;
}
.side h3 {
  margin: 0;
  font-size: 15px;
}
.facts {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 12px;
  margin: 4px 0 0;
  font-size: 13.5px;
}
.facts dt {
  color: var(--muted);
}
.facts dd {
  margin: 0;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.facts dd.more {
  font-weight: 600;
}

/* the three choices: a card per action, the chosen one in the accent */
.choices {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.choice {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
  width: 100%;
  padding: 10px 12px;
  font: inherit;
  text-align: left;
  color: var(--ink);
  background: var(--surface, #fff);
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  cursor: pointer;
}
.choice:hover {
  background: var(--paper);
}
.choice[data-state='checked'] {
  background: var(--select-tint);
  border-color: var(--accent);
  box-shadow: inset 0 0 0 1px var(--accent);
}
.choice:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--accent) 55%, transparent);
  outline-offset: 1px;
}
.mark {
  display: grid;
  place-items: center;
  width: 16px;
  height: 16px;
  margin-top: 2px;
  border: 1.5px solid var(--line-strong);
  border-radius: 50%;
  background: #fff;
}
.choice[data-state='checked'] .mark {
  border-color: var(--accent);
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
}
.text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.text b {
  font-size: 14px;
  font-weight: 600;
}
.text .d {
  font-size: 13px;
  color: var(--muted);
}
</style>
