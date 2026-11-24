<script setup lang="ts">
// Settings, Sync, on the website: this server is the OTHER side of a sync, so it never starts one. It shows what it holds and its access
// key (to copy into the desktop app's Settings, Sync), and can make a new key. Reaching this screen already needs the key, so showing it
// here gives nothing away.
import { onMounted, ref, watch } from 'vue'
import { mdiContentCopy, mdiEyeOffOutline, mdiEyeOutline } from '@mdi/js'
import { api, type AccessKey, type SyncSummary } from '../api/client'
import { formatWhen } from '../sync/format'
import { notifyError, notifySuccess } from '../state/toasts'
import Icon from './Icon.vue'
import UiDialog from './ui/UiDialog.vue'

// `active`: this section of the Settings window is the one showing (the counts are read again each time it is).
const props = defineProps<{ active?: boolean }>()

const key = ref<AccessKey | null>(null)
const summary = ref<SyncSummary | null>(null)
const shown = ref(false)
const renewOpen = ref(false)
const failed = ref(false)

async function load(): Promise<void> {
  try {
    ;[key.value, summary.value] = await Promise.all([api.sync.serverKey(), api.sync.serverSummary()])
    failed.value = false
  } catch {
    failed.value = true
  }
}
onMounted(load)
watch(
  () => props.active,
  (shownNow) => {
    if (shownNow) void load()
  },
)

async function copy(): Promise<void> {
  if (!key.value) return
  try {
    await navigator.clipboard.writeText(key.value.key)
    notifySuccess('Access key copied', 'Paste it into the desktop app, in Settings, Sync.')
  } catch {
    shown.value = true // the clipboard is refused here: show the key so it can be selected and copied by hand
    notifyError("Couldn't copy the key", 'Select it and copy it yourself.')
  }
}

async function renew(): Promise<void> {
  renewOpen.value = false
  try {
    key.value = await api.sync.renewServerKey()
    shown.value = true
    notifySuccess('New access key made', 'The old one no longer works. Enter the new one in each desktop app.')
  } catch {
    notifyError("Couldn't make a new key", 'Try again.')
  }
}

const masked = (value: string): string => '•'.repeat(Math.min(value.length, 32))
</script>

<template>
  <div class="sync">
    <div class="row"><span class="sync-tag">This is the server</span></div>
    <p class="muted small">Your desktop app starts the sync. To connect it, enter this server's address and this key in the desktop app, in Settings, Sync.</p>

    <p v-if="failed" class="muted small">The key could not be read. <button type="button" class="link-button" @click="load">Try again</button></p>

    <div v-else-if="key" class="field">
      <label for="server-key">Access key</label>
      <div class="row">
        <input id="server-key" class="keyfield" type="text" readonly :value="shown ? key.key : masked(key.key)" spellcheck="false" autocomplete="off" />
        <button type="button" class="btn btn-quiet" :aria-pressed="shown" @click="shown = !shown"><Icon :path="shown ? mdiEyeOffOutline : mdiEyeOutline" :size="18" />{{ shown ? 'Hide' : 'Show' }}</button>
        <button type="button" class="btn btn-quiet" @click="copy"><Icon :path="mdiContentCopy" :size="18" />Copy</button>
      </div>
    </div>

    <dl v-if="summary" class="facts">
      <dt>Answers stored</dt><dd>{{ summary.answers.toLocaleString() }}</dd>
      <dt>Saved lists</dt><dd>{{ summary.lists }}</dd>
      <dt>My cards</dt><dd>{{ summary.customCards }}</dd>
      <dt>Last sync</dt><dd>{{ formatWhen(summary.lastSyncAt) }}</dd>
    </dl>

    <div v-if="key" class="row">
      <button type="button" class="btn btn-quiet" :disabled="key.fixed" @click="renewOpen = true">Make a new key</button>
      <span class="muted small">{{ key.fixed ? 'This key is set from outside (SMARTJP_ACCESS_KEY), so it cannot be changed here.' : 'The old key stops working at once. You enter the new one in each desktop app.' }}</span>
    </div>

    <UiDialog
      v-model:open="renewOpen"
      title="Make a new access key?"
      description="The old key stops working at once. This browser stays unlocked; every other browser and every desktop app has to enter the new key."
    >
      <template #footer>
        <button type="button" class="btn btn-quiet" @click="renewOpen = false">Cancel</button>
        <button type="button" class="btn btn-primary" @click="renew">Make a new key</button>
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
.row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.small {
  font-size: 13px;
}
.keyfield {
  flex: 1 1 100%; /* the whole key on its own line, the buttons under it */
  min-width: 0;
  height: 32px;
  padding: 0 10px;
  font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace;
  font-size: 13px;
  color: var(--ink);
  background: #fff;
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  user-select: text;
}
.facts {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 12px;
  max-width: 340px;
  margin: 0;
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
</style>
