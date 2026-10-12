/*
 * HU-036 - Cambio/restablecimiento de contraseña.
 *
 * La aplicación actualmente implementa el flujo público en /recuperar-contrasena.
 * Las pruebas de interfaz usan respuestas simuladas para no enviar correos ni cambiar
 * contraseñas de cuentas reales.
 *
 * Variables locales opcionales en frontend/cypress.env.json:
 * ESTUDIANTE_CEDULA: identificación existente para el caso con API real (solo si se autoriza).
 * HU036_CEDULA_NO_REGISTRADA: identificación confirmada como no registrada.
 * HU036_PERMITIR_CORREO_REAL="SI": permite la prueba de no enumeración contra el backend real.
 *
 * La prueba que cambia la contraseña de verdad necesita leer el código del correo
 * recibido; esta suite no asume ni inventa un acceso a la bandeja de correo.
 */

const CEDULA_UI = Cypress.env('ESTUDIANTE_CEDULA') || 'HU036_CEDULA_PRUEBA';
const MENSAJE_GENERICO =
    'Si la cédula está registrada, recibirá un código en el correo de la cuenta en unos minutos.';
const MENSAJE_ERROR_CODIGO = 'El código no es válido o ya venció. Solicite uno nuevo.';
const CLAVE_VALIDA = 'NuevaClave#2026';

function stubSolicitudCodigo(statusCode = 200, body = { message: MENSAJE_GENERICO }) {
    cy.intercept('POST', '**/api/auth/recuperar-contrasena/solicitar', {
        statusCode,
        body,
    }).as('solicitarCodigo');
}

function stubConfirmacion(statusCode, body) {
    cy.intercept('POST', '**/api/auth/recuperar-contrasena/confirmar', {
        statusCode,
        body,
    }).as('confirmarRecuperacion');
}

function solicitarCodigoEnInterfaz(cedula = CEDULA_UI) {
    cy.visit('/recuperar-contrasena');
    cy.get('#cedula').clear().type(String(cedula));
    cy.contains('button', 'Enviar código').click();
    return cy.wait('@solicitarCodigo');
}

function introducirCodigoSeisDigitos(codigo = '123456') {
    cy.get('#codigo').should('be.visible').type(codigo);
}

function avanzarAlFormularioNuevaContrasena() {
    introducirCodigoSeisDigitos();
    cy.get('button').contains('Verificar código').should('be.enabled').click();
    cy.contains('h1', 'Cree una contraseña nueva').should('be.visible');
}

function escribirContrasena(contrasena, confirmacion = contrasena) {
    cy.get('#nuevaContrasena').clear().type(contrasena);
    cy.get('#confirmarContrasena').clear().type(confirmacion);
}

describe('HU-036 - Restablecer contraseña olvidada', () => {
    it('permite abrir la recuperación desde la pantalla de inicio de sesión', () => {
        cy.visit('/login');
        cy.contains('a', '¿Olvidó su contraseña?').should('be.visible').click();
        cy.location('pathname').should('eq', '/recuperar-contrasena');
        cy.contains('h1', 'Recuperar contraseña').should('be.visible');
        cy.get('#cedula').should('be.visible');
    });

    it('requiere la identificación y no solicita un código si está vacía', () => {
        cy.intercept('POST', '**/api/auth/recuperar-contrasena/solicitar').as('solicitarCodigo');
        cy.visit('/recuperar-contrasena');
        cy.contains('button', 'Enviar código').click();
        cy.contains('Escriba su número de cédula.').should('be.visible');
        cy.get('@solicitarCodigo.all').should('have.length', 0);
    });

    it('muestra una respuesta genérica al solicitar el código', () => {
        stubSolicitudCodigo();
        solicitarCodigoEnInterfaz().then(({ request, response }) => {
            expect(request.body.cedula).to.eq(String(CEDULA_UI));
            expect(response.statusCode).to.eq(200);
            expect(response.body.message).to.eq(MENSAJE_GENERICO);
        });
        cy.contains('h1', 'Revise su correo').should('be.visible');
        cy.contains('Si la cédula').should('be.visible');
        cy.contains('recibirá un código en unos minutos').should('be.visible');
    });

    it('mantiene el mismo mensaje visible para una identificación registrada y una no registrada (interfaz)', () => {
        const cedulaNoRegistrada = 'HU036_NO_REGISTRADA';
        stubSolicitudCodigo();

        solicitarCodigoEnInterfaz(CEDULA_UI).then(({ response: primera }) => {
            expect(primera.body.message).to.eq(MENSAJE_GENERICO);
        });

        cy.contains('button', 'Usar otra cédula').click();
        cy.get('#cedula').clear().type(cedulaNoRegistrada);
        cy.contains('button', 'Enviar código').click();

        cy.wait('@solicitarCodigo').then(({ request, response }) => {
            expect(request.body.cedula).to.eq(cedulaNoRegistrada);
            expect(response.statusCode).to.eq(200);
            expect(response.body.message).to.eq(MENSAJE_GENERICO);
        });
        cy.contains('Si la cédula').should('be.visible');
        cy.contains('no está registrada').should('not.exist');
    });

    it('no permite verificar hasta tener los seis dígitos y filtra caracteres no numéricos', () => {
        stubSolicitudCodigo();
        solicitarCodigoEnInterfaz();
        cy.get('#codigo').type('12ab');
        cy.get('button').contains('Verificar código').should('be.disabled');
        cy.get('[aria-label="Dígito 1 de 6"]').should('have.value', '1');
        cy.get('[aria-label="Dígito 2 de 6"]').should('have.value', '2');
        cy.get('[aria-label="Dígito 3 de 6"]').should('have.value', '');

        // Vaciar el código usando Backspace (la entrada es controlada por seis casillas).
        cy.get('[aria-label="Dígito 1 de 6"]').type('{selectall}{backspace}');
        ['1', '2', '3', '4', '5', '6'].forEach((digito, indice) => {
            cy.get('[aria-label="Dígito ' + (indice + 1) + ' de 6"]').type(digito);
        });
        cy.get('button').contains('Verificar código').should('be.enabled');
    });

    it('bloquea el reenvío inmediato del código en la interfaz', () => {
        stubSolicitudCodigo();
        solicitarCodigoEnInterfaz();
        cy.contains('button', 'Reenviar en').should('be.disabled');
    });

    it('muestra el error devuelto cuando el servicio limita solicitudes de códigos', () => {
        const mensajeLimite = 'Demasiadas solicitudes de códigos. Espere un momento e intente de nuevo.';
        stubSolicitudCodigo(400, { status: 400, message: mensajeLimite });
        cy.visit('/recuperar-contrasena');
        cy.get('#cedula').type(String(CEDULA_UI));
        cy.contains('button', 'Enviar código').click();
        cy.wait('@solicitarCodigo').its('response.statusCode').should('eq', 400);
        cy.contains(mensajeLimite).should('be.visible');
        cy.contains('h1', 'Recuperar contraseña').should('be.visible');
    });

    it('refleja una vigencia máxima de 10 minutos para el código', () => {
        // Criterio de aceptación HU-036: el código vence como máximo a los 10 minutos.
        // Actualmente frontend y backend declaran 15 minutos, por lo que este caso
        // señalará esa discrepancia hasta que se corrija la implementación.
        stubSolicitudCodigo();
        solicitarCodigoEnInterfaz();

        cy.get('#vigencia-codigo b').should('be.visible').invoke('text').then((valor) => {
            const [minutos, segundos] = valor.trim().split(':').map(Number);
            const segundosRestantes = minutos * 60 + segundos;
            expect(segundosRestantes, 'la vigencia inicial no puede superar 10 minutos')
                .to.be.at.most(10 * 60);
        });
    });

    it('mantiene deshabilitado el guardado si la contraseña y su confirmación no coinciden', () => {
        stubSolicitudCodigo();
        solicitarCodigoEnInterfaz();
        avanzarAlFormularioNuevaContrasena();
        escribirContrasena(CLAVE_VALIDA, 'OtraClave#2026');
        cy.contains('Las contraseñas no coinciden.').should('be.visible');
        cy.contains('button', 'Guardar contraseña').should('be.disabled');
    });

    it('requiere que la nueva contraseña cumpla la política y que ambas entradas coincidan', () => {
        stubSolicitudCodigo();
        solicitarCodigoEnInterfaz();
        avanzarAlFormularioNuevaContrasena();

        escribirContrasena('corta', 'corta');
        cy.contains('button', 'Guardar contraseña').should('be.disabled');

        escribirContrasena(CLAVE_VALIDA, CLAVE_VALIDA);
        cy.contains('Entre 12 y 16 caracteres').should('be.visible');
        cy.contains('Una letra mayúscula').should('be.visible');
        cy.contains('Un número').should('be.visible');
        cy.contains('Un carácter especial').should('be.visible');
        cy.contains('button', 'Guardar contraseña').should('be.enabled');
    });

    it('rechaza un código inválido o expirado y vuelve al paso de verificación', () => {
        stubSolicitudCodigo();
        stubConfirmacion(400, { status: 400, message: MENSAJE_ERROR_CODIGO });
        solicitarCodigoEnInterfaz();
        avanzarAlFormularioNuevaContrasena();
        escribirContrasena(CLAVE_VALIDA);
        cy.contains('button', 'Guardar contraseña').click();

        cy.wait('@confirmarRecuperacion').then(({ request, response }) => {
            expect(request.body.cedula).to.eq(String(CEDULA_UI));
            expect(request.body.codigo).to.eq('123456');
            expect(request.body.contrasenaNueva).to.eq(CLAVE_VALIDA);
            expect(response.statusCode).to.eq(400);
        });
        cy.contains('h1', 'Revise su correo').should('be.visible');
        cy.contains(MENSAJE_ERROR_CODIGO).should('be.visible');
        cy.get('button').contains('Verificar código').should('be.disabled');
    });

    it('presenta confirmación de éxito cuando el servidor confirma el restablecimiento (respuesta simulada)', () => {
        stubSolicitudCodigo();
        stubConfirmacion(200, { message: 'La contraseña se actualizó correctamente.' });
        solicitarCodigoEnInterfaz();
        avanzarAlFormularioNuevaContrasena();
        escribirContrasena(CLAVE_VALIDA);
        cy.contains('button', 'Guardar contraseña').click();

        cy.wait('@confirmarRecuperacion').then(({ request, response }) => {
            expect(request.body.codigo).to.eq('123456');
            expect(request.body.contrasenaNueva).to.eq(CLAVE_VALIDA);
            expect(response.statusCode).to.eq(200);
        });

        cy.contains('h1', 'Contraseña actualizada').should('be.visible');
        cy.contains('Su contraseña se actualizó correctamente. Ya puede ingresar con la nueva contraseña.')
            .should('be.visible');
        cy.contains('button', 'Ir a ingresar').click();
        cy.location('pathname').should('eq', '/login');
    });

    it('no revela si una cédula existe cuando se consulta al backend real (opcional; envía un correo)', function () {
        if (Cypress.env('HU036_PERMITIR_CORREO_REAL') !== 'SI') {
            cy.log('Omitido por seguridad. Configure HU036_PERMITIR_CORREO_REAL="SI" solo si autoriza el envío de un correo real.');
            this.skip();
            return;
        }

        const identificacionRegistrada = Cypress.env('ESTUDIANTE_CEDULA');
        const identificacionNoRegistrada = Cypress.env('HU036_CEDULA_NO_REGISTRADA');
        if (!identificacionRegistrada || !identificacionNoRegistrada) {
            cy.log('Configurar ESTUDIANTE_CEDULA y HU036_CEDULA_NO_REGISTRADA.');
            this.skip();
            return;
        }

        cy.request({
            method: 'POST',
            url: '/api/auth/recuperar-contrasena/solicitar',
            body: { cedula: identificacionRegistrada },
            failOnStatusCode: false,
        }).then((registrada) => {
            cy.request({
                method: 'POST',
                url: '/api/auth/recuperar-contrasena/solicitar',
                body: { cedula: identificacionNoRegistrada },
                failOnStatusCode: false,
            }).then((noRegistrada) => {
                expect(registrada.status).to.eq(200);
                expect(noRegistrada.status).to.eq(200);
                expect(registrada.body.message).to.eq(noRegistrada.body.message);
                expect(registrada.body.message).to.eq(MENSAJE_GENERICO);
            });
        });
    });
});

/*
 * No se automatizan aquí el código real recibido por correo, la reutilización de un
 * código ya gastado, la invalidez del anterior tras reenviar, el hash en almacenamiento
 * ni un login real tras el reset. Requieren capturar el email y/o inspeccionar datos
 * internos; el código de verificación nunca debe exponerse en la respuesta HTTP.
 */
