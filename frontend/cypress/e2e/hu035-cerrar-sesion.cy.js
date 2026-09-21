// HU-035 - Cerrar sesión
// Se valida desde el Header global de las páginas autenticadas.

const DIRECTOR_CEDULA = '987654321'
const DIRECTOR_PASSWORD = '12345678'

describe('HU-035 - Cerrar sesión', () => {

    beforeEach(() => {
        cy.loginComo(DIRECTOR_CEDULA, DIRECTOR_PASSWORD)
        cy.visit('/director-academico/cursos/crear')
    })

    it('Debe cerrar la sesión, invalidar los datos locales y redirigir al login', () => {
        cy.get('.header-user-trigger')
            .should('be.visible')
            .click()

        cy.get('.user-dropdown')
            .should('be.visible')
            .within(() => {
                cy.contains('button', 'Cerrar sesión')
                    .should('be.visible')
            })

        cy.intercept('POST', '**/api/auth/logout')
            .as('logout')

        cy.get('.user-dropdown')
            .contains('button', 'Cerrar sesión')
            .click()

        cy.wait('@logout')
            .its('response.statusCode')
            .should('be.oneOf', [200, 204])

        cy.location('pathname')
            .should('match', /^\/$/)

        cy.window()
            .then((win) => {
                expect(win.localStorage.getItem('token'))
                    .to.equal(null)

                expect(win.localStorage.getItem('rol'))
                    .to.equal(null)

                expect(win.localStorage.getItem('cedula'))
                    .to.equal(null)
            })
    })
})
