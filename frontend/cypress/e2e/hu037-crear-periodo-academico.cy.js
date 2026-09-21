// HU-037 - Crear y administrar período académico
// Ruta: /director-academico/periodos-academicos/crear

const DIRECTOR_CEDULA = '987654321'
const DIRECTOR_PASSWORD = '12345678'

describe('HU-037 - Crear período académico', () => {

    beforeEach(() => {
        cy.loginComo(DIRECTOR_CEDULA, DIRECTOR_PASSWORD)
        cy.visit('/director-academico/periodos-academicos/crear')
    })

    it('Debe impedir crear un período cuya fecha de fin sea anterior o igual a la de inicio', () => {
        cy.contains('h1', 'Crear período académico')
            .should('be.visible')

        cy.get('#nombre')
            .type('Cypress - Período inválido')

        cy.get('#fechaInicio')
            .type('2027-01-15')

        cy.get('#fechaFin')
            .type('2027-01-15')

        cy.intercept('POST', '**/api/periodos-academicos')
            .as('crearPeriodo')

        cy.contains(
            'button',
            'Crear período académico'
        ).click()

        cy.get('.curso-field__error')
            .contains(
                'La fecha de fin debe ser posterior a la fecha de inicio.'
            )
            .should('be.visible')

        cy.get('@crearPeriodo.all')
            .should('have.length', 0)
    })
})
