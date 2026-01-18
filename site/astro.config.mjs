// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	site: 'https://wboult.github.io/elastic-runner',
	base: '/elastic-runner/',
	integrations: [
		starlight({
			title: 'Elastic Runner',
			social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/wboult/elastic-runner' }],
			sidebar: [
				{
					label: 'Tutorials',
					autogenerate: { directory: 'tutorials' },
				},
				{
					label: 'How-to Guides',
					autogenerate: { directory: 'how-to' },
				},
				{
					label: 'Reference',
					autogenerate: { directory: 'reference' },
				},
				{
					label: 'Explanation',
					autogenerate: { directory: 'explanation' },
				},
			],
		}),
	],
});
