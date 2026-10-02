import { api } from './api';
import { showError } from './toasts.svelte';
import type { AttributeValues, MasterState, OperatingMode, OutputHealth, PlaybackStatus } from './types';

const SEND_INTERVAL_MS = 33;
/** Incoming snapshots are overlaid with local edits for this long, so faders do not jump back. */
const LOCAL_EDIT_HOLD_MS = 300;

type Pending = { fixtureIds: number[]; values: AttributeValues };

/**
 * Live connection to the server: receives programmer and master state and sends fast control changes via
 * WebSocket. Falls back to REST while the socket is down.
 */
class LiveState {
  connected = $state(false);
  programmer = $state<Record<number, AttributeValues>>({});
  master = $state<MasterState>({ level: 1, blackout: false });
  /** Incremented when the server reports patch or project changes; views reload when it changes. */
  patchRevision = $state(0);
  projectsRevision = $state(0);
  scenesRevision = $state(0);
  nodesRevision = $state(0);
  playback = $state<PlaybackStatus | null>(null);
  /** performance.now() when the playback status arrived, to interpolate fades locally */
  playbackReceivedAt = $state(0);
  mode = $state<OperatingMode>('setup');
  /** false until the server reported its mode, so a reload in show mode does not bounce to setup first */
  modeKnown = $state(false);
  /** render loop and Art-Net status, pushed every second */
  health = $state<OutputHealth | null>(null);

  private socket: WebSocket | null = null;
  private retries = 0;
  private pending = new Map<string, Pending>();
  private pendingMaster: number | null = null;
  private flushTimer: ReturnType<typeof setTimeout> | null = null;
  private localEdits = new Map<number, { values: AttributeValues; until: number }>();

  connect() {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    const socket = new WebSocket(`${protocol}://${location.host}/ws`);
    this.socket = socket;
    socket.onopen = () => {
      this.connected = true;
      this.retries = 0;
      this.patchRevision++;
      this.projectsRevision++;
      this.scenesRevision++;
      this.nodesRevision++;
    };
    socket.onmessage = (event) => this.receive(JSON.parse(event.data));
    socket.onclose = () => {
      this.connected = false;
      this.socket = null;
      const delay = Math.min(5000, 500 * 2 ** this.retries++);
      setTimeout(() => this.connect(), delay);
    };
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
        break;
      }
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
      case 'mode':
        this.mode = message.mode as OperatingMode;
        this.modeKnown = true;
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

    // group fixtures with identical values into one command
    for (const [key, values] of Object.entries(perFixture)) {
      const signature = JSON.stringify(Object.keys(values).sort());
      const entry = this.pending.get(signature) ?? { fixtureIds: [], values: {} };
      if (!entry.fixtureIds.includes(Number(key))) entry.fixtureIds.push(Number(key));
      Object.assign(entry.values, values);
      this.pending.set(signature, entry);
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
    const commands = [...this.pending.values()];
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
