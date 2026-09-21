// HU-015 - Asignar beca
// Ruta: /admin/administracion/becas-pagos
//
// La implementación actual de la HU-015 expone la operación de becas
// mediante la API. Estas pruebas usan la sesión obtenida mediante la UI
// y validan asignación, cálculo, modificación y rechazo de porcentajes > 100.

const ADMIN_CEDULA = '456456456'
const ADMIN_PASSWORD = '12345678'
const ESTUDIANTE_BUSQUEDA = 'Isaac'

function obtenerToken() {
    return cy.window()
        .its('localStorage.token')
        .should('exist')
}

function obtenerDatosBase() {
    return obtenerToken().then((token) => {
        return cy.request({
            method: 'GET',
            url: `/api/estudiantes?texto=${encodeURIComponent(ESTUDIANTE_BUSQUEDA)}`,
            headers: {
                Authorization: `Bearer ${token}`,
            },
        }).then((estudiantesResponse) => {

            expect(estudiantesResponse.status).to.eq(200)
            expect(estudiantesResponse.body)
                .to.be.an('array')
                .and.not.be.empty

            return cy.request({
                method: 'GET',
                url: '/api/becas/tipos-becas',
                headers: {
                    Authorization: `Bearer ${token}`,
                },
            }).then((tiposResponse) => {

                expect(tiposResponse.status).to.eq(200)
                expect(tiposResponse.body)
                    .to.be.an('array')
                    .and.not.be.empty

                return cy.request({
                    method: 'GET',
                    url: '/api/periodos-academicos',
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                }).then((periodosResponse) => {

                    expect(periodosResponse.status).to.eq(200)
                    expect(periodosResponse.body)
                        .to.be.an('array')
                        .and.not.be.empty

                    return {
                        token,
                        estudiante: estudiantesResponse.body[0],
                        tipoBeca: tiposResponse.body[0],
                        periodos: periodosResponse.body,
                    }
                })
            })
        })
    })
}

describe('HU-015 - Asignar beca', () => {

    beforeEach(() => {
        cy.loginComo(ADMIN_CEDULA, ADMIN_PASSWORD)
        cy.visit('/admin/administracion/becas-pagos')
    })

    it('Debe mostrar la pantalla administrativa de estudiantes y pagos', () => {
        cy.contains('h1', 'Estudiantes').should('be.visible')
        cy.contains('h2', 'Buscar estudiante').should('be.visible')
    })

    it('Debe cargar tipos de beca y períodos académicos', () => {
        obtenerDatosBase().then(({ tipoBeca, periodos }) => {
            expect(tipoBeca.id).to.exist
            expect(tipoBeca.nombre).to.be.a('string')

            expect(periodos.length).to.be.greaterThan(0)
            expect(periodos[0]).to.have.property('id')
        })
    })

    it('Debe rechazar un porcentaje de beca mayor a 100%', () => {
        obtenerDatosBase().then(({ token, estudiante, tipoBeca, periodos }) => {

            cy.request({
                method: 'POST',
                url: '/api/becas',
                headers: {
                    Authorization: `Bearer ${token}`,
                },
                failOnStatusCode: false,
                body: {
                    idEstudiante: estudiante.id,
                    idTipoBeca: tipoBeca.id,
                    idPeriodo: periodos[0].id,
                    porcentaje: 101,
                    fechaInicio: new Date().toISOString().slice(0, 10),
                    motivo: 'Cypress HU-015 - porcentaje inválido',
                },
            }).then((response) => {
                expect(response.status).to.be.oneOf([400, 422])
            })
        })
    })

    it('Debe asignar una beca y calcular correctamente el descuento', () => {
        obtenerDatosBase().then(({ token, estudiante, tipoBeca, periodos }) => {

            cy.request({
                method: 'GET',
                url: `/api/becas/estudiante/${estudiante.id}`,
                headers: {
                    Authorization: `Bearer ${token}`,
                },
            }).then((existentesResponse) => {

                expect(existentesResponse.status).to.eq(200)

                const periodosUsados = new Set(
                    existentesResponse.body.map(
                        (beca) => String(beca.idPeriodo)
                    )
                )

                const periodoDisponible = periodos.find(
                    (periodo) =>
                        !periodosUsados.has(String(periodo.id))
                )

                if (!periodoDisponible) {
                    cy.log(
                        'No existe un período sin beca para el estudiante de prueba.'
                    )
                    return
                }

                cy.request({
                    method: 'POST',
                    url: '/api/becas',
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                    body: {
                        idEstudiante: estudiante.id,
                        idTipoBeca: tipoBeca.id,
                        idPeriodo: periodoDisponible.id,
                        porcentaje: 25,
                        fechaInicio: new Date().toISOString().slice(0, 10),
                        motivo: 'Cypress HU-015',
                    },
                }).then((response) => {

                    expect(response.status).to.eq(201)

                    const beca = response.body

                    expect(Number(beca.porcentaje)).to.eq(25)

                    const mensualidad =
                        Number(beca.montoMensualidadBase)

                    const descuento =
                        Number(beca.montoDescuento)

                    const montoAPagar =
                        Number(beca.montoAPagar)

                    expect(descuento)
                        .to.be.closeTo(
                        mensualidad * 0.25,
                        0.01
                    )

                    expect(montoAPagar)
                        .to.be.closeTo(
                        mensualidad - descuento,
                        0.01
                    )

                    expect(beca.activa).to.be.true

                    return cy.request({
                        method: 'DELETE',
                        url: `/api/becas/${beca.idBeca}`,
                        headers: {
                            Authorization: `Bearer ${token}`,
                        },
                    })
                }).then((deleteResponse) => {

                    expect(deleteResponse.status).to.eq(204)
                })
            })
        })
    })

    it('Debe permitir modificar una beca existente', () => {
        obtenerDatosBase().then(({ token, estudiante, tipoBeca, periodos }) => {

            cy.request({
                method: 'GET',
                url: `/api/becas/estudiante/${estudiante.id}`,
                headers: {
                    Authorization: `Bearer ${token}`,
                },
            }).then((response) => {

                expect(response.status).to.eq(200)

                const beca = response.body[0]

                if (!beca) {
                    cy.log(
                        'No existe una beca previa para probar modificación.'
                    )
                    return
                }

                const porcentajeOriginal =
                    Number(beca.porcentaje)

                const tipoOriginal =
                    beca.idTipoBeca || tipoBeca.id

                const periodoOriginal =
                    beca.idPeriodo || periodos[0].id

                cy.request({
                    method: 'PUT',
                    url: `/api/becas/${beca.idBeca}`,
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                    body: {
                        idTipoBeca: tipoOriginal,
                        idPeriodo: periodoOriginal,
                        porcentaje: 26,
                        fechaFin: beca.fechaFin || null,
                        motivo: 'Cypress HU-015 - modificación',
                    },
                }).then((updateResponse) => {

                    expect(updateResponse.status).to.eq(200)
                    expect(
                        Number(updateResponse.body.porcentaje)
                    ).to.eq(26)

                    expect(
                        Number(updateResponse.body.montoDescuento)
                    ).to.be.closeTo(
                        Number(updateResponse.body.montoMensualidadBase) * 0.26,
                        0.01
                    )

                    return cy.request({
                        method: 'PUT',
                        url: `/api/becas/${beca.idBeca}`,
                        headers: {
                            Authorization: `Bearer ${token}`,
                        },
                        body: {
                            idTipoBeca: tipoOriginal,
                            idPeriodo: periodoOriginal,
                            porcentaje: porcentajeOriginal,
                            fechaFin: beca.fechaFin || null,
                            motivo:
                                beca.motivo ||
                                'Cypress HU-015 - restauración',
                        },
                    })
                }).then((restoreResponse) => {

                    expect(restoreResponse.status).to.eq(200)
                    expect(
                        Number(restoreResponse.body.porcentaje)
                    ).to.eq(porcentajeOriginal)
                })
            })
        })
    })
})