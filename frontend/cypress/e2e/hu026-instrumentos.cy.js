// HU-026 - Registrar instrumentos que aprende el estudiante

const DIRECTOR_CEDULA = '987654321'
const DIRECTOR_PASSWORD = '12345678'
const BUSQUEDA_ESTUDIANTE = 'Isaac'

function token() {
    return cy.window().its('localStorage.token').should('exist')
}

function datosDisponibles(minimo = 1) {
    return token().then((t) => cy.request({
        method: 'GET',
        url: '/api/estudiantes?texto=' + encodeURIComponent(BUSQUEDA_ESTUDIANTE),
        headers: { Authorization: 'Bearer ' + t },
    }).then((er) => {
        expect(er.status).to.eq(200)
        return cy.request({
            method: 'GET',
            url: '/api/instrumentos',
            headers: { Authorization: 'Bearer ' + t },
        }).then((ir) => {
            expect(ir.status).to.eq(200)

            function buscar(i = 0) {
                if (i >= er.body.length) {
                    throw new Error('No se encontró estudiante con instrumentos libres y profesor disponible.')
                }

                const estudiante = er.body[i]
                return cy.request({
                    method: 'GET',
                    url: '/api/estudiantes/' + estudiante.id + '/instrumentos/actual',
                    headers: { Authorization: 'Bearer ' + t },
                }).then((ar) => {
                    expect(ar.status).to.eq(200)

                    const usados = new Set(
                        ar.body.map(x => String(x.idInstrumento ?? x.instrumentoId ?? x.id))
                    )
                    const libres = ir.body.filter(x => !usados.has(String(x.id)))

                    function buscarInstrumento(j = 0, encontrados = []) {
                        if (encontrados.length >= minimo) {
                            return cy.wrap({ estudiante, instrumentos: encontrados })
                        }
                        if (j >= libres.length) return buscar(i + 1)

                        const instrumento = libres[j]
                        return cy.request({
                            method: 'GET',
                            url: '/api/profesores/por-instrumento/' + instrumento.id,
                            headers: { Authorization: 'Bearer ' + t },
                        }).then((pr) => {
                            expect(pr.status).to.eq(200)
                            if (pr.body.length) {
                                encontrados.push({
                                    instrumento,
                                    profesor: pr.body[0],
                                })
                            }
                            return buscarInstrumento(j + 1, encontrados)
                        })
                    }

                    return buscarInstrumento()
                })
            }

            return buscar()
        })
    }))
}

function buscar(d) {
    cy.get('input[placeholder="Ej: 118760542"]')
        .clear()
        .type(d.estudiante.numeroIdentificacion)

    cy.contains('button', 'Buscar estudiante').click()
    cy.contains('h2', 'Asignar instrumento').should('be.visible')
}

function seleccionar(d) {
    const form = cy.contains('h2', 'Asignar instrumento').closest('section')

    form.find('select').first().select(String(d.instrumento.id))

    form.find('select').then(($selects) => {
        if ($selects.length < 2) {
            return
        }

        const profesorSelect = cy.wrap($selects.eq(1))

        profesorSelect
            .should('not.be.disabled')
            .find('option')
            .not('[value=""]')
            .then(($options) => {
                if ($options.length > 0) {
                    profesorSelect.select($options.first().val())
                }
            })
    })
}

function limpiar(idEstudiante, idAsignacion) {
    return token().then((t) => cy.request({
        method: 'PUT',
        url: '/api/estudiantes/' + idEstudiante + '/instrumentos/' + idAsignacion + '/deshabilitar',
        headers: { Authorization: 'Bearer ' + t },
    }).then((r) => {
        expect(r.status).to.eq(200)
    }))
}

function asignacionActual(idEstudiante, idInstrumento) {
    return token().then((t) => cy.request({
        method: 'GET',
        url: '/api/estudiantes/' + idEstudiante + '/instrumentos/actual',
        headers: { Authorization: 'Bearer ' + t },
    }).then((r) => {
        expect(r.status).to.eq(200)
        const encontrada = r.body.find(
            x => String(x.idInstrumento ?? x.instrumentoId ?? x.id) === String(idInstrumento)
        )
        expect(encontrada).to.exist
        return encontrada.idEstudianteInstrumento
    }))
}

describe('HU-026 - Registrar instrumentos', () => {
    beforeEach(() => {
        cy.loginComo(DIRECTOR_CEDULA, DIRECTOR_PASSWORD)
        cy.visit('/director-academico/estudiantes/instrumentos')
    })

    it('Debe permitir buscar un estudiante registrado', () => {
        datosDisponibles().then((d) => {
            buscar(d)
            cy.contains('h2', 'Historial de instrumentos').should('be.visible')
        })
    })

    it('Debe validar que se haya seleccionado un instrumento', () => {
        datosDisponibles().then((d) => {
            buscar(d)

            cy.contains('h2', 'Asignar instrumento')
                .closest('section')
                .contains('button', 'Asignar instrumento')
                .click()

            cy.get('.field-error')
                .contains('Seleccione un instrumento.')
                .should('exist')
        })
    })

    it('Debe asignar un instrumento y registrar la fecha en el historial', () => {
        datosDisponibles().then((d) => {
            const elegido = d.instrumentos[0]
            buscar(d)
            seleccionar(elegido)

            cy.intercept('POST', '**/api/estudiantes/*/instrumentos').as('asignar')

            cy.contains('h2', 'Asignar instrumento')
                .closest('section')
                .contains('button', 'Asignar instrumento')
                .click()

            cy.wait('@asignar').then(({ response }) => {
                expect(response.statusCode, JSON.stringify(response.body)).to.eq(201)
            })

            cy.get('[role="status"]')
                .should('be.visible')
                .and('contain.text', 'asignado correctamente')

            cy.contains('h2', 'Historial de instrumentos')
                .closest('section')
                .within(() => cy.contains('td', elegido.instrumento.nombre).should('exist'))

            asignacionActual(d.estudiante.id, elegido.instrumento.id)
                .then((id) => limpiar(d.estudiante.id, id))
        })
    })
})