import { useEffect, useState } from "react";

import { useDialog } from "../../components/dialog/useDialog";
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

function ConsultaCursos() {
    const dialog = useDialog();
    const token = localStorage.getItem("token");

    const [programas, setProgramas] = useState([]);
    const [periodos, setPeriodos] = useState([]);
    const [cargandoFiltros, setCargandoFiltros] = useState(true);

    const [idPrograma, setIdPrograma] = useState("");
    const [idPeriodo, setIdPeriodo] = useState("");
    const [texto, setTexto] = useState("");

    const [cursos, setCursos] = useState([]);
    const [buscando, setBuscando] = useState(false);
    const [haBuscado, setHaBuscado] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        let cancelado = false;

        async function cargarFiltros() {
            setCargandoFiltros(true);

            try {
                const [programasData, periodosData] = await Promise.all([
                    consultarProgramas(token),
                    consultarPeriodos(token),
                ]);

                if (cancelado) return;

                setProgramas(Array.isArray(programasData) ? programasData : []);
                setPeriodos(Array.isArray(periodosData) ? periodosData : []);

                // El período más reciente es el primero de la lista.
                if (Array.isArray(periodosData) && periodosData.length > 0) {
                    setIdPeriodo(String(periodosData[0].id));
                }
            } catch (e) {
                if (!cancelado) {
                    dialog.error(
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
    }, [token, dialog]);

    const puedeBuscar = Boolean(idPeriodo) && Boolean(idPrograma) && !buscando && !cargandoFiltros;

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
                texto,
            });

            setCursos(Array.isArray(resultado) ? resultado : []);
            setHaBuscado(true);
        } catch (e) {
            setCursos([]);
            setHaBuscado(false);
            setError(e.message || "No fue posible consultar los cursos disponibles.");
        } finally {
            setBuscando(false);
        }
    }

    function handleLimpiar() {
        setTexto("");
        setCursos([]);
        setHaBuscado(false);
        setError("");
    }

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
                            onChange={(event) => setIdPeriodo(event.target.value)}
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
                            onChange={(event) => setIdPrograma(event.target.value)}
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
                            Buscar por nombre o código (opcional)
                        </label>

                        <input
                            id="texto"
                            type="text"
                            className="input"
                            value={texto}
                            onChange={(event) => setTexto(event.target.value)}
                            placeholder="Ej. Piano o MUS-101"
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
            {error && (
                <p className="alert alert--error" role="alert">
                    {error}
                </p>
            )}

            {haBuscado && !error && (
                <>
                    <p className="consulta-cursos-page__resultado" role="status">
                        {cursos.length === 0
                            ? "No se encontraron cursos disponibles con los filtros seleccionados."
                            : `${cursos.length} curso(s) disponible(s).`}
                    </p>

                    {cursos.length > 0 && (
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
                                        <th>Cupos</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {cursos.map((curso) => {
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
