import { useEffect, useMemo, useState } from "react";

import { BookOpen, CalendarDays, Check, CircleX, Plus, User } from "lucide-react";
import { useDialog } from "../../components/dialog/useDialog";
import Alert from "../../components/ui/Alert";
import EmptyState from "../../components/ui/EmptyState";
import PageHead from "../../components/ui/PageHead";
import StatusBadge from "../../components/ui/StatusBadge";
import { confirmarMatricula, consultarMiOferta } from "../../utils/matriculaApi";
import {
    formatearHorarios,
    mapaAprobados,
    tieneBloqueoEstatico,
    validarSeleccion,
} from "../../utils/matricula";
import "./seleccionCursos.css";
import { mostrarIdentificacion } from "../../utils/cedula";

// Historia 2 - Matricular cursos previamente autorizados.
//
// El administrativo revisa los requisitos y autoriza al estudiante (pantalla
// de Matrícula del administrativo). Solo entonces el estudiante habilitado
// puede seleccionar cursos y confirmar su matrícula, sin una segunda
// aprobación. Toda la información viene de la base de datos a través del
// backend; el servidor vuelve a validar todo al confirmar.

function formatearFecha(fecha) {
    if (!fecha) return "";
    const [anio, mes, dia] = fecha.split("-");
    return `${dia}/${mes}/${anio}`;
}

function textoProfesor(grupo) {
    return grupo.profesor ?? "Por definir";
}

function SeleccionCursos() {
    const dialog = useDialog();
    const token = localStorage.getItem("token");

    const [datos, setDatos] = useState(null);
    const [cargando, setCargando] = useState(true);
    const [errorCarga, setErrorCarga] = useState("");

    const [seleccionados, setSeleccionados] = useState([]);
    const [enviando, setEnviando] = useState(false);
    const [confirmacion, setConfirmacion] = useState(null);

    function aplicarRespuesta(respuesta) {
        setDatos(respuesta);
        setErrorCarga("");

        // Si la oferta cambió (cupos agotados, curso ya matriculado), se
        // descartan los grupos que dejaron de estar disponibles.
        const idsVigentes = new Set(respuesta.oferta.map((g) => g.idGrupo));
        setSeleccionados((prev) => prev.filter((id) => idsVigentes.has(id)));
    }

    // Carga inicial: es la única que muestra "Cargando…".
    useEffect(() => {
        let cancelado = false;

        consultarMiOferta(token)
            .then((respuesta) => {
                if (!cancelado) aplicarRespuesta(respuesta);
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
            aplicarRespuesta(await consultarMiOferta(token));
        } catch (error) {
            setErrorCarga(error.message || "No fue posible consultar la información de matrícula.");
        }
    }

    const aprobados = useMemo(() => mapaAprobados(datos?.cursosAprobados), [datos]);

    const idsCursosMatriculados = useMemo(
        () => (datos?.matriculados ?? []).map((g) => g.idCurso),
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

    const erroresPorGrupo = useMemo(() => {
        if (!datos) return {};
        return validarSeleccion({
            requisitosPorCurso: datos.requisitosPorCurso,
            aprobados,
            seleccionados: gruposSeleccionados,
            gruposYaMatriculados: datos.matriculados,
        });
    }, [datos, aprobados, gruposSeleccionados]);

    const hayValidacionesPendientes = Object.keys(erroresPorGrupo).length > 0;
    const puedeConfirmar = seleccionados.length > 0 && !hayValidacionesPendientes && !enviando;

    function toggleSeleccion(grupo) {
        if (bloqueosEstaticos[grupo.idGrupo]) return;
        setSeleccionados((prev) =>
            prev.includes(grupo.idGrupo)
                ? prev.filter((id) => id !== grupo.idGrupo)
                : [...prev, grupo.idGrupo]
        );
    }

    async function handleConfirmar() {
        if (!puedeConfirmar) return;

        // El estudiante debe confirmar su selección antes de registrar la matrícula.
        const acepta = await dialog.confirmar(
            "Revise su selección. Al confirmar quedará matriculado en estos cursos y se descontará un cupo en cada grupo.",
            {
                titulo: "Confirmar matrícula",
                lista: gruposSeleccionados.map(
                    (g) => `${g.nombreCurso} (${g.codigo}) · ${formatearHorarios(g.horarios)} · Profesor: ${textoProfesor(g)}`
                ),
                textoAceptar: "Confirmar matrícula",
            }
        );

        if (!acepta) return;

        setEnviando(true);

        try {
            const resultado = await confirmarMatricula(token, seleccionados);
            setConfirmacion(resultado);
            setSeleccionados([]);
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
    }

    const habilitado = Boolean(datos?.habilitado);

    function insigniaCupos(grupo) {
        if (grupo.cupoDisponible <= 0) return <StatusBadge tone="neutral">Sin cupos</StatusBadge>;
        if (grupo.cupoDisponible <= 2) {
            return <StatusBadge tone="warning">{grupo.cupoDisponible === 1 ? "Queda 1 cupo" : `Quedan ${grupo.cupoDisponible} cupos`}</StatusBadge>;
        }
        return <StatusBadge tone="success" icon={null}>{grupo.cupoDisponible} de {grupo.cupoMaximo} cupos</StatusBadge>;
    }

    return (
        <div className="seleccion-cursos-page">
            <PageHead
                title="Matrícula de cursos"
                sub="Seleccione los cursos autorizados del período vigente y confirme su matrícula."
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
                                    Quedó matriculado en los siguientes cursos de {confirmacion.periodo.nombre}. Los cupos ya fueron actualizados.
                                </p>
                                <p className="receipt">Matrícula <b>#{confirmacion.idMatricula}</b> · <StatusBadge tone="success">Registrada</StatusBadge></p>
                            </div>

                            <div className="table-wrap">
                                <table className="dtable dtable--cards">
                                    <caption className="sr-only">Cursos matriculados</caption>
                                    <thead>
                                        <tr>
                                            <th scope="col">Curso</th>
                                            <th scope="col">Horario</th>
                                            <th scope="col">Profesor</th>
                                            <th scope="col" className="num">Cupos restantes</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {confirmacion.cursos.map((curso) => (
                                            <tr key={curso.idGrupo}>
                                                <td className="c-main"><b>{curso.nombreCurso}</b><span className="sub">{curso.codigo}</span></td>
                                                <td data-label="Horario">{formatearHorarios(curso.horarios)}</td>
                                                <td data-label="Profesor">{textoProfesor(curso)}</td>
                                                <td className="num" data-label="Cupos">{curso.cupoDisponible}/{curso.cupoMaximo}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>

                            <div className="panel-foot">
                                <button type="button" className="sg-btn sg-btn--secondary" onClick={handleMatricularMas}>
                                    <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                                    Matricular más cursos
                                </button>
                            </div>
                        </div>
                    )}

                    {habilitado && !confirmacion && (
                        <>
                            {datos.matriculados.length > 0 && (
                                <section className="panel bloque">
                                    <div className="panel-head"><h2>Cursos ya matriculados en este período</h2></div>
                                    <div className="table-wrap">
                                        <table className="dtable dtable--cards">
                                            <caption className="sr-only">Cursos ya matriculados</caption>
                                            <thead>
                                                <tr>
                                                    <th scope="col">Curso</th>
                                                    <th scope="col">Horario</th>
                                                    <th scope="col">Profesor</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {datos.matriculados.map((grupo) => (
                                                    <tr key={grupo.idGrupo}>
                                                        <td className="c-main"><b>{grupo.nombreCurso}</b><span className="sub">{grupo.codigo}</span></td>
                                                        <td data-label="Horario">{formatearHorarios(grupo.horarios)}</td>
                                                        <td data-label="Profesor">{textoProfesor(grupo)}</td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                </section>
                            )}

                            <section className="panel bloque">
                                <div className="panel-head">
                                    <div>
                                        <h2>Cursos disponibles</h2>
                                        <p className="panel-head-sub">Los grupos sin cupo o con requisitos pendientes aparecen deshabilitados.</p>
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
                                                                {[grupo.codigo, grupo.nivel != null ? `Nivel ${grupo.nivel}` : null, formatearHorarios(grupo.horarios), textoProfesor(grupo)].filter(Boolean).join(" · ")}
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

                            <div className="panel sticky-save bloque">
                                <span className={hayValidacionesPendientes ? "dirty" : "muted conteo-seleccion"} aria-live="polite">
                                    {hayValidacionesPendientes && <i aria-hidden="true" />}
                                    {seleccionados.length === 1 ? "1 curso seleccionado" : `${seleccionados.length} cursos seleccionados`}
                                    {hayValidacionesPendientes && " — corrija las validaciones marcadas"}
                                </span>
                                <button
                                    type="button"
                                    className={`sg-btn sg-btn--primary${enviando ? " is-loading" : ""}`}
                                    disabled={!puedeConfirmar}
                                    onClick={handleConfirmar}
                                    aria-busy={enviando || undefined}
                                >
                                    {enviando ? <span className="sg-spinner" aria-hidden="true" /> : <Check size={18} strokeWidth={1.75} aria-hidden="true" />}
                                    {enviando ? "Registrando…" : "Confirmar matrícula"}
                                </button>
                            </div>
                        </>
                    )}
                </>
            )}
        </div>
    );
}

export default SeleccionCursos;
