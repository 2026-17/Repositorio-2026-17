// HU-001 - Registrar estudiante
// Ruta: /admin/administracion/estudiantes/crear
// Ajustar credenciales según los usuarios reales del ambiente de pruebas.

const ADMIN_CEDULA = '456456456'
const ADMIN_PASSWORD = '12345678'

// Devuelve YYYY-MM-DD usando la fecha LOCAL (evita el desfase de
// toISOString(), que convierte a UTC y puede correr el día/año).
function fechaLocalISO(date) {
    const y = date.getFullYear()
    const m = String(date.getMonth() + 1).padStart(2, '0')
    const d = String(date.getDate()).padStart(2, '0')
    return `${y}-${m}-${d}`
}

// Escribe un valor en un <input type="date"> usando el setter nativo del
// prototipo de HTMLInputElement antes de disparar el evento 'input'.
// Esto es necesario porque React intercepta el setter de .value para
// detectar cambios; escribir con .val()/.invoke('val') o cy.type() en
// inputs de fecha no siempre dispara ese setter interceptado, así que
// React nunca actualiza su estado y la UI no reacciona.
function setInputDate(selector, isoDate) {
    cy.get(selector)
        .first()
        .then(($el) => {
            const input = $el[0]
            const nativeInputValueSetter = Object.getOwnPropertyDescriptor(
                window.HTMLInputElement.prototype,
                'value'
            ).set
            nativeInputValueSetter.call(input, isoDate)
            input.dispatchEvent(new Event('input', { bubbles: true }))
            input.dispatchEvent(new Event('change', { bubbles: true }))
        })
}

describe('HU-001 - Registrar estudiante', () => {

    beforeEach(() => {
        cy.loginComo(ADMIN_CEDULA, ADMIN_PASSWORD)
        cy.visit('/admin/administracion/estudiantes/crear')
    })

    it('Debe mostrar el formulario de registro con sus secciones principales', () => {
        cy.contains('h1', 'Registrar estudiante').should('be.visible')
        cy.contains('h2', 'Datos personales').should('be.visible')
        cy.contains('h2', 'Información académica').should('be.visible')
        cy.contains('h2', 'Documentación').should('be.visible')
    })

    it('No debe permitir guardar si faltan los campos requeridos', () => {
        cy.contains('button', 'Guardar estudiante').click()
        // El formulario usa noValidate + validación propia; no debe navegar ni limpiar
        cy.contains('h2', 'Datos personales').should('be.visible')
    })

    it('Debe calcular la edad automáticamente y mostrar la sección de encargados si el estudiante es menor de edad', () => {
        const fechaMenor = new Date()
        fechaMenor.setFullYear(fechaMenor.getFullYear() - 10) // 10 años
        const isoMenor = fechaLocalISO(fechaMenor)

        setInputDate('input[type="date"]', isoMenor)
        cy.contains('10 años').should('be.visible')
        cy.contains('Menor de edad').should('be.visible')
        cy.contains('h2', 'Encargados').should('be.visible')
        cy.contains('Requerido para menores de edad').should('be.visible')
    })

    it('Debe mostrar "Mayor de edad" y ocultar la sección de encargados si el estudiante es mayor de edad', () => {
        const fechaMayor = new Date()
        fechaMayor.setFullYear(fechaMayor.getFullYear() - 25) // 25 años
        const isoMayor = fechaLocalISO(fechaMayor)

        setInputDate('input[type="date"]', isoMayor)
        cy.contains('25 años').should('be.visible')
        cy.contains('Mayor de edad').should('be.visible')
        cy.contains('h2', 'Encargados').should('not.exist')
    })

    it('Debe validar el formato de correo electrónico', () => {
        cy.contains('label', 'Correo electrónico')
            .parent()
            .find('input')
            .type('correo-invalido')
        cy.contains('button', 'Guardar estudiante').click()
        cy.contains('Ingrese un correo electrónico válido').should('be.visible')
    })

    it('Debe rechazar un archivo que no sea PDF para la póliza y aceptar uno PDF válido', () => {
        // Adjunta un archivo que no es PDF y confirma el mensaje de error al enviar
        cy.get('input[type="file"]').selectFile(
            { contents: Cypress.Buffer.from('contenido'), fileName: 'poliza.txt', mimeType: 'text/plain' },
            { force: true }
        )
        cy.contains('button', 'Guardar estudiante').click()
        cy.contains('La póliza debe ser un archivo PDF').should('be.visible')

        // Reemplaza por un PDF válido
        cy.get('input[type="file"]').selectFile(
            { contents: Cypress.Buffer.from('%PDF-1.4 contenido de prueba'), fileName: 'poliza.pdf', mimeType: 'application/pdf' },
            { force: true }
        )
        cy.contains('poliza.pdf').should('be.visible')
    })

    it('Debe permitir completar el formulario y registrar un estudiante exitosamente', () => {
        const cedula = `TEST${Date.now()}`

        cy.get('select').eq(0).select(1) // Tipo de identificación
        cy.contains('label', 'Número de identificación').parent().find('input').type(cedula)
        cy.contains('label', 'Nombres').parent().find('input').type('Estudiante')
        cy.contains('label', 'Primer apellido').parent().find('input').type('Prueba')

        cy.contains('label', 'Género').parent().find('select').select('MASCULINO')

        const fechaMayor = new Date()
        fechaMayor.setFullYear(fechaMayor.getFullYear() - 20)
        setInputDate('input[type="date"]', fechaLocalISO(fechaMayor))

        cy.contains('label', 'Dirección exacta de residencia').parent().find('textarea').type('San José, Costa Rica')
        cy.contains('label', 'Correo electrónico').parent().find('input').type(`${cedula}@correo.com`)

        cy.contains('label', 'Nivel escolar').parent().find('select').select(1)
        cy.contains('label', 'Instrumento musical').parent().find('select').select(1)

        cy.get('input[type="file"]').selectFile(
            { contents: Cypress.Buffer.from('%PDF-1.4 contenido de prueba'), fileName: 'poliza.pdf', mimeType: 'application/pdf' },
            { force: true }
        )

        cy.contains('button', 'Guardar estudiante').click()

        // El sistema debe mostrar confirmación de registro exitoso
        cy.contains('registrado', { matchCase: false }).should('be.visible')
    })
})
