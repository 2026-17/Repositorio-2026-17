/*
 * HU-009 - Dar de baja a un estudiante.
 * Variables para frontend/cypress.env.json (archivo local, no versionar):
 * PERSONAL_ACADEMICO_CEDULA / PERSONAL_ACADEMICO_CLAVE: cuenta con permiso ADMINISTRATIVO.
 * HU009_ESTUDIANTE_CON_MATRICULAS_ID / IDENTIFICACION: estudiante activo con matrículas activas.
 * HU009_ESTUDIANTE_SIN_MATRICULAS_ID / IDENTIFICACION: estudiante activo sin matrículas activas.
 * HU009_ESTUDIANTE_INACTIVO_ID: estudiante que ya está inactivo.
 * HU009_ESTUDIANTE_INEXISTENTE_ID: ID confirmado como inexistente.
 * HU009_ESTUDIANTE_INACTIVO_CEDULA / CLAVE: cuenta asociada a estudiante inactivo.
 * Opcionales para cupos: HU009_PERIODO_ID_CUPO / HU009_PROGRAMA_ID_CUPO / HU009_GRUPO_ID_CUPO.
 * HU009_PERMITIR_BAJAS debe ser exactamente "SI" para habilitar las bajas exitosas.
 * No se crean datos. Los casos destructivos se omiten por defecto; revisar el ambiente baseUrl.
 */

let PERSONAL_CEDULA;
let PERSONAL_CLAVE;
let ID_CON_MATRICULAS;
let CEDULA_CON_MATRICULAS;
let ID_SIN_MATRICULAS;
let CEDULA_SIN_MATRICULAS;
let ID_INACTIVO;
let ID_INEXISTENTE;
let CEDULA_ESTUDIANTE_INACTIVO;
let CLAVE_ESTUDIANTE_INACTIVO;
let ID_PERIODO_CUPO;
let ID_PROGRAMA_CUPO;
let ID_GRUPO_CUPO;
const MOTIVO = 'Prueba funcional automatizada HU-009';

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

function omitirSiNoSeAutorizaronBajas(contexto) {
    if (valorEntorno('HU009_PERMITIR_BAJAS') !== 'SI') {
        cy.log('Caso destructivo omitido. Configura HU009_PERMITIR_BAJAS="SI" solo si es seguro.');
        contexto.skip();
        return true;
    }
    return false;
}

function headers(token) {
    return token ? { Authorization: 'Bearer ' + token } : {};
}

function solicitarBaja(id, motivo) {
    return cy.window().then((ventana) => cy.request({
        method: 'PATCH',
        url: '/api/estudiantes/' + id + '/baja',
        headers: Object.assign({}, headers(ventana.localStorage.getItem('token')), {
            'Content-Type': 'application/json',
        }),
        body: { motivoBaja: motivo },
        failOnStatusCode: false,
    }));
}

function filaPorIdentificacion(identificacion) {
    return cy.get('tbody tr').filter((_, fila) =>
        fila.innerText.includes(String(identificacion))
    ).should('have.length', 1);
}

function abrirFormularioBaja(identificacion) {
    filaPorIdentificacion(identificacion)
        .find('button[aria-label^="Dar de baja a "]')
        .should('be.visible').click();
    cy.get('#form-baja-estudiante').should('be.visible');
}

function obtenerCupoGrupo(idPeriodo, idPrograma, idGrupo) {
    return cy.window().then((ventana) => cy.request({
        method: 'GET',
        url: '/api/consulta-cursos',
        qs: { idPeriodo: idPeriodo, idPrograma: idPrograma },
        headers: headers(ventana.localStorage.getItem('token')),
    })).then((respuesta) => {
        expect(respuesta.status).to.eq(200);
        expect(respuesta.body).to.be.an('array');
        const grupo = respuesta.body.find((item) => String(item.idGrupo) === String(idGrupo));
        expect(grupo, 'el grupo configurado debe estar en la oferta consultada').to.exist;
        return Number(grupo.cupoDisponible);
    });
}

// Cypress 16: cargar solo las variables utilizadas por esta especificación.
// cy.env() conserva las credenciales fuera de la configuración pública.
let variablesEntorno = {};
function valorEntorno(nombre) {
    return variablesEntorno[nombre];
}

before(function () {
    return cy.env([
    "PERSONAL_ACADEMICO_CEDULA",
    "PERSONAL_ACADEMICO_CLAVE",
    "HU009_ESTUDIANTE_CON_MATRICULAS_ID",
    "HU009_ESTUDIANTE_CON_MATRICULAS_IDENTIFICACION",
    "HU009_ESTUDIANTE_SIN_MATRICULAS_ID",
    "HU009_ESTUDIANTE_SIN_MATRICULAS_IDENTIFICACION",
    "HU009_ESTUDIANTE_INACTIVO_ID",
    "HU009_ESTUDIANTE_INEXISTENTE_ID",
    "HU009_ESTUDIANTE_INACTIVO_CEDULA",
    "HU009_ESTUDIANTE_INACTIVO_CLAVE",
    "HU009_PERIODO_ID_CUPO",
    "HU009_PROGRAMA_ID_CUPO",
    "HU009_GRUPO_ID_CUPO",
    "HU009_PERMITIR_BAJAS"
]).then((valores) => {
        variablesEntorno = valores;
        PERSONAL_CEDULA = valorEntorno('PERSONAL_ACADEMICO_CEDULA');
        PERSONAL_CLAVE = valorEntorno('PERSONAL_ACADEMICO_CLAVE');
        ID_CON_MATRICULAS = valorEntorno('HU009_ESTUDIANTE_CON_MATRICULAS_ID');
        CEDULA_CON_MATRICULAS = valorEntorno('HU009_ESTUDIANTE_CON_MATRICULAS_IDENTIFICACION');
        ID_SIN_MATRICULAS = valorEntorno('HU009_ESTUDIANTE_SIN_MATRICULAS_ID');
        CEDULA_SIN_MATRICULAS = valorEntorno('HU009_ESTUDIANTE_SIN_MATRICULAS_IDENTIFICACION');
        ID_INACTIVO = valorEntorno('HU009_ESTUDIANTE_INACTIVO_ID');
        ID_INEXISTENTE = valorEntorno('HU009_ESTUDIANTE_INEXISTENTE_ID');
        CEDULA_ESTUDIANTE_INACTIVO = valorEntorno('HU009_ESTUDIANTE_INACTIVO_CEDULA');
        CLAVE_ESTUDIANTE_INACTIVO = valorEntorno('HU009_ESTUDIANTE_INACTIVO_CLAVE');
        ID_PERIODO_CUPO = valorEntorno('HU009_PERIODO_ID_CUPO');
        ID_PROGRAMA_CUPO = valorEntorno('HU009_PROGRAMA_ID_CUPO');
        ID_GRUPO_CUPO = valorEntorno('HU009_GRUPO_ID_CUPO');
    });
});

describe('HU-009 - Dar de baja a un estudiante', () => {
    beforeEach(function () {
        if (omitirSiFaltan(this, ['PERSONAL_ACADEMICO_CEDULA', 'PERSONAL_ACADEMICO_CLAVE'])) return;
        cy.loginComo(PERSONAL_CEDULA, PERSONAL_CLAVE);
        cy.intercept('GET', '**/api/estudiantes/listado*').as('listadoEstudiantes');
        cy.visit('/admin/administracion/estudiantes');
        cy.contains('h1', 'Estudiantes').should('be.visible');
        cy.wait('@listadoEstudiantes').its('response.statusCode').should('eq', 200);
    });

    it('muestra el formulario y exige motivo antes de permitir la confirmación', () => {
        cy.get('button[aria-label^="Dar de baja a "]').first().click();
        cy.get('#form-baja-estudiante').should('be.visible');
        cy.get('#txtMotivoBaja').should('be.visible').and('have.value', '');
        cy.get('#form-baja-estudiante').contains('button', 'Dar de baja').click();
        cy.get('#form-baja-estudiante').should('contain.text', 'El motivo de baja es obligatorio.');
        cy.get('[role="alertdialog"]').should('not.exist');
    });

    it('permite cancelar el formulario sin cambiar el estado', () => {
        cy.get('button[aria-label^="Dar de baja a "]').first().then(($boton) => {
            const nombre = $boton.attr('aria-label').replace('Dar de baja a ', '');
            cy.wrap($boton).click();
            cy.get('#form-baja-estudiante').contains('button', 'Cancelar').click();
            cy.contains('b', nombre).closest('tr').should('contain.text', 'Activo');
        });
    });

    it('no ejecuta la baja si se cancela el diálogo de confirmación', () => {
        cy.get('button[aria-label^="Dar de baja a "]').first().then(($boton) => {
            const nombre = $boton.attr('aria-label').replace('Dar de baja a ', '');
            cy.wrap($boton).click();
            cy.get('#txtMotivoBaja').type(MOTIVO);
            cy.get('#form-baja-estudiante').contains('button', 'Dar de baja').click();
            cy.get('[role="alertdialog"]').within(() => cy.contains('button', 'Cancelar').click());
            cy.contains('b', nombre).closest('tr').should('contain.text', 'Activo');
            cy.contains('b', nombre).closest('tr').find('button[aria-label^="Dar de baja a "]').should('be.visible');
        });
    });

    it('rechaza un ID que no pertenece a un estudiante registrado', function () {
        if (omitirSiFaltan(this, ['HU009_ESTUDIANTE_INEXISTENTE_ID'])) return;
        solicitarBaja(ID_INEXISTENTE, MOTIVO).then((respuesta) => {
            expect(respuesta.status).to.eq(400);
            expect(respuesta.body.message).to.contain('no existe');
        });
    });

    it('rechaza dar de baja por segunda vez a un estudiante inactivo', function () {
        if (omitirSiFaltan(this, ['HU009_ESTUDIANTE_INACTIVO_ID'])) return;
        solicitarBaja(ID_INACTIVO, MOTIVO).then((respuesta) => {
            expect(respuesta.status).to.eq(400);
            expect(respuesta.body.message).to.contain('inactivo');
        });
    });

    it('inactiva al estudiante con matrículas, registra la fecha y conserva su expediente', function () {
        if (omitirSiFaltan(this, ['HU009_ESTUDIANTE_CON_MATRICULAS_ID', 'HU009_ESTUDIANTE_CON_MATRICULAS_IDENTIFICACION'])) return;
        if (omitirSiNoSeAutorizaronBajas(this)) return;

        const variablesCupo = ['HU009_PERIODO_ID_CUPO', 'HU009_PROGRAMA_ID_CUPO', 'HU009_GRUPO_ID_CUPO'];
        const probarCupo = variablesCupo.every((nombre) => {
            const valor = valorEntorno(nombre);
            return valor !== undefined && valor !== null && String(valor).trim() !== '';
        });
        const cupoInicial = probarCupo
            ? obtenerCupoGrupo(ID_PERIODO_CUPO, ID_PROGRAMA_CUPO, ID_GRUPO_CUPO)
            : cy.wrap(null);

        cupoInicial.then((antes) => {
            cy.intercept('PATCH', '**/api/estudiantes/' + ID_CON_MATRICULAS + '/baja').as('solicitudBaja');
            cy.get('#txtBuscarEstudiante').clear().type(CEDULA_CON_MATRICULAS);
            abrirFormularioBaja(CEDULA_CON_MATRICULAS);
            cy.get('#txtMotivoBaja').type(MOTIVO);
            cy.get('#form-baja-estudiante').contains('button', 'Dar de baja').click();
            cy.get('[role="alertdialog"]').should('contain.text', 'Se cancelarán sus matrículas activas')
                .within(() => cy.contains('button', 'Dar de baja').click());

            cy.wait('@solicitudBaja').then(({ request, response }) => {
                expect(request.body.motivoBaja).to.eq(MOTIVO);
                expect(response.statusCode).to.eq(200);
                expect(String(response.body.idEstudiante)).to.eq(String(ID_CON_MATRICULAS));
                expect(response.body.estado).to.eq('INACTIVO');
                expect(response.body.motivoBaja).to.eq(MOTIVO);
                expect(response.body.matriculasCanceladas).to.be.greaterThan(0);
                expect(response.body.cursosRetirados).to.be.greaterThan(0);
                const fecha = new Date(response.body.fechaBaja);
                expect(Number.isNaN(fecha.getTime())).to.eq(false);
                expect(fecha.getTime()).to.be.closeTo(Date.now(), 120000);
            });

            cy.get('.toast-region').should('contain.text', 'Baja realizada correctamente');
            cy.intercept({ method: 'GET', pathname: '/api/estudiantes/listado', query: { activo: 'false' } }).as('listadoInactivos');
            cy.get('#cmbEstadoEstudiante').select('false');
            cy.wait('@listadoInactivos').then(({ response }) => {
                expect(response.statusCode).to.eq(200);
                expect(response.body.some((item) => String(item.id) === String(ID_CON_MATRICULAS) && item.activo === false)).to.eq(true);
            });

            return cy.window().then((ventana) => cy.request({
                method: 'GET',
                url: '/api/estudiantes/' + ID_CON_MATRICULAS,
                headers: headers(ventana.localStorage.getItem('token')),
            })).then((respuesta) => {
                expect(respuesta.status).to.eq(200);
                expect(String(respuesta.body.id)).to.eq(String(ID_CON_MATRICULAS));
            }).then(() => {
                if (antes === null) return;
                return obtenerCupoGrupo(ID_PERIODO_CUPO, ID_PROGRAMA_CUPO, ID_GRUPO_CUPO)
                    .then((despues) => expect(despues).to.eq(antes + 1));
            });
        });
    });

    it('completa la baja con cero cancelaciones si el estudiante no tiene matrículas activas', function () {
        if (omitirSiFaltan(this, ['HU009_ESTUDIANTE_SIN_MATRICULAS_ID', 'HU009_ESTUDIANTE_SIN_MATRICULAS_IDENTIFICACION'])) return;
        if (omitirSiNoSeAutorizaronBajas(this)) return;

        cy.intercept('PATCH', '**/api/estudiantes/' + ID_SIN_MATRICULAS + '/baja').as('bajaSinMatriculas');
        cy.get('#txtBuscarEstudiante').clear().type(CEDULA_SIN_MATRICULAS);
        abrirFormularioBaja(CEDULA_SIN_MATRICULAS);
        cy.get('#txtMotivoBaja').type(MOTIVO);
        cy.get('#form-baja-estudiante').contains('button', 'Dar de baja').click();
        cy.get('[role="alertdialog"]').within(() => cy.contains('button', 'Dar de baja').click());
        cy.wait('@bajaSinMatriculas').then(({ response }) => {
            expect(response.statusCode).to.eq(200);
            expect(String(response.body.idEstudiante)).to.eq(String(ID_SIN_MATRICULAS));
            expect(response.body.estado).to.eq('INACTIVO');
            expect(response.body.motivoBaja).to.eq(MOTIVO);
            expect(response.body.matriculasCanceladas).to.eq(0);
            expect(response.body.cursosRetirados).to.eq(0);
            expect(Number.isNaN(new Date(response.body.fechaBaja).getTime())).to.eq(false);
        });
        cy.get('.toast-region').should('contain.text', 'Baja realizada correctamente');
    });
});

describe('HU-009 - Bloquear matrícula a estudiantes inactivos', () => {
    beforeEach(function () {
        if (omitirSiFaltan(this, ['HU009_ESTUDIANTE_INACTIVO_CEDULA', 'HU009_ESTUDIANTE_INACTIVO_CLAVE'])) return;
        cy.loginComo(CEDULA_ESTUDIANTE_INACTIVO, CLAVE_ESTUDIANTE_INACTIVO);
        cy.intercept('GET', '**/api/matriculas-estudiante/mi-oferta').as('miOfertaMatricula');
        cy.visit('/estudiante/matricula');
    });

    it('informa que la matrícula no está habilitada para un estudiante inactivo', () => {
        cy.wait('@miOfertaMatricula').then(({ response }) => {
            expect(response.statusCode).to.eq(200);
            expect(response.body.estudiante.activo).to.eq(false);
            expect(response.body.habilitado).to.eq(false);
            expect(response.body.motivoNoHabilitado).to.contain('Solo un estudiante en estado');
        });
        cy.contains('Matrícula no habilitada').should('be.visible');
        cy.contains('Solo un estudiante en estado «Activo» puede matricular cursos.').should('be.visible');
    });
});

/*
 * Requieren endpoint o pantalla consultable para comprobarlos automáticamente:
 * - bitácora específica de la baja y de cada desmatriculación;
 * - historial académico completo antes/después de la baja;
 * - política de conservación del expediente por 20 años.
 * El GET de expediente solo confirma que el registro sigue consultable.
 */