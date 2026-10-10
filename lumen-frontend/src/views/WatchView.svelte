<script lang="ts">
  import WatchHeader from '../components/WatchHeader.svelte';
  import { live } from '../lib/live.svelte';
  import { watchCaption } from '../lib/watch';
  import { watchScenes } from '../lib/watchScenes.svelte';
  import StageView from './StageView.svelte';

  /** The stage as it glows right now, for phones and tablets: nothing here changes the light. */

  const scenes = watchScenes();
  const caption = $derived(watchCaption(scenes.list, live.playback));
</script>

<section class="watch">
  <WatchHeader {caption} leave="#/setup/stage"
    other={{ href: '#/view/script', label: '📜 Script', title: 'Follow the script, view only' }} />

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

  .stage {
    padding: 8px;
  }

  .hint {
    margin: 0;
    text-align: center;
  }
</style>
