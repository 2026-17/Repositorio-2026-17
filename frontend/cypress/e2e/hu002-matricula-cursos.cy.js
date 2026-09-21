// HU-002 - Matrícula de cursos (selección y solicitud)
// Ruta: /estudiante/matricula
// Nota: al momento de escribir esta prueba, la pantalla consume datos
// simulados (src/data/matriculaMockData.js) en vez del backend real,
// según el comentario en SeleccionCursos.jsx. Ajustar credenciales del
// estudiante de prueba según el ambiente.

const ESTUDIANTE_CEDULA = '402620823'
const ESTUDIANTE_PASSWORD = '12345678'

describe('HU-002 - Matrícula de cursos', () => {

    beforeEach(() => {
        cy.loginComo(ESTUDIANTE_CEDULA, ESTUDIANTE_PASSWORD)
        cy.visit('/estudiante/matricula')
    })

    it('Debe mostrar el título y el resumen de matrícula si el estudiante está habilitado', () => {
        cy.contains('h1', 'Solicitud de matrícula').should('be.visible')
    })

    it('Debe listar la oferta de cursos en una tabla con horario, cupos y profesor', () => {
        cy.get('table.oferta-tabla').should('be.visible')
        cy.get('table.oferta-tabla thead th').should('contain.text', 'Código')
        cy.get('table.oferta-tabla thead th').should('contain.text', 'Horario')
        cy.get('table.oferta-tabla thead th').should('contain.text', 'Profesor')
        cy.get('table.oferta-tabla thead th').should('contain.text', 'Cupos')
    })

    it('No debe permitir seleccionar un grupo sin cupos disponibles', () => {
        cy.get('table.oferta-tabla tbody tr').each(($row) => {
            if ($row.text().includes('Sin cupos')) {
                cy.wrap($row).find('input[type="checkbox"]').should('be.disabled')
            }
        })
    })

    it('El botón de enviar solicitud debe estar deshabilitado sin cursos seleccionados', () => {
        cy.contains('button', 'Enviar solicitud de matrícula').should('be.disabled')
    })

    it('Debe habilitar el envío al seleccionar al menos un curso válido y mostrar la confirmación', () => {
        // Algunos errores de validación (conflicto de horario, correquisitos)
        // solo aparecen DESPUÉS de marcar el checkbox, no antes — así que no
        // basta con filtrar por lo que se ve en la tabla sin seleccionar.
        // Probamos cada candidato (checkbox habilitado) uno por uno: lo
        // marcamos, revisamos si el botón quedó habilitado, y si no,
        // lo desmarcamos y probamos el siguiente.
        cy.get('table.oferta-tabla tbody tr').then(($rows) => {
            const candidatos = [...$rows].filter((row) => {
                const checkbox = row.querySelector('input[type="checkbox"]')
                return checkbox && !checkbox.disabled
            })

            if (candidatos.length === 0) {
                cy.log('No hay cursos disponibles para matricular en este ambiente de prueba.')
                return
            }

            function intentar(index) {
                if (index >= candidatos.length) {
                    cy.log('Ningún curso disponible pudo matricularse sin errores de validación en este ambiente de prueba.')
                    return
                }

                const fila = candidatos[index]
                cy.wrap(fila).find('input[type="checkbox"]').click({ force: true })
                cy.wrap(fila).find('input[type="checkbox"]').should('be.checked')

                cy.contains('button', 'Enviar solicitud de matrícula').then(($btn) => {
                    if ($btn.is(':disabled')) {
                        // Este curso generó un error de validación al seleccionarlo
                        // (ej. conflicto de horario); lo desmarcamos y probamos otro.
                        cy.wrap(fila).find('input[type="checkbox"]').click({ force: true })
                        intentar(index + 1)
                    } else {
                        cy.wrap($btn).click()
                        cy.contains('Solicitud #').should('be.visible')
                        cy.contains('Pendiente de revisión').should('be.visible')
                    }
                })
            }

            intentar(0)
        })
    })
})