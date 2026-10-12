/*
 * HU-010 - Consultar cursos disponibles.
 * Configuración local opcional en frontend/cypress.env.json:
 * ESTUDIANTE_CEDULA / ESTUDIANTE_CLAVE: cuenta existente asociada a estudiante.
 * HU010_PERIODO y HU010_PROGRAMA: ID (value del select) o texto exacto de una opción real.
 * Si los filtros quedan vacíos, se selecciona la primera opción válida que devuelve la app.
 * No se crean programas, períodos, grupos ni cursos en la base.
 */

let ESTUDIANTE_CEDULA;
let ESTUDIANTE_CLAVE;
let PERIODO_CONFIGURADO;
let PROGRAMA_CONFIGURADO;
const TABLA = 'table.dtable--cards tbody tr';
const BOTON_BUSCAR = 'button[type="submit"]';

function omitirSiFaltanCredenciales(contexto) {
    const faltantes = ['ESTUDIANTE_CEDULA', 'ESTUDIANTE_CLAVE'].filter((nombre) => {
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

function seleccionarOpcionExistente(selector, configurada) {
    return cy.get(selector + ' option').then(($opciones) => {
        const opciones = [...$opciones].filter((opcion) => String(opcion.value).trim() !== '');
        let opcionElegida;

        if (configurada !== undefined && configurada !== null && String(configurada).trim() !== '') {
            const valor = String(configurada).trim();
            opcionElegida = opciones.find((opcion) =>
                String(opcion.value) === valor || opcion.textContent.trim() === valor
            );
            expect(opcionElegida, 'la opción configurada para ' + selector + ' debe existir en el catálogo').to.exist;
        } else {
            opcionElegida = opciones[0];
            expect(opcionElegida, 'debe existir una opción real para ' + selector).to.exist;
        }

        // Devolver la cadena Cypress, no un valor sincrónico después de cy.get().
        return cy.get(selector)
            .select(String(opcionElegida.value))
            .then(() => String(opcionElegida.value));
    });
}

function seleccionarFiltros() {
    return seleccionarOpcionExistente('#cmbPeriodoConsulta', PERIODO_CONFIGURADO)
        .then(() => seleccionarOpcionExistente('#cmbProgramaConsulta', PROGRAMA_CONFIGURADO));
}

function buscarCursos() {
    cy.intercept('GET', '**/api/consulta-cursos*').as('consultaCursos');
    return seleccionarFiltros().then(() => {
        cy.get(BOTON_BUSCAR).click();
        return cy.wait('@consultaCursos').then(({ response }) => {
            expect(response, 'el backend debe responder la consulta').to.exist;
            expect(response.statusCode).to.eq(200);
            expect(response.body, 'la consulta debe devolver una lista').to.be.an('array');
            return response.body;
        });
    });
}

function validarTablaContraRespuesta(cursos) {
    if (cursos.length === 0) {
        cy.contains('No hay cursos disponibles').should('be.visible');
        cy.get('table.dtable--cards').should('not.exist');
        return;
    }

    const visibles = cursos.slice(0, 10); // La interfaz pagina de 10 en 10.
    cy.get(TABLA).should('have.length', visibles.length);
    ['Curso', 'Nivel', 'Grupo', 'Horario', 'Profesor', 'Cupos']
        .forEach((columna) => cy.get('table.dtable--cards thead').contains('th', columna).should('be.visible'));

    visibles.forEach((curso, indice) => {
        cy.get(TABLA).eq(indice).within(() => {
            cy.get('td').eq(0).should('contain.text', curso.nombreCurso);
            cy.get('td').eq(0).should('contain.text', curso.codigoCurso);
            cy.get('td').eq(1).should('contain.text', curso.nivel ?? '—');
            cy.get('td').eq(2).should('contain.text', curso.codigoGrupo);
            cy.get('td').eq(3).should('not.be.empty');
            cy.get('td').eq(4).should('contain.text', curso.profesor ?? 'Por definir');
            cy.get('td').eq(5).should('contain.text',
                curso.cupoDisponible <= 0 ? 'Sin cupos' : curso.cupoDisponible + ' de ' + curso.cupoMaximo);
        });
    });
}

// No inventar cursos: los escenarios que dependen de datos se omiten cuando
// el catálogo publicado no tiene opciones reales para consultar.
function comprobarCatalogos(contexto) {
    cy.get('#cmbPeriodoConsulta option').then(($opciones) => {
        if (![...$opciones].some((opcion) => opcion.value)) {
            cy.log('No hay períodos académicos disponibles.');
            contexto.skip();
        }
    });
    cy.get('#cmbProgramaConsulta option').then(($opciones) => {
        if (![...$opciones].some((opcion) => opcion.value)) {
            cy.log('No hay programas académicos disponibles.');
            contexto.skip();
        }
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
    "ESTUDIANTE_CEDULA",
    "ESTUDIANTE_CLAVE",
    "HU010_PERIODO",
    "HU010_PROGRAMA"
]).then((valores) => {
        variablesEntorno = valores;
        ESTUDIANTE_CEDULA = valorEntorno('ESTUDIANTE_CEDULA');
        ESTUDIANTE_CLAVE = valorEntorno('ESTUDIANTE_CLAVE');
        PERIODO_CONFIGURADO = valorEntorno('HU010_PERIODO');
        PROGRAMA_CONFIGURADO = valorEntorno('HU010_PROGRAMA');
    });
});

describe('HU-010 - Consultar cursos disponibles', () => {
    beforeEach(function () {
        if (omitirSiFaltanCredenciales(this)) return;
        cy.loginComo(ESTUDIANTE_CEDULA, ESTUDIANTE_CLAVE);
        cy.intercept('GET', '**/api/periodos-academicos').as('catalogoPeriodos');
        cy.intercept('GET', '**/api/programas').as('catalogoProgramas');
        cy.visit('/estudiante/cursos-disponibles');
        cy.contains('h1', 'Cursos disponibles').should('be.visible');
        cy.wait('@catalogoPeriodos').its('response.statusCode').should('eq', 200);
        cy.wait('@catalogoProgramas').its('response.statusCode').should('eq', 200);
        cy.get('#cmbPeriodoConsulta').should('be.visible').and('not.be.disabled');
        cy.get('#cmbProgramaConsulta').should('be.visible').and('not.be.disabled');
    });

    it('muestra período, programa y texto de búsqueda opcional', () => {
        cy.get('#cmbPeriodoConsulta').should('be.visible');
        cy.get('#cmbProgramaConsulta').should('be.visible');
        cy.get('#txtFiltroCurso').should('be.visible')
            .and('have.attr', 'placeholder', 'Nombre o código del curso');
    });

    it('no permite buscar hasta seleccionar período y programa', function () {
        cy.get('#cmbPeriodoConsulta').select('');
        cy.get('#cmbProgramaConsulta').select('');
        cy.get(BOTON_BUSCAR).should('be.disabled');

        cy.get('#cmbPeriodoConsulta option').then((opciones) => {
            if (![...opciones].some((opcion) => opcion.value)) {
                cy.log('No hay períodos académicos disponibles para esta prueba.');
                this.skip();
            }
        });
        cy.get('#cmbProgramaConsulta option').then((opciones) => {
            if (![...opciones].some((opcion) => opcion.value)) {
                cy.log('No hay programas académicos disponibles para esta prueba.');
                this.skip();
            }
        });
        seleccionarOpcionExistente('#cmbPeriodoConsulta', PERIODO_CONFIGURADO);
        cy.get(BOTON_BUSCAR).should('be.disabled');
        seleccionarOpcionExistente('#cmbProgramaConsulta', PROGRAMA_CONFIGURADO);
        cy.get(BOTON_BUSCAR).should('be.enabled');
    });

    it('muestra cursos con horario, grupo, profesor y cupos devueltos por el servidor', function () {
        comprobarCatalogos(this);
        buscarCursos().then((cursos) => validarTablaContraRespuesta(cursos));
    });

    it('filtra las filas por nombre o código del curso', function () {
        comprobarCatalogos(this);
        buscarCursos().then((cursos) => {
            if (!cursos.length) {
                cy.log('No hay cursos reales para validar el filtro textual.');
                this.skip();
                return;
            }
            const termino = cursos[0].nombreCurso || cursos[0].codigoCurso;
            const esperados = cursos.filter((curso) =>
                curso.nombreCurso?.toLowerCase().includes(termino.toLowerCase()) ||
                curso.codigoCurso?.toLowerCase().includes(termino.toLowerCase()));
            cy.get('#txtFiltroCurso').clear().type(termino);
            cy.get(TABLA).should('have.length', Math.min(esperados.length, 10));
            cy.get(TABLA).each(($fila) => {
                expect($fila.text().toLowerCase()).to.include(termino.toLowerCase());
            });
        });
    });

    it('muestra un mensaje cuando ningún curso coincide con el texto', function () {
        comprobarCatalogos(this);
        buscarCursos().then((cursos) => {
            if (!cursos.length) {
                cy.log('No hay cursos para validar el estado de filtro sin coincidencias.');
                this.skip();
                return;
            }
            const textoImposible = 'ZZZ_HU010_' + Date.now();
            cy.get('#txtFiltroCurso').clear().type(textoImposible);
            cy.contains('Ningún curso coincide con «' + textoImposible + '»').should('be.visible');
            cy.get('table.dtable--cards').should('not.exist');
        });
    });

    it('Limpiar elimina texto y resultados anteriores', function () {
        comprobarCatalogos(this);
        buscarCursos();
        cy.get('#txtFiltroCurso').type('abc');
        cy.contains('button', 'Limpiar').click();
        cy.get('#txtFiltroCurso').should('have.value', '');
        cy.get('table.dtable--cards').should('not.exist');
        cy.contains('Seleccione un período y un programa').should('be.visible');
    });

    it('vuelve a consultar los cupos y representa la respuesta más reciente', function () {
        comprobarCatalogos(this);
        buscarCursos().then((primeraConsulta) => {
            if (!primeraConsulta.length) {
                cy.log('Sin cursos reales para verificar la actualización de cupos.');
                this.skip();
                return;
            }
            validarTablaContraRespuesta(primeraConsulta);
            cy.intercept('GET', '**/api/consulta-cursos*').as('consultaActualizada');
            cy.contains('button', 'Actualizar cupos').click();
            cy.wait('@consultaActualizada').then(({ response }) => {
                expect(response.statusCode).to.eq(200);
                expect(response.body).to.be.an('array');
                validarTablaContraRespuesta(response.body);
            });
        });
    });
});
