// Comando reutilizable para iniciar sesión en SIGAC desde la UI.
// Espera la respuesta real de /auth/login antes de continuar, para
// asegurar que el token ya quedó guardado en localStorage y las
// siguientes peticiones (catálogos, etc.) se hagan autenticadas.
// Uso: cy.loginComo('CEDULA_ADMIN', 'CLAVE_ADMIN')
Cypress.Commands.add('loginComo', (cedula, contrasena) => {
    cy.intercept('POST', '**/api/auth/login').as('login')
    cy.visit('/login')
    cy.get('#cedula').clear().type(cedula)
    cy.get('#contrasena').clear().type(contrasena)
    cy.contains('button', 'Ingresar').click()
    cy.wait('@login').its('response.statusCode').should('eq', 200)
    // Confirma que el token ya está disponible antes de que la prueba siga.
    cy.window().its('localStorage.token').should('exist')
})