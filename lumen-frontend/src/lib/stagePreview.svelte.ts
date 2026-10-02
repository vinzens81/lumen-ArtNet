import { api } from './api';
import { showError } from './toasts.svelte';

export interface StagePreviewStatus {
  compositionId: number | null;
  sceneId: number | null;
}

/**
 * What runs on the real stage for trying it out in setup mode: one composition or a whole scene. Server state –
 * every browser shows the same; reloaded when the server reports composition changes.
 */
class StagePreviewState {
  status = $state<StagePreviewStatus>({ compositionId: null, sceneId: null });
  active = $derived(this.status.compositionId !== null || this.status.sceneId !== null);

  async load() {
    try {
      this.status = await api.stagePreview.status();
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

  async stop() {
    await this.run(api.stagePreview.stop());
  }

  private async run(request: Promise<StagePreviewStatus>) {
    try {
      this.status = await request;
    } catch (e) {
      showError(e);
    }
  }
}

export const stagePreview = new StagePreviewState();
