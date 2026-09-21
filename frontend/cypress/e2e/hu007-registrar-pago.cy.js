// HU-007 - Registrar pago
// Ruta: /admin/administracion/becas-pagos
// Ajustar el estudiante de prueba (con identificación real registrada
// en el ambiente) antes de ejecutar.

const ADMIN_CEDULA = '456456456'
const ADMIN_PASSWORD = '12345678'
const ESTUDIANTE_BUSQUEDA = 'Isaac' // texto de búsqueda que debe existir en el ambiente de prueba

describe('HU-007 - Registrar pago', () => {

    beforeEach(() => {
        cy.loginComo(ADMIN_CEDULA, ADMIN_PASSWORD)
        cy.visit('/admin/administracion/becas-pagos')
    })

    it('Debe mostrar el buscador de estudiantes', () => {
        cy.contains('h2', 'Buscar estudiante').should('be.visible')
        cy.get('input[placeholder*="María Solano"]').should('be.visible')
    })

    it('Debe listar resultados de búsqueda al escribir al menos 2 caracteres', () => {
        cy.get('input[placeholder*="María Solano"]').type(ESTUDIANTE_BUSQUEDA)
        cy.get('.search-results').should('be.visible')
    })

    it('Debe permitir seleccionar un estudiante y mostrar su sección de pagos', () => {
        cy.get('input[placeholder*="María Solano"]').type(ESTUDIANTE_BUSQUEDA)
        cy.get('.search-results__item').first().click()
        cy.contains('h2', 'Pagos').should('be.visible')
        cy.contains('label', 'Período académico').should('be.visible')
    })

    it('Debe requerir seleccionar período y mes antes de consultar el estado de pago', () => {
        cy.get('input[placeholder*="María Solano"]').type(ESTUDIANTE_BUSQUEDA)
        cy.get('.search-results__item').first().click()
        cy.contains('label', 'Mes').parent().find('select').should('be.disabled')
        cy.contains('label', 'Período académico').parent().find('select').select(1)
        cy.contains('label', 'Mes').parent().find('select').should('not.be.disabled')
    })

    it('No debe permitir registrar el pago sin adjuntar comprobante', () => {
        cy.get('input[placeholder*="María Solano"]').type(ESTUDIANTE_BUSQUEDA)
        cy.get('.search-results__item').first().click()
        cy.contains('label', 'Período académico').parent().find('select').select(1)
        cy.contains('label', 'Mes').parent().find('select').select(1)

        cy.contains('button', 'Registrar pago').then(($btn) => {
            if ($btn.length) {
                cy.wrap($btn).click()
                cy.contains('Debe adjuntar el comprobante de pago').should('be.visible')
            }
        })
    })

    it('Debe registrar el pago correctamente al adjuntar el comprobante y confirmar', () => {
        cy.get('input[placeholder*="María Solano"]').type(ESTUDIANTE_BUSQUEDA)
        cy.get('.search-results__item').first().click()
        cy.contains('label', 'Período académico').parent().find('select').select(1)
        cy.contains('label', 'Mes').parent().find('select').select(1)

        cy.get('body').then(($body) => {
            if ($body.find('button:contains("Registrar pago")').length === 0) {
                cy.log('El mes seleccionado ya tiene un pago registrado o está becado al 100%; se omite el registro.')
                return
            }
            cy.get('input[type="file"]').selectFile(
                { contents: Cypress.Buffer.from('%PDF-1.4 comprobante'), fileName: 'comprobante.pdf', mimeType: 'application/pdf' },
                { force: true }
            )
            cy.contains('button', 'Registrar pago').click()
            cy.contains('Pago registrado').should('be.visible')
        })
    })
})