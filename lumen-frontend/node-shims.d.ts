// The one Node function vite.config.ts uses, so the build does not need @types/node.
declare module 'node:child_process' {
  export function execSync(command: string, options: { encoding: 'utf8' }): string;
}
