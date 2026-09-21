const ADMIN_CEDULA = '456456456'
const ADMIN_PASSWORD = '12345678'

describe('HU-023 - Registrar encargados', () => {
  beforeEach(() => {
    cy.loginComo(ADMIN_CEDULA, ADMIN_PASSWORD)
    cy.visit('/admin/administracion/estudiantes')
    cy.contains('button', 'Nuevo estudiante').click()
    cy.contains('h1', 'Registrar estudiante').should('be.visible')
  })

  it('Debe mostrar la sección de encargados para un estudiante menor de edad', () => {
    cy.get('input[type="date"]').first().type('2012-01-15')
    cy.contains('h2', 'Encargados').should('be.visible')
  })

  it('Debe permitir agregar hasta dos encargados', () => {
    cy.get('input[type="date"]').first().type('2012-01-15')
    cy.contains('+ Agregar encargado').click()
    cy.contains('strong', 'Encargado 1').should('be.visible')
    cy.contains('+ Agregar encargado').click()
    cy.contains('strong', 'Encargado 2').should('be.visible')
    cy.contains('+ Agregar encargado').should('be.disabled')
  })

  it('Debe impedir guardar un menor sin encargados', () => {
    cy.intercept('POST', '**/api/estudiantes').as('crearEstudiante')
    cy.get('input[type="date"]').first().type('2012-01-15')
    cy.contains('button', 'Guardar estudiante').click()
    cy.get('@crearEstudiante.all').should('have.length', 0)
    cy.get('[role="alertdialog"]').should('be.visible')
      .within(() => cy.contains('h2', 'Hay campos por corregir').should('be.visible'))
  })

  it('Debe exigir exactamente un responsable principal', () => {
    cy.get('input[type="date"]').first().type('2012-01-15')
    cy.contains('+ Agregar encargado').click()
    cy.intercept('POST', '**/api/estudiantes').as('crearEstudiante')
    cy.contains('button', 'Guardar estudiante').click()
    cy.get('@crearEstudiante.all').should('have.length', 0)
    cy.get('[role="alertdialog"]').should('be.visible')
      .within(() => cy.contains('Debe existir exactamente un responsable principal.').should('be.visible'))
  })
})