// HU-028 - Crear curso en malla curricular
// Ruta: /director-academico/cursos/crear

const DIRECTOR_CEDULA = '987654321'
const DIRECTOR_PASSWORD = '12345678'

describe('HU-028 - Crear curso en malla curricular', () => {

    beforeEach(() => {
        cy.loginComo(DIRECTOR_CEDULA, DIRECTOR_PASSWORD)
        cy.visit('/director-academico/cursos/crear')
    })

    it('Debe mostrar todos los campos principales del formulario', () => {
        cy.contains('h1', 'Crear curso')
            .should('be.visible')

        cy.get('#codigo')
            .should('be.visible')

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

        cy.get('#cupos')
            .should('be.visible')

        cy.get('#idPeriodo')
            .should('be.visible')
    })

    it('Debe validar los campos obligatorios', () => {
        cy.get('form').submit()

        cy.contains(
            '[role="alert"]',
            'Ingrese el código del curso.'
        ).should('be.visible')

        cy.contains(
            '[role="alert"]',
            'Ingrese el nombre del curso.'
        ).should('be.visible')

        cy.contains(
            '[role="alert"]',
            'La cantidad de horas debe ser mayor que cero.'
        ).should('be.visible')

        cy.contains(
            '[role="alert"]',
            'Seleccione un programa académico.'
        ).should('be.visible')

        cy.contains(
            '[role="alert"]',
            'El nivel académico debe ser mayor que cero.'
        ).should('be.visible')

        cy.contains(
            '[role="alert"]',
            'La cantidad de cupos debe ser mayor que cero.'
        ).should('be.visible')

        cy.contains(
            '[role="alert"]',
            'Seleccione un período lectivo.'
        ).should('be.visible')
    })

    it('Debe cargar programas y períodos académicos', () => {
        cy.get('#idPrograma')
            .find('option')
            .not('[value=""]')
            .should('have.length.greaterThan', 0)

        cy.get('#idPeriodo')
            .find('option')
            .not('[value=""]')
            .should('have.length.greaterThan', 0)
    })

    it('Debe crear un curso con datos válidos y confirmar la operación', () => {
        const codigo =
            `CYP-HU028-${Date.now()}`

        const nombre =
            'Curso creado por Cypress HU-028'

        cy.get('#codigo')
            .type(codigo)

        cy.get('#nombre')
            .type(nombre)

        cy.get('#descripcion')
            .type('Prueba funcional de la HU-028')

        cy.get('#horasTotales')
            .type('40')

        cy.get('#nivel')
            .type('1')

        cy.get('#idPrograma')
            .find('option')
            .not('[value=""]')
            .first()
            .then(($option) => {

                const programaId =
                    $option.val()

                expect(programaId)
                    .to.not.eq('')

                cy.get('#idPrograma')
                    .select(programaId)
            })

        cy.get('#idPeriodo')
            .find('option')
            .not('[value=""]')
            .first()
            .then(($option) => {

                const periodoId =
                    $option.val()

                expect(periodoId)
                    .to.not.eq('')

                cy.get('#idPeriodo')
                    .select(periodoId)
            })

        cy.get('#cupos')
            .type('20')

        cy.intercept(
            'POST',
            '**/api/cursos'
        ).as('crearCurso')

        cy.contains(
            'button',
            'Crear curso'
        ).click()

        cy.wait('@crearCurso')
            .then(({ response }) => {

                expect(response.statusCode)
                    .to.eq(201)

                expect(response.body)
                    .to.have.property('codigo')
                    .that.equals(codigo)

                expect(response.body)
                    .to.have.property('nombre')
                    .that.equals(nombre)

                expect(response.body)
                    .to.have.property('idCurso')

                const idCurso =
                    response.body.idCurso

                return cy.window()
                    .its('localStorage.token')
                    .then((token) => {

                        return cy.request({
                            method: 'PATCH',
                            url:
                                `/api/cursos/${idCurso}/desactivar`,
                            headers: {
                                Authorization:
                                    `Bearer ${token}`,
                            },
                            failOnStatusCode: false,
                        })
                    })
            })

        cy.get('[role="alertdialog"]')
            .should('be.visible')
            .within(() => {

                cy.contains(
                    'h2',
                    'Operación exitosa'
                ).should('be.visible')

                cy.contains(
                    'El curso'
                ).should('contain.text', codigo)

                cy.contains(
                    'button',
                    'Aceptar'
                ).click()
            })
    })

    it('Debe rechazar un código de curso vacío', () => {
        cy.get('#nombre')
            .type('Curso de prueba')

        cy.get('#horasTotales')
            .type('40')

        cy.get('#nivel')
            .type('1')

        cy.get('#idPrograma')
            .find('option')
            .not('[value=""]')
            .first()
            .then(($option) => {
                cy.get('#idPrograma')
                    .select($option.val())
            })

        cy.get('#idPeriodo')
            .find('option')
            .not('[value=""]')
            .first()
            .then(($option) => {
                cy.get('#idPeriodo')
                    .select($option.val())
            })

        cy.get('#cupos')
            .type('20')

        cy.get('form')
            .submit()

        cy.contains(
            '[role="alert"]',
            'Ingrese el código del curso.'
        ).should('be.visible')
    })
})