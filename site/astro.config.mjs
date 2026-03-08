// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	site: 'https://wboult.github.io/es-runner',
	base: '/es-runner/',
	integrations: [
		starlight({
			title: 'ES Runner',
			social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/wboult/es-runner' }],
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
