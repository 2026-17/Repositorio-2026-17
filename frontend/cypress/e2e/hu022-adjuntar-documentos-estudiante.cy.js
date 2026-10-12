/*
 * HU-022 - Adjuntar / reemplazar documentos en el expediente del estudiante.
 * La aplicación existente identifica esta historia como HU-022; HU-014 está asignada
 * en el repositorio a "Asignar profesores a cursos y grupos".
 *
 * Variables locales en frontend/cypress.env.json (no versionar):
 * PERSONAL_ACADEMICO_CEDULA / PERSONAL_ACADEMICO_CLAVE: cuenta con rol ADMINISTRATIVO.
 * HU022_ESTUDIANTE_IDENTIFICACION: identificación de un estudiante activo existente.
 *
 * Los documentos usados son PDFs ficticios de Cypress. Para no reemplazar documentos
 * previos reales, la prueba positiva elige un tipo documental sin documento vigente.
 * Luego reemplaza el PDF que ella misma acaba de adjuntar por otro PDF de prueba.
 */

const ADMIN_CEDULA = Cypress.env('PERSONAL_ACADEMICO_CEDULA');
const ADMIN_CLAVE = Cypress.env('PERSONAL_ACADEMICO_CLAVE');
const IDENTIFICACION = Cypress.env('HU022_ESTUDIANTE_IDENTIFICACION') || Cypress.env('ESTUDIANTE_CEDULA');

function omitirSiFaltan(contexto, variables) {
    const faltantes = variables.filter((nombre) => {
        const valor = Cypress.env(nombre);
        return valor === undefined || valor === null || String(valor).trim() === '';
    });
    if (faltantes.length) {
        cy.log('Completa frontend/cypress.env.json: ' + faltantes.join(', '));
        contexto.skip();
        return true;
    }
    return false;
}

function interceptarLecturas() {
    cy.intercept('GET', '**/api/registro/catalogos/tipos-documentos').as('tiposDocumentos');
    cy.intercept('GET', '**/api/estudiantes/*/documentos').as('documentosVigentes');
}

function abrirGestionDocumental() {
    cy.get('#txtBuscarEstudiante').clear().type(String(IDENTIFICACION));
    cy.get('table.dtable tbody tr')
        .filter((_, fila) => fila.innerText.includes(String(IDENTIFICACION)))
        .should('have.length', 1)
        .within(() => {
            cy.get('button[aria-label^="Administrar documentos de "]')
                .should('be.enabled')
                .click();
        });

    cy.get('#documentos-estudiante').should('be.visible');

    return cy.wait('@tiposDocumentos').then(({ response: tiposResponse }) => {
        expect(tiposResponse.statusCode).to.eq(200);
        expect(tiposResponse.body).to.be.an('array');

        return cy.wait('@documentosVigentes').then(({ request, response: documentosResponse }) => {
            expect(documentosResponse.statusCode).to.eq(200);
            expect(documentosResponse.body).to.be.an('array');
            const match = request.url.match(/\/estudiantes\/(\d+)\/documentos(?:\?|$)/);
            expect(match, 'la solicitud de documentos debe identificar al estudiante').to.exist;

            return {
                idEstudiante: Number(match[1]),
                tipos: tiposResponse.body,
                documentos: documentosResponse.body,
            };
        });
    });
}

function elegirTipoSinDocumentoVigente(tipos, documentos) {
    const tipo = tipos.find((item) =>
        !documentos.some((documento) =>
            String(documento.idTipoDocumento) === String(item.id)
        )
    );
    expect(tipo, 'para evitar reemplazar documentos reales, se requiere un tipo sin documento vigente')
        .to.exist;
    return cy.get('#tipoDocumentoEstudiante').select(String(tipo.id)).then(() => tipo);
}

function confirmarReemplazoSiSeSolicita() {
    return cy.get('body').then(($body) => {
        if ($body.find('[role="alertdialog"]').length) {
            return cy.get('[role="alertdialog"]').within(() => {
                cy.contains('button', 'Reemplazar documento').click();
            });
        }
    });
}

describe('HU-022 - Adjuntar documentos del estudiante', () => {
    beforeEach(function () {
        if (omitirSiFaltan(this, [
            'PERSONAL_ACADEMICO_CEDULA',
            'PERSONAL_ACADEMICO_CLAVE',
            'HU022_ESTUDIANTE_IDENTIFICACION',
        ])) return;

        cy.loginComo(ADMIN_CEDULA, ADMIN_CLAVE);
        cy.intercept('GET', '**/api/estudiantes/listado*').as('listadoEstudiantes');
        cy.visit('/admin/administracion/estudiantes');
        cy.contains('h1', 'Estudiantes').should('be.visible');
        cy.wait('@listadoEstudiantes').its('response.statusCode').should('eq', 200);
    });

    it('abre la gestión documental del estudiante activo y consulta tipos y documentos vigentes', () => {
        interceptarLecturas();
        abrirGestionDocumental().then(({ tipos, documentos }) => {
            expect(tipos.length, 'tipos documentales activos existentes').to.be.greaterThan(0);
            cy.contains('h2', 'Documentos del estudiante').should('be.visible');
            cy.contains('h3', 'Documentos vigentes').should('be.visible');
            cy.get('#tipoDocumentoEstudiante option').should('have.length.greaterThan', 1);
            cy.get('#archivoDocumentoEstudiante').should('have.attr', 'type', 'file');
            documentos.forEach((documento) => {
                cy.get('.documentos-estudiante__item').should('contain.text', documento.nombreOriginal);
            });
        });
    });

    it('rechaza un archivo que no tiene extensión PDF', function () {
        interceptarLecturas();
        abrirGestionDocumental().then(({ tipos, documentos }) => {
            expect(tipos.length).to.be.greaterThan(0);
            cy.get('#tipoDocumentoEstudiante').select(String(tipos[0].id));
            cy.get('#archivoDocumentoEstudiante').selectFile({
                contents: Cypress.Buffer.from('contenido ficticio de texto'),
                fileName: 'documento-no-pdf.txt',
                mimeType: 'text/plain',
            }, { force: true });

            cy.contains('#documentos-estudiante button', 'Adjuntar documento').click();
            cy.contains('El archivo debe tener extensión PDF.').should('be.visible');
            cy.get('@documentosVigentes').then(({ response }) => {
                expect(response.body).to.deep.equal(documentos);
            });
        });
    });

    it('rechaza un archivo con extensión PDF cuyo contenido está dañado', function () {
        interceptarLecturas();
        abrirGestionDocumental().then(({ tipos, documentos }) => {
            expect(tipos.length).to.be.greaterThan(0);
            cy.get('#tipoDocumentoEstudiante').select(String(tipos[0].id));
            cy.intercept('POST', '**/api/estudiantes/*/documentos').as('subirPdfDañado');
            cy.get('#archivoDocumentoEstudiante').selectFile({
                contents: Cypress.Buffer.from('%PDF-1.4\nEste contenido está incompleto y no es un PDF válido.'),
                fileName: 'documento-danado.pdf',
                mimeType: 'application/pdf',
            }, { force: true });

            cy.contains('#documentos-estudiante button', /Adjuntar documento|Reemplazar documento/).click();
            confirmarReemplazoSiSeSolicita();

            cy.wait('@subirPdfDañado').then(({ response }) => {
                expect(response.statusCode).to.eq(400);
                expect(response.body.message.toLowerCase()).to.satisfy((mensaje) =>
                    mensaje.includes('dañado') || mensaje.includes('válido') || mensaje.includes('pdf')
                );
            });
            cy.contains('#documentos-estudiante', /PDF está dañado|no es un PDF válido|debe ser de tipo PDF/i)
                .should('be.visible');

            cy.log('El backend rechazó el PDF dañado y la interfaz mostró el mensaje de validación.');
        });
    });

    it('adjunta un PDF válido, lo asocia al expediente y permite reemplazar el archivo de prueba', function () {
        interceptarLecturas();
        abrirGestionDocumental().then(({ tipos, documentos, idEstudiante }) => {
            return elegirTipoSinDocumentoVigente(tipos, documentos).then((tipo) => {
                cy.intercept('POST', '**/api/estudiantes/' + idEstudiante + '/documentos').as('subirDocumento');

                cy.fixture('hu022-documento-prueba.pdf', 'base64').then((contenido) => {
                    cy.get('#archivoDocumentoEstudiante').selectFile({
                        contents: Cypress.Buffer.from(contenido, 'base64'),
                        fileName: 'hu022-adjunto-prueba.pdf',
                        mimeType: 'application/pdf',
                    }, { force: true });
                });
                cy.contains('#documentos-estudiante button', 'Adjuntar documento').click();

                return cy.wait('@subirDocumento').then(({ response }) => {
                    expect(response.statusCode).to.eq(201);
                    expect(String(response.body.idEstudiante)).to.eq(String(idEstudiante));
                    expect(String(response.body.idTipoDocumento)).to.eq(String(tipo.id));
                    expect(response.body.nombreOriginal).to.eq('hu022-adjunto-prueba.pdf');
                    expect(response.body.tipoMime).to.eq('application/pdf');
                    expect(response.body.esActual).to.eq(true);
                    expect(response.body.tamanoBytes).to.be.greaterThan(0);
                }).then(() => {
                    cy.contains('#documentos-estudiante', 'hu022-adjunto-prueba.pdf quedó asociado correctamente').should('be.visible');
                    cy.get('.toast-region').should('contain.text', 'Documento adjuntado correctamente');
                    cy.get('.documentos-estudiante__item').should('contain.text', 'hu022-adjunto-prueba.pdf');

                    cy.get('#tipoDocumentoEstudiante').select(String(tipo.id));
                    cy.fixture('hu022-documento-prueba.pdf', 'base64').then((contenido) => {
                        cy.get('#archivoDocumentoEstudiante').selectFile({
                            contents: Cypress.Buffer.from(contenido, 'base64'),
                            fileName: 'hu022-reemplazo-prueba.pdf',
                            mimeType: 'application/pdf',
                        }, { force: true });
                    });

                    cy.contains('Este tipo ya tiene un documento vigente').should('be.visible');
                    cy.contains('#documentos-estudiante button', 'Reemplazar documento').click();
                    cy.get('[role="alertdialog"]').should('be.visible')
                        .and('contain.text', 'se conservará en el historial')
                        .within(() => cy.contains('button', 'Reemplazar documento').click());

                    return cy.wait('@subirDocumento').then(({ response }) => {
                        expect(response.statusCode).to.eq(201);
                        expect(String(response.body.idEstudiante)).to.eq(String(idEstudiante));
                        expect(String(response.body.idTipoDocumento)).to.eq(String(tipo.id));
                        expect(response.body.nombreOriginal).to.eq('hu022-reemplazo-prueba.pdf');
                        expect(response.body.tipoMime).to.eq('application/pdf');
                        expect(response.body.esActual).to.eq(true);
                    }).then(() => {
                        cy.contains('#documentos-estudiante', 'hu022-reemplazo-prueba.pdf quedó asociado correctamente').should('be.visible');
                        cy.get('.toast-region').should('contain.text', 'Documento reemplazado correctamente');
                        cy.get('.documentos-estudiante__item').should('contain.text', 'hu022-reemplazo-prueba.pdf')
                            .and('not.contain.text', 'hu022-adjunto-prueba.pdf');
                    });
                });
            });
        });
    });
});
