import { defineCollection, z } from 'astro:content';
import { docsLoader } from '@astrojs/starlight/loaders';
import { docsSchema } from '@astrojs/starlight/schema';

// Extends Starlight's schema with the em slice-doc frontmatter so the
// dashboard can render pattern/status/ratification without re-parsing files.
export const collections = {
	docs: defineCollection({
		loader: docsLoader(),
		schema: docsSchema({
			extend: z.object({
				schemaVersion: z.number().optional(),
				pattern: z.string().optional(),
				swimlane: z.string().optional(),
				status: z.string().optional(),
				implementedIn: z.string().optional(),
				ratifiedBy: z.string().optional(),
				ratifiedOn: z.coerce.date().optional(),
				reviewedBy: z.string().optional(),
				reviewedOn: z.coerce.date().optional(),
				version: z.number().optional(),
			}),
		}),
	}),
};
