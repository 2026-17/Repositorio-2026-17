// HU-026 - Registrar instrumentos que aprende el estudiante
// Ruta: /director-academico/estudiantes/instrumentos
// Usa los comandos de login ya existentes en la rama.

const DIRECTOR_CEDULA = '987654321'
const DIRECTOR_PASSWORD = '12345678'
const BUSQUEDA_ESTUDIANTE = 'Isaac'

function obtenerIdentificacionEstudiante() {
    return cy.window()
        .its('localStorage.token')
        .then((token) => {
            return cy.request({
                method: 'GET',
                url: `/api/estudiantes?texto=${encodeURIComponent(BUSQUEDA_ESTUDIANTE)}`,
                headers: { Authorization: `Bearer ${token}` },
            }).then((response) => {
                expect(response.status).to.eq(200)
                expect(response.body.length).to.be.greaterThan(0)
                return response.body[0].numeroIdentificacion
            })
        })
}

describe('HU-026 - Registrar instrumentos', () => {

    beforeEach(() => {
        cy.loginComo(DIRECTOR_CEDULA, DIRECTOR_PASSWORD)
        cy.visit('/director-academico/estudiantes/instrumentos')
    })

    it('Debe permitir buscar un estudiante registrado', () => {
        obtenerIdentificacionEstudiante().then((identificacion) => {
            cy.get('input[placeholder="Ej: 118760542"]')
                .type(identificacion)

            cy.contains('button', 'Buscar estudiante').click()

            cy.contains('h2', 'Instrumento(s) actual(es)').should('be.visible')
            cy.contains('h2', 'Historial de instrumentos').should('be.visible')
        })
    })

    it('Debe validar que se haya seleccionado un instrumento', () => {
        obtenerIdentificacionEstudiante().then((identificacion) => {
            cy.get('input[placeholder="Ej: 118760542"]')
                .type(identificacion)

            cy.contains('button', 'Buscar estudiante').click()

            cy.contains('h2', 'Asignar instrumento')
                .closest('section')
                .within(() => {
                    cy.contains('button', 'Asignar instrumento').click()
                })

            cy.contains('Seleccione un instrumento').should('be.visible')
        })
    })

    it('Debe asignar un instrumento y registrar la fecha en el historial', () => {
        obtenerIdentificacionEstudiante().then((identificacion) => {
            cy.get('input[placeholder="Ej: 118760542"]')
                .type(identificacion)

            cy.contains('button', 'Buscar estudiante').click()

            const formulario = () =>
                cy.contains('h2', 'Asignar instrumento').closest('section')

            formulario()
                .find('select')
                .first()
                .find('option')
                .not('[value=""]')
                .first()
                .then(($option) => {
                    const instrumento = $option.text().trim()

                    formulario().find('select').first().select($option.val())

                    cy.intercept('POST', '**/api/estudiantes/*/instrumentos')
                        .as('asignarInstrumento')

                    formulario()
                        .contains('button', 'Asignar instrumento')
                        .click()

                    cy.wait('@asignarInstrumento')
                        .its('response.statusCode')
                        .should('eq', 201)

                    cy.get('[role="status"]')
                        .should('be.visible')
                        .and('contain.text', 'asignado correctamente')

                    cy.contains('h2', 'Historial de instrumentos')
                        .closest('section')
                        .within(() => {
                            cy.contains('td', instrumento).should('exist')
                        })
                })
        })
    })

    it('Debe conservar la asignación anterior en el historial al asignar otro instrumento', () => {
        obtenerIdentificacionEstudiante().then((identificacion) => {
            cy.get('input[placeholder="Ej: 118760542"]')
                .type(identificacion)

            cy.contains('button', 'Buscar estudiante').click()

            const formulario = () =>
                cy.contains('h2', 'Asignar instrumento').closest('section')

            formulario()
                .find('select')
                .first()
                .find('option')
                .not('[value=""]')
                .first()
                .then(($first) => {
                    const primero = $first.text().trim()

                    formulario().find('select').first().select($first.val())
                    formulario().contains('button', 'Asignar instrumento').click()

                    cy.get('[role="status"]')
                        .should('contain.text', 'asignado correctamente')

                    formulario()
                        .find('select')
                        .first()
                        .find('option')
                        .not('[value=""]')
                        .eq(1)
                        .should('exist')
                        .then(($second) => {
                            const segundo = $second.text().trim()

                            formulario().find('select').first().select($second.val())
                            formulario().contains('button', 'Asignar instrumento').click()

                            cy.get('[role="status"]')
                                .should('contain.text', 'asignado correctamente')

                            cy.contains('h2', 'Historial de instrumentos')
                                .closest('section')
                                .within(() => {
                                    cy.contains('td', primero).should('exist')
                                    cy.contains('td', segundo).should('exist')
                                })
                        })
                })
        })
    })
})
