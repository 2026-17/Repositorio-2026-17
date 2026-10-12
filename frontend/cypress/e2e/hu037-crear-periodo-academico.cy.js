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

    it('Debe exigir nombre y fechas obligatorias sin llamar al backend', () => {
        cy.intercept('POST', '**/api/periodos-academicos').as('crearPeriodoIncompleto')
        cy.get('form').submit()
        cy.contains('Ingrese el nombre del período académico.').should('be.visible')
        cy.contains('Ingrese la fecha de inicio.').should('be.visible')
        cy.contains('Ingrese la fecha de fin.').should('be.visible')
        cy.get('@crearPeriodoIncompleto.all').should('have.length', 0)
    })

    it('Debe impedir que las fechas de matrícula queden invertidas', () => {
        cy.get('#nombre').type('Cypress - Periodo fechas de matrícula inválidas')
        cy.get('#fechaInicio').type('2027-01-01')
        cy.get('#fechaFin').type('2027-12-31')
        cy.get('#fechaInicioMatricula').type('2026-12-01')
        cy.get('#fechaFinMatricula').type('2026-11-30')

        cy.intercept('POST', '**/api/periodos-academicos').as('crearPeriodoRangoInvalido')
        cy.contains('button', 'Crear período académico').click()
        cy.contains('La fecha de fin de matrícula no puede ser anterior a la de inicio.')
            .should('be.visible')
        cy.get('@crearPeriodoRangoInvalido.all').should('have.length', 0)
    })

    it('Debe rechazar una fecha final anterior a la fecha de inicio', () => {
        cy.get('#nombre').type('Cypress - Período fin anterior')
        cy.get('#fechaInicio').type('2027-01-15')
        cy.get('#fechaFin').type('2027-01-14')

        cy.intercept('POST', '**/api/periodos-academicos').as('crearPeriodoFinAnterior')
        cy.contains('button', 'Crear período académico').click()

        cy.get('.curso-field__error')
            .contains('La fecha de fin debe ser posterior a la fecha de inicio.')
            .should('be.visible')
        cy.get('@crearPeriodoFinAnterior.all').should('have.length', 0)
    })

    it('Debe validar que el fin de la ventana de matrícula no sea anterior al inicio', () => {
        cy.get('#nombre').type('Cypress - Fechas de matrícula inválidas')
        cy.get('#fechaInicio').type('2027-01-01')
        cy.get('#fechaFin').type('2027-12-31')
        cy.get('#fechaInicioMatricula').type('2027-02-10')
        cy.get('#fechaFinMatricula').type('2027-02-09')

        cy.intercept('POST', '**/api/periodos-academicos').as('crearPeriodoVentanaInvalida')
        cy.contains('button', 'Crear período académico').click()

        cy.get('.curso-field__error')
            .contains('La fecha de fin de matrícula no puede ser anterior a la de inicio.')
            .should('be.visible')
        cy.get('@crearPeriodoVentanaInvalida.all').should('have.length', 0)
    })

    it('Debe rechazar un nombre de período que ya existe sin duplicar el registro', () => {
        cy.window().its('localStorage.token').should('exist').then((token) => {
            cy.request({
                method: 'GET',
                url: '/api/periodos-academicos/todos',
                headers: { Authorization: 'Bearer ' + token },
            }).then((response) => {
                expect(response.status).to.eq(200)
                expect(response.body).to.be.an('array').and.not.be.empty
                const nombreExistente = response.body[0].nombre

                cy.get('#nombre').type(nombreExistente)
                cy.get('#fechaInicio').type('2027-01-01')
                cy.get('#fechaFin').type('2027-12-31')
                cy.intercept('POST', '**/api/periodos-academicos').as('crearPeriodoDuplicado')
                cy.contains('button', 'Crear período académico').click()

                cy.wait('@crearPeriodoDuplicado').then(({ response: post }) => {
                    expect([400, 409, 422], 'Un nombre duplicado debe ser error de validación, no error interno').to.include(post.statusCode)
                })
                cy.get('[role="alertdialog"]').should('be.visible')
                    .and('contain.text', 'Ya existe un período académico con el nombre ingresado.')
            })
        })
    })

})
