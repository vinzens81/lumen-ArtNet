<script lang="ts">
  /** How far the scene on stage is in its script: "3 / 12", the bar behind the count fills with every stop. */
  let { index, count }: {
    /** the stop the show is at, from 0 */
    index: number;
    /** speeches, pauses and light markers of the script */
    count: number;
  } = $props();
</script>

<span class="speech-progress mono" title="Step {index + 1} of {count}" style="--done: {((index + 1) / count) * 100}%">
  {index + 1} / {count}
</span>

<style>
  .speech-progress {
    display: inline-block;
    flex: none;
    min-width: 140px;
    padding: 3px 10px;
    border: 1px solid color-mix(in srgb, var(--warning) 50%, transparent);
    border-radius: 999px;
    background: linear-gradient(90deg, color-mix(in srgb, var(--warning) 45%, transparent) var(--done),
      transparent var(--done));
    color: var(--text);
    font-size: 13px;
    text-align: center;
    transition: background 0.3s;
  }
</style>
