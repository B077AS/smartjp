// The only place that talks to the outside: it reads and writes what is stored (for now the flashcard lists, in a SQLite
// database next to the application). The same pages run in two places, so there are two transports to the same Java code:
//   - in a browser (the website, or `npm run dev`): HTTP to /api (ListsController);
//   - in the desktop window (Jolt): `window.jolt.call('lists.…')` into a Java bean (ListsRpc; the types are in jolt-api.d.ts,
//     written by Jolt from the Java records).
// Pages never know which one they use. Errors are thrown as ApiError(status, code, message).

import { access } from '../state/access'
import type {
  AppInfoSummary,
  AppearanceSummary,
  CardListSummary,
  CardStatsSummary,
  ClipboardText,
  CompareResponse,
  ConnectRequest,
  ConnectResponse,
  CustomCardSummary,
  RecordAnswerRequest,
  RecordAnswerResponse,
  RunSyncRequest,
  RunSyncResponse,
  SaveAppearanceRequest,
  SaveAppearanceResponse,
  SaveCustomCardRequest,
  SaveCustomCardResponse,
  SaveListRequest,
  SaveListResponse,
  SyncCounts,
  SyncStatus,
  SyncSummary,
} from '../jolt-api'

export type CardList = CardListSummary
export type {
  AppInfoSummary,
  AppearanceSummary,
  CardStatsSummary,
  ClipboardText,
  CompareResponse,
  ConnectRequest,
  ConnectResponse,
  CustomCardSummary,
  RecordAnswerRequest,
  RecordAnswerResponse,
  RunSyncRequest,
  RunSyncResponse,
  SaveAppearanceRequest,
  SaveAppearanceResponse,
  SaveCustomCardRequest,
  SaveCustomCardResponse,
  SaveListRequest,
  SaveListResponse,
  SyncCounts,
  SyncStatus,
  SyncSummary,
}

/** The website's access key, as its own Settings shows it (website only, so not one of the generated Jolt types). */
export interface AccessKey {
  key: string
  /** Set from outside (SMARTJP_ACCESS_KEY): it cannot be renewed in the app. */
  fixed: boolean
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string | null

  constructor(status: number, code: string | null, message: string | null) {
    super(message ?? '')
    this.status = status
    this.code = code
  }
}

/** Jolt puts `window.jolt` into the pages of its windows; a normal browser has none. */
function bridge(): Window['jolt'] | undefined {
  return typeof window !== 'undefined' && 'jolt' in window ? window.jolt : undefined
}

export const inDesktopApp = (): boolean => bridge() !== undefined

async function http<T>(method: 'GET' | 'POST' | 'DELETE', url: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), credentials: 'same-origin' })
  } catch {
    throw new ApiError(0, 'network', 'Cannot reach the server.')
  }
  const isJson = (response.headers.get('content-type') || '').includes('application/json')
  const data: unknown = isJson ? await response.json().catch(() => null) : null
  if (!response.ok) {
    const error = (data ?? {}) as { code?: string | null; message?: string | null }
    // A website that wants its access key: the page shows the unlock screen (state/access.ts). The call still fails, so nothing shows half-loaded data.
    if (response.status === 401 && error.code === 'locked') access.locked = true
    throw new ApiError(response.status, error.code ?? null, error.message ?? null)
  }
  return data as T
}

/** A call into the desktop app's Java side; a failure there comes back as an error with a code. */
async function call<T>(run: (jolt: Window['jolt']) => Promise<T>): Promise<T> {
  const jolt = bridge()
  if (!jolt) throw new ApiError(0, 'network', 'Not running in the desktop app.')
  try {
    return await run(jolt)
  } catch (error) {
    const e = error as { code?: string; message?: string }
    throw new ApiError(0, e.code ?? 'internal', e.message ?? null)
  }
}

export const api = {
  lists: {
    all: (): Promise<CardList[]> => (bridge() ? call((jolt) => jolt.call('lists.all')) : http<CardList[]>('GET', '/api/lists')),

    /** The answer says what came of it (`result`): the rules live in Java. */
    save: (request: SaveListRequest): Promise<SaveListResponse> =>
      bridge() ? call((jolt) => jolt.call('lists.save', request)) : http<SaveListResponse>('POST', '/api/lists', request),

    remove: async (id: string): Promise<void> => {
      if (bridge()) await call((jolt) => jolt.call('lists.delete', id))
      else await http<void>('DELETE', '/api/lists/' + encodeURIComponent(id))
    },
  },

  /** The log of answers: what is stored is one event per answer; what the page reads back is the totals per card. */
  answers: {
    stats: (): Promise<CardStatsSummary[]> =>
      bridge() ? call((jolt) => jolt.call('answers.stats')) : http<CardStatsSummary[]>('GET', '/api/answers/stats'),

    /** The answer says what came of it (`result`); sending the same id twice stores it once. */
    record: (request: RecordAnswerRequest): Promise<RecordAnswerResponse> =>
      bridge() ? call((jolt) => jolt.call('answers.record', request)) : http<RecordAnswerResponse>('POST', '/api/answers', request),
  },

  /** What the About tab shows (version, system, how it runs) and links to other sites. */
  app: {
    info: (): Promise<AppInfoSummary> => (bridge() ? call((jolt) => jolt.call('app.info')) : http<AppInfoSummary>('GET', '/api/app')),

    /** The desktop app hands the link to the user's own browser (a link inside its window would replace the app); the website opens a new tab. */
    openLink: async (url: string): Promise<void> => {
      if (bridge()) {
        await call((jolt) => jolt.call('app.openLink', url))
        return
      }
      window.open(url, '_blank', 'noopener,noreferrer')
    },
  },

  /** How this installation looks (the accent colour). Kept per device: it is never part of a sync. */
  appearance: {
    get: (): Promise<AppearanceSummary> => (bridge() ? call((jolt) => jolt.call('appearance.get')) : http<AppearanceSummary>('GET', '/api/appearance')),

    save: (request: SaveAppearanceRequest): Promise<SaveAppearanceResponse> =>
      bridge() ? call((jolt) => jolt.call('appearance.save', request)) : http<SaveAppearanceResponse>('POST', '/api/appearance', request),
  },

  /** The website's access key: a browser hands it in once and gets a cookie. Website only (the desktop app has no web server). */
  session: {
    unlock: (key: string): Promise<{ unlocked: boolean }> => http('POST', '/api/session', { key }),
  },

  /**
   * Sync between a desktop app and a self-hosted website. The desktop app runs it (it is the one that connects, so every call but the
   * server's own two is a Jolt call); the website is the other side and only shows its access key and what it holds.
   */
  sync: {
    status: (): Promise<SyncStatus> => call((jolt) => jolt.call('sync.status')),
    /** Checks the address and the key against the server and keeps them (the key in the system keystore) only when the server accepts them. */
    connect: (request: ConnectRequest): Promise<ConnectResponse> => call((jolt) => jolt.call('sync.connect', request)),
    disconnect: async (): Promise<void> => {
      await call((jolt) => jolt.call('sync.disconnect'))
    },
    /** This desktop and the server side by side. */
    compare: (): Promise<CompareResponse> => call((jolt) => jolt.call('sync.compare')),
    /** merge, pull (replace this desktop with the server) or push (replace the server with this desktop). */
    run: (request: RunSyncRequest): Promise<RunSyncResponse> => call((jolt) => jolt.call('sync.run', request)),
    /** Website only: what this server holds, and its access key (with a way to make a new one). */
    serverSummary: (): Promise<SyncSummary> => http<SyncSummary>('GET', '/api/sync/summary'),
    serverKey: (): Promise<AccessKey> => http<AccessKey>('GET', '/api/sync/key'),
    renewServerKey: (): Promise<AccessKey> => http<AccessKey>('POST', '/api/sync/key/renew'),
  },

  /**
   * The text on the system clipboard, for the Paste item of the page's own right-click menu. Desktop app only: a web view does not
   * let a page read the clipboard, so Java does. (The website has no such menu: a browser shows its own.)
   */
  clipboard: {
    readText: async (): Promise<string> => (await call((jolt) => jolt.call('clipboard.readText'))).text,
  },

  /** The cards the user wrote themselves (front text and answer text). */
  customCards: {
    all: (): Promise<CustomCardSummary[]> =>
      bridge() ? call((jolt) => jolt.call('customCards.all')) : http<CustomCardSummary[]>('GET', '/api/custom-cards'),

    /** Without an id a card is created, with one it is changed; the answer says what came of it (`result`). */
    save: (request: SaveCustomCardRequest): Promise<SaveCustomCardResponse> =>
      bridge() ? call((jolt) => jolt.call('customCards.save', request)) : http<SaveCustomCardResponse>('POST', '/api/custom-cards', request),

    remove: async (id: string): Promise<void> => {
      if (bridge()) await call((jolt) => jolt.call('customCards.delete', id))
      else await http<void>('DELETE', '/api/custom-cards/' + encodeURIComponent(id))
    },
  },
}
