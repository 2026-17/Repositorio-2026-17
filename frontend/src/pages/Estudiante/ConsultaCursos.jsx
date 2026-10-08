import { useEffect, useMemo, useState } from "react";

import {
    consultarCursosDisponibles,
    consultarPeriodos,
    consultarProgramas,
} from "../../utils/consultaCursosApi";
import { formatearHorarios } from "../../utils/matricula";
import "./seleccionCursos.css";
import "./consultaCursos.css";

// HU-10 - Consultar cursos disponibles de un período académico.
// A diferencia de la pantalla de matrícula, aquí se puede explorar
// cualquier programa y período, con búsqueda opcional por texto.

function textoProfesor(curso) {
    return curso.profesor ?? "Por definir";
}

// Antes de solicitar matrícula interesa el período que está en matrícula;
// si no hay ninguno, el más reciente (la lista viene ordenada así).
function periodoPorDefecto(periodos) {
    const enMatricula = periodos.find((periodo) => periodo.estado === "MATRICULA");
    return enMatricula ?? periodos[0];
}

function horaActual() {
    return new Date().toLocaleTimeString("es-CR", { hour: "2-digit", minute: "2-digit" });
}

function ConsultaCursos() {
    const token = localStorage.getItem("token");

    const [programas, setProgramas] = useState([]);
    const [periodos, setPeriodos] = useState([]);
    const [cargandoFiltros, setCargandoFiltros] = useState(true);
    const [errorFiltros, setErrorFiltros] = useState("");

    const [idPrograma, setIdPrograma] = useState("");
    const [idPeriodo, setIdPeriodo] = useState("");
    const [texto, setTexto] = useState("");

    const [cursos, setCursos] = useState([]);
    const [buscando, setBuscando] = useState(false);
    const [haBuscado, setHaBuscado] = useState(false);
    const [horaConsulta, setHoraConsulta] = useState("");
    const [error, setError] = useState("");

    useEffect(() => {
        let cancelado = false;

        async function cargarFiltros() {
            setCargandoFiltros(true);
            setErrorFiltros("");

            try {
                const [programasData, periodosData] = await Promise.all([
                    consultarProgramas(token),
                    consultarPeriodos(token),
                ]);

                if (cancelado) return;

                const listaPeriodos = Array.isArray(periodosData) ? periodosData : [];

                setProgramas(Array.isArray(programasData) ? programasData : []);
                setPeriodos(listaPeriodos);

                if (listaPeriodos.length > 0) {
                    setIdPeriodo(String(periodoPorDefecto(listaPeriodos).id));
                }
            } catch (e) {
                if (!cancelado) {
                    setErrorFiltros(
                        e.message || "No fue posible cargar los períodos y programas académicos."
                    );
                }
            } finally {
                if (!cancelado) setCargandoFiltros(false);
            }
        }

        cargarFiltros();

        return () => {
            cancelado = true;
        };
    }, [token]);

    const puedeBuscar = Boolean(idPeriodo) && Boolean(idPrograma) && !buscando && !cargandoFiltros;

    // El texto no se envía al servidor: filtra en el momento la lista ya
    // cargada, para que el estudiante pueda escribir mientras ve los
    // cursos reales en vez de adivinar un nombre o código antes de verlos.
    const cursosFiltrados = useMemo(() => {
        const textoNormalizado = texto.trim().toLowerCase();

        if (!textoNormalizado) return cursos;

        return cursos.filter(
            (curso) =>
                curso.nombreCurso?.toLowerCase().includes(textoNormalizado) ||
                curso.codigoCurso?.toLowerCase().includes(textoNormalizado)
        );
    }, [cursos, texto]);

    async function handleBuscar(event) {
        event.preventDefault();

        if (!idPeriodo || !idPrograma) {
            setError("Seleccione un período académico y un programa académico.");
            return;
        }

        setBuscando(true);
        setError("");

        try {
            const resultado = await consultarCursosDisponibles(token, {
                idPeriodo,
                idPrograma,
            });

            setCursos(Array.isArray(resultado) ? resultado : []);
            setHaBuscado(true);
            setHoraConsulta(horaActual());
        } catch (e) {
            setCursos([]);
            setHaBuscado(false);
            setError(e.message || "No fue posible consultar los cursos disponibles.");
        } finally {
            setBuscando(false);
        }
    }

    // Los resultados mostrados siempre deben corresponder al período y
    // programa seleccionados: al cambiar cualquiera de ellos se descartan.
    function limpiarResultados() {
        setCursos([]);
        setHaBuscado(false);
        setHoraConsulta("");
        setError("");
    }

    function handleCambiarPeriodo(event) {
        setIdPeriodo(event.target.value);
        limpiarResultados();
    }

    function handleCambiarPrograma(event) {
        setIdPrograma(event.target.value);
        limpiarResultados();
    }

    function handleLimpiar() {
        setTexto("");
        limpiarResultados();
    }

    const sinPeriodos = !cargandoFiltros && !errorFiltros && periodos.length === 0;

    return (
        <div className="container consulta-cursos-page">
            <div className="consulta-cursos-page__header">
                <h1>Cursos disponibles</h1>
                <p>
                    Consulte los cursos ofertados en un período académico para conocer
                    su horario, grupo, profesor asignado y cupos disponibles antes de
                    solicitar matrícula.
                </p>
            </div>

            <form className="consulta-cursos-filtros" onSubmit={handleBuscar} noValidate>
                <div className="consulta-cursos-filtros__campos">
                    <div className="field">
                        <label className="field-label" htmlFor="idPeriodo">
                            Período académico <span className="required">*</span>
                        </label>

                        <select
                            id="idPeriodo"
                            className="input"
                            value={idPeriodo}
                            onChange={handleCambiarPeriodo}
                            disabled={cargandoFiltros}
                            aria-required="true"
                        >
                            <option value="">Seleccione un período</option>

                            {periodos.map((periodo) => (
                                <option key={periodo.id} value={periodo.id}>
                                    {periodo.nombre} ({periodo.codigo})
                                </option>
                            ))}
                        </select>
                    </div>

                    <div className="field">
                        <label className="field-label" htmlFor="idPrograma">
                            Programa académico <span className="required">*</span>
                        </label>

                        <select
                            id="idPrograma"
                            className="input"
                            value={idPrograma}
                            onChange={handleCambiarPrograma}
                            disabled={cargandoFiltros}
                            aria-required="true"
                        >
                            <option value="">Seleccione un programa</option>

                            {programas.map((programa) => (
                                <option key={programa.id} value={programa.id}>
                                    {programa.nombre}
                                </option>
                            ))}
                        </select>
                    </div>

                    <div className="field">
                        <label className="field-label" htmlFor="texto">
                            Filtrar por nombre o código (opcional)
                        </label>

                        <input
                            id="texto"
                            type="text"
                            className="input"
                            value={texto}
                            onChange={(event) => setTexto(event.target.value)}
                            placeholder="Escriba para filtrar los resultados"
                            disabled={cargandoFiltros}
                        />
                    </div>
                </div>

                <div className="consulta-cursos-filtros__acciones">
                    <button
                        type="button"
                        className="btn btn--secondary"
                        onClick={handleLimpiar}
                        disabled={buscando}
                    >
                        Limpiar
                    </button>

                    <button
                        type="submit"
                        className="btn btn--primary"
                        disabled={!puedeBuscar}
                    >
                        {buscando ? "Buscando..." : "Buscar cursos"}
                    </button>
                </div>
            </form>

            {cargandoFiltros && <p>Cargando períodos y programas académicos...</p>}
            {errorFiltros && (
                <p className="alert alert--error" role="alert">
                    {errorFiltros}
                </p>
            )}
            {sinPeriodos && (
                <p className="alert alert--info">
                    No hay períodos académicos disponibles para consulta en este momento.
                </p>
            )}
            {error && (
                <p className="alert alert--error" role="alert">
                    {error}
                </p>
            )}

            {haBuscado && !error && (
                <>
                    <p className="consulta-cursos-page__resultado" role="status">
                        {cursos.length === 0
                            ? "No se encontraron cursos disponibles con los filtros seleccionados. Pruebe con otro período o programa académico."
                            : cursosFiltrados.length === 0
                                ? `Ningún curso coincide con "${texto.trim()}". Pruebe con otro nombre o código, o borre el filtro.`
                                : `${cursosFiltrados.length} curso(s) disponible(s). Cupos consultados a las ${horaConsulta}; vuelva a buscar para actualizarlos.`}
                    </p>

                    {cursosFiltrados.length > 0 && (
                        <div className="oferta-tabla-wrap">
                            <table className="oferta-tabla">
                                <thead>
                                    <tr>
                                        <th>Código</th>
                                        <th>Curso</th>
                                        <th>Nivel</th>
                                        <th>Grupo</th>
                                        <th>Horario</th>
                                        <th>Profesor</th>
                                        <th>Cupos disponibles</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {cursosFiltrados.map((curso) => {
                                        const sinCupos = curso.cupoDisponible <= 0;

                                        return (
                                            <tr key={curso.idGrupo}>
                                                <td className="oferta-tabla__codigo">
                                                    {curso.codigoCurso}
                                                </td>
                                                <td>{curso.nombreCurso}</td>
                                                <td>{curso.nivel ?? "—"}</td>
                                                <td>{curso.codigoGrupo}</td>
                                                <td>{formatearHorarios(curso.horarios)}</td>
                                                <td>{textoProfesor(curso)}</td>
                                                <td>
                                                    {sinCupos ? (
                                                        <span className="badge badge--sin-cupos">
                                                            Sin cupos
                                                        </span>
                                                    ) : (
                                                        `${curso.cupoDisponible}/${curso.cupoMaximo}`
                                                    )}
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    )}
                </>
            )}
        </div>
    );
}

export default ConsultaCursos;
