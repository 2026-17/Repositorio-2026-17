import { defineConfig } from 'cypress'

export default defineConfig({
    e2e: {
        baseUrl: 'https://sigac.acusab.ed.cr',
        setupNodeEvents(on, config) {
            return config
        },
    },
})
