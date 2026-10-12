<script lang="ts">
  let {
    folder,
    count,
    open,
    ontoggle,
  }: {
    folder: string;
    count: number;
    open: boolean;
    ontoggle: () => void;
  } = $props();
</script>

<!-- a folder of a list; it stays at the top of the list while its entries scroll by -->
<button type="button" class="folder" aria-expanded={open} onclick={ontoggle}>
  <span class="chevron" class:open aria-hidden="true">▸</span>
  <span aria-hidden="true">{open ? '📂' : '📁'}</span>
  <span class="name">{folder}</span>
  <span class="count">{count}</span>
</button>

<style>
  .folder {
    position: sticky;
    top: -8px;
    z-index: 1;
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 8px;
    padding: 8px 10px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    background: var(--panel-solid);
    color: var(--text);
    font: inherit;
    font-size: 13px;
    font-weight: 700;
    text-align: left;
    cursor: pointer;
  }

  .folder:first-child {
    margin-top: 0;
  }

  .folder:hover {
    color: var(--accent);
  }

  .chevron {
    transition: transform 0.15s;
  }

  .chevron.open {
    transform: rotate(90deg);
  }

  .name {
    flex: 1;
  }

  .count {
    min-width: 22px;
    padding: 1px 7px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.1);
    color: var(--text-dim);
    font-size: 11px;
    text-align: center;
  }
</style>
