import { api } from './api';
import { showError } from './toasts.svelte';
import type { StagePreviewStatus } from './types';

/**
 * What runs on the real stage for trying it out in setup mode: one composition, one move, a scene or one part of it
 * after a light marker, or a fade from the light on stage into one of them. Server state – every browser shows the same; reloaded when the server reports composition changes.
 */
class StagePreviewState {
  status = $state<StagePreviewStatus>({ compositionId: null, moveId: null, sceneId: null, marker: null,
    fade: null });
  /** performance.now() when the status arrived, to interpolate the fade */
  receivedAt = $state(0);
  active = $derived(this.status.compositionId !== null || this.status.moveId !== null
    || this.status.sceneId !== null);

  async load() {
    try {
      this.update(await api.stagePreview.status());
    } catch {
      // not essential: the status is fetched again with the next change
    }
  }

  /** Plays the start of the scene, or with a marker the part after it. */
  async showScene(projectId: number, sceneId: number, marker: string | null = null) {
    await this.run(api.stagePreview.showScene(projectId, sceneId, marker));
  }

  async showComposition(projectId: number, compositionId: number) {
    await this.run(api.stagePreview.showComposition(projectId, compositionId));
  }

  /** Plays a move on stage: its heads follow the figure. */
  async showMove(projectId: number, moveId: number) {
    await this.run(api.stagePreview.showMove(projectId, moveId));
  }

  /**
   * Fades from the light on stage into the scene with the fade times of the GO into it – or with a marker into the
   * part after it, with the times of passing the marker.
   */
  async fadeIn(projectId: number, sceneId: number, marker: string | null = null) {
    await this.run(api.stagePreview.fadeIn(projectId, sceneId, marker));
  }

  async stop() {
    await this.run(api.stagePreview.stop());
  }

  private async run(request: Promise<StagePreviewStatus>) {
    try {
      this.update(await request);
    } catch (e) {
      showError(e);
    }
  }

  private update(status: StagePreviewStatus) {
    this.status = status;
    this.receivedAt = performance.now();
  }
}

export const stagePreview = new StagePreviewState();
