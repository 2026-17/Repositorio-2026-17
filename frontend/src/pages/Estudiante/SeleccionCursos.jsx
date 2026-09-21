import { useEffect, useMemo, useState } from "react";

import { useDialog } from "../../components/dialog/useDialog";
import { confirmarMatricula, consultarMiOferta } from "../../utils/matriculaApi";
import {
    formatearHorarios,
    mapaAprobados,
    tieneBloqueoEstatico,
    validarSeleccion,
} from "../../utils/matricula";
import "./seleccionCursos.css";

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

    return (
        <div className="container seleccion-cursos-page">
            <div className="seleccion-cursos-page__header">
                <h1>Matrícula de cursos</h1>
                <p>
                    Seleccione los cursos autorizados del período vigente y confirme su matrícula.
                </p>
            </div>

            {cargando && <p>Cargando información de matrícula…</p>}
            {errorCarga && <p className="alert alert--error">{errorCarga}</p>}

            {!cargando && datos && (
                <>
                    <div className="resumen-matricula">
                        <div>
                            <span className="resumen-matricula__label">Estudiante</span>
                            <strong>{datos.estudiante.nombreCompleto}</strong>
                            <span>{datos.estudiante.numeroIdentificacion}</span>
                        </div>

                        {datos.periodo && (
                            <div>
                                <span className="resumen-matricula__label">Período en matrícula</span>
                                <strong>{datos.periodo.nombre} ({datos.periodo.codigo})</strong>
                                {datos.periodo.fechaInicioMatricula && datos.periodo.fechaFinMatricula && (
                                    <span>
                                        Matrícula del {formatearFecha(datos.periodo.fechaInicioMatricula)} al{" "}
                                        {formatearFecha(datos.periodo.fechaFinMatricula)}
                                    </span>
                                )}
                            </div>
                        )}

                        {habilitado && datos.planes.length > 0 && (
                            <div>
                                <span className="resumen-matricula__label">Planes de estudio</span>
                                {datos.planes.map((plan) => (
                                    <strong key={plan.id}>{plan.nombre}</strong>
                                ))}
                            </div>
                        )}
                    </div>

                    {!habilitado && (
                        <p className="alert alert--info">{datos.motivoNoHabilitado}</p>
                    )}

                    {habilitado && confirmacion && (
                        <div className="matricula-confirmada">
                            <h2>Matrícula confirmada</h2>
                            <p>
                                Matrícula #{confirmacion.idMatricula} ·{" "}
                                <span className="badge badge--registrada">Registrada</span>
                            </p>
                            <p>
                                Quedó matriculado en los siguientes cursos de {confirmacion.periodo.nombre}.
                                Los cupos ya fueron actualizados.
                            </p>

                            <div className="oferta-tabla-wrap">
                                <table className="oferta-tabla">
                                    <thead>
                                        <tr>
                                            <th>Código</th>
                                            <th>Curso</th>
                                            <th>Horario</th>
                                            <th>Profesor</th>
                                            <th>Cupos restantes</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {confirmacion.cursos.map((curso) => (
                                            <tr key={curso.idGrupo}>
                                                <td className="oferta-tabla__codigo">{curso.codigo}</td>
                                                <td>{curso.nombreCurso}</td>
                                                <td>{formatearHorarios(curso.horarios)}</td>
                                                <td>{textoProfesor(curso)}</td>
                                                <td>{curso.cupoDisponible}/{curso.cupoMaximo}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>

                            <button className="btn btn--secondary" onClick={handleMatricularMas}>
                                Matricular más cursos
                            </button>
                        </div>
                    )}

                    {habilitado && !confirmacion && (
                        <>
                            {datos.matriculados.length > 0 && (
                                <>
                                    <h2 className="seccion-titulo">Cursos ya matriculados en este período</h2>
                                    <div className="oferta-tabla-wrap">
                                        <table className="oferta-tabla">
                                            <thead>
                                                <tr>
                                                    <th>Código</th>
                                                    <th>Curso</th>
                                                    <th>Horario</th>
                                                    <th>Profesor</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {datos.matriculados.map((grupo) => (
                                                    <tr key={grupo.idGrupo}>
                                                        <td className="oferta-tabla__codigo">{grupo.codigo}</td>
                                                        <td>{grupo.nombreCurso}</td>
                                                        <td>{formatearHorarios(grupo.horarios)}</td>
                                                        <td>{textoProfesor(grupo)}</td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                </>
                            )}

                            <h2 className="seccion-titulo">Cursos disponibles</h2>
                            <div className="oferta-tabla-wrap">
                                <table className="oferta-tabla">
                                    <thead>
                                        <tr>
                                            <th></th>
                                            <th>Código</th>
                                            <th>Curso</th>
                                            <th>Nivel</th>
                                            <th>Horario</th>
                                            <th>Profesor</th>
                                            <th>Cupos</th>
                                            <th>Validación</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {datos.oferta.map((grupo) => {
                                            const bloqueo = bloqueosEstaticos[grupo.idGrupo];
                                            const seleccionado = seleccionados.includes(grupo.idGrupo);
                                            const erroresDinamicos = erroresPorGrupo[grupo.idGrupo] ?? [];
                                            const sinCupos = grupo.cupoDisponible <= 0;

                                            return (
                                                <tr
                                                    key={grupo.idGrupo}
                                                    className={
                                                        erroresDinamicos.length > 0
                                                            ? "fila--error"
                                                            : seleccionado
                                                                ? "fila--seleccionada"
                                                                : ""
                                                    }
                                                >
                                                    <td>
                                                        <input
                                                            type="checkbox"
                                                            aria-label={`Seleccionar ${grupo.nombreCurso}`}
                                                            checked={seleccionado}
                                                            disabled={Boolean(bloqueo)}
                                                            onChange={() => toggleSeleccion(grupo)}
                                                        />
                                                    </td>
                                                    <td className="oferta-tabla__codigo">{grupo.codigo}</td>
                                                    <td>
                                                        {grupo.nombreCurso}
                                                        {grupo.nombrePlan && (
                                                            <small className="oferta-tabla__plan">{grupo.nombrePlan}</small>
                                                        )}
                                                    </td>
                                                    <td>{grupo.nivel ?? "—"}</td>
                                                    <td>{formatearHorarios(grupo.horarios)}</td>
                                                    <td>{textoProfesor(grupo)}</td>
                                                    <td>
                                                        {sinCupos ? (
                                                            <span className="badge badge--sin-cupos">Sin cupos</span>
                                                        ) : (
                                                            `${grupo.cupoDisponible}/${grupo.cupoMaximo}`
                                                        )}
                                                    </td>
                                                    <td className="oferta-tabla__validacion">
                                                        {bloqueo && <span className="texto-error">{bloqueo}</span>}
                                                        {erroresDinamicos.map((msg) => (
                                                            <span className="texto-error" key={msg}>
                                                                {msg}
                                                            </span>
                                                        ))}
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                                {datos.oferta.length === 0 && (
                                    <p className="oferta-tabla-empty">
                                        No hay cursos autorizados con oferta disponible en este período.
                                    </p>
                                )}
                            </div>

                            <div className="seleccion-cursos-page__acciones">
                                <p>
                                    {seleccionados.length} curso(s) seleccionado(s)
                                    {hayValidacionesPendientes && " — corrija las validaciones marcadas para poder confirmar."}
                                </p>
                                <button className="btn btn--primary" disabled={!puedeConfirmar} onClick={handleConfirmar}>
                                    {enviando ? "Registrando..." : "Confirmar matrícula"}
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
