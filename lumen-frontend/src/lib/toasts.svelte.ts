import { ApiError } from './api';

export interface Toast {
  id: number;
  kind: 'info' | 'error';
  message: string;
  details: string[];
}

let nextId = 1;

export const toasts = $state<{ items: Toast[] }>({ items: [] });

export function notify(message: string, kind: Toast['kind'] = 'info', details: string[] = []) {
  const toast = { id: nextId++, kind, message, details };
  toasts.items.push(toast);
  setTimeout(() => dismiss(toast.id), kind === 'error' ? 8000 : 3500);
}

export function dismiss(id: number) {
  toasts.items = toasts.items.filter((t) => t.id !== id);
}

/** Shows an error toast for a failed API call; returns false so callers can `return showError(e)`. */
export function showError(error: unknown): false {
  if (error instanceof ApiError) {
    notify(error.message, 'error', error.errors);
  } else {
    notify(String(error), 'error');
  }
  return false;
}
