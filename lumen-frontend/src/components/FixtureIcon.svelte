<script lang="ts">
  let { category, size = 20 }: { category: string; size?: number } = $props();
</script>

<svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"
  stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
  {#if category === 'moving-head' || category === 'scanner'}
    <rect x="7" y="3" width="10" height="11" rx="3" />
    <circle cx="12" cy="8.5" r="2.2" />
    <path d="M5 21h14M9 14v4M15 14v4" />
  {:else if category === 'bar'}
    <rect x="2" y="9" width="20" height="6" rx="2" />
    <path d="M6 12h.01M10 12h.01M14 12h.01M18 12h.01" />
  {:else if category === 'dimmer'}
    <path d="M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3Z" />
  {:else if category === 'strobe'}
    <path d="M13 2 4 14h7l-1 8 9-12h-7l1-8Z" />
  {:else if category === 'fog'}
    <path d="M4 15a4 4 0 0 1 3-6.9A5 5 0 0 1 16.5 7 4 4 0 0 1 20 14M5 19h14M8 22h8" />
  {:else if category === 'par' || category === 'fresnel' || category === 'profile'}
    <circle cx="12" cy="12" r="6" />
    <circle cx="12" cy="12" r="2.4" />
    <path d="M12 2v2M12 20v2M2 12h2M20 12h2" />
  {:else}
    <circle cx="12" cy="12" r="7" />
  {/if}
</svg>
