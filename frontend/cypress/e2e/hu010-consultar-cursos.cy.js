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
const TABLA = 'table.oferta-tabla tbody tr';
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

        cy.get(selector).select(String(opcionElegida.value));
        return String(opcionElegida.value);
    });
}

function seleccionarFiltros() {
    return seleccionarOpcionExistente('#idPeriodo', PERIODO_CONFIGURADO)
        .then(() => seleccionarOpcionExistente('#idPrograma', PROGRAMA_CONFIGURADO));
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
        cy.contains('[role="status"]', 'No se encontraron cursos disponibles').should('be.visible');
        cy.get('table.oferta-tabla').should('not.exist');
        return;
    }

    cy.get(TABLA).should('have.length', cursos.length);
    ['Código', 'Curso', 'Nivel', 'Grupo', 'Horario', 'Profesor', 'Cupos disponibles']
        .forEach((columna) => cy.contains('th', columna).should('be.visible'));

    cursos.forEach((curso, indice) => {
        cy.get(TABLA).eq(indice).within(() => {
            cy.get('td').eq(0).should('contain.text', curso.codigoCurso);
            cy.get('td').eq(1).should('contain.text', curso.nombreCurso);
            cy.get('td').eq(2).should('contain.text', curso.nivel === null || curso.nivel === undefined ? '—' : curso.nivel);
            cy.get('td').eq(3).should('contain.text', curso.codigoGrupo);
            cy.get('td').eq(4).should('not.be.empty');
            cy.get('td').eq(5).should('contain.text', curso.profesor || 'Por definir');
            if (curso.cupoDisponible <= 0) {
                cy.get('td').eq(6).should('contain.text', 'Sin cupos');
            } else {
                cy.get('td').eq(6).should('contain.text', String(curso.cupoDisponible) + '/' + String(curso.cupoMaximo));
            }
        });
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
        cy.get('#idPeriodo option').should('have.length.greaterThan', 1);
        cy.get('#idPrograma option').should('have.length.greaterThan', 1);
    });

    it('muestra período, programa y texto de búsqueda opcional', () => {
        cy.get('#idPeriodo').should('be.visible');
        cy.get('#idPrograma').should('be.visible');
        cy.get('#texto').should('be.visible');
        cy.get('#texto').should('have.attr', 'placeholder', 'Escriba para filtrar los resultados');
    });

    it('no permite buscar hasta seleccionar período y programa', () => {
        cy.get('#idPeriodo').select('');
        cy.get('#idPrograma').select('');
        cy.get(BOTON_BUSCAR).should('be.disabled');
        seleccionarOpcionExistente('#idPeriodo', PERIODO_CONFIGURADO);
        cy.get(BOTON_BUSCAR).should('be.disabled');
        seleccionarOpcionExistente('#idPrograma', PROGRAMA_CONFIGURADO);
        cy.get(BOTON_BUSCAR).should('be.enabled');
    });

    it('muestra cursos con horario, grupo, profesor y cupos devueltos por el servidor', () => {
        buscarCursos().then((cursos) => validarTablaContraRespuesta(cursos));
    });

    it('filtra las filas por nombre o código del curso', () => {
        buscarCursos().then((cursos) => {
            if (cursos.length === 0) {
                cy.log('No existen cursos ofertados para los filtros elegidos; no hay filas reales para filtrar.');
                cy.contains('[role="status"]', 'No se encontraron cursos disponibles').should('be.visible');
                return;
            }

            const curso = cursos[0];
            const termino = curso.nombreCurso || curso.codigoCurso;
            cy.get('#texto').clear().type(termino);
            cy.get(TABLA).should('have.length.greaterThan', 0).each(($fila) => {
                const codigo = $fila.find('td').eq(0).text().toLowerCase();
                const nombre = $fila.find('td').eq(1).text().toLowerCase();
                const buscado = termino.toLowerCase();
                expect(codigo.includes(buscado) || nombre.includes(buscado),
                    'la fila debe coincidir con el texto buscado').to.be.true;
            });
        });
    });

    it('muestra un mensaje cuando ningún curso coincide con el texto', () => {
        buscarCursos().then((cursos) => {
            if (cursos.length === 0) {
                cy.contains('[role="status"]', 'No se encontraron cursos disponibles').should('be.visible');
                cy.log('La combinación real de período/programa no devuelve cursos; no se puede probar el filtro textual.');
                return;
            }

            const textoImposible = 'ZZZ_HU010_' + Date.now();
            cy.get('#texto').clear().type(textoImposible);
            cy.contains('[role="status"]', 'Ningún curso coincide con "' + textoImposible + '"').should('be.visible');
            cy.get('table.oferta-tabla').should('not.exist');
        });
    });

    it('Limpiar elimina texto y resultados anteriores', () => {
        buscarCursos();
        cy.get('#texto').type('abc');
        cy.contains('button', 'Limpiar').click();
        cy.get('#texto').should('have.value', '');
        cy.get('table.oferta-tabla').should('not.exist');
        cy.get('[role="status"]').should('not.exist');
    });

    it('vuelve a consultar los cupos y representa la respuesta más reciente', () => {
        buscarCursos().then((primeraConsulta) => {
            if (primeraConsulta.length === 0) {
                cy.contains('[role="status"]', 'No se encontraron cursos disponibles').should('be.visible');
                cy.log('Sin grupos reales en esta combinación para validar actualización de cupos.');
                return;
            }

            validarTablaContraRespuesta(primeraConsulta);
            cy.intercept('GET', '**/api/consulta-cursos*').as('consultaActualizada');
            cy.get(BOTON_BUSCAR).click();
            cy.wait('@consultaActualizada').then(({ response }) => {
                expect(response.statusCode).to.eq(200);
                expect(response.body).to.be.an('array');
                validarTablaContraRespuesta(response.body);
            });
        });
    });
});