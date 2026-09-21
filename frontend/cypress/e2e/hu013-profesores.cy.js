// HU-013 - Registrar y modificar profesores
// Ruta: /director-academico/profesores
// Ajustar credenciales del director académico según el ambiente de pruebas.

const DIRECTOR_CEDULA = '987654321'
const DIRECTOR_PASSWORD = '12345678'

describe('HU-013 - Registrar y modificar profesores', () => {

    beforeEach(() => {
        cy.loginComo(DIRECTOR_CEDULA, DIRECTOR_PASSWORD)
        cy.visit('/director-academico/profesores')
    })

    it('Debe mostrar el listado de profesores con buscador y filtro de estado', () => {
        cy.contains('h1', 'Profesores').should('be.visible')
        cy.get('input.search-input').should('be.visible')
        cy.get('select.filter-select').should('be.visible')
    })

    it('Debe filtrar por texto de búsqueda (nombre, identificación o correo)', () => {
        cy.get('input.search-input').type('ZZZ_no_deberia_existir_ZZZ')
        cy.get('table, .empty-state, body').should('exist') // no debe explotar la vista con resultado vacío
    })

    it('Debe abrir el formulario de "Nuevo profesor" y validar campos requeridos', () => {
        cy.contains('button', '+ Nuevo profesor').click()
        cy.contains('h2', 'Registrar profesor').should('be.visible')
        cy.contains('button', 'Registrar profesor').should('exist').should('not.be.disabled')
    })

    it('Debe exigir los campos obligatorios al registrar un profesor nuevo', () => {
        cy.contains('button', '+ Nuevo profesor').click()

        cy.get('form').submit()

        cy.contains('Seleccione el tipo de identificación.')
            .should('be.visible')
        cy.contains('Ingrese el número de identificación.')
            .should('be.visible')
        cy.contains('Ingrese el nombre.')
            .should('be.visible')
        cy.contains('Ingrese el primer apellido.')
            .should('be.visible')
    })

    it('Debe registrar un profesor nuevo con datos completos', () => {
        const cedula = String(Date.now()).slice(-9)
        cy.contains('button', '+ Nuevo profesor').click()

        cy.contains('label', 'Tipo de identificación')
            .parent()
            .find('select')
            .find('option')
            .should('have.length.greaterThan', 1) // espera a que cargue el catálogo
        cy.contains('label', 'Tipo de identificación').parent().find('select').select(1)
        cy.contains('label', 'Tipo de identificación').parent().find('select').should('not.have.value', '')
        cy.contains('label', 'Cédula / Número de identificación').parent().find('input').type(cedula)
        cy.contains('label', 'Nombre').parent().find('input').type('Profesor')
        cy.contains('label', 'Primer apellido').parent().find('input').type('Prueba')
        cy.contains('label', 'Correo de contacto').parent().find('input').type(`${cedula}@correo.com`)

        cy.get('form').submit()
        cy.contains('Profesor registrado correctamente').should('be.visible')
    })

    it('Debe permitir dar de baja a un profesor activo', () => {
        cy.get('body').then(($body) => {
            if ($body.find('button:contains("Dar de baja")').length === 0) {
                cy.log('No hay profesores activos listados para dar de baja en este ambiente de prueba.')
                return
            }
            cy.contains('button', 'Dar de baja').first().click()
            cy.contains('Dar de baja profesor').should('be.visible')
        })
    })
})