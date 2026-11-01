// Sync generated site content from the canonical em artifacts.
//
// Sources (canonical, never edited here):
//   ../slices/*.md + *.svg   — ratified slice docs
//   ../toolshed.svg          — full model diagram
//
// Outputs (generated, gitignored):
//   src/content/docs/slices/*.md       — slice docs with a Starlight `title` injected
//   src/content/docs/invariants.md     — cross-slice invariant index
//   public/toolshed.svg                — model diagram for the viewer page

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, '..', '..');
const slicesSrc = path.join(repoRoot, 'slices');
const docsDir = path.resolve(here, '..', 'src', 'content', 'docs');
const outDir = path.join(docsDir, 'slices');
const publicDir = path.resolve(here, '..', 'public');

const INV_DEF_RE = /^- \*\*(INV-[A-Z0-9]+-\d+):\*\*\s*(.+)$/gm;
const INV_REF_RE = /INV-[A-Z0-9]+-\d+/g;

fs.rmSync(outDir, { recursive: true, force: true });
fs.mkdirSync(outDir, { recursive: true });
fs.mkdirSync(publicDir, { recursive: true });
fs.copyFileSync(path.join(repoRoot, 'toolshed.svg'), path.join(publicDir, 'toolshed.svg'));

/** @type {Map<string, {text: string, sliceKey: string}>} */
const definitions = new Map();
/** @type {Map<string, Set<string>>} id -> slice keys that mention it */
const references = new Map();
/** @type {Map<string, string>} slice key -> human title */
const sliceTitles = new Map();

const mdFiles = fs
  .readdirSync(slicesSrc)
  .filter((f) => f.endsWith('.md'))
  .sort();

for (const file of mdFiles) {
  const key = file.replace(/\.md$/, '');
  const raw = fs.readFileSync(path.join(slicesSrc, file), 'utf8');

  const fmMatch = raw.match(/^---\n([\s\S]*?)\n---\n/);
  const frontmatter = fmMatch ? fmMatch[1] : '';
  let body = fmMatch ? raw.slice(fmMatch[0].length) : raw;

  const h1 = body.match(/^# Slice: (.+)$/m);
  const title = h1 ? h1[1].trim() : key;
  if (h1) body = body.replace(h1[0], '').replace(/^\n+/, '');
  sliceTitles.set(key, title);

  for (const m of body.matchAll(INV_DEF_RE)) {
    if (!definitions.has(m[1])) definitions.set(m[1], { text: m[2].trim(), sliceKey: key });
  }
  for (const m of body.matchAll(INV_REF_RE)) {
    if (!references.has(m[0])) references.set(m[0], new Set());
    references.get(m[0]).add(key);
  }

  const fm = `title: ${JSON.stringify(title)}\n${frontmatter}`;
  fs.writeFileSync(path.join(outDir, file), `---\n${fm}\n---\n\n${body}`);
}

for (const file of fs.readdirSync(slicesSrc).filter((f) => f.endsWith('.svg'))) {
  fs.copyFileSync(path.join(slicesSrc, file), path.join(outDir, file));
}

// Invariant index: one anchor per invariant (h4 -> #inv-xxx-n), grouped by owning slice.
const bySlice = new Map();
for (const [id, def] of definitions) {
  if (!bySlice.has(def.sliceKey)) bySlice.set(def.sliceKey, []);
  bySlice.get(def.sliceKey).push({ id, ...def });
}

const numeric = (id) => Number(id.match(/(\d+)$/)[1]);
let page = `---
title: Invariant Index
description: Every business rule in the toolshed model, grouped by the slice that defines it.
---

${definitions.size} invariants across ${bySlice.size} slices. Every \`INV-*\` mention
anywhere on this site links back to its entry here.

`;

for (const key of [...bySlice.keys()].sort()) {
  const invs = bySlice.get(key).sort((a, b) => numeric(a.id) - numeric(b.id));
  page += `## ${sliceTitles.get(key)}\n\n`;
  for (const inv of invs) {
    const refs = [...(references.get(inv.id) ?? [])].filter((k) => k !== inv.sliceKey).sort();
    page += `#### ${inv.id}\n\n${inv.text}\n\n`;
    page += `Defined in [${sliceTitles.get(inv.sliceKey)}](/slices/${inv.sliceKey}/#invariants--business-rules)`;
    if (refs.length) {
      page += ` · referenced by ${refs
        .map((k) => `[${sliceTitles.get(k)}](/slices/${k}/)`)
        .join(', ')}`;
    }
    page += `\n\n`;
  }
}

fs.writeFileSync(path.join(docsDir, 'invariants.md'), page);

console.log(
  `synced ${mdFiles.length} slice docs, ${definitions.size} invariants from ${path.relative(process.cwd(), slicesSrc)}`
);
