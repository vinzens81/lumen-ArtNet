import { api } from './api';
import { live } from './live.svelte';
import { app } from './state.svelte';
import { showError } from './toasts.svelte';
import type { Scene } from './types';

/**
 * The scenes of the active project for the view-only pages, reloaded when they change; empty without an active
 * project, so no scenes of a previous project linger. Call it while a component is created.
 */
export function watchScenes(): { readonly list: Scene[] } {
  let list = $state<Scene[]>([]);
  $effect(() => {
    void live.scenesRevision;
    const projectId = app.active?.id ?? null;
    if (projectId === null) {
      list = [];
      return;
    }
    let stale = false;
    api.scenes.list(projectId).then((scenes) => {
      if (!stale) list = scenes;
    }, showError);
    return () => {
      stale = true;
    };
  });
  return {
    get list() {
      return list;
    },
  };
}
