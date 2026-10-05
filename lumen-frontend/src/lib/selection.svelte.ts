/**
 * Fixtures selected in the programmer. Shared, so the scene editor can merge only the selected fixtures; it lives in
 * this browser only.
 */
export const programmerSelection = $state<{ ids: number[] }>({ ids: [] });
