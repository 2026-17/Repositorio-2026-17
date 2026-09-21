// HU-031 - Desactivar curso de la malla curricular
// Ruta: /director-academico/cursos/desactivar

const DIRECTOR_CEDULA = '987654321'
const DIRECTOR_PASSWORD = '12345678'

describe('HU-031 - Desactivar curso', () => {

    beforeEach(() => {
        cy.loginComo(DIRECTOR_CEDULA, DIRECTOR_PASSWORD)
        cy.visit('/director-academico/cursos/desactivar')
    })

    it('Debe mostrar cursos activos y solicitar confirmación antes de desactivar', () => {
        cy.contains('h1', 'Desactivar curso')
            .should('be.visible')

        cy.get('.curso-card')
            .should('have.length.greaterThan', 0)

        cy.get('.curso-card')
            .first()
            .within(() => {
                cy.get('h3')
                    .should('be.visible')
                    .invoke('text')
                    .as('nombreCurso')

                cy.contains('button', 'Desactivar')
                    .should('be.visible')
                    .click()
            })

        cy.get('[role="alertdialog"]')
            .should('be.visible')
            .within(() => {
                cy.contains('h2', '¿Desactivar este curso?')
                    .should('be.visible')

                cy.contains(
                    'dejará de aparecer en la oferta académica vigente'
                ).should('be.visible')

                cy.contains(
                    'Su información histórica no será eliminada'
                ).should('be.visible')
            })
    })
})
