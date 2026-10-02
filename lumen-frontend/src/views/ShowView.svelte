<script lang="ts">
  import { app } from '../lib/state.svelte';
</script>

<section class="show">
  <div class="panel glass">
    <h3>Show</h3>
    <h1>{app.active?.name ?? 'No active project'}</h1>
    <p class="dim">Playback arrives together with scenes. Then this is where you press GO.</p>
    <button class="go" type="button" disabled>GO</button>
  </div>
</section>

<style>
  .show {
    flex: 1;
    display: grid;
    place-items: center;
  }

  .panel {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 14px;
    padding: 48px;
    text-align: center;
  }

  .go {
    margin-top: 12px;
    width: 220px;
    height: 120px;
    border-radius: 24px;
    border: 2px solid var(--accent);
    background: var(--accent-soft);
    color: var(--accent);
    font: inherit;
    font-size: 40px;
    font-weight: 800;
    letter-spacing: 0.2em;
    opacity: 0.4;
  }
</style>
