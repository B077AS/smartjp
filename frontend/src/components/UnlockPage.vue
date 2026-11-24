<script setup lang="ts">
// Shown instead of the app while a website wants its access key (see state/access.ts). The key is made by the server at its first start
// and printed in its log; it is also in Settings, Sync, on a browser that is already unlocked. The server answers with a cookie, so this
// is asked once per browser.
import { ref } from 'vue'
import { mdiLockOpenVariantOutline } from '@mdi/js'
import { ApiError, api } from '../api/client'
import { access } from '../state/access'
import { notifyError } from '../state/toasts'
import { loadEverything } from '../startup'
import Icon from './Icon.vue'
import Wordmark from './Wordmark.vue'

const key = ref('')
const busy = ref(false)

async function unlock(): Promise<void> {
  if (busy.value || key.value.trim() === '') return
  busy.value = true
  try {
    await api.session.unlock(key.value.trim())
    access.locked = false
    key.value = ''
    await loadEverything() // the first loads were refused: read everything now
  } catch (error) {
    if (error instanceof ApiError && error.code === 'bad_key') notifyError("That isn't this server's key", 'Check it and try again. It is in the server\'s log.')
    else notifyError("Couldn't unlock", 'The server cannot be reached. Check that it is running and try again.')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="unlock">
    <form class="card box" @submit.prevent="unlock">
      <Wordmark />
      <h1>This server is locked</h1>
      <p class="muted">Enter its access key. You only need to do this once in this browser.</p>
      <div class="field">
        <label for="access-key">Access key</label>
        <input id="access-key" v-model="key" type="password" autocomplete="off" spellcheck="false" autofocus />
        <span class="hint">It is printed in the server's log when it starts, and shown in Settings, Sync, in a browser that is already unlocked.</span>
      </div>
      <button type="submit" class="btn btn-primary" :disabled="busy || key.trim() === ''"><Icon :path="mdiLockOpenVariantOutline" :size="18" />Unlock</button>
    </form>
  </main>
</template>

<style scoped>
.unlock {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 24px 16px;
}
.box {
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: min(100%, 400px);
  padding: 22px 24px 24px;
}
.box h1 {
  margin: 4px 0 0;
}
.box p {
  margin: 0;
  font-size: 14px;
}
.box .btn {
  align-self: flex-start;
}
</style>
