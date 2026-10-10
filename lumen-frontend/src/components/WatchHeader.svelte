<script lang="ts">
  import type { Snippet } from 'svelte';
  import { live } from '../lib/live.svelte';
  import { app } from '../lib/state.svelte';
  import type { WatchCaption } from '../lib/watch';

  /** Header of the view-only pages: what is on stage, blackout, connection, the other view-only page and the way out. */
  let {
    caption,
    other,
    leave,
    children,
  }: {
    caption: WatchCaption;
    /** the other view-only page */
    other: { href: string; label: string; title: string };
    /** where ✕ leads back into the app */
    leave: string;
    /** extra badges and controls of the page */
    children?: Snippet;
  } = $props();
</script>

<header class="bar glass">
  <span class="orb" aria-hidden="true"></span>
  <div class="what">
    <span class="project dim small">{app.active?.name ?? 'No active project'} · {live.mode} mode</span>
    <strong class="title">{caption.title}</strong>
    {#if caption.part}<span class="part">💡 {caption.part}</span>{/if}
  </div>
  {#if live.master.blackout}<span class="badge danger">Blackout</span>{/if}
  {@render children?.()}
  <span class="badge" class:off={!live.connected} title={live.connected ? 'Live' : 'Connection lost – reconnecting'}>
    <span class="dot"></span>{live.connected ? 'Live' : 'Offline'}
  </span>
  <a class="link" href={other.href} title={other.title}>{other.label}</a>
  <a class="link" href={leave} title="Leave the view-only page">✕</a>
</header>

<style>
  .bar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px 12px;
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

  .link {
    padding: 4px 8px;
    border-radius: var(--radius-sm);
    color: var(--text-dim);
    font-size: 13px;
    text-decoration: none;
    white-space: nowrap;
  }

  .link:hover {
    color: var(--text);
    background: var(--panel-raised);
  }
</style>
