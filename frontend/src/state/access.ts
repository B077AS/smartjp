import { reactive } from 'vue'

// Whether the website wants its access key before it shows anything (a "locked" answer from the server sets it, api/client.ts).
// The desktop app has no web server and never gets one. Not saved: after the key is accepted the browser holds a cookie.
export const access = reactive({ locked: false })
