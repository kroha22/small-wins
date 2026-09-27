import { readFileSync, readdirSync, existsSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
const root = new URL('../', import.meta.url);
const lock = JSON.parse(readFileSync(new URL('package-lock.json', root), 'utf8')) as {
  packages: Record<string, { version?: string; dev?: boolean }>;
};
const notices: string[] = [
  'Small Wins — dependency license notices\nGenerated from the installed production dependency tree.',
];
for (const [path, entry] of Object.entries(lock.packages)) {
  if (!path || entry.dev || !existsSync(new URL(path, root))) continue;
  const dir = new URL(`${path}/`, root);
  const pkg = JSON.parse(readFileSync(new URL('package.json', dir), 'utf8')) as {
    name: string;
    version: string;
    license?: string;
  };
  const files = readdirSync(dir).filter((name) => /^(licen[cs]e|copying|notice)(\.|$)/i.test(name));
  const upstream = new URL(`scripts/licenses/${pkg.name}-LICENSE.txt`, root);
  if (!files.length && !existsSync(upstream))
    throw new Error(`No license file for ${pkg.name}; inspect upstream notices before shipping.`);
  const text = files.length
    ? files.map((name) => readFileSync(new URL(join(name), dir), 'utf8')).join('\n')
    : readFileSync(upstream, 'utf8');
  notices.push(`\n${'='.repeat(72)}\n${pkg.name} ${pkg.version}\n${text}`);
}
writeFileSync(new URL('public/third-party-licenses.txt', root), notices.join('\n') + '\n');
console.log(`Collected notices for ${notices.length - 1} packages.`);
