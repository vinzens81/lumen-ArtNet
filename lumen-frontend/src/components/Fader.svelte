<script lang="ts">
  let {
    label,
    value,
    onchange,
    color = 'var(--accent)',
    mixed = false,
  }: {
    label: string;
    value: number;
    onchange: (value: number) => void;
    color?: string;
    /** The selected fixtures have different values. */
    mixed?: boolean;
  } = $props();

  const percent = $derived(Math.round(value * 100));
</script>

<label class="fader" style="--fill: {percent}%; --color: {color}">
  <span class="head">
    <span class="label">{label}</span>
    <span class="value mono">{mixed ? '—' : `${percent}%`}</span>
  </span>
  <input
    type="range"
    min="0"
    max="1"
    step="0.001"
    {value}
    oninput={(e) => onchange(Number(e.currentTarget.value))}
  />
</label>

<style>
  .fader {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .head {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
  }

  .label {
    color: var(--text-dim);
  }

  input {
    width: 100%;
    height: 28px;
    margin: 0;
    background: transparent;
    appearance: none;
    cursor: pointer;
  }

  input::-webkit-slider-runnable-track {
    height: 10px;
    border-radius: 999px;
    background: linear-gradient(90deg, var(--color) var(--fill), rgba(255, 255, 255, 0.08) var(--fill));
    box-shadow: 0 0 14px color-mix(in srgb, var(--color) calc(var(--fill) * 0.6), transparent);
  }

  input::-moz-range-track {
    height: 10px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.08);
  }

  input::-moz-range-progress {
    height: 10px;
    border-radius: 999px;
    background: var(--color);
  }

  input::-webkit-slider-thumb {
    appearance: none;
    width: 26px;
    height: 26px;
    margin-top: -8px;
    border-radius: 50%;
    background: #fff;
    border: 3px solid var(--color);
    box-shadow: 0 0 12px var(--color);
  }

  input::-moz-range-thumb {
    width: 20px;
    height: 20px;
    border-radius: 50%;
    background: #fff;
    border: 3px solid var(--color);
  }

  input:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: 4px;
    border-radius: 999px;
  }
</style>
