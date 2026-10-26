import { cp, mkdir, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const webDirectory = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repositoryDirectory = resolve(webDirectory, '..');
const libraryDirectory = resolve(repositoryDirectory, 'library');
const distributionDirectory = resolve(
  libraryDirectory,
  'build/compileSync/js/main/productionLibrary/kotlin',
);
const generatedDirectory = resolve(webDirectory, 'src/generated/puzzle-core');
const syncStatePath = resolve(libraryDirectory, 'build/puzzle-core-sync-state.json');
const entrypoint = resolve(distributionDirectory, 'small-wins-puzzle-core.mjs');

async function newestModified(path) {
  const metadata = await stat(path);
  if (!metadata.isDirectory()) return metadata.mtimeMs;
  const children = await readdir(path, { withFileTypes: true });
  const times = await Promise.all(
    children.map((child) => newestModified(resolve(path, child.name))),
  );
  return Math.max(metadata.mtimeMs, ...times);
}

async function exists(path) {
  try {
    await stat(path);
    return true;
  } catch {
    return false;
  }
}

const inputs = [
  resolve(libraryDirectory, 'build.gradle.kts'),
  resolve(libraryDirectory, 'settings.gradle.kts'),
  resolve(libraryDirectory, 'gradle.properties'),
  resolve(libraryDirectory, 'src/commonMain'),
  resolve(libraryDirectory, 'src/jsMain'),
];
const inputModified = Math.max(...(await Promise.all(inputs.map(newestModified))));
const previousInputModified = await readFile(syncStatePath, 'utf8')
  .then((value) => JSON.parse(value).inputModified)
  .catch(() => null);
const shouldBuild = !(await exists(entrypoint)) || previousInputModified !== inputModified;

if (shouldBuild) {
  const result = spawnSync('./gradlew', ['jsBrowserProductionLibraryDistribution'], {
    cwd: libraryDirectory,
    stdio: 'inherit',
  });
  if (result.status !== 0) {
    throw new Error(`Kotlin/JS build failed with status ${result.status ?? 'unknown'}`);
  }
  await mkdir(dirname(syncStatePath), { recursive: true });
  await writeFile(syncStatePath, `${JSON.stringify({ inputModified })}\n`);
}

await rm(generatedDirectory, { recursive: true, force: true });
await mkdir(generatedDirectory, { recursive: true });
await cp(distributionDirectory, generatedDirectory, { recursive: true });

for (const file of await readdir(generatedDirectory)) {
  if (file.endsWith('.mjs')) {
    const path = resolve(generatedDirectory, file);
    const source = await readFile(path, 'utf8');
    await writeFile(
      path,
      source.replace(/\n\/\/# sourceMappingURL=[^\r\n]*\r?\n?$/u, '\n'),
    );
  } else if (file.endsWith('.mjs.map')) {
    await rm(resolve(generatedDirectory, file));
  }
}
