<script lang="ts">
  import { api } from '../lib/api';
  import { live } from '../lib/live.svelte';
  import { app } from '../lib/state.svelte';
  import { showError } from '../lib/toasts.svelte';
  import type { Scene } from '../lib/types';
  import { watchCaption } from '../lib/watch';
  import StageView from './StageView.svelte';

  /** The stage as it glows right now, for phones and tablets: nothing here changes the light. */

  let scenes = $state<Scene[]>([]);
  const projectId = $derived(app.active?.id ?? null);
  const caption = $derived(watchCaption(scenes, live.playback));
  const blackout = $derived(live.master.blackout);

  $effect(() => {
    void live.scenesRevision;
    if (projectId !== null) load(projectId);
  });

  async function load(id: number) {
    try {
      scenes = await api.scenes.list(id);
    } catch (e) {
      showError(e);
    }
  }
</script>

<section class="watch">
  <header class="bar glass">
    <span class="orb" aria-hidden="true"></span>
    <div class="what">
      <span class="project dim small">{app.active?.name ?? 'No active project'} · {live.mode} mode</span>
      <strong class="title">{caption.title}</strong>
      {#if caption.part}<span class="part">💡 {caption.part}</span>{/if}
    </div>
    {#if blackout}<span class="badge danger">Blackout</span>{/if}
    <span class="badge" class:off={!live.connected} title={live.connected ? 'Live' : 'Connection lost – reconnecting'}>
      <span class="dot"></span>{live.connected ? 'Live' : 'Offline'}
    </span>
    <a class="leave" href="#/setup/stage" title="Leave the view-only stage">✕</a>
  </header>

  <div class="stage glass">
    <StageView embedded reservedHeight={110} />
  </div>
  <p class="dim small hint">View only – nothing here changes the light.</p>
</section>

<style>
  .watch {
    display: flex;
    flex-direction: column;
    gap: 10px;
    min-height: 100dvh;
    padding: 10px;
  }

  .bar {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 14px;
  }

  .orb {
    flex: none;
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: radial-gradient(circle, #fff 0 25%, var(--accent) 60%);
    box-shadow: 0 0 12px var(--accent-glow);
  }

  .what {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    column-gap: 10px;
  }

  .project {
    flex-basis: 100%;
    text-transform: none;
  }

  .title {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .part {
    color: var(--warning);
    font-size: 13px;
  }

  .badge {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 3px 9px;
    border-radius: 999px;
    border: 1px solid var(--border-strong);
    font-size: 12px;
    color: var(--text-dim);
  }

  .badge.danger {
    border-color: var(--danger);
    color: var(--danger);
    font-weight: 700;
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--ok);
    box-shadow: 0 0 8px var(--ok);
  }

  .badge.off .dot {
    background: var(--danger);
    box-shadow: 0 0 8px var(--danger);
  }

  .leave {
    padding: 4px 8px;
    border-radius: var(--radius-sm);
    color: var(--text-dim);
    text-decoration: none;
  }

  .leave:hover {
    color: var(--text);
    background: var(--panel-raised);
  }

  .stage {
    padding: 8px;
  }

  .hint {
    margin: 0;
    text-align: center;
  }
</style>
