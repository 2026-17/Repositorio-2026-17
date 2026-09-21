// HU-030 - Modificar curso
// Ruta: /director-academico/cursos/modificar

const DIRECTOR_CEDULA = '987654321'
const DIRECTOR_PASSWORD = '12345678'

function obtenerToken() {
    return cy.window()
        .its('localStorage.token')
        .should('exist')
}

function obtenerCursoActivo() {
    return obtenerToken().then((token) => {

        return cy.request({
            method: 'GET',
            url: '/api/cursos',
            headers: {
                Authorization: `Bearer ${token}`,
            },
        }).then((response) => {

            expect(response.status).to.eq(200)

            expect(response.body)
                .to.be.an('array')
                .and.not.be.empty

            return response.body[0]
        })
    })
}

describe('HU-030 - Modificar curso', () => {

    beforeEach(() => {
        cy.loginComo(DIRECTOR_CEDULA, DIRECTOR_PASSWORD)
        cy.visit('/director-academico/cursos/modificar')
    })

    it('Debe mostrar el buscador de cursos', () => {
        cy.contains(
            'h1',
            'Modificar curso'
        ).should('be.visible')

        cy.get('#buscarCurso')
            .should('be.visible')

        cy.contains(
            '2.',
            { matchCase: false }
        ).should('not.exist')
    })

    it('Debe permitir seleccionar un curso activo', () => {
        obtenerCursoActivo().then((curso) => {

            cy.get('#buscarCurso')
                .clear()
                .type(curso.codigo)

            cy.contains(
                'article.modificar-curso-card',
                curso.codigo
            )
                .should('be.visible')
                .find('button')
                .click()

            cy.get('#codigo')
                .should('be.visible')
                .and('have.value', curso.codigo)

            cy.get('#nombre')
                .should('be.visible')

            cy.get('#descripcion')
                .should('be.visible')

            cy.get('#horasTotales')
                .should('be.visible')

            cy.get('#nivel')
                .should('be.visible')

            cy.get('#idPrograma')
                .should('be.visible')
        })
    })

    it('No debe permitir guardar mientras no haya cambios', () => {
        obtenerCursoActivo().then((curso) => {

            cy.get('#buscarCurso')
                .clear()
                .type(curso.codigo)

            cy.contains(
                'article.modificar-curso-card',
                curso.codigo
            )
                .find('button')
                .click()

            cy.contains(
                'button',
                'Guardar cambios'
            )
                .should('be.disabled')
        })
    })

    it('Debe detectar los cambios realizados en el formulario', () => {
        obtenerCursoActivo().then((curso) => {

            cy.get('#buscarCurso')
                .clear()
                .type(curso.codigo)

            cy.contains(
                'article.modificar-curso-card',
                curso.codigo
            )
                .find('button')
                .click()

            const nombreOriginal =
                curso.nombre

            const nombreNuevo =
                `${nombreOriginal} [Cypress]`

            cy.get('#nombre')
                .clear()
                .type(nombreNuevo)

            cy.contains(
                'button',
                'Guardar cambios'
            )
                .should('not.be.disabled')

            cy.contains(
                'campo',
                { matchCase: false }
            ).should('be.visible')
        })
    })

    it('Debe modificar el curso y luego restaurar sus valores originales', () => {
        obtenerCursoActivo().then((curso) => {

            const nombreOriginal =
                curso.nombre

            const descripcionOriginal =
                curso.descripcion || ''

            const horasOriginales =
                curso.horasTotales

            const nivelOriginal =
                curso.nivel

            const programaOriginal =
                curso.idPrograma

            const nombreNuevo =
                `${nombreOriginal} [Cypress]`

            cy.get('#buscarCurso')
                .clear()
                .type(curso.codigo)

            cy.contains(
                'article.modificar-curso-card',
                curso.codigo
            )
                .find('button')
                .click()

            cy.get('#nombre')
                .clear()
                .type(nombreNuevo)

            cy.intercept(
                'PUT',
                `**/api/cursos/${curso.idCurso}`
            ).as('modificarCurso')

            cy.contains(
                'button',
                'Guardar cambios'
            ).click()

            cy.get('[role="alertdialog"]')
                .should('be.visible')
                .within(() => {

                    cy.contains(
                        'h2',
                        'Confirmar modificación'
                    ).should('be.visible')

                    cy.contains(
                        'button',
                        'Confirmar cambios'
                    ).click()
                })

            cy.wait('@modificarCurso')
                .its('response.statusCode')
                .should('eq', 200)

            cy.get('[role="alertdialog"]')
                .should('be.visible')
                .within(() => {

                    cy.contains(
                        'El curso'
                    ).should('contain.text', curso.codigo)

                    cy.contains(
                        'button',
                        'Aceptar'
                    ).click()
                })

            cy.get('#nombre')
                .should('have.value', nombreNuevo)

            obtenerToken().then((token) => {

                cy.request({
                    method: 'PUT',
                    url:
                        `/api/cursos/${curso.idCurso}`,
                    headers: {
                        Authorization:
                            `Bearer ${token}`,
                        'Content-Type':
                            'application/json',
                    },
                    body: {
                        nombre: nombreOriginal,
                        descripcion: descripcionOriginal,
                        horasTotales: horasOriginales,
                        idPrograma: programaOriginal,
                        nivel: nivelOriginal,
                    },
                }).then((restoreResponse) => {

                    expect(
                        restoreResponse.status
                    ).to.eq(200)

                    expect(
                        restoreResponse.body.nombre
                    ).to.eq(nombreOriginal)
                })
            })
        })
    })

    it('Debe validar que el nombre no quede vacío', () => {
        obtenerCursoActivo().then((curso) => {

            cy.get('#buscarCurso')
                .clear()
                .type(curso.codigo)

            cy.contains(
                'article.modificar-curso-card',
                curso.codigo
            )
                .find('button')
                .click()

            cy.get('#nombre')
                .clear()

            cy.get('#horasTotales')
                .invoke('val')
                .then((valorHoras) => {

                    expect(valorHoras)
                        .to.not.be.empty

                    cy.get('#nombre')
                        .type('x')
                        .clear()

                    cy.get('form')
                        .submit()

                    cy.contains(
                        '[role="alert"]',
                        'Ingrese el nombre del curso.'
                    ).should('be.visible')
                })
        })
    })
})