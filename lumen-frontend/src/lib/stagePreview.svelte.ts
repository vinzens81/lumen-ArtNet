import { api } from './api';
import { showError } from './toasts.svelte';
import type { StagePreviewStatus } from './types';

/**
 * What runs on the real stage for trying it out in setup mode: one composition, a whole scene or a rehearsed fade
 * into a scene. Server state – every browser shows the same; reloaded when the server reports composition changes.
 */
class StagePreviewState {
  status = $state<StagePreviewStatus>({ compositionId: null, sceneId: null, fade: null });
  /** performance.now() when the status arrived, to interpolate the fade */
  receivedAt = $state(0);
  active = $derived(this.status.compositionId !== null || this.status.sceneId !== null);

  async load() {
    try {
      this.update(await api.stagePreview.status());
    } catch {
      // not essential: the status is fetched again with the next change
    }
  }

  async showScene(projectId: number, sceneId: number) {
    await this.run(api.stagePreview.showScene(projectId, sceneId));
  }

  async showComposition(projectId: number, compositionId: number) {
    await this.run(api.stagePreview.showComposition(projectId, compositionId));
  }

  /** Plays the GO into the scene from the scene before it, with the fade times of the show. */
  async rehearseFade(projectId: number, sceneId: number) {
    await this.run(api.stagePreview.rehearseFade(projectId, sceneId));
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
