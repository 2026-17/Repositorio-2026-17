// HU-032 - Configurar prerrequisitos y correquisitos de un curso
// La configuración está integrada en /director-academico/cursos/modificar.

const DIRECTOR_CEDULA = '987654321'
const DIRECTOR_PASSWORD = '12345678'

let cleanupRequisitos = null

function obtenerToken() {
    return cy.window().its('localStorage.token').should('exist')
}

function obtenerCursoActivo() {
    return obtenerToken().then((token) => cy.request({
        method: 'GET',
        url: '/api/cursos',
        headers: { Authorization: `Bearer ${token}` },
    }).then((response) => {
        expect(response.status).to.eq(200)
        expect(response.body).to.be.an('array').and.not.be.empty
        return response.body[0]
    }))
}

describe('HU-032 - Configurar prerrequisitos y correquisitos', () => {
    beforeEach(() => {
        cleanupRequisitos = null
        cy.loginComo(DIRECTOR_CEDULA, DIRECTOR_PASSWORD)
        cy.visit('/director-academico/cursos/modificar')
    })

    it('Debe guardar prerrequisitos, correquisitos y edades, validar la configuración y restaurar los datos originales', () => {
        obtenerCursoActivo().then((curso) => {
            obtenerToken().then((token) => {
                cy.request({
                    method: 'GET',
                    url: '/api/cursos/' + curso.idCurso,
                    headers: { Authorization: 'Bearer ' + token },
                }).then((detalleResponse) => {
                    expect(detalleResponse.status).to.eq(200)
                    const detalle = detalleResponse.body

                    cy.get('#buscarCurso').clear().type(curso.codigo)
                    cy.contains('article.modificar-curso-card', curso.codigo).find('button').click()

                    cy.intercept('GET', '**/api/requisitos-cursos/opciones*').as('opcionesRequisitos')
                    cy.contains('button', 'Configurar requisitos').should('be.visible').click()
                    cy.wait('@opcionesRequisitos').then(({ response }) => {
                        expect(response.statusCode).to.eq(200)
                        const original = response.body
                        expect(original.cursos).to.be.an('array').and.not.be.empty
                        expect(original.cursos.some((c) => String(c.idCurso) === String(curso.idCurso))).to.be.false

                        const primero = original.cursos[0].idCurso
                        const segundo = (original.cursos.length > 1 ? original.cursos[1] : original.cursos[0]).idCurso
                        const alternar = (ids, id) => (ids || []).some((x) => String(x) === String(id))
                            ? ids.filter((x) => String(x) !== String(id))
                            : [...(ids || []), id]
                        const esperadosPre = alternar(original.prerrequisitos, primero)
                        const esperadosCo = alternar(original.correquisitos, segundo)

                        cleanupRequisitos = {
                            idCurso: curso.idCurso,
                            payload: {
                                nombre: detalle.nombre,
                                descripcion: detalle.descripcion || '',
                                horasTotales: detalle.horasTotales,
                                idPrograma: Number(detalle.idPrograma),
                                nivel: detalle.nivel,
                                requisitos: {
                                    prerrequisitos: original.prerrequisitos || [],
                                    correquisitos: original.correquisitos || [],
                                    edadMinima: original.edadMinima == null ? null : original.edadMinima,
                                    edadMaxima: original.edadMaxima == null ? null : original.edadMaxima,
                                },
                            },
                        }

                        cy.get('.requisitos-curso__grid section').eq(0)
                            .find('label.requisito-card input[type="checkbox"]').first().click()
                        cy.get('.requisitos-curso__grid section').eq(1)
                            .find('label.requisito-card input[type="checkbox"]').first().click()
                        cy.contains('label', 'Edad mínima').find('input').clear().type('8')
                        cy.contains('label', 'Edad máxima').find('input').clear().type('18')

                        cy.intercept('PUT', '**/api/cursos/' + curso.idCurso).as('guardarRequisitos')
                        cy.contains('button', 'Guardar cambios').should('not.be.disabled').click()
                        cy.get('[role="alertdialog"]').should('be.visible').within(() => {
                            cy.contains('h2', 'Confirmar modificación').should('be.visible')
                            cy.contains('button', 'Confirmar cambios').click()
                        })
                        cy.wait('@guardarRequisitos').then(({ response: saved }) => {
                            expect(saved.statusCode).to.eq(200)
                        })
                        cy.get('[role="alertdialog"]').should('be.visible').within(() => {
                            cy.contains('El curso').should('contain.text', curso.codigo)
                            cy.contains('button', 'Aceptar').click()
                        })

                        cy.request({
                            method: 'GET',
                            url: '/api/requisitos-cursos/opciones?idPrograma=' + detalle.idPrograma + '&idCurso=' + curso.idCurso,
                            headers: { Authorization: 'Bearer ' + token },
                        }).then((verifyResponse) => {
                            expect(verifyResponse.status).to.eq(200)
                            expect(verifyResponse.body.prerrequisitos.map(String).sort()).to.deep.eq(esperadosPre.map(String).sort())
                            expect(verifyResponse.body.correquisitos.map(String).sort()).to.deep.eq(esperadosCo.map(String).sort())
                            expect(verifyResponse.body.edadMinima).to.eq(8)
                            expect(verifyResponse.body.edadMaxima).to.eq(18)
                        })
                    })
                })
            })
        })
    })

    afterEach(() => {
        if (!cleanupRequisitos) return
        cy.window().then((win) => {
            const token = win.localStorage.getItem('token')
            if (!token) return
            return cy.request({
                method: 'PUT',
                url: '/api/cursos/' + cleanupRequisitos.idCurso,
                headers: {
                    Authorization: 'Bearer ' + token,
                    'Content-Type': 'application/json',
                },
                body: cleanupRequisitos.payload,
                failOnStatusCode: false,
            }).then((response) => {
                expect(response.status, 'Debe restaurar la configuración original tras la prueba').to.eq(200)
            })
        })
    })

})