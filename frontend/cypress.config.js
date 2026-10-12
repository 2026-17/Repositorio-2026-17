import { defineConfig } from 'cypress'

export default defineConfig({
    e2e: {
        // Todas las visitas relativas de Cypress se resuelven en el sitio oficial de SIGAC.
        baseUrl: 'https://sigac.acusab.ed.cr/',
        setupNodeEvents(on, config) {
            return config
        },
    },
})
