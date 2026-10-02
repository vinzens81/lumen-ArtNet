<script lang="ts">
  let { title, text }: { title: string; text: string } = $props();
</script>

<section class="glass empty">
  <span class="badge">Coming soon</span>
  <h1>{title}</h1>
  <p>{text}</p>
</section>
