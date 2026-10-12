// HU-014 - Asignar profesores a cursos y grupos. Solo IDs que ya existen en la DB.
// Variables locales: DIRECTOR_CEDULA / DIRECTOR_CLAVE, HU014_*.
// HU014_GRUPO_ASIGNABLE_ID: grupo activo, sin profesor, con horario y período futuro.
// HU014_PROFESOR_ASIGNAR_ID: profesor activo. HU014_PROFESOR_INACTIVO_ID: ID de profesor inactivo.
// HU014_GRUPO_CONFLICTO_ID / HU014_PROFESOR_CONFLICTO_ID: destino y profesor con cruce real.
// HU014_PERMITIR_CAMBIOS="SI" permite asignación real; esa prueba modifica la asignación y deja bitácora.
let CEDULA;
let CLAVE;
let ID_GRUPO;
let ID_PROFESOR;
let ID_GRUPO_CONFLICTO;
let ID_PROFESOR_CONFLICTO;
let ID_PROFESOR_INACTIVO;
const TABLA = '#tblAsignacionesProfesores';

function omitirFaltantes(ctx, keys) {
    const faltan = keys.filter(k => valorEntorno(k) === undefined || valorEntorno(k) === null || String(valorEntorno(k)).trim() === '');
    if (faltan.length) { cy.log('Completar configuración: ' + faltan.join(', ')); ctx.skip(); return true; }
    return false;
}
function hoy() {
    const p = new Intl.DateTimeFormat('en-CA',{timeZone:'America/Costa_Rica',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());
    const v = t => p.find(x=>x.type===t)?.value || '';
    return v('year')+'-'+v('month')+'-'+v('day');
}
function api(method,path,body) {
    return cy.window().then(w=>cy.request({
        method,url:'/api'+path,
        headers:{Authorization:'Bearer '+(w.localStorage.getItem('token')||''),'Content-Type':'application/json'},
        ...(body===undefined?{}:{body}),failOnStatusCode:false
    }));
}
function mostrarGrupo(codigo) {
    cy.get('#txtBuscarAsignacion').clear().type(codigo);
    cy.get(TABLA+' tbody tr').filter((_,tr)=>tr.innerText.includes(codigo)).should('have.length',1);
}

// Cypress 16: cargar solo las variables utilizadas por esta especificación.
// cy.env() conserva las credenciales fuera de la configuración pública.
let variablesEntorno = {};
function valorEntorno(nombre) {
    return variablesEntorno[nombre];
}

before(function () {
    return cy.env([
    "DIRECTOR_CEDULA",
    "DIRECTOR_CLAVE",
    "HU014_GRUPO_ASIGNABLE_ID",
    "HU014_PROFESOR_ASIGNAR_ID",
    "HU014_GRUPO_CONFLICTO_ID",
    "HU014_PROFESOR_CONFLICTO_ID",
    "HU014_PROFESOR_INACTIVO_ID",
    "HU014_PERMITIR_CAMBIOS"
]).then((valores) => {
        variablesEntorno = valores;
        CEDULA = valorEntorno('DIRECTOR_CEDULA');
        CLAVE = valorEntorno('DIRECTOR_CLAVE');
        ID_GRUPO = valorEntorno('HU014_GRUPO_ASIGNABLE_ID');
        ID_PROFESOR = valorEntorno('HU014_PROFESOR_ASIGNAR_ID');
        ID_GRUPO_CONFLICTO = valorEntorno('HU014_GRUPO_CONFLICTO_ID');
        ID_PROFESOR_CONFLICTO = valorEntorno('HU014_PROFESOR_CONFLICTO_ID');
        ID_PROFESOR_INACTIVO = valorEntorno('HU014_PROFESOR_INACTIVO_ID');
    });
});

describe('HU-014 - Asignar profesores a cursos y grupos',()=>{
    beforeEach(function(){
        if(omitirFaltantes(this,['DIRECTOR_CEDULA','DIRECTOR_CLAVE'])) return;
        cy.loginComo(CEDULA,CLAVE);
        cy.intercept('GET','**/api/asignaciones-profesores').as('grupos');
        cy.intercept('GET','**/api/asignaciones-profesores/profesores-activos').as('activos');
        cy.visit('/director-academico/asignar-profesores');
        cy.contains('h1','Asignar profesores a cursos y grupos').should('be.visible');
        // No consumir los aliases aquí: cada escenario inspecciona su respuesta del GET inicial.
    });

    it('comprueba que el catálogo real de profesores activos no incluya registros incompletos', () => {
        cy.wait('@activos').then(({ response }) => {
            expect(response.statusCode).to.eq(200);
            expect(response.body).to.be.an('array');
            response.body.forEach((profesor) => {
                expect(profesor.idProfesor, 'identificador del profesor').to.exist;
            });
        });
    });

    it('muestra cursos, grupos, períodos, horarios y profesor responsable',()=>{
        cy.wait('@grupos').then(({response})=>{
            expect(response.statusCode).to.eq(200);
            expect(response.body).to.be.an('array');
            if(!response.body.length){cy.contains('No hay grupos para asignar').should('be.visible');return;}
            cy.get(TABLA).should('be.visible');
            ['Período','Curso','Grupo','Horario','Profesor responsable','Estado','Acciones']
                .forEach(t=>cy.get(TABLA).contains('th',t).should('be.visible'));
        });
    });

    it('presenta en los selectores únicamente profesores del catálogo activo',()=>{
        cy.wait('@activos').then(({response})=>{
            expect(response.statusCode).to.eq(200);
            expect(response.body).to.be.an('array');
            if(!response.body.length){cy.log('El catálogo no tiene profesores activos existentes.');return;}
            cy.get(TABLA+' tbody select[id^="cmbProfesorGrupo-"]').first().then($s=>{
                const ui=[...$s[0].options].map(o=>String(o.value)).filter(Boolean).sort();
                const ids=response.body.map(p=>String(p.idProfesor)).sort();
                expect(ui).to.deep.equal(ids);
            });
        });
    });

    it('filtra por código real de curso o grupo',()=>{
        cy.wait('@grupos').then(({response})=>{
            if(!response.body.length){cy.contains('No hay grupos para asignar').should('be.visible');return;}
            const termino=response.body[0].codigoCurso||response.body[0].codigoGrupo;
            cy.get('#txtBuscarAsignacion').clear().type(termino);
            cy.get(TABLA+' tbody tr').should('have.length.greaterThan',0).each($r=>expect($r.text().toLowerCase()).to.include(termino.toLowerCase()));
        });
    });

    it('deshabilita asignar si el grupo no tiene horario',function(){
        cy.wait('@grupos').then(({response})=>{
            const g=response.body.find(x=>!x.horarios||!x.horarios.length);
            if(!g){cy.log('No existen grupos sin horario para validar.');this.skip();return;}
            mostrarGrupo(g.codigoGrupo);
            cy.get('#btnAsignarProfesor'+g.idGrupo).should('be.disabled');
            cy.contains('Registre el horario del grupo en la pantalla de gestión de grupos y horarios antes de asignar un profesor.').should('be.visible');
        });
    });

    it('bloquea cambios cuando el período del grupo ya inició',function(){
        cy.wait('@grupos').then(({response})=>{
            const g=response.body.find(x=>x.inicioPeriodo&&x.inicioPeriodo<=hoy());
            if(!g){cy.log('No existen grupos de un período iniciado.');this.skip();return;}
            mostrarGrupo(g.codigoGrupo);
            cy.get('#btnAsignarProfesor'+g.idGrupo).should('be.disabled');
            if(g.idProfesor)cy.get('#btnQuitarProfesor'+g.idGrupo).should('be.disabled');
        });
    });

    it('rechaza un profesor inactivo sin intentar usar un profesor que figure como activo',function(){
        if(omitirFaltantes(this,['HU014_PROFESOR_INACTIVO_ID']))return;
        cy.wait('@grupos').then(({response:grupos})=>{
            cy.wait('@activos').then(({response:activos})=>{
                expect(activos.body.some(p=>String(p.idProfesor)===String(ID_PROFESOR_INACTIVO))).to.eq(false);
                const g=grupos.body.find(x=>!x.idProfesor&&x.cursoActivo&&x.inicioPeriodo>hoy()&&x.horarios&&x.horarios.length);
                if(!g){cy.log('No hay grupo futuro disponible y sin profesor.');this.skip();return;}
                api('PATCH','/asignaciones-profesores/grupos/'+g.idGrupo,{idProfesor:Number(ID_PROFESOR_INACTIVO)}).then(r=>{
                    expect(r.status).to.eq(400);
                    expect(r.body.message).to.contain('Solo se pueden asignar profesores activos y registrados');
                });
            });
        });
    });

    it('rechaza un profesor cuando sus horarios se solapan con otro grupo',function(){
        if(omitirFaltantes(this,['HU014_GRUPO_CONFLICTO_ID','HU014_PROFESOR_CONFLICTO_ID']))return;
        cy.wait('@grupos').then(({response})=>{
            const gs=response.body;
            const destino=gs.find(g=>String(g.idGrupo)===String(ID_GRUPO_CONFLICTO));
            expect(destino,'grupo destino existente').to.exist;
            expect(destino.cursoActivo).to.eq(true);
            expect(destino.inicioPeriodo>hoy()).to.eq(true);
            expect(destino.horarios).to.be.an('array').and.not.be.empty;
            const origen=gs.find(g=>String(g.idGrupo)!==String(destino.idGrupo)&&String(g.idPeriodo)===String(destino.idPeriodo)&&String(g.idProfesor)===String(ID_PROFESOR_CONFLICTO)&&(g.horarios||[]).some(a=>(destino.horarios||[]).some(b=>Number(a.diaSemana)===Number(b.diaSemana)&&String(a.horaInicio).slice(0,5)<String(b.horaFin).slice(0,5)&&String(b.horaInicio).slice(0,5)<String(a.horaFin).slice(0,5))));
            // No se envía la petición si los datos reales no confirman el conflicto.
            expect(origen,'debe existir otro grupo del mismo período/profesor con horario solapado').to.exist;
            api('PATCH','/asignaciones-profesores/grupos/'+destino.idGrupo,{idProfesor:Number(ID_PROFESOR_CONFLICTO)}).then(r=>{
                expect(r.status).to.eq(400);expect(r.body.message).to.contain('Conflicto de horario');
            });
        });
    });

    it('asigna un profesor activo al grupo de prueba y confirma la operación',function(){
        if(omitirFaltantes(this,['HU014_GRUPO_ASIGNABLE_ID','HU014_PROFESOR_ASIGNAR_ID']))return;
        if(valorEntorno('HU014_PERMITIR_CAMBIOS')!=='SI'){cy.log('Omitido para proteger datos existentes; configura HU014_PERMITIR_CAMBIOS="SI" solo para un grupo de pruebas.');this.skip();return;}
        cy.wait('@grupos').then(({response})=>{
            const g=response.body.find(x=>String(x.idGrupo)===String(ID_GRUPO));
            expect(g,'grupo real configurado').to.exist;
            expect(g.idProfesor,'el grupo debe estar sin profesor').to.be.oneOf([null,undefined]);
            expect(g.cursoActivo).to.eq(true);expect(g.horarios).to.be.an('array').and.not.be.empty;expect(g.inicioPeriodo>hoy()).to.eq(true);
            cy.wait('@activos').then(({response:activos})=>{
                expect(activos.body.some(p=>String(p.idProfesor)===String(ID_PROFESOR))).to.eq(true);
                mostrarGrupo(g.codigoGrupo);
                cy.intercept('PATCH','**/api/asignaciones-profesores/grupos/'+ID_GRUPO).as('asignar');
                cy.get('#cmbProfesorGrupo-'+ID_GRUPO).select(String(ID_PROFESOR));
                cy.get('#btnAsignarProfesor'+ID_GRUPO).should('be.enabled').click();
                cy.wait('@asignar').then(({response:r})=>{
                    expect(r.statusCode).to.eq(200);
                    expect(String(r.body.idGrupo)).to.eq(String(ID_GRUPO));
                    expect(String(r.body.idProfesor)).to.eq(String(ID_PROFESOR));
                });
                cy.contains('.asignar-profesores-alert','Operación completada').should('be.visible');
                cy.contains('.asignar-profesores-alert','Asignación guardada:').should('be.visible');
            });
        });
    });

    it('permite cancelar el diálogo de quitar sin cambiar la asignación',()=>{
        cy.wait('@grupos').then(({response})=>{
            const g=response.body.find(x=>x.idProfesor&&x.inicioPeriodo>hoy());
            if(!g){cy.log('No hay una asignación futura para probar Cancelar.');return;}
            mostrarGrupo(g.codigoGrupo);
            cy.get('#btnQuitarProfesor'+g.idGrupo).should('be.enabled').click();
            cy.get('[role="alertdialog"]').should('contain.text',g.nombreProfesor).within(()=>cy.contains('button','Cancelar').click());
            cy.get(TABLA).contains('tr',g.codigoGrupo).should('contain.text',g.nombreProfesor).and('contain.text','Asignado');
        });
    });
});

// La interfaz actual no expone consulta de bitácora; validar el detalle de auditoría
// requiere endpoint/pantalla de bitácora o una prueba de integración de DB.
