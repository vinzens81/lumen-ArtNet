import { api } from './api';
import { groupByValues } from './liveCommands';
import { showError } from './toasts.svelte';
import type {
  AttributeValues, MasterState, OperatingMode, OutputHealth, PartyStatus, PlaybackStatus, PresetRefs,
} from './types';

const SEND_INTERVAL_MS = 33;
/** Incoming snapshots are overlaid with local edits for this long, so faders do not jump back. */
const LOCAL_EDIT_HOLD_MS = 300;
/**
 * The server sends the health status every second. Without any message for this long the connection is
 * considered dead (e.g. a tablet that lost WiFi while the socket still looks open) and is reopened, so commands
 * like GO fall back to REST instead of getting lost.
 */
const SILENCE_LIMIT_MS = 3500;

/**
 * Live connection to the server: receives programmer and master state and sends fast control changes via
 * WebSocket. Falls back to REST while the socket is down.
 */
class LiveState {
  connected = $state(false);
  programmer = $state<Record<number, AttributeValues>>({});
  /** presets the programmer values come from */
  programmerPresets = $state<PresetRefs>({});
  presetsRevision = $state(0);
  master = $state<MasterState>({ level: 1, blackout: false });
  /** Incremented when the server reports patch or project changes; views reload when it changes. */
  patchRevision = $state(0);
  projectsRevision = $state(0);
  scenesRevision = $state(0);
  nodesRevision = $state(0);
  accessRevision = $state(0);
  playback = $state<PlaybackStatus | null>(null);
  /** performance.now() when the playback status arrived, to interpolate fades locally */
  playbackReceivedAt = $state(0);
  mode = $state<OperatingMode>('setup');
  /** false until the server reported its mode, so a reload in show mode does not bounce to setup first */
  modeKnown = $state(false);
  /** attribute values as sent to the fixtures (before grand master), while a view watches 'output' */
  output = $state<Record<number, AttributeValues>>({});
  /** raw DMX per universe number, while a view watches 'dmx:<n>' */
  dmx = $state<Record<number, number[]>>({});
  libraryRevision = $state(0);
  compositionsRevision = $state(0);
  /** composition id → values of its on-screen preview, while a view watches 'preview:<id>' */
  previews = $state<Record<number, Record<number, AttributeValues>>>({});
  party = $state<PartyStatus | null>(null);
  /** performance.now() when the party status arrived, to run the beat indicator locally */
  partyReceivedAt = $state(0);
  partyButtonsRevision = $state(0);
  /** render loop and Art-Net status, pushed every second */
  health = $state<OutputHealth | null>(null);

  private socket: WebSocket | null = null;
  private retries = 0;
  /** fixture id → values not sent yet */
  private pending = new Map<number, AttributeValues>();
  private pendingMaster: number | null = null;
  private flushTimer: ReturnType<typeof setTimeout> | null = null;
  private localEdits = new Map<number, { values: AttributeValues; until: number }>();
  /** live output topics → number of views watching them */
  private watchers = new Map<string, number>();
  private lastMessageAt = 0;
  private silenceTimer: ReturnType<typeof setInterval> | null = null;

  connect() {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    const socket = new WebSocket(`${protocol}://${location.host}/ws`);
    this.socket = socket;
    socket.onopen = () => {
      if (this.socket !== socket) return;
      this.connected = true;
      this.lastMessageAt = Date.now();
      this.watchSilence();
      this.retries = 0;
      this.patchRevision++;
      this.projectsRevision++;
      this.scenesRevision++;
      this.nodesRevision++;
      this.accessRevision++;
      this.libraryRevision++;
      this.compositionsRevision++;
      this.partyButtonsRevision++;
      this.presetsRevision++;
      this.sendSubscriptions();
    };
    socket.onmessage = (event) => {
      if (this.socket !== socket) return;
      this.lastMessageAt = Date.now();
      this.receive(JSON.parse(event.data));
    };
    socket.onclose = () => {
      if (this.socket !== socket) return; // already given up by the silence watchdog
      this.connected = false;
      this.socket = null;
      const delay = Math.min(5000, 500 * 2 ** this.retries++);
      setTimeout(() => this.connect(), delay);
    };
  }

  /**
   * Receives live output while a view is shown: 'output' (values per fixture) or 'dmx:<universe number>'.
   * Returns a function that stops watching.
   */
  watch(topic: string): () => void {
    this.watchers.set(topic, (this.watchers.get(topic) ?? 0) + 1);
    this.sendSubscriptions();
    return () => {
      const count = (this.watchers.get(topic) ?? 1) - 1;
      if (count > 0) this.watchers.set(topic, count);
      else this.watchers.delete(topic);
      this.sendSubscriptions();
    };
  }

  private sendSubscriptions() {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ type: 'subscribe', topics: [...this.watchers.keys()] }));
    }
  }

  /** Gives up a socket that stays silent: close() of a dead connection may take long to report onclose. */
  private watchSilence() {
    if (this.silenceTimer !== null) return;
    this.silenceTimer = setInterval(() => {
      const socket = this.socket;
      if (socket?.readyState !== WebSocket.OPEN || Date.now() - this.lastMessageAt < SILENCE_LIMIT_MS) return;
      this.socket = null;
      this.connected = false;
      socket.close();
      this.connect();
    }, 1000);
  }

  /** Reconnects, e.g. after unlocking setup, so the server sees the new session. */
  reconnect() {
    this.retries = 0;
    this.socket?.close();
  }

  private receive(message: { type: string; [key: string]: unknown }) {
    switch (message.type) {
      case 'programmer': {
        const values = message.values as Record<number, AttributeValues>;
        const now = Date.now();
        for (const [id, edit] of this.localEdits) {
          if (edit.until < now) this.localEdits.delete(id);
          else values[id] = { ...values[id], ...edit.values };
        }
        this.programmer = values;
        this.programmerPresets = (message.presets as PresetRefs | undefined) ?? {};
        break;
      }
      case 'presets':
        this.presetsRevision++;
        break;
      case 'master':
        if (this.pendingMaster === null) {
          this.master = { level: message.level as number, blackout: message.blackout as boolean };
        }
        break;
      case 'patch':
        this.patchRevision++;
        break;
      case 'projects':
        this.projectsRevision++;
        break;
      case 'scenes':
        this.scenesRevision++;
        break;
      case 'nodes':
        this.nodesRevision++;
        break;
      case 'playback':
        this.playback = message.status as PlaybackStatus;
        this.playbackReceivedAt = performance.now();
        break;
      case 'party':
        this.updateParty(message.status as PartyStatus);
        break;
      case 'partyButtons':
        this.partyButtonsRevision++;
        break;
      case 'mode':
        this.mode = message.mode as OperatingMode;
        this.modeKnown = true;
        break;
      case 'output':
        this.output = message.values as Record<number, AttributeValues>;
        break;
      case 'dmx':
        this.dmx = { ...this.dmx, [message.universe as number]: message.values as number[] };
        break;
      case 'preview':
        this.previews = {
          ...this.previews,
          [message.compositionId as number]: message.values as Record<number, AttributeValues>,
        };
        break;
      case 'compositions':
        this.compositionsRevision++;
        break;
      case 'library':
        this.libraryRevision++;
        break;
      case 'access':
        this.accessRevision++;
        break;
      case 'health':
        this.health = message.health as OutputHealth;
        break;
      case 'error':
        showError(new Error(String(message.message)));
        break;
    }
  }

  /** Sets values for fixtures (each receiving only its supported attributes, see `perFixture`). */
  setValues(perFixture: Record<number, AttributeValues>) {
    const until = Date.now() + LOCAL_EDIT_HOLD_MS;
    const next = { ...this.programmer };
    for (const [key, values] of Object.entries(perFixture)) {
      const id = Number(key);
      next[id] = { ...next[id], ...values };
      this.localEdits.set(id, { values: { ...this.localEdits.get(id)?.values, ...values }, until });
    }
    this.programmer = next;

    for (const [key, values] of Object.entries(perFixture)) {
      const id = Number(key);
      this.pending.set(id, { ...this.pending.get(id), ...values });
    }
    this.scheduleFlush();
  }

  setMasterLevel(level: number) {
    this.master = { ...this.master, level };
    this.pendingMaster = level;
    this.scheduleFlush();
  }

  async setBlackout(active: boolean) {
    this.master = { ...this.master, blackout: active };
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ type: 'blackout.set', active }));
    } else {
      await api.output.setBlackout(active).catch(showError);
    }
  }

  /** Playback commands go over the socket for the lowest latency, REST as fallback. */
  playbackCommand(command: 'go' | 'back' | 'pause' | 'finish') {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ type: `playback.${command}` }));
    } else {
      api.playback[command]()
        .then((status) => {
          this.playback = status;
          this.playbackReceivedAt = performance.now();
        })
        .catch(showError);
    }
  }

  /**
   * Highlights a sentence of the script of the scene on stage for all clients. Shown here at once, so fast key
   * presses do not step from an old position.
   */
  scriptCommand(sceneId: number, line: number) {
    if (this.playback?.currentSceneId === sceneId) this.playback = { ...this.playback, scriptLine: line };
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ type: 'playback.script', sceneId, line }));
    } else {
      api.playback.setScriptLine(sceneId, line)
        .then((status) => {
          this.playback = status;
          this.playbackReceivedAt = performance.now();
        })
        .catch(showError);
    }
  }

  /** Extra key pressed or released; via WebSocket for low latency, REST as fallback. */
  keyCommand(slot: number, action: 'press' | 'release') {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ type: `keys.${action}`, slot }));
    } else {
      (action === 'press' ? api.playback.pressKey(slot) : api.playback.releaseKey(slot))
        .then((status) => {
          this.playback = status;
          this.playbackReceivedAt = performance.now();
        })
        .catch(showError);
    }
  }

  /** Takes over a party status, e.g. the answer to a party command. */
  updateParty(status: PartyStatus) {
    this.party = status;
    this.partyReceivedAt = performance.now();
  }

  async setMode(mode: OperatingMode) {
    try {
      this.mode = (await api.mode.set(mode)).mode;
    } catch (e) {
      showError(e);
    }
  }

  async clear(fixtureIds: number[]) {
    const next = { ...this.programmer };
    fixtureIds.forEach((id) => {
      delete next[id];
      this.localEdits.delete(id);
    });
    this.programmer = next;
    await api.programmer.clearFixtures(fixtureIds).catch(showError);
  }

  private scheduleFlush() {
    if (this.flushTimer === null) {
      this.flushTimer = setTimeout(() => this.flush(), SEND_INTERVAL_MS);
    }
  }

  private flush() {
    this.flushTimer = null;
    const commands = groupByValues(this.pending);
    const masterLevel = this.pendingMaster;
    this.pending.clear();
    this.pendingMaster = null;
    const open = this.socket?.readyState === WebSocket.OPEN;

    for (const command of commands) {
      if (open) {
        this.socket!.send(JSON.stringify({ type: 'programmer.set', ...command }));
      } else {
        api.programmer.setSelection(command.fixtureIds, command.values).catch(showError);
      }
    }
    if (masterLevel !== null) {
      if (open) this.socket!.send(JSON.stringify({ type: 'master.set', level: masterLevel }));
      else api.output.setMaster(masterLevel).catch(showError);
    }
  }
}

export const live = new LiveState();
