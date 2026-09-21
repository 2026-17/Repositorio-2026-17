import { useEffect, useMemo, useState } from "react";
import {
    enviarSolicitudMatricula,
    obtenerEstudianteActual,
    obtenerMatriculasVigentes,
    obtenerOfertaCursos,
    obtenerPeriodoEnMatricula,
    obtenerPreaprobacion,
    obtenerRequisitosPorPlan,
} from "../../data/matriculaMockData";
import { formatearHorarios, tieneBloqueoEstatico, validarSeleccion } from "../../utils/matricula";
import "./seleccionCursos.css";

// HU-002 / Issue #61 - Selección de cursos para matrícula.
//
// El backend real de matrícula (oferta de grupos con horario/cupo/profesor,
// requisitos por curso y el envío de la solicitud) todavía no existe; esta
// pantalla trabaja contra datos simulados en src/data/matriculaMockData.js
// que respetan el modelo definido en sigac_db.sql, para poder integrarse más
// adelante sin cambios en la lógica de esta página.
function SeleccionCursos() {
    const [estudiante, setEstudiante] = useState(null);
    const [periodo, setPeriodo] = useState(null);
    const [preaprobacion, setPreaprobacion] = useState(null);
    const [oferta, setOferta] = useState([]);
    const [requisitosPorCurso, setRequisitosPorCurso] = useState({});
    const [cursosMatriculados, setCursosMatriculados] = useState([]);

    const [cargando, setCargando] = useState(true);
    const [error, setError] = useState("");
    const [mensaje, setMensaje] = useState("");

    const [seleccionados, setSeleccionados] = useState([]);
    const [enviando, setEnviando] = useState(false);
    const [solicitud, setSolicitud] = useState(null);

    useEffect(() => {
        let cancelado = false;

        async function cargarDatosIniciales() {
            setCargando(true);
            setError("");
            try {
                const est = await obtenerEstudianteActual();
                if (cancelado) return;
                setEstudiante(est);

                if (!est.activo) {
                    setError('Solo un estudiante en estado "Activo" puede acceder al módulo de matrícula.');
                    return;
                }

                const per = await obtenerPeriodoEnMatricula();
                if (cancelado) return;
                setPeriodo(per);

                if (!per || per.estado !== "MATRICULA") {
                    setError("No hay un período académico en etapa de matrícula actualmente.");
                    return;
                }

                const preap = await obtenerPreaprobacion(est.id, per.id);
                if (cancelado) return;
                setPreaprobacion(preap);

                if (!preap.habilitado) {
                    setError(
                        "El estudiante no está habilitado para matricular en este período. Se requiere la revisión previa del administrador académico."
                    );
                    return;
                }

                const [ofertaCursos, requisitos, matriculasVigentes] = await Promise.all([
                    obtenerOfertaCursos(per.id),
                    obtenerRequisitosPorPlan(est.idPlanEstudio),
                    obtenerMatriculasVigentes(est.id, per.id),
                ]);
                if (cancelado) return;
                setOferta(ofertaCursos.filter((grupo) => preap.idsCursosAutorizados.includes(grupo.idCurso)));
                setRequisitosPorCurso(requisitos);
                setCursosMatriculados(matriculasVigentes);
            } catch (e) {
                if (!cancelado) setError(e.message || "No se pudo cargar la información de matrícula.");
            } finally {
                if (!cancelado) setCargando(false);
            }
        }

        cargarDatosIniciales();
        return () => {
            cancelado = true;
        };
    }, []);

    const idsCursosMatriculados = useMemo(
        () => cursosMatriculados.map((c) => c.idCurso),
        [cursosMatriculados]
    );

    const bloqueosEstaticos = useMemo(() => {
        if (!estudiante) return {};
        const mapa = {};
        oferta.forEach((grupo) => {
            const bloqueo = tieneBloqueoEstatico({
                grupo,
                requisitosPorCurso,
                cursosAprobados: estudiante.cursosAprobados,
                oferta,
                idsCursosMatriculados,
            });
            if (bloqueo) mapa[grupo.idGrupo] = bloqueo;
        });
        return mapa;
    }, [oferta, requisitosPorCurso, estudiante, idsCursosMatriculados]);

    const erroresPorGrupo = useMemo(() => {
        if (!estudiante) return {};
        return validarSeleccion({
            oferta,
            requisitosPorCurso,
            cursosAprobados: estudiante.cursosAprobados,
            idsGruposSeleccionados: seleccionados,
            gruposYaMatriculados: cursosMatriculados,
        });
    }, [oferta, requisitosPorCurso, estudiante, seleccionados, cursosMatriculados]);

    const hayValidacionesPendientes = Object.keys(erroresPorGrupo).length > 0;
    const puedeEnviar = seleccionados.length > 0 && !hayValidacionesPendientes && !enviando;

    function toggleSeleccion(grupo) {
        if (bloqueosEstaticos[grupo.idGrupo]) return;
        setMensaje("");
        setSeleccionados((prev) =>
            prev.includes(grupo.idGrupo)
                ? prev.filter((id) => id !== grupo.idGrupo)
                : [...prev, grupo.idGrupo]
        );
    }

    async function handleEnviarSolicitud() {
        setError("");
        setMensaje("");
        if (!puedeEnviar) return;

        setEnviando(true);
        try {
            const resultado = await enviarSolicitudMatricula({
                idEstudiante: estudiante.id,
                idPeriodo: periodo.id,
                idsGrupos: seleccionados,
            });

            setOferta((prev) =>
                prev.map((grupo) => {
                    const actualizado = resultado.cursos.find((c) => c.idGrupo === grupo.idGrupo);
                    return actualizado ? { ...grupo, cupoDisponible: actualizado.cupoDisponible } : grupo;
                })
            );
            setSolicitud(resultado);
            setSeleccionados([]);
            setMensaje(
                `Solicitud #${resultado.idSolicitud} enviada correctamente. Se reservó un cupo provisional en cada curso seleccionado y el administrador fue notificado para su revisión.`
            );
        } catch (e) {
            setError(e.message || "No se pudo enviar la solicitud de matrícula.");
        } finally {
            setEnviando(false);
        }
    }

    function handleNuevaSolicitud() {
        setSolicitud(null);
        setMensaje("");
        setError("");
    }

    return (
        <div className="container seleccion-cursos-page">
            <div className="seleccion-cursos-page__header">
                <div>
                    <h1>Solicitud de matrícula</h1>
                    <p>Seleccione los cursos disponibles del período vigente para solicitar su matrícula.</p>
                </div>
            </div>

            {cargando && <p>Cargando información de matrícula…</p>}
            {error && <p className="alert alert--error">{error}</p>}
            {mensaje && <p className="alert alert--success">{mensaje}</p>}

            {!cargando &&
                estudiante &&
                estudiante.activo &&
                periodo &&
                periodo.estado === "MATRICULA" &&
                preaprobacion &&
                preaprobacion.habilitado && (
                <>
                    <div className="resumen-matricula">
                        <div>
                            <span className="resumen-matricula__label">Estudiante</span>
                            <strong>{estudiante.nombreCompleto}</strong>
                            <span>{estudiante.nombrePlanEstudio}</span>
                        </div>
                        <div>
                            <span className="resumen-matricula__label">Período en matrícula</span>
                            <strong>{periodo.nombre} ({periodo.codigo})</strong>
                            <span>
                                Matrícula del {periodo.fechaInicioMatricula} al {periodo.fechaFinMatricula}
                            </span>
                        </div>
                    </div>

                    {solicitud ? (
                        <div className="solicitud-confirmacion">
                            <h2>Solicitud #{solicitud.idSolicitud}</h2>
                            <p>
                                Estado: <span className="badge badge--pendiente">Pendiente de revisión</span>
                            </p>
                            <ul>
                                {solicitud.cursos.map((c) => (
                                    <li key={c.idGrupo}>
                                        {c.codigo} — cupo provisional reservado (quedan {c.cupoDisponible} disponibles)
                                    </li>
                                ))}
                            </ul>
                            <button className="btn btn--secondary" onClick={handleNuevaSolicitud}>
                                Realizar otra solicitud
                            </button>
                        </div>
                    ) : (
                        <>
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
                                        {oferta.map((grupo) => {
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
                                                            checked={seleccionado}
                                                            disabled={Boolean(bloqueo)}
                                                            onChange={() => toggleSeleccion(grupo)}
                                                        />
                                                    </td>
                                                    <td>{grupo.codigo}</td>
                                                    <td>{grupo.nombreCurso}</td>
                                                    <td>{grupo.nivel ?? "—"}</td>
                                                    <td>{formatearHorarios(grupo.horarios)}</td>
                                                    <td>{grupo.profesor}</td>
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
                                {oferta.length === 0 && (
                                    <p className="oferta-tabla-empty">No hay cursos disponibles para este período.</p>
                                )}
                            </div>

                            <div className="seleccion-cursos-page__acciones">
                                <p>
                                    {seleccionados.length} curso(s) seleccionado(s)
                                    {hayValidacionesPendientes && " — corrija las validaciones marcadas para poder enviar la solicitud."}
                                </p>
                                <button className="btn btn--primary" disabled={!puedeEnviar} onClick={handleEnviarSolicitud}>
                                    {enviando ? "Enviando..." : "Enviar solicitud de matrícula"}
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
