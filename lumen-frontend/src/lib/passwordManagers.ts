/**
 * Lumen has no credentials to fill in, but password managers take fields like "Name" for identities: 1Password puts
 * its icon into them and opens a menu while typing. Every input and text area is marked to be left alone – also the
 * ones that appear later.
 */
export function ignorePasswordManagers(root: ParentNode & Node): MutationObserver {
  markFields(root);
  const observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      mutation.addedNodes.forEach((node) => {
        if (node instanceof Element) markFields(node);
      });
    }
  });
  observer.observe(root, { childList: true, subtree: true });
  return observer;
}

export function markFields(root: ParentNode | Element) {
  const fields = [...root.querySelectorAll('input, textarea')];
  if (root instanceof HTMLInputElement || root instanceof HTMLTextAreaElement) fields.push(root);
  for (const field of fields) {
    field.setAttribute('data-1p-ignore', '');
    field.setAttribute('data-lpignore', 'true');
    if (!field.hasAttribute('autocomplete')) field.setAttribute('autocomplete', 'off');
  }
}
