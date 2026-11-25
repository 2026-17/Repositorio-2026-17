import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Plus, Save } from "lucide-react";
import "./styles/admin_Sis.css";
import "./styles/Estudiantes.css";
import "./styles/CreateStudent.css";
import Alert from "../../components/ui/Alert";
import Field from "../../components/ui/Field";
import StatusBadge from "../../components/ui/StatusBadge";
import { useDialog } from "../../components/dialog/useDialog";
import CedulaInput from "../../components/ui/CedulaInput";
import { useAvisoCambiosSinGuardar } from "../../hooks/useAvisoCambiosSinGuardar";
import { ajustarIdentificacionAlTipo, esCedulaNacional, mostrarIdentificacion } from "../../utils/cedula";
import UbicacionSelects from "../../components/UbicacionSelects";
import AvisoCorreoEnUso from "../../components/AvisoCorreoEnUso";
import { useCorreoEnUso } from "../../hooks/useCorreoEnUso";
import { API_URL } from "../../config/api";
import { lanzarError, leerError, mensajeDeExcepcion } from "../../utils/api";
import { hoyISO, limitesFechaNacimiento } from "../../utils/validaciones";
import EncargadoEdicion from "./EncargadoEdicion";
import { ACCEPT_POLIZA, AYUDA_FORMATO_POLIZA } from "../../utils/validacionDocumento";
import {
    MAX_CONTACTOS,
    MAX_ENCARGADOS,
    OBSERVACION_CARGA_INICIAL,
    SECCIONES,
    construirDatos,
    contactoVacio,
    contarCambios,
    edadDesdeFecha,
    encargadoVacio,
    fechaCorta,
    formDesdeRespuesta,
    parentescoPorConfirmar,
    requiereCentro,
    seccionDeCampo,
    tituloSeccion,
    validarFormulario,
} from "./edicionEstudiante";

const GENEROS = ["MASCULINO", "FEMENINO", "OTRO"];
const NIVELES = ["Preescolar", "Primaria", "Secundaria", "Universitaria", "Ninguno"];
const RUTA_ESTUDIANTES = "/admin/administracion/estudiantes";
const RUTA_INSTRUMENTOS = "/admin/administracion/estudiantes/instrumentos";
const RUTA_BECAS = "/admin/administracion/becas-pagos";

const encabezados = () => {
    const token = localStorage.getItem("token");
    return token ? { Authorization: `Bearer ${token}` } : {};
};

function estadoPoliza(poliza, archivo) {
    if (archivo) return { tono: "info", texto: "Archivo listo para subir" };
    if (!poliza) return { tono: "warning", texto: "Sin póliza registrada" };
    if (poliza.vencida) return { tono: "danger", texto: `Vencida el ${fechaCorta(poliza.fechaVencimiento)}` };
    if (poliza.fechaVencimiento) return { tono: "success", texto: `Vigente hasta ${fechaCorta(poliza.fechaVencimiento)}` };
    return { tono: "success", texto: "Vigente, sin fecha de vencimiento" };
}

function exigenciasPorEdad(edad) {
    if (edad === null) return undefined;
    if (edad >= 18) return `${edad} años · mayor de edad`;
    if (requiereCentro(edad)) return `${edad} años · menor de edad: requiere encargado y centro educativo`;
    return `${edad} años · menor de edad: requiere encargado`;
}

function EditarEstudiante({ studentId, onCancel, onSuccess } = {}) {
    const dialog = useDialog();
    const { id: routeId } = useParams();
    const id = studentId ?? routeId;
    const navigate = useNavigate();
    const idValido = /^\d+$/.test(String(id ?? ""));

    const [catalogos, setCatalogos] = useState({ tipos: [], instrumentos: [], planes: [] });
    const [detalle, setDetalle] = useState(null);
    const [form, setForm] = useState(null);
    const [formCargado, setFormCargado] = useState(null);
    const [archivo, setArchivo] = useState(null);
    const [claveArchivo, setClaveArchivo] = useState(0);
    const [errores, setErrores] = useState({});
    const [erroresServidor, setErroresServidor] = useState([]);
    const [mensajeGeneral, setMensajeGeneral] = useState("");
    const [exito, setExito] = useState("");
    const [intento, setIntento] = useState(0);
    const [abiertos, setAbiertos] = useState({});
    const [profesoresDe, setProfesoresDe] = useState({ idInstrumento: "", lista: null, error: "" });
    const [loading, setLoading] = useState(idValido);
    const [saving, setSaving] = useState(false);
    // Si ya se guardó algo, al salir hay que refrescar la lista de Estudiantes.
    const huboGuardado = useRef(false);
    const correoEnUso = useCorreoEnUso(id);

    const cambios = form && formCargado ? contarCambios(form, formCargado, archivo) : 0;
    const { confirmarDescarte, permitirSalida } = useAvisoCambiosSinGuardar(cambios > 0);

    const cargarEstudiante = useCallback(async (tipos) => {
        const respuesta = await fetch(`${API_URL}/estudiantes/${id}/edicion`, { headers: encabezados() });
        if (!respuesta.ok) await lanzarError(respuesta, "No fue posible cargar la información del estudiante.");
        const datos = await respuesta.json();
        const cargado = formDesdeRespuesta(datos, tipos);
        setDetalle(datos);
        setForm(cargado);
        setFormCargado(cargado);
        setArchivo(null);
        setClaveArchivo(n => n + 1);
    }, [id]);

    useEffect(() => {
        if (!idValido) return;
        async function cargar() {
            try {
                const headers = encabezados();
                const [tiposR, instrumentosR, planesR] = await Promise.all([
                    fetch(`${API_URL}/registro/catalogos/tipos-identificacion`, { headers }),
                    fetch(`${API_URL}/registro/catalogos/instrumentos`, { headers }),
                    fetch(`${API_URL}/planes-estudio`, { headers }),
                ]);
                if (!tiposR.ok) throw new Error("No se pudo cargar el catálogo de tipos de identificación.");
                if (!instrumentosR.ok) throw new Error("No se pudo cargar el catálogo de instrumentos.");
                if (!planesR.ok) throw new Error("No se pudo cargar el catálogo de planes de estudio.");
                const [tipos, instrumentos, planes] = await Promise.all([tiposR.json(), instrumentosR.json(), planesR.json()]);
                setCatalogos({ tipos, instrumentos, planes });
                await cargarEstudiante(tipos);
            } catch (e) {
                dialog.error(mensajeDeExcepcion(e, "No fue posible cargar la información del estudiante."));
            } finally {
                setLoading(false);
            }
        }
        cargar();
    }, [idValido, dialog, cargarEstudiante]);

    // Profesores activos del instrumento elegido
    const idInstrumento = form?.idInstrumento ?? "";
    useEffect(() => {
        if (!idInstrumento) return undefined;
        let cancelado = false;
        fetch(`${API_URL}/profesores/por-instrumento/${idInstrumento}`, { headers: encabezados() })
            .then(r => (r.ok ? r.json() : lanzarError(r, "No se pudo cargar los profesores.")))
            .then(lista => { if (!cancelado) setProfesoresDe({ idInstrumento, lista, error: "" }); })
            .catch(e => {
                if (!cancelado) setProfesoresDe({ idInstrumento, lista: null, error: mensajeDeExcepcion(e, "No fue posible cargar los profesores de ese instrumento.") });
            });
        return () => { cancelado = true; };
    }, [idInstrumento]);

    const edad = useMemo(() => edadDesdeFecha(form?.fechaNacimiento), [form?.fechaNacimiento]);
    const limitesNacimiento = useMemo(() => limitesFechaNacimiento(), []);

    const profesoresListos = profesoresDe.idInstrumento === idInstrumento;
    const profesores = profesoresListos ? profesoresDe.lista : null;
    const esMenor = edad !== null && edad < 18;
    const centroAplica = requiereCentro(edad);
    const tipoDe = idTipo => catalogos.tipos.find(t => String(t.id) === String(idTipo));

    function limpiarError(...campos) {
        setErrores(actual => {
            const siguiente = { ...actual };
            campos.forEach(campo => delete siguiente[campo]);
            return siguiente;
        });
    }

    function setField(nombre, valor) {
        setExito("");
        setForm(actual => {
            const siguiente = { ...actual, [nombre]: valor };
            if (nombre === "fechaNacimiento" && !requiereCentro(edadDesdeFecha(valor))) siguiente.centroEducativo = "";
            if (nombre === "idInstrumento") siguiente.idProfesor = "";
            return siguiente;
        });
        limpiarError(nombre);
    }

    function togglePlan(idPlan) {
        setExito("");
        setForm(actual => ({
            ...actual,
            idsPlanEstudio: actual.idsPlanEstudio.includes(idPlan)
                ? actual.idsPlanEstudio.filter(x => x !== idPlan)
                : [...actual.idsPlanEstudio, idPlan],
        }));
        limpiarError("idsPlanEstudio");
    }

    function cambiarEncargados(transformar) {
        setExito("");
        setForm(actual => ({ ...actual, encargados: transformar(actual.encargados) }));
    }

    function campoEncargado(clave, campo, valor) {
        cambiarEncargados(lista => lista.map(e => {
            if (e.clave !== clave) return e;
            const siguiente = { ...e, [campo]: valor };
            // Al confirmar el parentesco, la marca de la carga inicial ya no aplica
            if (campo === "parentesco" && valor !== "OTRO" && e.observaciones.trim() === OBSERVACION_CARGA_INICIAL) {
                siguiente.observaciones = "";
            }
            return siguiente;
        }));
        limpiarError(`enc-${clave}-${campo}`, "encargados", ...(campo === "observaciones" ? [`enc-${clave}-parentesco`] : []));
    }

    function marcarPrincipal(clave, marcado) {
        cambiarEncargados(lista => lista.map(e => (
            e.clave === clave ? { ...e, esResponsablePrincipal: marcado } : marcado ? { ...e, esResponsablePrincipal: false } : e
        )));
        limpiarError("encargados");
    }

    function agregarEncargado() {
        const nuevo = encargadoVacio(form.encargados.every(e => !e.esResponsablePrincipal));
        cambiarEncargados(lista => [...lista, nuevo]);
        setAbiertos(actual => ({ ...actual, [nuevo.clave]: true }));
        limpiarError("encargados");
    }

    function cambiarContactos(clave, transformar) {
        cambiarEncargados(lista => lista.map(e => (
            e.clave === clave ? { ...e, contactosEmergencia: transformar(e.contactosEmergencia) } : e
        )));
    }

    function elegirArchivo(evento) {
        setExito("");
        setArchivo(evento.target.files?.[0] ?? null);
        limpiarError("polizaArchivo");
    }

    function salirDeLaPantalla() {
        permitirSalida();
        if (huboGuardado.current && onSuccess) onSuccess();
        else if (onCancel) onCancel();
        else navigate(RUTA_ESTUDIANTES);
    }

    async function cancelar() {
        if (cambios > 0 && !(await confirmarDescarte())) return;
        salirDeLaPantalla();
    }

    function abrirEncargadosConError(clavesOIndices) {
        setAbiertos(actual => {
            const siguiente = { ...actual };
            clavesOIndices.forEach(clave => { siguiente[clave] = true; });
            return siguiente;
        });
    }

    async function guardar(evento) {
        evento.preventDefault();
        setExito("");
        setErroresServidor([]);
        setMensajeGeneral("");

        const encontrados = validarFormulario(form, archivo, {
            tipos: catalogos.tipos,
            profesores,
            contactosSinEncargado: detalle.contactosSinEncargado,
            activo: detalle.activo,
        });
        setErrores(encontrados);
        if (Object.keys(encontrados).length > 0) {
            abrirEncargadosConError(form.encargados
                .filter(e => Object.keys(encontrados).some(campo => campo.startsWith(`enc-${e.clave}-`)))
                .map(e => e.clave));
            setIntento(n => n + 1);
            return;
        }

        const cuerpo = new FormData();
        cuerpo.append("datos", new Blob([JSON.stringify(construirDatos(form))], { type: "application/json" }));
        if (archivo) cuerpo.append("poliza", archivo);
        if (form.polizaFechaEmision) cuerpo.append("polizaFechaEmision", form.polizaFechaEmision);
        if (form.polizaFechaVencimiento) cuerpo.append("polizaFechaVencimiento", form.polizaFechaVencimiento);

        try {
            setSaving(true);
            const respuesta = await fetch(`${API_URL}/estudiantes/${id}/completo`, {
                method: "PUT",
                headers: encabezados(),
                body: cuerpo,
            });
            if (!respuesta.ok) {
                const error = await leerError(respuesta, "No fue posible guardar los cambios.");
                if (error.errores.length > 0) {
                    setErroresServidor(error.errores);
                    abrirEncargadosConError(error.errores
                        .filter(e => e.indiceEncargado != null && form.encargados[e.indiceEncargado])
                        .map(e => form.encargados[e.indiceEncargado].clave));
                } else {
                    setMensajeGeneral(error.message);
                }
                setIntento(n => n + 1);
                return;
            }
            const nombre = [form.nombres, form.primerApellido, form.segundoApellido].map(t => t.trim()).filter(Boolean).join(" ");
            huboGuardado.current = true;
            await cargarEstudiante(catalogos.tipos);
            setErrores({});
            setExito(`Se guardaron todos los cambios de ${nombre}.`);
            setIntento(n => n + 1);
        } catch (e) {
            setMensajeGeneral(mensajeDeExcepcion(e, "No fue posible guardar los cambios. Revise su conexión e intente de nuevo."));
            setIntento(n => n + 1);
        } finally {
            setSaving(false);
        }
    }

    const volver = (
        <Link className="sg-btn sg-btn--secondary" to={RUTA_ESTUDIANTES}>
            <ArrowLeft size={18} strokeWidth={1.75} aria-hidden="true" /> Volver a estudiantes
        </Link>
    );
    if (!idValido) {
        return (
            <div className="editar-estudiante">
                <Alert tone="danger" title="El estudiante indicado no existe" className="form-alert" />
                {volver}
            </div>
        );
    }
    if (loading) return <Alert tone="info" title="Cargando estudiante..." />;
    if (!form || !detalle) return onCancel
        ? <button type="button" className="sg-btn sg-btn--secondary" onClick={onCancel}><ArrowLeft size={18} strokeWidth={1.75} aria-hidden="true" /> Volver a estudiantes</button>
        : volver;

    const err = campo => ({
        "aria-invalid": errores[campo] ? "true" : undefined,
        "aria-describedby": errores[campo] ? `edit-${campo}-error` : undefined,
    });

    const resumen = [
        ...Object.entries(errores).filter(([, texto]) => texto).map(([campo, texto]) => {
            const seccion = seccionDeCampo(campo);
            return { href: `#seccion-${seccion}`, text: `${tituloSeccion(seccion)}: ${texto}` };
        }),
        ...erroresServidor.map(e => ({ href: `#seccion-${e.seccion}`, text: `${tituloSeccion(e.seccion)}: ${e.mensaje}` })),
    ];

    const nombreVisible = [detalle.estudiante.nombres, detalle.estudiante.primerApellido, detalle.estudiante.segundoApellido].filter(Boolean).join(" ");
    const pendientes = form.encargados.filter(parentescoPorConfirmar).length;
    const totalContactos = form.encargados.reduce((total, e) => total + e.contactosEmergencia.length, 0);
    const contactosDisponibles = Math.max(0, MAX_CONTACTOS - detalle.contactosSinEncargado - totalContactos);
    const poliza = estadoPoliza(detalle.poliza, archivo);
    const faltaPoliza = !detalle.poliza && !archivo;
    const idsExtra = new Set(detalle.instrumentosExtra.map(x => String(x.idInstrumento)));
    const principal = detalle.instrumentoPrincipal;
    const instrumentoCargado = principal && String(principal.idInstrumento) === form.idInstrumento ? principal : null;
    const opcionesInstrumento = catalogos.instrumentos.filter(i => !idsExtra.has(String(i.id)));
    const instrumentoFueraDeCatalogo = instrumentoCargado && !catalogos.instrumentos.some(i => String(i.id) === form.idInstrumento);
    const profesorFueraDeLista = form.idProfesor && profesores && !profesores.some(p => String(p.id) === form.idProfesor);
    const nombreProfesorActual = instrumentoCargado && String(instrumentoCargado.idProfesor) === form.idProfesor
        ? instrumentoCargado.nombreProfesor
        : "Profesor asignado";

    const notaIndice = seccion => {
        if (seccion === "encargados" && esMenor && pendientes > 0) return pendientes === 1 ? "1 por confirmar" : `${pendientes} por confirmar`;
        if (seccion === "poliza" && faltaPoliza) return "Falta";
        return null;
    };

    return (
        <div className="editar-estudiante">
            <nav className="edicion-migas" aria-label="Ubicación del estudiante">
                {onCancel
                    ? <button type="button" className="link-btn" onClick={cancelar}>Estudiantes</button>
                    : <Link className="link-btn" to={RUTA_ESTUDIANTES}>Estudiantes</Link>}
                <span className="muted" aria-hidden="true">/</span>
                <span aria-current="page">{nombreVisible}</span>
            </nav>
            <h2 className="form-section-title editar-titulo">Editar estudiante</h2>
            <div className="edicion-chips edicion-cabecera">
                <span className="muted">{mostrarIdentificacion(detalle.estudiante.numeroIdentificacion, tipoDe(detalle.estudiante.idTipoIdentificacion))}</span>
                {edad !== null && <span className="muted">{edad} años</span>}
                {esMenor && <StatusBadge tone="info" icon={null}>Menor de edad</StatusBadge>}
                <StatusBadge tone={detalle.activo ? "success" : "neutral"}>{detalle.activo ? "Activo" : "Inactivo"}</StatusBadge>
            </div>

            <form onSubmit={guardar} noValidate>
                {(resumen.length > 0 || mensajeGeneral) && (
                    <Alert key={intento} tone="danger" autoFocus className="form-alert" title="No se guardó: revise lo siguiente." errors={resumen}>
                        {mensajeGeneral || undefined}
                    </Alert>
                )}
                {exito && <Alert key={`ok-${intento}`} tone="success" autoFocus className="form-alert" title={exito} />}

                <div className="edicion-layout">
                    <nav className="edicion-indice panel" aria-label="Secciones del estudiante">
                        <ol>
                            {SECCIONES.map(seccion => (
                                <li key={seccion.id}>
                                    <a href={`#seccion-${seccion.id}`}>{seccion.titulo}</a>
                                    {notaIndice(seccion.id) && <StatusBadge tone="warning" icon={null}>{notaIndice(seccion.id)}</StatusBadge>}
                                </li>
                            ))}
                        </ol>
                    </nav>

                    <div className="edicion-secciones">
                        <section className="panel" id="seccion-personal" aria-labelledby="titulo-personal">
                            <div className="panel-head"><h2 id="titulo-personal">Datos personales</h2></div>
                            <div className="panel-body form-grid">
                                <Field id="edit-idTipoIdentificacion" errorKey="idTipoIdentificacion" label="Tipo de identificación" error={errores.idTipoIdentificacion} select>
                                    <select id="edit-idTipoIdentificacion" className="sg-input sg-select" value={form.idTipoIdentificacion} onChange={e => {
                                        setField("idTipoIdentificacion", e.target.value);
                                        setField("numeroIdentificacion", ajustarIdentificacionAlTipo(form.numeroIdentificacion, tipoDe(e.target.value)));
                                    }} {...err("idTipoIdentificacion")}>
                                        <option value="">Seleccione</option>
                                        {catalogos.tipos.map(t => <option key={t.id} value={t.id}>{t.nombre}</option>)}
                                    </select>
                                </Field>
                                <Field id="edit-numeroIdentificacion" errorKey="numeroIdentificacion" label="Número de identificación" hint="También es el usuario para entrar a SIGAC." error={errores.numeroIdentificacion}>
                                    <CedulaInput id="edit-numeroIdentificacion" className="sg-input" mascara={esCedulaNacional(tipoDe(form.idTipoIdentificacion))} maxLength={40} value={form.numeroIdentificacion} onChange={valor => setField("numeroIdentificacion", valor)} {...err("numeroIdentificacion")} />
                                </Field>
                                <Field id="edit-genero" errorKey="genero" label="Género" error={errores.genero} select>
                                    <select id="edit-genero" className="sg-input sg-select" value={form.genero} onChange={e => setField("genero", e.target.value)} {...err("genero")}>
                                        <option value="">Seleccione</option>
                                        {GENEROS.map(g => <option key={g}>{g}</option>)}
                                    </select>
                                </Field>
                                <Field id="edit-nombres" errorKey="nombres" label="Nombres" error={errores.nombres}>
                                    <input id="edit-nombres" className="sg-input" maxLength={100} value={form.nombres} onChange={e => setField("nombres", e.target.value)} {...err("nombres")} />
                                </Field>
                                <Field id="edit-primerApellido" errorKey="primerApellido" label="Primer apellido" error={errores.primerApellido}>
                                    <input id="edit-primerApellido" className="sg-input" maxLength={80} value={form.primerApellido} onChange={e => setField("primerApellido", e.target.value)} {...err("primerApellido")} />
                                </Field>
                                <Field id="edit-segundoApellido" errorKey="segundoApellido" label="Segundo apellido" optional error={errores.segundoApellido}>
                                    <input id="edit-segundoApellido" className="sg-input" maxLength={80} value={form.segundoApellido} onChange={e => setField("segundoApellido", e.target.value)} {...err("segundoApellido")} />
                                </Field>
                                <Field id="edit-fechaNacimiento" errorKey="fechaNacimiento" label="Fecha de nacimiento" hint={exigenciasPorEdad(edad)} error={errores.fechaNacimiento}>
                                    <input id="edit-fechaNacimiento" className="sg-input" type="date" min={limitesNacimiento.min} max={limitesNacimiento.max} value={form.fechaNacimiento} onChange={e => setField("fechaNacimiento", e.target.value)} {...err("fechaNacimiento")} />
                                </Field>
                                <Field id="edit-condicionesRelevantes" errorKey="condicionesRelevantes" label="Condiciones relevantes" optional className="span-all" hint="Máximo 2000 caracteres." error={errores.condicionesRelevantes}>
                                    <textarea id="edit-condicionesRelevantes" className="sg-input" maxLength={2000} value={form.condicionesRelevantes} onChange={e => setField("condicionesRelevantes", e.target.value)} {...err("condicionesRelevantes")} />
                                </Field>
                            </div>
                        </section>

                        <section className="panel" id="seccion-contacto" aria-labelledby="titulo-contacto">
                            <div className="panel-head"><h2 id="titulo-contacto">Contacto y residencia</h2></div>
                            <div className="panel-body form-grid">
                                <div>
                                    <Field id="edit-correo" errorKey="correo" label="Correo electrónico" hint="Si lo cambia, también cambia el correo de su cuenta, donde le llega la recuperación de contraseña." error={errores.correo}>
                                        <input id="edit-correo" className="sg-input" type="email" maxLength={254} value={form.correo} onChange={e => { setField("correo", e.target.value); correoEnUso.limpiar(); }} onBlur={e => correoEnUso.consultar(e.target.value)} {...err("correo")} />
                                    </Field>
                                    <AvisoCorreoEnUso cuentas={correoEnUso.cuentas} />
                                </div>
                                <Field id="edit-telefono" errorKey="telefono" label="Teléfono" optional hint="8 dígitos, puede escribirlo con guion." error={errores.telefono}>
                                    <input id="edit-telefono" className="sg-input" type="tel" inputMode="tel" maxLength={9} value={form.telefono} onChange={e => setField("telefono", e.target.value)} placeholder="8888-4321" {...err("telefono")} />
                                </Field>
                                <UbicacionSelects required idPrefix="edit-" value={form.ubicacion} errors={errores} onChange={ubicacion => {
                                    setExito("");
                                    setForm(actual => ({ ...actual, ubicacion }));
                                    limpiarError("idProvincia", "codigoCanton", "codigoDistrito");
                                }} />
                                <Field id="edit-direccion" errorKey="direccion" label="Dirección exacta (señas)" className="span-all" error={errores.direccion}>
                                    <textarea id="edit-direccion" className="sg-input" maxLength={500} value={form.direccion} onChange={e => setField("direccion", e.target.value)} placeholder="Ej.: 200 m norte de la iglesia, casa verde a mano derecha" {...err("direccion")} />
                                </Field>
                            </div>
                        </section>

                        <section className="panel" id="seccion-academico" aria-labelledby="titulo-academico">
                            <div className="panel-head"><h2 id="titulo-academico">Información académica</h2></div>
                            <div className="panel-body form-grid">
                                <Field id="edit-nivelEscolar" errorKey="nivelEscolar" label="Nivel escolar" error={errores.nivelEscolar} select>
                                    <select id="edit-nivelEscolar" className="sg-input sg-select" value={form.nivelEscolar} onChange={e => setField("nivelEscolar", e.target.value)} {...err("nivelEscolar")}>
                                        <option value="">Seleccione</option>
                                        {form.nivelEscolar && !NIVELES.includes(form.nivelEscolar) && <option>{form.nivelEscolar}</option>}
                                        {NIVELES.map(nivel => <option key={nivel}>{nivel}</option>)}
                                    </select>
                                </Field>
                                <Field id="edit-centroEducativo" errorKey="centroEducativo" label="Centro educativo" optional={!centroAplica} hint={centroAplica ? undefined : "No aplica para esta edad."} error={errores.centroEducativo}>
                                    <input id="edit-centroEducativo" className="sg-input" maxLength={180} value={form.centroEducativo} onChange={e => setField("centroEducativo", e.target.value)} disabled={!centroAplica} placeholder={centroAplica ? "Nombre del centro educativo" : "No aplica"} {...err("centroEducativo")} />
                                </Field>
                                <Field id="edit-fechaIngreso" errorKey="fechaIngreso" label="Fecha de ingreso a ACUSAB" error={errores.fechaIngreso}>
                                    <input id="edit-fechaIngreso" className="sg-input" type="date" max={hoyISO()} value={form.fechaIngreso} onChange={e => setField("fechaIngreso", e.target.value)} {...err("fechaIngreso")} />
                                </Field>
                                <fieldset className="fieldset span-all" id="edit-idsPlanEstudio" tabIndex={-1} data-error-field="idsPlanEstudio" aria-describedby={errores.idsPlanEstudio ? "edit-idsPlanEstudio-error" : undefined}>
                                    <legend>Planes de estudio</legend>
                                    <p className="sg-field__hint plan-hint">
                                        {catalogos.planes.length === 0 ? "No hay planes de estudio vigentes." : "Determinan los cursos que el estudiante podrá matricular. Puede seleccionar más de uno."}
                                    </p>
                                    {errores.idsPlanEstudio && <p className="sg-field__error plan-error" id="edit-idsPlanEstudio-error">{errores.idsPlanEstudio}</p>}
                                    <div className="choice-list">
                                        {catalogos.planes.map(plan => {
                                            const marcado = form.idsPlanEstudio.includes(plan.id);
                                            return (
                                                <label key={plan.id} className={`choice${marcado ? " is-checked" : ""}`}>
                                                    <input type="checkbox" checked={marcado} onChange={() => togglePlan(plan.id)} />
                                                    <span className="choice-main"><b>{plan.nombre}</b></span>
                                                </label>
                                            );
                                        })}
                                    </div>
                                </fieldset>
                                <div className="review-block span-all">
                                    <dl>
                                        <dt>Beca del período actual</dt>
                                        <dd>{detalle.beca ? `${detalle.beca.porcentaje} % · ${detalle.beca.nombreTipoBeca} (${detalle.beca.codigoPeriodo})` : "Sin beca en el período actual"}</dd>
                                        <dt>Cursos reconocidos</dt>
                                        <dd>{detalle.cursosReconocidos}</dd>
                                    </dl>
                                    <div className="edicion-enlaces">
                                        <Link className="link-btn" to={RUTA_BECAS}>Ir a Becas</Link>
                                        <Link className="link-btn" to={RUTA_INSTRUMENTOS}>Ver avance académico</Link>
                                    </div>
                                </div>
                            </div>
                        </section>

                        <section className="panel" id="seccion-instrumentos" aria-labelledby="titulo-instrumentos">
                            <div className="panel-head"><h2 id="titulo-instrumentos">Instrumentos</h2></div>
                            <div className="panel-body form-grid">
                                <Field id="edit-idInstrumento" errorKey="idInstrumento" label="Instrumento principal" hint={instrumentoCargado?.nivelActual ? `Nivel actual: ${instrumentoCargado.nivelActual}` : undefined} error={errores.idInstrumento} select>
                                    <select id="edit-idInstrumento" className="sg-input sg-select" value={form.idInstrumento} onChange={e => setField("idInstrumento", e.target.value)} {...err("idInstrumento")}>
                                        <option value="">Seleccione</option>
                                        {instrumentoFueraDeCatalogo && <option value={form.idInstrumento}>{instrumentoCargado.nombreInstrumento} (inactivo)</option>}
                                        {opcionesInstrumento.map(i => <option key={i.id} value={i.id}>{i.nombre}</option>)}
                                    </select>
                                </Field>
                                <Field id="edit-idProfesor" errorKey="idProfesor" label="Profesor asignado" hint={profesoresDe.error && profesoresListos ? profesoresDe.error : undefined} error={errores.idProfesor} select>
                                    <select id="edit-idProfesor" className="sg-input sg-select" value={form.idProfesor} onChange={e => setField("idProfesor", e.target.value)} disabled={!form.idInstrumento || !profesoresListos} {...err("idProfesor")}>
                                        <option value="">{form.idInstrumento && !profesoresListos ? "Cargando profesores..." : "Sin profesor asignado"}</option>
                                        {(profesorFueraDeLista || (form.idProfesor && !profesores)) && <option value={form.idProfesor}>{nombreProfesorActual}{profesorFueraDeLista ? " (no disponible)" : ""}</option>}
                                        {(profesores ?? []).map(p => <option key={p.id} value={p.id}>{[p.nombres, p.primerApellido, p.segundoApellido].filter(Boolean).join(" ")}</option>)}
                                    </select>
                                </Field>
                                {!form.idProfesor && (
                                    <Alert tone="warning" className="span-all">Asigne un profesor para que pueda matricular su espacio de instrumento.</Alert>
                                )}
                                {detalle.instrumentosExtra.length > 0 && (
                                    <div className="review-block span-all">
                                        <h3>Instrumentos extra</h3>
                                        <dl>
                                            {detalle.instrumentosExtra.map(extra => (
                                                <div className="review-pair" key={extra.idEstudianteInstrumento}>
                                                    <dt>{extra.nombreInstrumento}</dt>
                                                    <dd>{extra.nombreProfesor ?? "Sin profesor asignado"}{extra.nivelActual ? ` · ${extra.nivelActual}` : ""}</dd>
                                                </div>
                                            ))}
                                        </dl>
                                    </div>
                                )}
                                <div className="span-all">
                                    <Link className="link-btn" to={RUTA_INSTRUMENTOS}>Asignar o cambiar instrumentos extra</Link>
                                </div>
                            </div>
                        </section>

                        <section className="panel" id="seccion-encargados" aria-labelledby="titulo-encargados">
                            <div className="panel-head"><h2 id="titulo-encargados">Encargados</h2></div>
                            <div className="panel-body">
                                {!esMenor ? (
                                    <p className="muted">Los encargados solo se registran para menores de edad.</p>
                                ) : (
                                    <>
                                        {pendientes > 0 && (
                                            <Alert tone="warning" className="form-alert" title={pendientes === 1 ? "Falta confirmar 1 parentesco" : `Faltan confirmar ${pendientes} parentescos`}>
                                                Vienen de la carga inicial. Elija el parentesco real para poder guardar.
                                            </Alert>
                                        )}
                                        {errores.encargados && <Alert tone="danger" className="form-alert" title={errores.encargados} />}
                                        {form.encargados.map(encargado => (
                                            <EncargadoEdicion
                                                key={encargado.clave}
                                                encargado={encargado}
                                                tipos={catalogos.tipos}
                                                errores={errores}
                                                abierto={Boolean(abiertos[encargado.clave])}
                                                contactosDisponibles={contactosDisponibles}
                                                onAlternar={() => setAbiertos(actual => ({ ...actual, [encargado.clave]: !actual[encargado.clave] }))}
                                                onCampo={(campo, valor) => campoEncargado(encargado.clave, campo, valor)}
                                                onPrincipal={marcado => marcarPrincipal(encargado.clave, marcado)}
                                                onAgregarContacto={() => cambiarContactos(encargado.clave, lista => [...lista, contactoVacio()])}
                                                onCampoContacto={(claveContacto, campo, valor) => {
                                                    cambiarContactos(encargado.clave, lista => lista.map(c => (c.clave === claveContacto ? { ...c, [campo]: valor } : c)));
                                                    limpiarError(`enc-${encargado.clave}-con-${claveContacto}-${campo}`);
                                                }}
                                                onQuitarContacto={claveContacto => {
                                                    cambiarContactos(encargado.clave, lista => lista.filter(c => c.clave !== claveContacto));
                                                    limpiarError("encargados", ...["nombre", "telefono", "relacion"].map(campo => `enc-${encargado.clave}-con-${claveContacto}-${campo}`));
                                                }}
                                                onQuitar={() => {
                                                    cambiarEncargados(lista => lista.filter(e => e.clave !== encargado.clave));
                                                    setErrores(actual => Object.fromEntries(Object.entries(actual).filter(([campo]) => !campo.startsWith(`enc-${encargado.clave}-`) && campo !== "encargados")));
                                                }}
                                            />
                                        ))}
                                        <div className="inline-actions guardian-add">
                                            <button type="button" className="sg-btn sg-btn--secondary" onClick={agregarEncargado} disabled={form.encargados.length >= MAX_ENCARGADOS}>
                                                <Plus size={18} strokeWidth={1.75} aria-hidden="true" /> Agregar encargado
                                            </button>
                                            {form.encargados.length >= MAX_ENCARGADOS && <span className="muted">Ya tiene los 2 permitidos.</span>}
                                        </div>
                                    </>
                                )}
                            </div>
                        </section>

                        <section className="panel" id="seccion-poliza" aria-labelledby="titulo-poliza">
                            <div className="panel-head">
                                <h2 id="titulo-poliza">Póliza estudiantil</h2>
                                <StatusBadge tone={poliza.tono}>{poliza.texto}</StatusBadge>
                            </div>
                            <div className="panel-body form-grid">
                                {detalle.poliza && <p className="muted span-all">Archivo actual: {detalle.poliza.nombreArchivo}</p>}
                                <Field id="edit-polizaArchivo" errorKey="polizaArchivo" label="Archivo de la póliza" optional className="span-all" hint={detalle.activo ? `${AYUDA_FORMATO_POLIZA}. Tamaño máximo: 50 MB. Se sube al apretar Guardar cambios.` : "Un estudiante inactivo no puede recibir documentos."} error={errores.polizaArchivo}>
                                    <input key={claveArchivo} id="edit-polizaArchivo" className="sg-input" type="file" accept={ACCEPT_POLIZA} onChange={elegirArchivo} disabled={!detalle.activo} {...err("polizaArchivo")} />
                                </Field>
                                <Field id="edit-polizaFechaEmision" errorKey="polizaFechaEmision" label="Fecha de emisión" optional error={errores.polizaFechaEmision}>
                                    <input id="edit-polizaFechaEmision" className="sg-input" type="date" value={form.polizaFechaEmision} onChange={e => { setField("polizaFechaEmision", e.target.value); limpiarError("polizaFechaVencimiento", "polizaArchivo"); }} disabled={!detalle.activo} {...err("polizaFechaEmision")} />
                                </Field>
                                <Field id="edit-polizaFechaVencimiento" errorKey="polizaFechaVencimiento" label="Fecha de vencimiento" optional error={errores.polizaFechaVencimiento}>
                                    <input id="edit-polizaFechaVencimiento" className="sg-input" type="date" value={form.polizaFechaVencimiento} onChange={e => { setField("polizaFechaVencimiento", e.target.value); limpiarError("polizaArchivo"); }} disabled={!detalle.activo} {...err("polizaFechaVencimiento")} />
                                </Field>
                            </div>
                        </section>

                        <div className="panel sticky-save">
                            {cambios > 0
                                ? <span className="dirty" role="status"><i aria-hidden="true" />{cambios === 1 ? "1 cambio sin guardar" : `${cambios} cambios sin guardar`}</span>
                                : <span className="muted" role="status">Sin cambios pendientes</span>}
                            <div className="inline-actions">
                                <button type="button" className="sg-btn sg-btn--secondary" onClick={cancelar} disabled={saving}>Cancelar</button>
                                <button id="btnGuardarCambios" className={`sg-btn sg-btn--primary${saving ? " is-loading" : ""}`} type="submit" disabled={saving} aria-busy={saving || undefined}>
                                    {saving ? <span className="sg-spinner" aria-hidden="true" /> : <Save size={18} strokeWidth={1.75} aria-hidden="true" />}
                                    {saving ? "Guardando…" : "Guardar cambios"}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </form>
        </div>
    );
}

export default EditarEstudiante;
