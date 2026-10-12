/*
 * HU-036 - Cambio/restablecimiento de contraseña.
 *
 * Estas pruebas navegan el sitio oficial y dejan pasar las peticiones al backend real.
 * Los intercepts solo observan las solicitudes; no reemplazan respuestas del servidor.
 *
 * frontend/cypress.env.json (archivo local, no versionar):
 * - HU036_CEDULA_NO_REGISTRADA: identificación confirmada como no registrada.
 * - ESTUDIANTE_CEDULA: identificación registrada (solo para no enumeración opcional).
 * - HU036_PERMITIR_CORREO_REAL="SI": autoriza un envío real para probar no enumeración.
 *
 * No se fuerza el caso de éxito del restablecimiento: requiere leer el código de un correo
 * real y cambiar la contraseña de una cuenta. No se simula ese código ni la respuesta.
 */

let CEDULA_NO_REGISTRADA;
let CEDULA_REGISTRADA;
const MENSAJE_GENERICO =
    'Si la cédula está registrada, recibirá un código en el correo de la cuenta en unos minutos.';
const MENSAJE_ERROR_CODIGO = 'El código no es válido o ya venció. Solicite uno nuevo.';
const CLAVE_VALIDA = 'NuevaClave#2026';

function omitirSiFaltan(contexto, nombres) {
    const faltantes = nombres.filter((nombre) => {
        const valor = valorEntorno(nombre);
        return valor === undefined || valor === null || String(valor).trim() === '';
    });
    if (faltantes.length) {
        cy.log('Completa frontend/cypress.env.json: ' + faltantes.join(', '));
        contexto.skip();
        return true;
    }
    return false;
}

function observarSolicitudCodigo() {
    // Sin respuesta estática: Cypress deja que la solicitud llegue al servidor real.
    cy.intercept('POST', '**/api/auth/recuperar-contrasena/solicitar').as('solicitarCodigo');
}

function observarConfirmacion() {
    // Sin respuesta estática: el backend valida el código y la contraseña.
    cy.intercept('POST', '**/api/auth/recuperar-contrasena/confirmar').as('confirmarRecuperacion');
}

function solicitarCodigoEnInterfaz(cedula) {
    observarSolicitudCodigo();
    cy.visit('/recuperar-contrasena');
    cy.get('#cedula').clear().type(String(cedula));
    cy.contains('button', 'Enviar código').click();
    return cy.wait('@solicitarCodigo');
}

function introducirCodigoSeisDigitos(codigo = '123456') {
    cy.get('#codigo').should('be.visible').type(codigo);
}

function escribirContrasena(contrasena, confirmacion = contrasena) {
    cy.get('#nuevaContrasena').clear().type(contrasena);
    cy.get('#confirmarContrasena').clear().type(confirmacion);
}

// Cypress 16: leer solo las variables requeridas mediante cy.env().
let variablesEntorno = {};
function valorEntorno(nombre) { return variablesEntorno[nombre]; }

before(function () {
    return cy.env(["HU036_CEDULA_NO_REGISTRADA","ESTUDIANTE_CEDULA","HU036_PERMITIR_CORREO_REAL"]).then((valores) => {
        variablesEntorno = valores;
        CEDULA_NO_REGISTRADA = valorEntorno('HU036_CEDULA_NO_REGISTRADA');
        CEDULA_REGISTRADA = valorEntorno('ESTUDIANTE_CEDULA');
    });
});

describe('HU-036 - Restablecer contraseña olvidada (sitio real)', () => {
    it('permite abrir la recuperación desde la pantalla de inicio de sesión', () => {
        cy.visit('/login');
        cy.contains('a', '¿Olvidó su contraseña?').should('be.visible').click();
        cy.location('pathname').should('eq', '/recuperar-contrasena');
        cy.contains('h1', 'Recuperar contraseña').should('be.visible');
        cy.get('#cedula').should('be.visible');
    });

    it('requiere la identificación y no solicita un código si está vacía', () => {
        observarSolicitudCodigo();
        cy.visit('/recuperar-contrasena');
        cy.contains('button', 'Enviar código').click();
        cy.contains('Escriba su número de cédula.').should('be.visible');
        cy.get('@solicitarCodigo.all').should('have.length', 0);
    });

    it('usa el backend real y valida el flujo de solicitud, los controles y el rechazo de un código inválido', function () {
        if (omitirSiFaltan(this, ['HU036_CEDULA_NO_REGISTRADA'])) return;

        solicitarCodigoEnInterfaz(CEDULA_NO_REGISTRADA).then(({ request, response }) => {
            expect(request.body.cedula).to.eq(String(CEDULA_NO_REGISTRADA));
            expect(response, 'el servidor real debe responder a la solicitud').to.exist;
            expect(response.statusCode).to.eq(200);
            expect(response.body.message).to.eq(MENSAJE_GENERICO);
        });

        cy.contains('h1', 'Revise su correo').should('be.visible');
        cy.contains('Si la cédula').should('be.visible');
        cy.contains('recibirá un código en unos minutos').should('be.visible');

        let vigenciaSegundos = 0;
        cy.get('#vigencia-codigo b').should('be.visible').invoke('text').then((valor) => {
            const [minutos, segundos] = valor.trim().split(':').map(Number);
            vigenciaSegundos = minutos * 60 + segundos;
        });

        cy.contains('button', 'Reenviar en').should('be.disabled');

        // La interfaz debe descartar caracteres no numéricos y exigir seis dígitos.
        cy.get('#codigo').type('12ab');
        cy.get('button').contains('Verificar código').should('be.disabled');
        cy.get('[aria-label="Dígito 1 de 6"]').should('have.value', '1');
        cy.get('[aria-label="Dígito 2 de 6"]').should('have.value', '2');
        cy.get('[aria-label="Dígito 3 de 6"]').should('have.value', '');

        cy.get('[aria-label="Dígito 1 de 6"]').type('{selectall}{backspace}');
        ['1', '2', '3', '4', '5', '6'].forEach((digito, indice) => {
            cy.get('[aria-label="Dígito ' + (indice + 1) + ' de 6"]').type(digito);
        });
        cy.get('button').contains('Verificar código').should('be.enabled').click();
        cy.contains('h1', 'Cree una contraseña nueva').should('be.visible');

        // Las validaciones de contraseña se verifican en el navegador real del sitio.
        escribirContrasena(CLAVE_VALIDA, 'OtraClave#2026');
        cy.contains('Las contraseñas no coinciden.').should('be.visible');
        cy.contains('button', 'Guardar contraseña').should('be.disabled');

        escribirContrasena('corta', 'corta');
        cy.contains('button', 'Guardar contraseña').should('be.disabled');

        escribirContrasena(CLAVE_VALIDA, CLAVE_VALIDA);
        cy.contains('Entre 12 y 16 caracteres').should('be.visible');
        cy.contains('Una letra mayúscula').should('be.visible');
        cy.contains('Un número').should('be.visible');
        cy.contains('Un carácter especial').should('be.visible');
        cy.contains('button', 'Guardar contraseña').should('be.enabled');

        // El identificador se configuró como no registrado, así que el código inventado
        // no debería permitir un cambio real de contraseña.
        observarConfirmacion();
        cy.contains('button', 'Guardar contraseña').click();
        cy.wait('@confirmarRecuperacion').then(({ request, response }) => {
            expect(request.body.cedula).to.eq(String(CEDULA_NO_REGISTRADA));
            expect(request.body.codigo).to.eq('123456');
            expect(request.body.contrasenaNueva).to.eq(CLAVE_VALIDA);
            expect(response.statusCode).to.eq(400);
            expect(response.body.message).to.eq(MENSAJE_ERROR_CODIGO);
        });

        cy.contains('h1', 'Revise su correo').should('be.visible');
        cy.contains(MENSAJE_ERROR_CODIGO).should('be.visible');

        // Se valida al final para que el resto de controles se pruebe aunque el entorno
        // publicado todavía tenga una vigencia mayor que el criterio de aceptación.
        expect(vigenciaSegundos, 'la vigencia inicial no puede superar 10 minutos')
            .to.be.at.most(10 * 60);
    });

    it('mantiene una respuesta genérica para cédula registrada y no registrada (opcional; envía correo real)', function () {
        if (valorEntorno('HU036_PERMITIR_CORREO_REAL') !== 'SI') {
            cy.log('Omitido por seguridad. Configure HU036_PERMITIR_CORREO_REAL="SI" solo con autorización para enviar un correo real.');
            this.skip();
            return;
        }
        if (omitirSiFaltan(this, ['ESTUDIANTE_CEDULA', 'HU036_CEDULA_NO_REGISTRADA'])) return;

        solicitarCodigoEnInterfaz(CEDULA_REGISTRADA).then(({ response }) => {
            expect(response.statusCode).to.eq(200);
            expect(response.body.message).to.eq(MENSAJE_GENERICO);
        });
        cy.contains('Si la cédula').should('be.visible');

        cy.contains('button', 'Usar otra cédula').click();
        cy.get('#cedula').clear().type(String(CEDULA_NO_REGISTRADA));
        cy.contains('button', 'Enviar código').click();
        cy.wait('@solicitarCodigo').then(({ request, response }) => {
            expect(request.body.cedula).to.eq(String(CEDULA_NO_REGISTRADA));
            expect(response.statusCode).to.eq(200);
            expect(response.body.message).to.eq(MENSAJE_GENERICO);
        });
        cy.contains('Si la cédula').should('be.visible');
        cy.contains('no está registrada').should('not.exist');
    });
});

/*
 * Cobertura aún pendiente para un entorno de pruebas con buzón accesible:
 * - código de correo correcto y vencimiento real después de 10 minutos;
 * - reutilización del código, invalidación al reenviar y cambio de contraseña exitoso;
 * - login real con la nueva contraseña y revisión segura del hash persistido.
 *
 * No se simulan respuestas del servidor ni se realizan escrituras exitosas sobre cuentas
 * reales sin un buzón y una cuenta de prueba identificados explícitamente.
 */
