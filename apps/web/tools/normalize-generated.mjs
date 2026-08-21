import { readFile, writeFile } from 'node:fs/promises';

const generatedFile = new URL('../src/app/api/generated/graphql.ts', import.meta.url);
const source = await readFile(generatedFile, 'utf8');
const normalized = source.replace(/[\t ]+$/gm, '');

if (normalized !== source) {
  await writeFile(generatedFile, normalized, 'utf8');
}
