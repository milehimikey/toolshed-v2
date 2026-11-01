# docs-site

A [Starlight](https://starlight.astro.build) site that renders the toolshed event model
for humans. It is a **derived view**: the canonical artifacts stay `toolshed.em` and
`slices/*.md`, exactly as `em` validates and CI gates them. Nothing here feeds back
into the model. Reference example for [em#396](https://github.com/milehimikey/em/issues/396).

```sh
npm install
npm run dev      # syncs slice docs, serves http://localhost:4321
npm run build    # syncs + builds to dist/
```

## How it works

| Piece | What it does |
|-------|--------------|
| `scripts/sync-slices.mjs` | Runs before dev/build. Copies `../slices/*.md` + `*.svg` into `src/content/docs/slices/` (injecting the `title` Starlight requires from each doc's H1), copies `../toolshed.svg` to `public/`, and generates `src/content/docs/invariants.md` — an index of every `INV-*` definition with its text, owning slice, and the slices that reference it. |
| `plugins/remark-inv-links.mjs` | Remark plugin: every `INV-XXX-N` mention in prose becomes a link to its anchor on the invariant index. |
| `src/content.config.ts` | Extends Starlight's schema with the em slice frontmatter (`pattern`, `status`, `ratifiedBy`, `implementedIn`, …). |
| `src/components/SliceDashboard.astro` | Status table over all slice frontmatter (used by `/status/`). |
| `src/components/ModelViewer.astro` | Pan/zoom viewer for the full model SVG (used by `/model/`). |

Generated content (`src/content/docs/slices/`, `src/content/docs/invariants.md`,
`public/toolshed.svg`) is gitignored — edit the canonical slice docs instead and re-run
`npm run sync`.

## Why the slice docs stay `.md`, not `.mdx`

MDX is not a practical superset of markdown: `{` and `<` become JSX syntax, and GFM
tables/frontmatter need plugins. The slice docs are consumed as plain markdown by
`em validate`, `em-ci`, and implementer agents. MDX is used only for the hand-authored
pages (`index.mdx`, `model.mdx`, `status.mdx`) that compose components.
