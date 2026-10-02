<script lang="ts">
  let {
    x,
    y,
    onchange,
    label = 'Position',
  }: { x: number; y: number; onchange: (x: number, y: number) => void; label?: string } = $props();

  let pad: HTMLDivElement | undefined = $state();
  let dragging = false;

  function pick(event: PointerEvent) {
    if (!pad) return;
    const rect = pad.getBoundingClientRect();
    const nx = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
    const ny = Math.min(1, Math.max(0, 1 - (event.clientY - rect.top) / rect.height));
    onchange(nx, ny);
  }

  function onkeydown(event: KeyboardEvent) {
    const step = event.shiftKey ? 0.05 : 0.005;
    const moves: Record<string, [number, number]> = {
      ArrowLeft: [-step, 0],
      ArrowRight: [step, 0],
      ArrowUp: [0, step],
      ArrowDown: [0, -step],
    };
    const move = moves[event.key];
    if (!move) return;
    event.preventDefault();
    onchange(Math.min(1, Math.max(0, x + move[0])), Math.min(1, Math.max(0, y + move[1])));
  }
</script>

<div class="wrap">
  <div
    bind:this={pad}
    class="pad"
    role="slider"
    tabindex="0"
    aria-label="{label}: arrow keys move pan and tilt"
    aria-valuemin={0}
    aria-valuemax={100}
    aria-valuenow={Math.round(x * 100)}
    onpointerdown={(e) => { dragging = true; pad?.setPointerCapture(e.pointerId); pick(e); }}
    onpointermove={(e) => dragging && pick(e)}
    onpointerup={() => (dragging = false)}
    onpointercancel={() => (dragging = false)}
    {onkeydown}
  >
    <span class="line h" style="top: {(1 - y) * 100}%"></span>
    <span class="line v" style="left: {x * 100}%"></span>
    <span class="dot" style="left: {x * 100}%; top: {(1 - y) * 100}%"></span>
  </div>
  <div class="values mono dim">Pan {Math.round(x * 100)}% · Tilt {Math.round(y * 100)}%</div>
</div>

<style>
  .wrap {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .pad {
    position: relative;
    aspect-ratio: 1;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-strong);
    background:
      linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px) 0 0 / 12.5% 12.5%,
      linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px) 0 0 / 12.5% 12.5%,
      rgba(0, 0, 0, 0.3);
    overflow: hidden;
    touch-action: none;
    cursor: crosshair;
  }

  .pad:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: 3px;
  }

  .line {
    position: absolute;
    background: var(--accent-soft);
    pointer-events: none;
  }

  .line.h {
    left: 0;
    right: 0;
    height: 1px;
  }

  .line.v {
    top: 0;
    bottom: 0;
    width: 1px;
  }

  .dot {
    position: absolute;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: var(--accent);
    transform: translate(-50%, -50%);
    box-shadow: 0 0 16px var(--accent-glow), 0 0 4px #fff;
    pointer-events: none;
  }

  .values {
    font-size: 11px;
    text-align: right;
  }
</style>
