import { defineConfig } from 'cypress'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// Resolver la ruta desde este archivo, no desde la carpeta donde se ejecute npx.
const frontendDir = dirname(fileURLToPath(import.meta.url))
const rutasEntorno = [
    resolve(frontendDir, 'cypress.env.json'),
    resolve(frontendDir, '..', 'cypress.env.json'),
]

function leerEntornoLocal() {
    const ruta = rutasEntorno.find((archivo) => existsSync(archivo))
    if (!ruta) {
        console.warn(
            '[Cypress] No se encontró cypress.env.json en frontend/ ni en la raíz del repositorio. ' +
            'Copie frontend/cypress.env.example.json a frontend/cypress.env.json y complete las credenciales locales.'
        )
        return {}
    }
    try {
        const entorno = JSON.parse(readFileSync(ruta, 'utf8'))
        if (!entorno || typeof entorno !== 'object' || Array.isArray(entorno)) {
            throw new Error('El contenido debe ser un objeto JSON')
        }
        console.info('[Cypress] Configuración de pruebas cargada desde ' + ruta + ' (valores ocultos)')
        return entorno
    } catch (error) {
        throw new Error('[Cypress] No se pudo leer cypress.env.json: ' + error.message)
    }
}

export default defineConfig({
    // Cypress 16 lee estas variables con cy.env([...]) desde el proceso Node.
    env: leerEntornoLocal(),
    e2e: {
        baseUrl: 'https://sigac.acusab.ed.cr/',
        setupNodeEvents(on, config) {
            return config
        },
    },
})
