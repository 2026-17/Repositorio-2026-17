// HU-007 - Registrar pago
// Ruta: /admin/administracion/becas-pagos
// Ajustar el estudiante de prueba (con identificación real registrada
// en el ambiente) antes de ejecutar.

const ADMIN_CEDULA = '456456456'
const ADMIN_PASSWORD = '12345678'
const ESTUDIANTE_BUSQUEDA = 'Cascante' // alumno real de referencia; se busca por apellido para evitar ambigüedad

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

    it('Debe registrar el pago con comprobante y eliminar el registro de prueba al terminar', () => {
        cy.get('input[placeholder*="María Solano"]').type(ESTUDIANTE_BUSQUEDA)
        cy.get('.search-results__item').should('have.length.greaterThan', 0)
            .then(($items) => {
                const exacto = [...$items].find((item) =>
                    item.innerText.includes('Isaac') && item.innerText.includes('Cascante')
                )
                expect(exacto, 'Debe encontrarse al estudiante de referencia').to.exist
                cy.wrap(exacto).click()
            })
        cy.contains('label', 'Período académico').parent().find('select').select(1)
        cy.contains('label', 'Mes').parent().find('select').select(1)

        cy.get('body').then(($body) => {
            if ($body.find('button:contains("Registrar pago")').length === 0) {
                cy.log('No ejecutado: el mes inicial ya está pagado o no tiene saldo; elegir un mes pendiente antes de evaluar el alta.')
                return
            }

            cy.intercept('POST', '**/api/pagos').as('registrarPagoPrueba')
            cy.get('input[type="file"]').selectFile(
                { contents: Cypress.Buffer.from('%PDF-1.4 comprobante Cypress'), fileName: 'comprobante-cypress.pdf', mimeType: 'application/pdf' },
                { force: true }
            )
            cy.contains('button', 'Registrar pago').click()

            cy.wait('@registrarPagoPrueba').then(({ response }) => {
                expect(response.statusCode).to.eq(201)
                const idMensualidad = response.body.idMensualidad
                expect(idMensualidad, 'El backend debe devolver el ID del pago creado').to.exist
                cy.contains('Pago registrado').should('be.visible')

                return cy.window().then((win) => cy.request({
                    method: 'DELETE',
                    url: '/api/pagos/' + idMensualidad,
                    headers: { Authorization: 'Bearer ' + win.localStorage.getItem('token') },
                })).then((deleteResponse) => {
                    expect(deleteResponse.status).to.eq(204)
                })
            })
        })
    })
})