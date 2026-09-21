// HU-023 - Registrar encargados
//
// La implementación actual dispone del API de encargados, pero no existe
// todavía una pantalla específica de encargados en el frontend.
// Las pruebas usan la sesión de la UI y validan directamente el recurso real.

const ADMIN_CEDULA = '456456456'
const ADMIN_PASSWORD = '12345678'

function obtenerToken() {
    return cy.window()
        .its('localStorage.token')
        .should('exist')
}

function obtenerEstudianteMenor() {
    return obtenerToken().then((token) => {

        return cy.request({
            method: 'GET',
            url: '/api/estudiantes/listado',
            headers: {
                Authorization: `Bearer ${token}`,
            },
        }).then((response) => {

            expect(response.status).to.eq(200)
            expect(response.body)
                .to.be.an('array')
                .and.not.be.empty

            const menor = response.body.find(
                (estudiante) => estudiante.edad < 18
            )

            if (!menor) {
                throw new Error(
                    'No existe un estudiante menor de edad disponible para HU-023.'
                )
            }

            return {
                token,
                estudiante: menor,
            }
        })
    })
}

function obtenerTipoIdentificacion(token) {
    return cy.request({
        method: 'GET',
        url: '/api/registro/catalogos/tipos-identificacion',
        headers: {
            Authorization: `Bearer ${token}`,
        },
    }).then((response) => {

        expect(response.status).to.eq(200)
        expect(response.body)
            .to.be.an('array')
            .and.not.be.empty

        return response.body[0].id
    })
}

describe('HU-023 - Registrar encargados', () => {

    beforeEach(() => {
        cy.loginComo(ADMIN_CEDULA, ADMIN_PASSWORD)
        cy.visit('/admin/administracion/estudiantes')
    })

    it('Debe poder consultar los encargados asociados a un estudiante', () => {
        obtenerEstudianteMenor().then(({ token, estudiante }) => {

            cy.request({
                method: 'GET',
                url: `/api/encargados/estudiante/${estudiante.id}`,
                headers: {
                    Authorization: `Bearer ${token}`,
                },
            }).then((response) => {

                expect(response.status).to.eq(200)
                expect(response.body).to.be.an('array')
            })
        })
    })

    it('Debe validar los campos obligatorios al registrar un encargado', () => {
        obtenerEstudianteMenor().then(({ token, estudiante }) => {

            obtenerTipoIdentificacion(token)
                .then((idTipoIdentificacion) => {

                    cy.request({
                        method: 'POST',
                        url: '/api/encargados',
                        headers: {
                            Authorization: `Bearer ${token}`,
                        },
                        failOnStatusCode: false,
                        body: {
                            idEstudiante: estudiante.id,
                            idTipoIdentificacion,
                            numeroIdentificacion: '',
                            nombres: '',
                            primerApellido: '',
                            segundoApellido: '',
                            telefonoPrincipal: '',
                            telefonoSecundario: '',
                            correo: '',
                            ocupacion: '',
                            estadoCivil: '',
                            direccion: '',
                            parentesco: '',
                            esResponsablePrincipal: true,
                            autorizadoRetiro: true,
                            observaciones: '',
                            contactosEmergencia: [],
                        },
                    }).then((response) => {

                        expect(response.status)
                            .to.be.oneOf([400, 422])
                    })
                })
        })
    })

    it('Debe rechazar más de dos contactos de emergencia', () => {
        obtenerEstudianteMenor().then(({ token, estudiante }) => {

            obtenerTipoIdentificacion(token)
                .then((idTipoIdentificacion) => {

                    const contactos = [
                        {
                            nombre: 'Emergencia Cypress 1',
                            telefono: '88881111',
                            relacion: 'Madre',
                        },
                        {
                            nombre: 'Emergencia Cypress 2',
                            telefono: '88882222',
                            relacion: 'Padre',
                        },
                        {
                            nombre: 'Emergencia Cypress 3',
                            telefono: '88883333',
                            relacion: 'Tío',
                        },
                    ]

                    cy.request({
                        method: 'POST',
                        url: '/api/encargados',
                        headers: {
                            Authorization: `Bearer ${token}`,
                        },
                        failOnStatusCode: false,
                        body: {
                            idEstudiante: estudiante.id,
                            idTipoIdentificacion,
                            numeroIdentificacion: `CYP-${Date.now()}`,
                            nombres: 'Encargado',
                            primerApellido: 'Cypress',
                            segundoApellido: 'Prueba',
                            telefonoPrincipal: '88880000',
                            telefonoSecundario: '',
                            correo: `cypress-${Date.now()}@correo.test`,
                            ocupacion: 'Pruebas',
                            estadoCivil: 'Soltero',
                            direccion: 'Dirección de prueba',
                            parentesco: 'Madre',
                            esResponsablePrincipal: false,
                            autorizadoRetiro: true,
                            observaciones: 'Prueba funcional HU-023',
                            contactosEmergencia: contactos,
                        },
                    }).then((response) => {

                        expect(response.status)
                            .to.be.oneOf([400, 422])
                    })
                })
        })
    })

    it('Debe permitir registrar un encargado y consultarlo posteriormente', () => {
        obtenerEstudianteMenor().then(({ token, estudiante }) => {

            obtenerTipoIdentificacion(token)
                .then((idTipoIdentificacion) => {

                    const identificacion =
                        `CYP-${Date.now()}`

                    const body = {
                        idEstudiante: estudiante.id,
                        idTipoIdentificacion,
                        numeroIdentificacion: identificacion,
                        nombres: 'Encargado',
                        primerApellido: 'Cypress',
                        segundoApellido: 'Prueba',
                        telefonoPrincipal: '88880000',
                        telefonoSecundario: '',
                        correo: `cypress-${Date.now()}@correo.test`,
                        ocupacion: 'Pruebas',
                        estadoCivil: 'Soltero',
                        direccion: 'Dirección de prueba',
                        parentesco: 'Madre',
                        esResponsablePrincipal: false,
                        autorizadoRetiro: true,
                        observaciones: 'Prueba funcional HU-023',
                        contactosEmergencia: [
                            {
                                nombre: 'Contacto Cypress',
                                telefono: '88881111',
                                relacion: 'Tía',
                            },
                        ],
                    }

                    cy.request({
                        method: 'POST',
                        url: '/api/encargados',
                        headers: {
                            Authorization: `Bearer ${token}`,
                        },
                        failOnStatusCode: false,
                        body,
                    }).then((createResponse) => {

                        expect(createResponse.status).to.eq(201)

                        const encargado = createResponse.body

                        expect(encargado.id).to.exist
                        expect(encargado.idEstudiante)
                            .to.eq(estudiante.id)

                        expect(encargado.numeroIdentificacion)
                            .to.eq(identificacion)

                        expect(encargado.nombres)
                            .to.eq('Encargado')

                        if (encargado.contactosEmergencia) {
                            expect(
                                encargado.contactosEmergencia
                            ).to.have.length(1)
                        }

                        return cy.request({
                            method: 'GET',
                            url: `/api/encargados/estudiante/${estudiante.id}`,
                            headers: {
                                Authorization: `Bearer ${token}`,
                            },
                        }).then((listResponse) => {

                            expect(listResponse.status).to.eq(200)

                            const encontrado =
                                listResponse.body.find(
                                    (item) =>
                                        item.id === encargado.id
                                )

                            expect(encontrado).to.exist
                        })
                    })
                })
        })
    })

    it('Debe permitir modificar un encargado existente', () => {
        obtenerEstudianteMenor().then(({ token, estudiante }) => {

            cy.request({
                method: 'GET',
                url: `/api/encargados/estudiante/${estudiante.id}`,
                headers: {
                    Authorization: `Bearer ${token}`,
                },
            }).then((response) => {

                expect(response.status).to.eq(200)

                const encargado = response.body[0]

                if (!encargado) {
                    cy.log(
                        'No existe un encargado previo para probar modificación.'
                    )
                    return
                }

                const original = {
                    idTipoIdentificacion:
                    encargado.tipoIdentificacion.id,
                    numeroIdentificacion:
                    encargado.numeroIdentificacion,
                    nombres:
                    encargado.nombres,
                    primerApellido:
                    encargado.primerApellido,
                    segundoApellido:
                        encargado.segundoApellido || '',
                    telefonoPrincipal:
                    encargado.telefonoPrincipal,
                    telefonoSecundario:
                        encargado.telefonoSecundario || '',
                    correo:
                        encargado.correo || '',
                    ocupacion:
                        encargado.ocupacion || '',
                    estadoCivil:
                        encargado.estadoCivil || '',
                    direccion:
                        encargado.direccion || '',
                    parentesco:
                    encargado.parentesco,
                    esResponsablePrincipal:
                        Boolean(
                            encargado.esResponsablePrincipal
                        ),
                    autorizadoRetiro:
                        Boolean(
                            encargado.autorizadoRetiro
                        ),
                    observaciones:
                        encargado.observaciones || '',
                    contactosEmergencia:
                        (encargado.contactosEmergencia || [])
                            .slice(0, 2)
                            .map((contacto) => ({
                                idContactoEmergencia:
                                contacto.id,
                                nombre:
                                contacto.nombre,
                                telefono:
                                contacto.telefono,
                                relacion:
                                contacto.relacion,
                            })),
                }

                cy.request({
                    method: 'PUT',
                    url:
                        `/api/encargados/estudiante/` +
                        `${estudiante.id}/${encargado.id}`,
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                    body: {
                        ...original,
                        observaciones:
                            'Modificado por Cypress HU-023',
                    },
                }).then((updateResponse) => {

                    expect(updateResponse.status).to.eq(200)

                    expect(
                        updateResponse.body.observaciones
                    ).to.eq(
                        'Modificado por Cypress HU-023'
                    )

                    return cy.request({
                        method: 'PUT',
                        url:
                            `/api/encargados/estudiante/` +
                            `${estudiante.id}/${encargado.id}`,
                        headers: {
                            Authorization: `Bearer ${token}`,
                        },
                        body: original,
                    })
                }).then((restoreResponse) => {

                    expect(restoreResponse.status).to.eq(200)

                    expect(
                        restoreResponse.body.numeroIdentificacion
                    ).to.eq(
                        original.numeroIdentificacion
                    )

                    expect(
                        restoreResponse.body.nombres
                    ).to.eq(
                        original.nombres
                    )
                })
            })
        })
    })
})