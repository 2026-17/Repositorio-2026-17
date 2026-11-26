/*
 * Lógica de la pantalla "Editar estudiante" que no depende de React: pasar la
 * respuesta del backend al formulario, contar cambios, validar y armar lo que
 * se envía. Las reglas coinciden con EstudianteService.actualizarCompleto.
 */
import { UBICACION_VACIA, ubicacionDesdeRespuesta } from "../../components/ubicacion";
import { esCedulaNacional, formatearCedula } from "../../utils/cedula";
import { errorArchivoPoliza, errorFechasDocumento } from "../../utils/validacionDocumento";
import {
    MENSAJE_NOMBRE,
    MENSAJE_TELEFONO,
    errorEdad,
    errorIdentificacion,
    esCorreoValido,
    esNombreValido,
    esTelefonoValido,
    hoyISO,
    limpiarIdentificacion,
    limpiarTelefono,
} from "../../utils/validaciones";

export const OBSERVACION_CARGA_INICIAL = "Carga inicial: confirmar parentesco";
export const MAX_ENCARGADOS = 2;
export const MAX_CONTACTOS = 2;

export const PARENTESCOS = [
    { valor: "MADRE", etiqueta: "Madre" },
    { valor: "PADRE", etiqueta: "Padre" },
    { valor: "TUTOR", etiqueta: "Tutor(a)" },
    { valor: "FAMILIAR", etiqueta: "Familiar" },
    { valor: "OTRO", etiqueta: "Otro" },
];

export const SECCIONES = [
    { id: "personal", titulo: "Datos personales" },
    { id: "contacto", titulo: "Contacto y residencia" },
    { id: "academico", titulo: "Información académica" },
    { id: "instrumentos", titulo: "Instrumentos" },
    { id: "encargados", titulo: "Encargados" },
    { id: "poliza", titulo: "Póliza estudiantil" },
    { id: "acceso", titulo: "Acceso a SIGAC" },
];

const CAMPOS_POR_SECCION = {
    personal: ["idTipoIdentificacion", "numeroIdentificacion", "genero", "nombres", "primerApellido", "segundoApellido", "fechaNacimiento", "condicionesRelevantes"],
    contacto: ["correo", "telefono", "idProvincia", "codigoCanton", "codigoDistrito", "direccion"],
    academico: ["nivelEscolar", "centroEducativo", "fechaIngreso", "idsPlanEstudio"],
    instrumentos: ["idInstrumento", "idProfesor"],
    poliza: ["polizaArchivo", "polizaFechaEmision", "polizaFechaVencimiento"],
};

const CAMPOS_SIMPLES = [...CAMPOS_POR_SECCION.personal, "correo", "telefono", "direccion", "nivelEscolar", "centroEducativo", "fechaIngreso", "idInstrumento", "idProfesor", "polizaFechaEmision", "polizaFechaVencimiento"];
const CAMPOS_ENCARGADO = ["idTipoIdentificacion", "numeroIdentificacion", "nombres", "primerApellido", "segundoApellido", "telefonoPrincipal", "telefonoSecundario", "correo", "ocupacion", "estadoCivil", "parentesco", "esResponsablePrincipal", "autorizadoRetiro", "observaciones"];
const CAMPOS_CONTACTO = ["nombre", "telefono", "relacion"];

export function seccionDeCampo(campo) {
    if (campo === "encargados" || campo.startsWith("enc-")) return "encargados";
    return Object.keys(CAMPOS_POR_SECCION).find(seccion => CAMPOS_POR_SECCION[seccion].includes(campo)) ?? "personal";
}

export function tituloSeccion(id) {
    return SECCIONES.find(seccion => seccion.id === id)?.titulo ?? "Datos";
}

export function edadDesdeFecha(valor) {
    if (!valor) return null;
    const nacimiento = new Date(`${valor}T00:00:00`);
    const hoy = new Date();
    if (Number.isNaN(nacimiento.getTime()) || nacimiento > hoy) return null;
    let edad = hoy.getFullYear() - nacimiento.getFullYear();
    const mes = hoy.getMonth() - nacimiento.getMonth();
    if (mes < 0 || (mes === 0 && hoy.getDate() < nacimiento.getDate())) edad--;
    return edad;
}

export const requiereCentro = edad => edad !== null && edad >= 6 && edad < 18;

// yyyy-mm-dd -> dd/mm/aaaa (sin pasar por Date, para no correr el día)
export function fechaCorta(iso) {
    if (!iso) return "";
    const [anio, mes, dia] = String(iso).split("-");
    return `${dia}/${mes}/${anio}`;
}

export function parentescoPorConfirmar(encargado) {
    return encargado.parentesco === "OTRO" && encargado.observaciones.trim() === OBSERVACION_CARGA_INICIAL;
}

let siguienteClave = 1;
const claveNueva = () => `n${siguienteClave++}`;

export function contactoVacio() {
    return { clave: claveNueva(), idContactoEmergencia: null, nombre: "", telefono: "", relacion: "" };
}

export function encargadoVacio(esPrincipal) {
    return {
        clave: claveNueva(), idEncargado: null, idTipoIdentificacion: "", numeroIdentificacion: "", nombres: "",
        primerApellido: "", segundoApellido: "", telefonoPrincipal: "", telefonoSecundario: "", correo: "",
        ocupacion: "", estadoCivil: "", direccion: "", parentesco: "MADRE", esResponsablePrincipal: esPrincipal,
        autorizadoRetiro: false, observaciones: "", contactosEmergencia: [], otrosEstudiantes: [],
    };
}

function encargadoDesdeRespuesta(e) {
    const tipo = e.tipoIdentificacion;
    return {
        clave: `e${e.id}`,
        idEncargado: e.id,
        idTipoIdentificacion: String(tipo?.id ?? ""),
        numeroIdentificacion: esCedulaNacional(tipo) ? formatearCedula(e.numeroIdentificacion ?? "") : e.numeroIdentificacion ?? "",
        nombres: e.nombres ?? "",
        primerApellido: e.primerApellido ?? "",
        segundoApellido: e.segundoApellido ?? "",
        telefonoPrincipal: e.telefonoPrincipal ?? "",
        telefonoSecundario: e.telefonoSecundario ?? "",
        correo: e.correo ?? "",
        ocupacion: e.ocupacion ?? "",
        estadoCivil: e.estadoCivil ?? "",
        direccion: e.direccion ?? "",
        parentesco: e.parentesco ?? "OTRO",
        esResponsablePrincipal: Boolean(e.esResponsablePrincipal),
        autorizadoRetiro: Boolean(e.autorizadoRetiro),
        observaciones: e.observaciones ?? "",
        contactosEmergencia: (e.contactosEmergencia ?? []).map(c => ({
            clave: `c${c.id ?? c.idContactoEmergencia}`,
            idContactoEmergencia: c.id ?? c.idContactoEmergencia,
            nombre: c.nombre ?? "",
            telefono: c.telefono ?? "",
            relacion: c.relacion ?? "",
        })),
        otrosEstudiantes: e.otrosEstudiantes ?? [],
    };
}

export function formDesdeRespuesta(data, tipos) {
    const e = data.estudiante;
    const tipo = tipos.find(t => String(t.id) === String(e.idTipoIdentificacion));
    const numero = e.numeroIdentificacion ?? "";
    const principal = data.instrumentoPrincipal;
    return {
        idTipoIdentificacion: String(e.idTipoIdentificacion ?? ""),
        numeroIdentificacion: esCedulaNacional(tipo) ? formatearCedula(numero) : numero,
        genero: e.genero ?? "",
        nombres: e.nombres ?? "",
        primerApellido: e.primerApellido ?? "",
        segundoApellido: e.segundoApellido ?? "",
        fechaNacimiento: e.fechaNacimiento ?? "",
        condicionesRelevantes: e.condicionesRelevantes ?? "",
        correo: e.correo ?? "",
        telefono: e.telefono ?? "",
        ubicacion: e.ubicacion ? ubicacionDesdeRespuesta(e.ubicacion) : UBICACION_VACIA,
        direccion: e.direccion ?? "",
        nivelEscolar: e.nivelEscolar ?? "",
        centroEducativo: e.centroEducativo ?? "",
        fechaIngreso: data.fechaIngreso ?? "",
        idsPlanEstudio: Array.isArray(e.idsPlanEstudio) ? e.idsPlanEstudio : [],
        idInstrumento: principal ? String(principal.idInstrumento) : "",
        idProfesor: principal?.idProfesor ? String(principal.idProfesor) : "",
        encargados: (data.encargados ?? []).map(encargadoDesdeRespuesta),
        polizaFechaEmision: "",
        polizaFechaVencimiento: "",
    };
}

function contarDiferencias(actual, inicial, campos) {
    return campos.filter(campo => actual[campo] !== inicial[campo]).length;
}

function contarCambiosEncargado(actual, inicial) {
    let cambios = contarDiferencias(actual, inicial, CAMPOS_ENCARGADO);
    const contactosIniciales = new Map(inicial.contactosEmergencia.map(c => [c.clave, c]));
    actual.contactosEmergencia.forEach(contacto => {
        const previo = contactosIniciales.get(contacto.clave);
        cambios += previo ? contarDiferencias(contacto, previo, CAMPOS_CONTACTO) : 1;
        contactosIniciales.delete(contacto.clave);
    });
    return cambios + contactosIniciales.size;
}

// Cantidad de datos distintos a los cargados (lo que muestra la barra inferior).
export function contarCambios(form, inicial, archivo) {
    let cambios = contarDiferencias(form, inicial, CAMPOS_SIMPLES);
    if (form.ubicacion.codigoDistrito !== inicial.ubicacion.codigoDistrito) cambios++;
    const planes = lista => [...lista].sort((a, b) => a - b).join(",");
    if (planes(form.idsPlanEstudio) !== planes(inicial.idsPlanEstudio)) cambios++;
    if (archivo) cambios++;
    const iniciales = new Map(inicial.encargados.map(e => [e.clave, e]));
    form.encargados.forEach(encargado => {
        const previo = iniciales.get(encargado.clave);
        cambios += previo ? contarCambiosEncargado(encargado, previo) : 1;
    });
    return cambios;
}

function validarEncargados(form, { tipos, contactosSinEncargado }, errores) {
    if (form.encargados.length < 1 || form.encargados.length > MAX_ENCARGADOS) {
        errores.encargados = "Un menor de edad debe tener uno o dos encargados.";
    } else if (form.encargados.filter(e => e.esResponsablePrincipal).length !== 1) {
        errores.encargados = "Debe existir exactamente un responsable principal.";
    }

    const totalContactos = form.encargados.reduce((total, e) => total + e.contactosEmergencia.length, 0);
    if (totalContactos + contactosSinEncargado > MAX_CONTACTOS) {
        errores.encargados = "El estudiante puede tener como máximo dos contactos de emergencia.";
    }

    const tipoEstudiante = String(form.idTipoIdentificacion);
    const numeroEstudiante = limpiarIdentificacion(form.numeroIdentificacion).toUpperCase();
    const vistas = new Set();

    form.encargados.forEach(e => {
        const k = campo => `enc-${e.clave}-${campo}`;
        const tipo = tipos.find(t => String(t.id) === String(e.idTipoIdentificacion));
        const numero = limpiarIdentificacion(e.numeroIdentificacion).toUpperCase();

        if (!e.idTipoIdentificacion) errores[k("idTipoIdentificacion")] = "Seleccione el tipo de identificación del encargado.";
        if (!numero) errores[k("numeroIdentificacion")] = "Ingrese la identificación del encargado.";
        else if (errorIdentificacion(tipo, e.numeroIdentificacion, "del encargado")) errores[k("numeroIdentificacion")] = errorIdentificacion(tipo, e.numeroIdentificacion, "del encargado");
        else if (String(e.idTipoIdentificacion) === tipoEstudiante && numero === numeroEstudiante) errores[k("numeroIdentificacion")] = "El encargado no puede tener la misma identificación que el estudiante.";
        else if (vistas.has(`${e.idTipoIdentificacion}:${numero}`)) errores[k("numeroIdentificacion")] = "Los dos encargados no pueden tener la misma identificación.";
        vistas.add(`${e.idTipoIdentificacion}:${numero}`);

        if (!e.nombres.trim()) errores[k("nombres")] = "Ingrese el nombre del encargado.";
        else if (!esNombreValido(e.nombres)) errores[k("nombres")] = MENSAJE_NOMBRE;
        if (!e.primerApellido.trim()) errores[k("primerApellido")] = "Ingrese el primer apellido del encargado.";
        else if (!esNombreValido(e.primerApellido)) errores[k("primerApellido")] = MENSAJE_NOMBRE;
        if (e.segundoApellido.trim() && !esNombreValido(e.segundoApellido)) errores[k("segundoApellido")] = MENSAJE_NOMBRE;
        if (!esTelefonoValido(e.telefonoPrincipal)) errores[k("telefonoPrincipal")] = MENSAJE_TELEFONO;
        if (e.telefonoSecundario.trim() && !esTelefonoValido(e.telefonoSecundario)) errores[k("telefonoSecundario")] = MENSAJE_TELEFONO;
        if (e.correo.trim() && !esCorreoValido(e.correo)) errores[k("correo")] = "Ingrese un correo electrónico válido.";
        if (parentescoPorConfirmar(e)) errores[k("parentesco")] = `Confirme el parentesco de ${e.nombres.trim() || "este encargado"}.`;

        e.contactosEmergencia.forEach(c => {
            const kc = campo => k(`con-${c.clave}-${campo}`);
            if (!c.nombre.trim()) errores[kc("nombre")] = "Ingrese el nombre del contacto.";
            if (!c.telefono.trim()) errores[kc("telefono")] = "Ingrese el teléfono del contacto.";
            if (!c.relacion.trim()) errores[kc("relacion")] = "Ingrese la relación con el estudiante.";
        });
    });
}

/*
 * Devuelve { campo: mensaje }. contexto: tipos, profesores (lista activa del
 * instrumento o null si no se pudo cargar), contactosSinEncargado y activo.
 */
export function validarFormulario(form, archivo, contexto) {
    const errores = {};
    const obligatorios = [
        ["idTipoIdentificacion", "Seleccione el tipo de identificación."], ["numeroIdentificacion", "Ingrese el número de identificación."],
        ["genero", "Seleccione el género."], ["nombres", "Ingrese los nombres."], ["primerApellido", "Ingrese el primer apellido."],
        ["fechaNacimiento", "Ingrese la fecha de nacimiento."], ["correo", "Ingrese el correo electrónico."],
        ["direccion", "Ingrese las señas exactas de residencia."], ["nivelEscolar", "Seleccione el nivel escolar."],
        ["fechaIngreso", "Ingrese la fecha de ingreso a ACUSAB."], ["idInstrumento", "Seleccione el instrumento principal."],
    ];
    obligatorios.forEach(([campo, mensaje]) => { if (!String(form[campo] ?? "").trim()) errores[campo] = mensaje; });

    const edad = edadDesdeFecha(form.fechaNacimiento);
    if (form.fechaNacimiento && edad === null) errores.fechaNacimiento = "La fecha de nacimiento no es válida.";
    else if (errorEdad(edad)) errores.fechaNacimiento = errorEdad(edad);

    const tipo = contexto.tipos.find(t => String(t.id) === String(form.idTipoIdentificacion));
    if (form.numeroIdentificacion.trim() && errorIdentificacion(tipo, form.numeroIdentificacion)) {
        errores.numeroIdentificacion = errorIdentificacion(tipo, form.numeroIdentificacion);
    }
    ["nombres", "primerApellido", "segundoApellido"].forEach(campo => {
        if (form[campo].trim() && !esNombreValido(form[campo])) errores[campo] = MENSAJE_NOMBRE;
    });
    if (form.condicionesRelevantes.length > 2000) errores.condicionesRelevantes = "Las condiciones relevantes no pueden superar 2000 caracteres.";
    if (form.correo.trim() && !esCorreoValido(form.correo)) errores.correo = "Ingrese un correo electrónico válido.";
    if (form.telefono.trim() && !esTelefonoValido(form.telefono)) errores.telefono = MENSAJE_TELEFONO;
    if (!form.ubicacion.idProvincia) errores.idProvincia = "Seleccione la provincia.";
    if (!form.ubicacion.codigoCanton) errores.codigoCanton = "Seleccione el cantón.";
    if (!form.ubicacion.codigoDistrito) errores.codigoDistrito = "Seleccione el distrito.";
    if (requiereCentro(edad) && !form.centroEducativo.trim()) errores.centroEducativo = "Ingrese el centro educativo.";
    if (form.fechaIngreso && form.fechaIngreso > hoyISO()) errores.fechaIngreso = "La fecha de ingreso no puede ser futura.";
    if (form.idsPlanEstudio.length === 0) errores.idsPlanEstudio = "Seleccione al menos un plan de estudio.";

    if (form.idProfesor && contexto.profesores && !contexto.profesores.some(p => String(p.id) === form.idProfesor)) {
        errores.idProfesor = "Ese profesor ya no está activo o no da este instrumento. Elija otro o deje «Sin profesor asignado».";
    }

    if (edad !== null && edad < 18) validarEncargados(form, contexto, errores);

    if (archivo) {
        // La póliza puede ser PDF o imagen (JPG, PNG o WEBP).
        const errorArchivo = errorArchivoPoliza(archivo);
        if (errorArchivo) errores.polizaArchivo = errorArchivo;
        if (!contexto.activo) errores.polizaArchivo = "No se puede adjuntar la póliza de un estudiante inactivo.";
    } else if (form.polizaFechaEmision || form.polizaFechaVencimiento) {
        errores.polizaArchivo = "Para registrar las fechas de la póliza, adjunte el archivo.";
    }
    const errorFechas = errorFechasDocumento(form.polizaFechaEmision, form.polizaFechaVencimiento);
    if (errorFechas) errores.polizaFechaVencimiento = errorFechas;

    return errores;
}

// La parte "datos" del PUT /estudiantes/{id}/completo.
export function construirDatos(form) {
    const edad = edadDesdeFecha(form.fechaNacimiento);
    return {
        estudiante: {
            idTipoIdentificacion: Number(form.idTipoIdentificacion),
            numeroIdentificacion: limpiarIdentificacion(form.numeroIdentificacion),
            nombres: form.nombres,
            primerApellido: form.primerApellido,
            segundoApellido: form.segundoApellido,
            fechaNacimiento: form.fechaNacimiento,
            genero: form.genero,
            codigoDistrito: Number(form.ubicacion.codigoDistrito),
            direccion: form.direccion,
            telefono: limpiarTelefono(form.telefono),
            correo: form.correo.trim(),
            nivelEscolar: form.nivelEscolar,
            centroEducativo: requiereCentro(edad) ? form.centroEducativo.trim() : null,
            condicionesRelevantes: form.condicionesRelevantes,
            idInstrumento: Number(form.idInstrumento),
            idsPlanEstudio: form.idsPlanEstudio,
        },
        fechaIngreso: form.fechaIngreso,
        idProfesor: form.idProfesor ? Number(form.idProfesor) : null,
        encargados: edad !== null && edad < 18 ? form.encargados.map(e => ({
            idEncargado: e.idEncargado,
            datos: {
                idTipoIdentificacion: Number(e.idTipoIdentificacion),
                numeroIdentificacion: limpiarIdentificacion(e.numeroIdentificacion),
                nombres: e.nombres.trim(),
                primerApellido: e.primerApellido.trim(),
                segundoApellido: e.segundoApellido.trim(),
                telefonoPrincipal: limpiarTelefono(e.telefonoPrincipal),
                telefonoSecundario: limpiarTelefono(e.telefonoSecundario),
                correo: e.correo.trim(),
                ocupacion: e.ocupacion.trim(),
                estadoCivil: e.estadoCivil.trim(),
                direccion: e.direccion.trim(),
                parentesco: e.parentesco,
                esResponsablePrincipal: e.esResponsablePrincipal,
                autorizadoRetiro: e.autorizadoRetiro,
                observaciones: e.observaciones.trim(),
                contactosEmergencia: e.contactosEmergencia.map(c => ({
                    idContactoEmergencia: c.idContactoEmergencia,
                    nombre: c.nombre.trim(),
                    telefono: c.telefono.trim(),
                    relacion: c.relacion.trim(),
                })),
            },
        })) : [],
    };
}
