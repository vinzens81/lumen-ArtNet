<script lang="ts">
  import { live } from '../lib/live.svelte';
</script>

<footer class="master glass" class:blackout={live.master.blackout}>
  <label class="level">
    <span class="title">Master</span>
    <input
      type="range"
      min="0"
      max="1"
      step="0.001"
      value={live.master.level}
      oninput={(e) => live.setMasterLevel(Number(e.currentTarget.value))}
      style="--fill: {live.master.level * 100}%"
    />
    <span class="mono value">{Math.round(live.master.level * 100)}%</span>
  </label>

  <span class="link" class:off={!live.connected} title={live.connected ? 'Live sync connected' : 'Live sync reconnecting…'}>
    <span class="dot"></span>{live.connected ? 'Live' : 'Reconnecting'}
  </span>

  <button
    type="button"
    class="blackout-btn"
    aria-pressed={live.master.blackout}
    title="Blackout (B)"
    onclick={() => live.setBlackout(!live.master.blackout)}
  >
    {live.master.blackout ? 'BLACKOUT ON' : 'BLACKOUT'}
  </button>
</footer>

<style>
  .master {
    position: sticky;
    bottom: 12px;
    z-index: 10;
    display: flex;
    align-items: center;
    gap: 20px;
    padding: 10px 14px;
  }

  .level {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 14px;
    max-width: 640px;
  }

  .title {
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.14em;
    text-transform: uppercase;
    color: var(--text-dim);
  }

  .value {
    width: 44px;
    text-align: right;
  }

  input {
    flex: 1;
    height: 28px;
    margin: 0;
    background: transparent;
    appearance: none;
    cursor: pointer;
  }

  input::-webkit-slider-runnable-track {
    height: 8px;
    border-radius: 999px;
    background: linear-gradient(90deg, var(--accent) var(--fill), rgba(255, 255, 255, 0.08) var(--fill));
  }

  input::-moz-range-progress {
    height: 8px;
    border-radius: 999px;
    background: var(--accent);
  }

  input::-webkit-slider-thumb {
    appearance: none;
    width: 22px;
    height: 22px;
    margin-top: -7px;
    border-radius: 50%;
    background: #fff;
    border: 3px solid var(--accent);
    box-shadow: 0 0 10px var(--accent-glow);
  }

  .link {
    display: flex;
    align-items: center;
    gap: 7px;
    margin-left: auto;
    font-size: 12px;
    color: var(--text-dim);
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--ok);
    box-shadow: 0 0 8px var(--ok);
  }

  .link.off .dot {
    background: var(--warning);
    box-shadow: 0 0 8px var(--warning);
  }

  .blackout-btn {
    min-height: 42px;
    padding: 0 22px;
    border-radius: var(--radius-sm);
    border: 2px solid var(--danger);
    background: transparent;
    color: var(--danger);
    font: inherit;
    font-weight: 800;
    letter-spacing: 0.14em;
    cursor: pointer;
  }

  .blackout-btn:hover {
    background: var(--danger-soft);
  }

  .blackout .blackout-btn {
    background: var(--danger);
    color: #fff;
    box-shadow: 0 0 24px var(--danger);
    animation: pulse 1s ease-in-out infinite alternate;
  }

  @keyframes pulse {
    to {
      box-shadow: 0 0 6px var(--danger);
    }
  }
</style>
