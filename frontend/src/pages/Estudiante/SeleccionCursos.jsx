import { useEffect, useMemo, useState } from "react";

import { ArrowLeft, ArrowRight, BookOpen, CalendarDays, Check, CircleX, Music, Plus, User } from "lucide-react";
import { useDialog } from "../../components/dialog/useDialog";
import Alert from "../../components/ui/Alert";
import EmptyState from "../../components/ui/EmptyState";
import PageHead from "../../components/ui/PageHead";
import StatusBadge from "../../components/ui/StatusBadge";
import Stepper from "../../components/ui/Stepper";
import { confirmarMatricula, consultarMiOferta, consultarMisAgrupaciones } from "../../utils/matriculaApi";
import {
    espacioChocaCon,
    formatearHorarios,
    mapaAprobados,
    TEXTO_GRUPO_LLENO,
    TEXTO_SIN_CUPO,
    textoCoordinacion,
    textoCupos,
    textoHorarioGrupo,
    textoProfesorGrupo,
    tieneBloqueoEstatico,
    validarSeleccion,
} from "../../utils/matricula";
import AgrupacionesMatricula from "./AgrupacionesMatricula";
import LeccionesInstrumento from "./LeccionesInstrumento";
import "./seleccionCursos.css";
import { mostrarIdentificacion } from "../../utils/cedula";

const PASOS = ["Instrumento", "Cursos", "Agrupaciones", "Confirmar"];
const PASO_INSTRUMENTO = 0;
const PASO_CURSOS = 1;
const PASO_AGRUPACIONES = 2;
const PASO_CONFIRMAR = 3;

// "Choca con «Solfeo II» (Lun 5:00 – 6:20 p. m.)" o con la lección del otro instrumento.
function motivoChoque(otro) {
    const que = otro.espacioInstrumento
        ? `su lección de ${otro.nombreInstrumento}`
        : `«${otro.nombreCurso}»`;
    return `Choca con ${que} (${formatearHorarios(otro.horarios)}).`;
}

// Una agrupación con la forma de un grupo para las validaciones de horario
// (validarSeleccion y espacioChocaCon usan nombreCurso, codigo y horarios).
function comoGrupo(agrupacion) {
    return { ...agrupacion, nombreCurso: agrupacion.nombre, esAgrupacion: true };
}

// Historia 2 - Matricular cursos previamente autorizados.
//
// El administrativo revisa los requisitos y autoriza al estudiante (pantalla
// de Matrícula del administrativo). Solo entonces el estudiante habilitado
// puede seleccionar, en cuatro pasos, su lección de instrumento, sus cursos y
// sus agrupaciones, y confirmar su matrícula sin una segunda aprobación.
// Toda la información viene de la base de datos a través del backend; el
// servidor vuelve a validar todo al confirmar.

function formatearFecha(fecha) {
    if (!fecha) return "";
    const [anio, mes, dia] = fecha.split("-");
    return `${dia}/${mes}/${anio}`;
}

// Oferta y agrupaciones a la vez. Si las agrupaciones fallan, la matrícula de
// cursos sigue funcionando sin ellas.
async function cargarTodo(token) {
    return Promise.all([
        consultarMiOferta(token),
        consultarMisAgrupaciones(token).catch(() => null),
    ]);
}

// En un curso a coordinar en Dirección, el profesor que asignó la Dirección.
function textoProfesor(grupo) {
    return textoProfesorGrupo(grupo);
}

// "Música de Cámara III (MC-III-1) · Coordinar en Dirección" o con horario y profesor.
function lineaGrupo(g) {
    return g.aCoordinar
        ? `${g.nombreCurso} (${g.codigo}) · ${textoCoordinacion(g)}`
        : `${g.nombreCurso} (${g.codigo}) · ${formatearHorarios(g.horarios)} · Profesor: ${textoProfesor(g)}`;
}

// Subtítulo de una fila matriculada: lección, agrupación o código del grupo.
function subtituloClase(grupo) {
    if (grupo.espacioInstrumento) return `Lección individual · ${grupo.nombreInstrumento}`;
    if (grupo.agrupacion) return "Agrupación";
    return grupo.codigo;
}

function SeleccionCursos() {
    const dialog = useDialog();
    const token = localStorage.getItem("token");

    const [datos, setDatos] = useState(null);
    const [datosAgrupaciones, setDatosAgrupaciones] = useState(null);
    const [cargando, setCargando] = useState(true);
    const [errorCarga, setErrorCarga] = useState("");

    const [paso, setPaso] = useState(PASO_INSTRUMENTO);
    const [seleccionados, setSeleccionados] = useState([]);
    // Espacio de instrumento elegido por instrumento: { [idInstrumento]: idGrupo }.
    const [espacios, setEspacios] = useState({});
    // Grupos de agrupación elegidos, en el orden en que se marcaron (el
    // servidor usa ese orden para la regla del coro; el estudiante no la ve).
    const [agrupacionesElegidas, setAgrupacionesElegidas] = useState([]);
    const [enviando, setEnviando] = useState(false);
    const [confirmacion, setConfirmacion] = useState(null);

    function aplicarRespuesta(respuesta, agrupaciones) {
        setDatos(respuesta);
        setDatosAgrupaciones(agrupaciones);
        setErrorCarga("");

        // Si la oferta cambió (cupos agotados, curso ya matriculado), se
        // descartan los grupos que dejaron de estar disponibles.
        const idsVigentes = new Set((respuesta.oferta ?? []).map((g) => g.idGrupo));
        setSeleccionados((prev) => prev.filter((id) => idsVigentes.has(id)));

        // Igual con los espacios que otro estudiante tomó mientras tanto.
        const libres = new Set((respuesta.leccionesInstrumento ?? [])
            .flatMap((l) => l.espaciosLibres ?? [])
            .map((e) => e.idGrupo));
        setEspacios((prev) => Object.fromEntries(
            Object.entries(prev).filter(([, idGrupo]) => libres.has(idGrupo))
        ));

        // Y con las agrupaciones que se llenaron o ya quedaron matriculadas.
        const elegibles = new Set((agrupaciones?.agrupaciones ?? [])
            .filter((a) => !a.bloqueada && !a.matriculada)
            .map((a) => a.idGrupo));
        setAgrupacionesElegidas((prev) => prev.filter((id) => elegibles.has(id)));
    }

    // Carga inicial: es la única que muestra "Cargando…".
    useEffect(() => {
        let cancelado = false;

        cargarTodo(token)
            .then(([oferta, agrupaciones]) => {
                if (!cancelado) aplicarRespuesta(oferta, agrupaciones);
            })
            .catch((error) => {
                if (!cancelado) {
                    setErrorCarga(error.message || "No fue posible consultar la información de matrícula.");
                }
            })
            .finally(() => {
                if (!cancelado) setCargando(false);
            });

        return () => {
            cancelado = true;
        };
    }, [token]);

    // Recarga posterior a una confirmación: actualiza cupos y matriculados
    // sin ocultar la pantalla.
    async function recargarOferta() {
        try {
            const [oferta, agrupaciones] = await cargarTodo(token);
            aplicarRespuesta(oferta, agrupaciones);
        } catch (error) {
            setErrorCarga(error.message || "No fue posible consultar la información de matrícula.");
        }
    }

    const aprobados = useMemo(() => mapaAprobados(datos?.cursosAprobados), [datos]);

    const idsCursosMatriculados = useMemo(
        () => (datos?.matriculados ?? []).filter((g) => !g.agrupacion).map((g) => g.idCurso),
        [datos]
    );

    const bloqueosEstaticos = useMemo(() => {
        const mapa = {};
        (datos?.oferta ?? []).forEach((grupo) => {
            const bloqueo = tieneBloqueoEstatico({
                grupo,
                requisitosPorCurso: datos.requisitosPorCurso,
                aprobados,
                idsCursosMatriculados,
            });
            if (bloqueo) mapa[grupo.idGrupo] = bloqueo;
        });
        return mapa;
    }, [datos, aprobados, idsCursosMatriculados]);

    const gruposSeleccionados = useMemo(
        () => (datos?.oferta ?? []).filter((g) => seleccionados.includes(g.idGrupo)),
        [datos, seleccionados]
    );

    const lecciones = useMemo(() => datos?.leccionesInstrumento ?? [], [datos]);
    const agrupaciones = useMemo(() => datosAgrupaciones?.agrupaciones ?? [], [datosAgrupaciones]);

    // Agrupaciones elegidas, en el orden en que se marcaron.
    const agrupacionesSeleccionadas = useMemo(
        () => agrupacionesElegidas
            .map((id) => agrupaciones.find((a) => a.idGrupo === id))
            .filter(Boolean)
            .map(comoGrupo),
        [agrupacionesElegidas, agrupaciones]
    );

    /*
     * Lecciones de instrumento. Un espacio elegido sigue valiendo mientras
     * no choque con un curso o agrupación elegidos o ya matriculados, ni con
     * la lección (válida) de otro instrumento. Los demás espacios muestran
     * el motivo por el que no se pueden elegir.
     *
     * Un espacio de "cualquier instrumento del profesor" puede aparecer en
     * dos lecciones (por ejemplo Violín y Viola con la misma profesora):
     * vale para una sola. Por eso los motivos van por lección:
     * { "idInstrumento:idGrupo": texto }.
     */
    const { espaciosEfectivos, motivosEspacio } = useMemo(() => {
        const fijos = [...gruposSeleccionados, ...agrupacionesSeleccionadas, ...(datos?.matriculados ?? [])];
        const efectivos = {};
        const elegidosValidos = [];

        lecciones.forEach((leccion) => {
            const espacio = (leccion.espaciosLibres ?? []).find((e) => e.idGrupo === espacios[leccion.idInstrumento]);
            const yaUsado = espacio && elegidosValidos.some((e) => e.idGrupo === espacio.idGrupo);
            if (espacio && !yaUsado && !espacioChocaCon(espacio, [...fijos, ...elegidosValidos])) {
                efectivos[leccion.idInstrumento] = espacio.idGrupo;
                elegidosValidos.push(espacio);
            }
        });

        const motivos = {};
        lecciones.forEach((leccion) => {
            const otrasLecciones = elegidosValidos.filter((e) => e.idInstrumento !== leccion.idInstrumento);
            (leccion.espaciosLibres ?? []).forEach((espacio) => {
                const clave = `${leccion.idInstrumento}:${espacio.idGrupo}`;
                const enOtra = otrasLecciones.find((e) => e.idGrupo === espacio.idGrupo);
                if (enOtra) {
                    motivos[clave] = `Ya lo eligió para su lección de ${enOtra.nombreInstrumento}.`;
                    return;
                }
                const choque = espacioChocaCon(espacio, [...fijos, ...otrasLecciones]);
                if (choque) motivos[clave] = motivoChoque(choque);
            });
        });

        return { espaciosEfectivos: efectivos, motivosEspacio: motivos };
    }, [lecciones, espacios, gruposSeleccionados, agrupacionesSeleccionadas, datos]);

    // Un espacio por lección, con el instrumento de esa lección.
    const espaciosElegidos = useMemo(
        () => lecciones
            .map((l) => (l.espaciosLibres ?? []).find((e) => e.idGrupo === espaciosEfectivos[l.idInstrumento]))
            .filter(Boolean),
        [lecciones, espaciosEfectivos]
    );

    // Lecciones que todavía necesitan su espacio para poder confirmar.
    const leccionesPendientes = lecciones.filter((l) => l.requerida && !espaciosEfectivos[l.idInstrumento]);

    /*
     * Choques y requisitos de lo elegido (cursos y agrupaciones) entre sí y
     * con lo ya matriculado y las lecciones elegidas. Se recalcula al marcar.
     */
    const erroresPorGrupo = useMemo(() => {
        if (!datos) return {};
        return validarSeleccion({
            requisitosPorCurso: datos.requisitosPorCurso,
            aprobados,
            seleccionados: [...gruposSeleccionados, ...agrupacionesSeleccionadas],
            gruposYaMatriculados: [...datos.matriculados, ...espaciosElegidos],
        });
    }, [datos, aprobados, gruposSeleccionados, agrupacionesSeleccionadas, espaciosElegidos]);

    // Agrupaciones no marcadas que chocan con algo ya elegido: se deshabilitan con el motivo.
    const motivosAgrupacion = useMemo(() => {
        const fijos = [
            ...gruposSeleccionados,
            ...agrupacionesSeleccionadas,
            ...espaciosElegidos,
            ...(datos?.matriculados ?? []),
        ];
        const motivos = {};
        agrupaciones.forEach((a) => {
            if (a.matriculada || agrupacionesElegidas.includes(a.idGrupo)) return;
            const choque = espacioChocaCon(a, fijos);
            if (choque) motivos[a.idGrupo] = motivoChoque(choque);
        });
        return motivos;
    }, [agrupaciones, agrupacionesElegidas, gruposSeleccionados, agrupacionesSeleccionadas, espaciosElegidos, datos]);

    const erroresCursos = gruposSeleccionados.some((g) => erroresPorGrupo[g.idGrupo]);
    const erroresAgrupaciones = agrupacionesSeleccionadas.some((a) => erroresPorGrupo[a.idGrupo]);
    const hayValidacionesPendientes = erroresCursos || erroresAgrupaciones;

    const totalSeleccion = seleccionados.length + espaciosElegidos.length + agrupacionesSeleccionadas.length;
    const haySeleccion = totalSeleccion > 0;
    const puedeConfirmar = haySeleccion && !hayValidacionesPendientes && leccionesPendientes.length === 0 && !enviando;

    // Qué impide avanzar desde el paso actual (null = puede).
    function motivoParaNoAvanzar() {
        if (paso === PASO_INSTRUMENTO && leccionesPendientes.length > 0) {
            return `Elija el espacio de su lección de ${leccionesPendientes.map((l) => l.nombreInstrumento).join(" y ")} para continuar.`;
        }
        if (paso === PASO_CURSOS && erroresCursos) return "Corrija las validaciones marcadas para continuar.";
        if (paso === PASO_AGRUPACIONES && erroresAgrupaciones) return "Corrija las agrupaciones marcadas para continuar.";
        return null;
    }

    const motivoAvance = motivoParaNoAvanzar();

    function elegirEspacio(idInstrumento, idGrupo) {
        setEspacios((prev) => ({ ...prev, [idInstrumento]: idGrupo }));
    }

    function toggleSeleccion(grupo) {
        if (bloqueosEstaticos[grupo.idGrupo]) return;
        setSeleccionados((prev) =>
            prev.includes(grupo.idGrupo)
                ? prev.filter((id) => id !== grupo.idGrupo)
                : [...prev, grupo.idGrupo]
        );
    }

    function toggleAgrupacion(agrupacion) {
        if (agrupacion.matriculada) return;
        setAgrupacionesElegidas((prev) => {
            if (prev.includes(agrupacion.idGrupo)) return prev.filter((id) => id !== agrupacion.idGrupo);
            if (agrupacion.bloqueada || motivosAgrupacion[agrupacion.idGrupo]) return prev;
            return [...prev, agrupacion.idGrupo];
        });
    }

    function irAPaso(siguiente) {
        setPaso(siguiente);
        // Lleva el foco al título para lectores de pantalla y teclado.
        requestAnimationFrame(() => document.getElementById("page-title")?.focus());
    }

    async function handleConfirmar() {
        if (!puedeConfirmar) return;

        // El estudiante debe confirmar su selección antes de registrar la matrícula.
        const acepta = await dialog.confirmar(
            "Revise su selección. Al confirmar quedará matriculado y se descontará un cupo en cada grupo.",
            {
                titulo: "Confirmar matrícula",
                lista: [
                    ...espaciosElegidos.map(
                        (e) => `Lección individual · ${e.nombreCurso} · ${formatearHorarios(e.horarios)} · Profesor: ${textoProfesor(e)}`
                    ),
                    ...gruposSeleccionados.map(lineaGrupo),
                    ...agrupacionesSeleccionadas.map(
                        (a) => `Agrupación · ${a.nombre} · ${formatearHorarios(a.horarios)}`
                    ),
                ],
                textoAceptar: "Confirmar matrícula",
            }
        );

        if (!acepta) return;

        setEnviando(true);

        try {
            const resultado = await confirmarMatricula(
                token,
                seleccionados,
                espaciosElegidos.map((e) => ({ idEspacio: e.idGrupo, idInstrumento: e.idInstrumento })),
                // En el orden en que se marcaron.
                agrupacionesSeleccionadas.map((a) => a.idGrupo)
            );
            setConfirmacion(resultado);
            setSeleccionados([]);
            setEspacios({});
            setAgrupacionesElegidas([]);
            await recargarOferta();
        } catch (error) {
            if (error.errores?.length > 0) {
                await dialog.validacion(error.errores, {
                    mensaje: "No fue posible registrar la matrícula:",
                });
            } else {
                await dialog.error(error.message || "No fue posible registrar la matrícula.");
            }

            // Los cupos o la habilitación pudieron cambiar mientras tanto.
            await recargarOferta();
        } finally {
            setEnviando(false);
        }
    }

    function handleMatricularMas() {
        setConfirmacion(null);
        setPaso(PASO_INSTRUMENTO);
    }

    const habilitado = Boolean(datos?.habilitado);

    function insigniaCupos(grupo) {
        // Curso a coordinar: no tiene cupo y nunca se llena.
        if (grupo.sinCupo) return <StatusBadge tone="info" icon={null}>{TEXTO_SIN_CUPO}</StatusBadge>;
        if (grupo.cupoDisponible <= 0) return <StatusBadge tone="neutral">{TEXTO_GRUPO_LLENO}</StatusBadge>;
        if (grupo.cupoDisponible <= 2) {
            return <StatusBadge tone="warning">{grupo.cupoDisponible === 1 ? "Queda 1 cupo" : `Quedan ${grupo.cupoDisponible} cupos`}</StatusBadge>;
        }
        return <StatusBadge tone="success" icon={null}>{grupo.cupoDisponible} de {grupo.cupoMaximo} cupos</StatusBadge>;
    }

    // Filas del resumen: todo lo elegido, con nombre y horario (sin nivel en las agrupaciones).
    const filasResumen = [
        ...espaciosElegidos.map((e) => ({ id: e.idGrupo, tipo: "Lección individual", nombre: e.nombreCurso, horario: formatearHorarios(e.horarios), profesor: textoProfesor(e) })),
        ...gruposSeleccionados.map((g) => ({ id: g.idGrupo, tipo: "Curso", nombre: g.nombreCurso, horario: textoHorarioGrupo(g), profesor: textoProfesor(g) })),
        ...agrupacionesSeleccionadas.map((a) => ({
            id: a.idGrupo,
            tipo: "Agrupación",
            nombre: a.nombre,
            horario: formatearHorarios(a.horarios),
            profesor: a.profesores?.length ? a.profesores.join(", ") : "Por definir",
        })),
    ];

    return (
        <div className="seleccion-cursos-page">
            <PageHead
                title="Matrícula"
                sub="Elija su lección de instrumento, sus cursos y sus agrupaciones del período vigente, y confirme su matrícula."
            />

            {cargando && <p className="muted">Cargando información de matrícula…</p>}
            {errorCarga && <Alert tone="danger" title={errorCarga} className="bloque" />}

            {!cargando && datos && (
                <>
                    <section className="panel resumen-matricula" aria-label="Resumen de la matrícula">
                        <div>
                            <p className="kpi-label"><User size={18} strokeWidth={1.75} aria-hidden="true" />Estudiante</p>
                            <p className="resumen-valor">{datos.estudiante.nombreCompleto}</p>
                            <p className="kpi-note tnum">{mostrarIdentificacion(datos.estudiante.numeroIdentificacion)}</p>
                        </div>

                        {datos.periodo && (
                            <div>
                                <p className="kpi-label"><CalendarDays size={18} strokeWidth={1.75} aria-hidden="true" />Período en matrícula</p>
                                <p className="resumen-valor">{datos.periodo.nombre} ({datos.periodo.codigo})</p>
                                {datos.periodo.fechaInicioMatricula && datos.periodo.fechaFinMatricula && (
                                    <p className="kpi-note">
                                        Del {formatearFecha(datos.periodo.fechaInicioMatricula)} al {formatearFecha(datos.periodo.fechaFinMatricula)}
                                    </p>
                                )}
                            </div>
                        )}

                        {habilitado && datos.planes.length > 0 && (
                            <div>
                                <p className="kpi-label"><BookOpen size={18} strokeWidth={1.75} aria-hidden="true" />Planes de estudio</p>
                                {datos.planes.map((plan) => (
                                    <p className="resumen-valor" key={plan.id}>{plan.nombre}</p>
                                ))}
                            </div>
                        )}
                    </section>

                    {!habilitado && (
                        <Alert tone="info" title="Matrícula no habilitada" className="bloque">
                            {datos.motivoNoHabilitado}
                        </Alert>
                    )}

                    {habilitado && confirmacion && (
                        <div className="panel bloque">
                            <div className="success-screen">
                                <span className="success-mark"><Check size={32} strokeWidth={1.75} aria-hidden="true" /></span>
                                <h2 id="page-title" tabIndex={-1}>Matrícula confirmada</h2>
                                <p className="muted">
                                    Quedó matriculado en lo siguiente de {confirmacion.periodo.nombre}. Los cupos ya fueron actualizados.
                                </p>
                                <p className="receipt">Matrícula <b>#{confirmacion.idMatricula}</b> · <StatusBadge tone="success">Registrada</StatusBadge></p>
                            </div>

                            <div className="table-wrap">
                                <table className="dtable dtable--cards">
                                    <caption className="sr-only">Cursos, lecciones y agrupaciones matriculados</caption>
                                    <thead>
                                        <tr>
                                            <th scope="col">Curso o agrupación</th>
                                            <th scope="col">Horario</th>
                                            <th scope="col">Profesor</th>
                                            <th scope="col" className="num">Cupos restantes</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {confirmacion.cursos.map((curso) => (
                                            <tr key={curso.idGrupo}>
                                                <td className="c-main"><b>{curso.nombreCurso}</b><span className="sub">{subtituloClase(curso)}</span></td>
                                                <td data-label="Horario">{textoHorarioGrupo(curso)}</td>
                                                <td data-label="Profesor">{textoProfesor(curso)}</td>
                                                <td className="num" data-label="Cupos">{textoCupos(curso)}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>

                            <div className="panel-foot">
                                <button type="button" className="sg-btn sg-btn--secondary" onClick={handleMatricularMas}>
                                    <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                                    Matricular más
                                </button>
                            </div>
                        </div>
                    )}

                    {habilitado && !confirmacion && (
                        <>
                            <div className="bloque">
                                <Stepper pasos={PASOS} actual={paso} etiqueta="Pasos de la matrícula" />
                            </div>

                            {datos.matriculados.length > 0 && (
                                <section className="panel bloque">
                                    <div className="panel-head"><h2>Ya matriculado en este período</h2></div>
                                    <div className="table-wrap">
                                        <table className="dtable dtable--cards">
                                            <caption className="sr-only">Cursos ya matriculados</caption>
                                            <thead>
                                                <tr>
                                                    <th scope="col">Curso o agrupación</th>
                                                    <th scope="col">Horario</th>
                                                    <th scope="col">Profesor</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {datos.matriculados.map((grupo) => (
                                                    <tr key={grupo.idGrupo}>
                                                        <td className="c-main">
                                                            <b>{grupo.nombreCurso}</b>
                                                            <span className="sub">{subtituloClase(grupo)}</span>
                                                        </td>
                                                        <td data-label="Horario">{textoHorarioGrupo(grupo)}</td>
                                                        <td data-label="Profesor">{textoProfesor(grupo)}</td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                </section>
                            )}

                            {paso === PASO_INSTRUMENTO && (
                                lecciones.length > 0 ? (
                                    <LeccionesInstrumento
                                        lecciones={lecciones}
                                        efectivos={espaciosEfectivos}
                                        motivos={motivosEspacio}
                                        onElegir={elegirEspacio}
                                        numero={1}
                                    />
                                ) : (
                                    <section className="panel bloque" aria-labelledby="t-sin-lecciones">
                                        <div className="panel-head"><h2 id="t-sin-lecciones">1. Lección de instrumento</h2></div>
                                        <EmptyState
                                            icon={Music}
                                            title="Sin lecciones por elegir"
                                            description="Este período no tiene lecciones de instrumento pendientes para usted. Continúe con sus cursos."
                                            compact
                                        />
                                    </section>
                                )
                            )}

                            {paso === PASO_CURSOS && (
                                <section className="panel bloque">
                                    <div className="panel-head">
                                        <div>
                                            <h2>2. Cursos de su plan</h2>
                                            <p className="panel-head-sub">
                                                Los grupos llenos o con requisitos pendientes aparecen deshabilitados.
                                                Si un curso choca con su lección de instrumento, el sistema se lo indica antes de confirmar.
                                            </p>
                                        </div>
                                    </div>

                                    {datos.oferta.length === 0 ? (
                                        <EmptyState
                                            icon={BookOpen}
                                            title="Sin oferta disponible"
                                            description="No hay cursos autorizados con oferta disponible en este período."
                                        />
                                    ) : (
                                        <fieldset className="fieldset panel-body">
                                            <legend className="sr-only">Seleccione los cursos a matricular</legend>
                                            <div className="choice-list">
                                                {datos.oferta.map((grupo) => {
                                                    const bloqueo = bloqueosEstaticos[grupo.idGrupo];
                                                    const seleccionado = seleccionados.includes(grupo.idGrupo);
                                                    const erroresDinamicos = erroresPorGrupo[grupo.idGrupo] ?? [];
                                                    const mensajes = [bloqueo, ...erroresDinamicos].filter(Boolean);

                                                    return (
                                                        <label
                                                            key={grupo.idGrupo}
                                                            className={`choice opcion-curso${seleccionado ? " is-checked" : ""}${bloqueo ? " is-disabled" : ""}${erroresDinamicos.length > 0 ? " has-error" : ""}`}
                                                        >
                                                            <input
                                                                type="checkbox"
                                                                checked={seleccionado}
                                                                disabled={Boolean(bloqueo)}
                                                                onChange={() => toggleSeleccion(grupo)}
                                                                aria-describedby={mensajes.length > 0 ? `validacion-${grupo.idGrupo}` : undefined}
                                                            />
                                                            <span className="choice-main">
                                                                <b>{grupo.nombreCurso}</b>
                                                                <span>
                                                                    {(grupo.aCoordinar
                                                                        ? [grupo.codigo, grupo.nivel != null ? `Nivel ${grupo.nivel}` : null, textoCoordinacion(grupo)]
                                                                        : [grupo.codigo, grupo.nivel != null ? `Nivel ${grupo.nivel}` : null, formatearHorarios(grupo.horarios), textoProfesor(grupo)]
                                                                    ).filter(Boolean).join(" · ")}
                                                                </span>
                                                                {grupo.nombrePlan && <span className="opcion-plan">{grupo.nombrePlan}</span>}
                                                                {mensajes.length > 0 && (
                                                                    <span className="opcion-errores" id={`validacion-${grupo.idGrupo}`}>
                                                                        {mensajes.map((msg) => (
                                                                            <span className="sg-field__error" key={msg}>
                                                                                <CircleX size={16} strokeWidth={1.75} aria-hidden="true" />
                                                                                {msg}
                                                                            </span>
                                                                        ))}
                                                                    </span>
                                                                )}
                                                            </span>
                                                            {insigniaCupos(grupo)}
                                                        </label>
                                                    );
                                                })}
                                            </div>
                                        </fieldset>
                                    )}
                                </section>
                            )}

                            {paso === PASO_AGRUPACIONES && (
                                <AgrupacionesMatricula
                                    datos={datosAgrupaciones}
                                    elegidas={agrupacionesElegidas}
                                    motivos={motivosAgrupacion}
                                    errores={erroresPorGrupo}
                                    onToggle={toggleAgrupacion}
                                    numero={3}
                                />
                            )}

                            {paso === PASO_CONFIRMAR && (
                                <section className="panel bloque" aria-labelledby="t-resumen">
                                    <div className="panel-head">
                                        <div>
                                            <h2 id="t-resumen">4. Confirmar</h2>
                                            <p className="panel-head-sub">Revise lo que eligió antes de confirmar.</p>
                                        </div>
                                    </div>

                                    {filasResumen.length === 0 ? (
                                        <EmptyState
                                            icon={BookOpen}
                                            title="Todavía no eligió nada"
                                            description="Vuelva a los pasos anteriores y elija al menos una lección, un curso o una agrupación."
                                            compact
                                        />
                                    ) : (
                                        <div className="table-wrap">
                                            <table className="dtable dtable--cards">
                                                <caption className="sr-only">Resumen de lo elegido</caption>
                                                <thead>
                                                    <tr>
                                                        <th scope="col">Curso o agrupación</th>
                                                        <th scope="col">Horario</th>
                                                        <th scope="col">Profesor</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    {filasResumen.map((fila) => (
                                                        <tr key={fila.id}>
                                                            <td className="c-main"><b>{fila.nombre}</b><span className="sub">{fila.tipo}</span></td>
                                                            <td data-label="Horario">{fila.horario}</td>
                                                            <td data-label="Profesor">{fila.profesor}</td>
                                                        </tr>
                                                    ))}
                                                </tbody>
                                            </table>
                                        </div>
                                    )}

                                </section>
                            )}

                            <div className="panel sticky-save bloque">
                                <span
                                    id="motivo-confirmar"
                                    className={motivoAvance || (paso === PASO_CONFIRMAR && hayValidacionesPendientes) ? "dirty" : "muted conteo-seleccion"}
                                    aria-live="polite"
                                >
                                    {(motivoAvance || (paso === PASO_CONFIRMAR && hayValidacionesPendientes)) && <i aria-hidden="true" />}
                                    {totalSeleccion === 1 ? "1 elegido" : `${totalSeleccion} elegidos`}
                                    {motivoAvance && ` — ${motivoAvance}`}
                                    {!motivoAvance && paso === PASO_CONFIRMAR && hayValidacionesPendientes && " — corrija las validaciones marcadas en los pasos anteriores"}
                                    {!motivoAvance && paso === PASO_CONFIRMAR && leccionesPendientes.length > 0 && " — falta el espacio de su lección de instrumento"}
                                </span>
                                <div className="pasos-acciones">
                                    {paso > PASO_INSTRUMENTO && (
                                        <button type="button" className="sg-btn sg-btn--secondary" onClick={() => irAPaso(paso - 1)} disabled={enviando}>
                                            <ArrowLeft size={18} strokeWidth={1.75} aria-hidden="true" />
                                            Atrás
                                        </button>
                                    )}
                                    {paso < PASO_CONFIRMAR ? (
                                        <button
                                            type="button"
                                            className="sg-btn sg-btn--primary"
                                            disabled={Boolean(motivoAvance)}
                                            onClick={() => irAPaso(paso + 1)}
                                            aria-describedby="motivo-confirmar"
                                        >
                                            Continuar
                                            <ArrowRight size={18} strokeWidth={1.75} aria-hidden="true" />
                                        </button>
                                    ) : (
                                        <button
                                            type="button"
                                            className={`sg-btn sg-btn--primary${enviando ? " is-loading" : ""}`}
                                            disabled={!puedeConfirmar}
                                            onClick={handleConfirmar}
                                            aria-busy={enviando || undefined}
                                            aria-describedby="motivo-confirmar"
                                        >
                                            {enviando ? <span className="sg-spinner" aria-hidden="true" /> : <Check size={18} strokeWidth={1.75} aria-hidden="true" />}
                                            {enviando ? "Registrando…" : "Confirmar matrícula"}
                                        </button>
                                    )}
                                </div>
                            </div>
                        </>
                    )}
                </>
            )}
        </div>
    );
}

export default SeleccionCursos;
