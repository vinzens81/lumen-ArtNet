import { api } from './api';
import { showError } from './toasts.svelte';
import type { FixtureType, LibraryIssue, Project } from './types';

/** Application-wide state: projects and fixture library. */
class AppState {
  projects = $state<Project[]>([]);
  library = $state<FixtureType[]>([]);
  libraryIssues = $state<LibraryIssue[]>([]);
  loaded = $state(false);
  online = $state(true);

  active = $derived(this.projects.find((p) => p.active) ?? null);

  async load() {
    try {
      const [projects, library, issues] = await Promise.all([
        api.projects.list(),
        api.library.fixtureTypes(),
        api.library.issues(),
      ]);
      this.projects = projects;
      this.library = library;
      this.libraryIssues = issues;
    } catch (e) {
      showError(e);
    } finally {
      this.loaded = true;
    }
  }

  async reloadLibrary() {
    try {
      const [library, issues] = await Promise.all([api.library.fixtureTypes(), api.library.issues()]);
      this.library = library;
      this.libraryIssues = issues;
    } catch (e) {
      showError(e);
    }
  }

  async reloadProjects() {
    try {
      this.projects = await api.projects.list();
    } catch (e) {
      showError(e);
    }
  }

  async activate(id: number) {
    try {
      await api.projects.activate(id);
      await this.reloadProjects();
    } catch (e) {
      showError(e);
    }
  }

  fixtureType(id: string): FixtureType | undefined {
    return this.library.find((t) => t.id === id);
  }

  async checkHealth() {
    try {
      await api.health();
      this.online = true;
    } catch {
      this.online = false;
    }
  }
}

export const app = new AppState();
