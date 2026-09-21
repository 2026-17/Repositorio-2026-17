// HU-034 - Autenticación e inicio de sesión
// Ruta: /login

const DIRECTOR_CEDULA = '987654321'
const DIRECTOR_PASSWORD = '12345678'

describe('HU-034 - Autenticación e inicio de sesión', () => {

    it('Debe iniciar sesión con credenciales válidas y crear la sesión', () => {
        cy.intercept('POST', '**/api/auth/login')
            .as('login')

        cy.visit('/login')

        cy.get('#cedula')
            .type(DIRECTOR_CEDULA)

        cy.get('#contrasena')
            .type(DIRECTOR_PASSWORD)

        cy.contains('button', 'Ingresar')
            .click()

        cy.wait('@login')
            .its('response.statusCode')
            .should('eq', 200)

        cy.window()
            .its('localStorage.token')
            .should('exist')
            .and('not.be.empty')

        cy.window()
            .its('localStorage.rol')
            .should('eq', 'DIRECTOR_ACADEMICO')

        cy.url()
            .should('include', '/director-academico/cursos/crear')
    })

})
