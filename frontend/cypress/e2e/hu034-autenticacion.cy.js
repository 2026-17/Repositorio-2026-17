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


    it('Debe rechazar credenciales incorrectas sin crear sesión', () => {
        cy.intercept('POST', '**/api/auth/login').as('loginInvalido')
        cy.visit('/login')
        cy.get('#cedula').type('CYPRESS-ID-INEXISTENTE-999')
        cy.get('#contrasena').type('ClaveIncorrecta-NoReal')
        cy.contains('button', 'Ingresar').click()

        cy.wait('@loginInvalido').then(({ response }) => {
            expect(response.statusCode).to.be.oneOf([400, 401, 403])
        })
        cy.get('[role="alertdialog"]')
            .should('be.visible')
            .and('contain.text', 'La cédula o la contraseña son incorrectas.')
        cy.window().then((win) => {
            expect(win.localStorage.getItem('token')).to.be.null
        })
    })

    it('Debe dirigir al usuario administrativo a su módulo correspondiente', () => {
        cy.intercept('POST', '**/api/auth/login').as('loginAdministrativo')
        cy.visit('/login')
        cy.get('#cedula').type('456456456')
        cy.get('#contrasena').type('12345678')
        cy.contains('button', 'Ingresar').click()

        cy.wait('@loginAdministrativo').its('response.statusCode').should('eq', 200)
        cy.window().its('localStorage.rol').should('eq', 'ADMINISTRATIVO')
        cy.url().should('include', '/admin/administracion/estudiantes')
    })

    it('Debe exigir identificación y contraseña antes de llamar al backend', () => {
        cy.intercept('POST', '**/api/auth/login').as('loginVacio')
        cy.visit('/login')
        cy.get('#cedula').should('have.attr', 'required')
        cy.get('#contrasena').should('have.attr', 'required')
        cy.contains('button', 'Ingresar').click()
        cy.get('@loginVacio.all').should('have.length', 0)
        cy.location('pathname').should('eq', '/login')
    })

})
