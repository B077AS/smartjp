import { createApp } from 'vue'

// Fonts ship with the app (no Google Fonts request): the desktop window must work offline.
// All of them SIL OFL. Hina Mincho (mincho) and Noto Sans JP (gothic) are the two kana faces; each "japanese" file
// is one 1-1.4 MB file the browser only downloads once a page actually shows Japanese text.
// Latin subsets only: a name with other letters falls back to the system font.
import '@fontsource/inter/latin-400.css'
import '@fontsource/inter/latin-500.css'
import '@fontsource/inter/latin-600.css'
import '@fontsource/newsreader/latin-500.css'
import '@fontsource/hina-mincho/japanese-400.css'
import '@fontsource/noto-sans-jp/japanese-400.css'

import './styles/fonts-kanji.css'
import './styles/base.css'
import './styles/ui.css'
import App from './App.vue'
import { inDesktopApp } from './api/client'
import router from './router'
import { installHoverScroll } from './state/hoverScroll'
import { installScrollLock } from './state/scrollLock'
import { installSelectionClear } from './state/selection'
import { applyTheme } from './state/theme'
import { loadEverything } from './startup'

// The desktop window has a native title bar above the page: base.css draws a thin line under it (a website has none).
if (inDesktopApp()) document.documentElement.classList.add('desktop')
applyTheme() // the chosen accent, before the first paint
installScrollLock()
installHoverScroll()
installSelectionClear()
createApp(App).use(router).mount('#app')

// The saved lists, the answer log and the cards come from the database (SQLite, through the Java side); the page shows them as soon as they arrive.
loadEverything()
