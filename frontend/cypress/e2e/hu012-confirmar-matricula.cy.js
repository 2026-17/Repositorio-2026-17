// HU-012 - Confirmar matrícula (aprobar / rechazar preaprobaciones)
// Ruta: /admin/administracion/matricula
// Ajustar credenciales del administrador según el ambiente de pruebas.

const ADMIN_CEDULA = '456456456'
const ADMIN_PASSWORD = '12345678'

describe('HU-012 - Confirmar matrícula', () => {

    beforeEach(() => {
        cy.loginComo(ADMIN_CEDULA, ADMIN_PASSWORD)
        cy.visit('/admin/administracion/matricula')
    })

    it('Debe mostrar la pestaña de solicitudes pendientes por defecto', () => {
        cy.get('body').then(($body) => {
            cy.contains(/Sin preaprobaciones pendientes|Preaprobación #/).should('be.visible')
        })
    })

    it('Debe requerir observaciones administrativas para rechazar una solicitud', () => {
        cy.get('body').then(($body) => {
            if (!$body.text().includes('Preaprobación #')) {
                cy.log('No hay preaprobaciones pendientes en este ambiente de prueba.')
                return
            }
            cy.contains('button', 'Rechazar').first().click()
            cy.contains('Debe indicar una observación administrativa para rechazar la solicitud').should('be.visible')
        })
    })

    it('No debe permitir habilitar la matrícula si los requisitos no están validados', () => {
        cy.get('body').then(($body) => {
            if (!$body.text().includes('Esta preaprobación no puede')) {
                cy.log('No hay solicitudes con requisitos pendientes en este ambiente de prueba.')
                return
            }
            cy.contains('Esta preaprobación no puede')
                .parents('.card')
                .contains('button', 'Habilitar matrícula')
                .should('be.disabled')
        })
    })

    it('Debe solicitar confirmación antes de rechazar una solicitud con observación', () => {
        cy.get('body').then(($body) => {
            if (!$body.text().includes('Preaprobación #')) {
                cy.log('No hay preaprobaciones pendientes en este ambiente de prueba.')
                return
            }
            cy.get('.card').first().within(() => {
                cy.get('textarea').type('Documentación incompleta, favor corregir.')
                cy.contains('button', 'Rechazar').click()
            })
            // El diálogo de confirmación de la app pide confirmar la decisión
            cy.contains('Rechazar matrícula').should('be.visible')
        })
    })

    it('Debe permitir consultar el historial de decisiones', () => {
        cy.contains('Historial').click()
        cy.contains(/Historial vacío|Preaprobación #/).should('be.visible')
    })

    it('Debe cancelar el rechazo sin cambiar la solicitud pendiente', () => {
        cy.get('body').then(($body) => {
            if (!$body.text().includes('Preaprobación #')) {
                cy.log('Bloqueado: no hay solicitudes pendientes para comprobar la cancelación.')
                return
            }
            cy.get('.card').first().within(() => {
                cy.get('textarea').type('Observación de prueba; se cancelará la decisión.')
                cy.contains('button', 'Rechazar').click()
            })
            cy.get('[role="alertdialog"]').should('be.visible')
                .within(() => {
                    cy.contains('h2', 'Rechazar matrícula').should('be.visible')
                    cy.contains('button', 'Cancelar').click()
                })
            cy.contains(/Preaprobación #/).should('be.visible')
        })
    })

})
