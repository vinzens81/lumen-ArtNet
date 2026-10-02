<script lang="ts">
  import { hsvToRgb, rgbToCss } from '../lib/color';

  let {
    hue,
    saturation,
    onchange,
  }: { hue: number; saturation: number; onchange: (hue: number, saturation: number) => void } = $props();

  let wheel: HTMLDivElement | undefined = $state();
  let dragging = false;

  const handleX = $derived(50 + Math.sin(hue * 2 * Math.PI) * saturation * 50);
  const handleY = $derived(50 - Math.cos(hue * 2 * Math.PI) * saturation * 50);
  const handleColor = $derived(rgbToCss(hsvToRgb(hue, saturation, 1)));

  function pick(event: PointerEvent) {
    if (!wheel) return;
    const rect = wheel.getBoundingClientRect();
    const dx = event.clientX - (rect.left + rect.width / 2);
    const dy = event.clientY - (rect.top + rect.height / 2);
    const radius = Math.min(1, Math.hypot(dx, dy) / (rect.width / 2));
    let angle = Math.atan2(dx, -dy) / (2 * Math.PI);
    if (angle < 0) angle += 1;
    onchange(angle, radius);
  }

  function onkeydown(event: KeyboardEvent) {
    const step = event.shiftKey ? 0.05 : 0.01;
    const moves: Record<string, [number, number]> = {
      ArrowLeft: [-step, 0],
      ArrowRight: [step, 0],
      ArrowUp: [0, step],
      ArrowDown: [0, -step],
    };
    const move = moves[event.key];
    if (!move) return;
    event.preventDefault();
    onchange((hue + move[0] + 1) % 1, Math.min(1, Math.max(0, saturation + move[1])));
  }
</script>

<div
  bind:this={wheel}
  class="wheel"
  role="slider"
  tabindex="0"
  aria-label="Color: arrow keys change hue (left/right) and saturation (up/down)"
  aria-valuemin={0}
  aria-valuemax={360}
  aria-valuenow={Math.round(hue * 360)}
  onpointerdown={(e) => { dragging = true; wheel?.setPointerCapture(e.pointerId); pick(e); }}
  onpointermove={(e) => dragging && pick(e)}
  onpointerup={() => (dragging = false)}
  onpointercancel={() => (dragging = false)}
  {onkeydown}
>
  <span class="handle" style="left: {handleX}%; top: {handleY}%; background: {handleColor}"></span>
</div>

<style>
  .wheel {
    position: relative;
    width: 100%;
    max-width: 240px;
    aspect-ratio: 1;
    margin: 0 auto;
    border-radius: 50%;
    background:
      radial-gradient(circle closest-side, #fff, rgba(255, 255, 255, 0) 100%),
      conic-gradient(red, yellow, lime, cyan, blue, magenta, red);
    box-shadow: 0 0 40px rgba(255, 255, 255, 0.08), inset 0 0 0 1px rgba(255, 255, 255, 0.15);
    touch-action: none;
    cursor: crosshair;
  }

  .wheel:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: 4px;
  }

  .handle {
    position: absolute;
    width: 26px;
    height: 26px;
    border-radius: 50%;
    border: 3px solid #fff;
    transform: translate(-50%, -50%);
    box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.4), 0 0 16px rgba(255, 255, 255, 0.6);
    pointer-events: none;
  }
</style>
