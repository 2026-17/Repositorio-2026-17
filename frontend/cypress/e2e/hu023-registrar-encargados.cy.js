// HU-023 - Registrar encargados
// Los encargados se registran durante la creación del estudiante.
// Estas pruebas validan la sección integrada del formulario.

const ADMIN_CEDULA = '456456456'
const ADMIN_PASSWORD = '12345678'

describe('HU-023 - Registrar encargados', () => {
    beforeEach(() => {
        cy.loginComo(ADMIN_CEDULA, ADMIN_PASSWORD)
        cy.visit('/admin/administracion/estudiantes')
        cy.contains('button', 'Nuevo estudiante').click()
        cy.contains('h1', 'Registrar estudiante').should('be.visible')
    })

    it('Debe mostrar la sección de encargados para un estudiante menor de edad', () => {
        cy.get('input[type="date"]').first().type('2012-01-15')

        cy.contains('h2', 'Encargados').should('be.visible')
        cy.contains('Requerido para menores de edad').should('be.visible')
        cy.contains('+ Agregar encargado').should('be.visible')
    })

    it('Debe permitir agregar hasta dos encargados', () => {
        cy.get('input[type="date"]').first().type('2012-01-15')

        cy.contains('+ Agregar encargado').click()
        cy.contains('strong', 'Encargado 1').should('be.visible')

        cy.contains('+ Agregar encargado').click()
        cy.contains('strong', 'Encargado 2').should('be.visible')

        cy.contains('+ Agregar encargado').should('be.disabled')
    })

    it('Debe exigir al menos un encargado para un menor', () => {
        cy.get('input[type="date"]').first().type('2012-01-15')

        cy.get('form').submit()

        cy.contains('Un menor debe tener uno o dos encargados.')
            .should('be.visible')
    })

    it('Debe exigir exactamente un responsable principal', () => {
        cy.get('input[type="date"]').first().type('2012-01-15')

        cy.contains('+ Agregar encargado').click()

        cy.get('form').submit()

        cy.contains('Debe existir exactamente un responsable principal.')
            .should('be.visible')
    })
})
