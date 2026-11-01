// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import { unified } from '@astrojs/markdown-remark';
import remarkInvLinks from './plugins/remark-inv-links.mjs';

// https://astro.build/config
export default defineConfig({
	markdown: {
		processor: unified({ remarkPlugins: [remarkInvLinks] }),
	},
	integrations: [
		starlight({
			title: 'Toolshed Event Model',
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/milehimikey/toolshed-v2' },
			],
			sidebar: [
				{ label: 'The Model', slug: 'model' },
				{ label: 'Slice Status', slug: 'status' },
				{ label: 'Invariant Index', slug: 'invariants' },
				{ label: 'Slices', items: [{ autogenerate: { directory: 'slices' } }] },
			],
		}),
	],
});
