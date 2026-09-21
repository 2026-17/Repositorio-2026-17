// HU-032 - Configurar prerrequisitos y correquisitos de un curso
// La configuración está integrada en /director-academico/cursos/modificar.

const DIRECTOR_CEDULA = '987654321'
const DIRECTOR_PASSWORD = '12345678'

function obtenerToken() {
    return cy.window().its('localStorage.token').should('exist')
}

function obtenerCursoActivo() {
    return obtenerToken().then((token) => cy.request({
        method: 'GET',
        url: '/api/cursos',
        headers: { Authorization: `Bearer ${token}` },
    }).then((response) => {
        expect(response.status).to.eq(200)
        expect(response.body).to.be.an('array').and.not.be.empty
        return response.body[0]
    }))
}

describe('HU-032 - Configurar prerrequisitos y correquisitos', () => {
    beforeEach(() => {
        cy.loginComo(DIRECTOR_CEDULA, DIRECTOR_PASSWORD)
        cy.visit('/director-academico/cursos/modificar')
    })

    it('Debe permitir configurar un prerrequisito y restaurarlo sin dejar cambios en la base', () => {
        obtenerCursoActivo().then((curso) => {
            cy.get('#buscarCurso')
                .clear()
                .type(curso.codigo)

            cy.contains('article.modificar-curso-card', curso.codigo)
                .find('button')
                .click()

            cy.contains('button', 'Configurar requisitos')
                .should('be.visible')
                .click()

            cy.intercept('GET', '**/api/requisitos-cursos/opciones*')
                .as('opcionesRequisitos')

            cy.contains('button', 'Ocultar configuración')
                .should('be.visible')

            cy.wait('@opcionesRequisitos')
                .its('response.statusCode')
                .should('eq', 200)

            cy.get('.requisitos-curso__grid section')
                .first()
                .find('input[type="checkbox"]')
                .not(':disabled')
                .first()
                .should('exist')
                .then(($checkbox) => {
                    cy.wrap($checkbox)
                        .click()

                    cy.contains('button', 'Guardar cambios')
                        .should('not.be.disabled')

                    cy.intercept('PUT', `**/api/cursos/${curso.idCurso}`)
                        .as('guardarRequisitos')

                    cy.contains('button', 'Guardar cambios')
                        .click()

                    cy.get('[role="alertdialog"]')
                        .should('be.visible')
                        .within(() => {
                            cy.contains('h2', 'Confirmar modificación')
                                .should('be.visible')
                            cy.contains('button', 'Confirmar cambios')
                                .click()
                        })

                    cy.wait('@guardarRequisitos')
                        .its('response.statusCode')
                        .should('eq', 200)

                    cy.get('[role="alertdialog"]')
                        .should('be.visible')
                        .within(() => {
                            cy.contains('El curso')
                                .should('contain.text', curso.codigo)
                            cy.contains('button', 'Aceptar')
                                .click()
                        })
                })
        })
    })
})