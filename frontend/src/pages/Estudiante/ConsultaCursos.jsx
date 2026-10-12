import { useEffect, useMemo, useState } from "react";
import { BookOpen, RotateCcw, Search, X } from "lucide-react";

import Alert from "../../components/ui/Alert";
import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import Pagination from "../../components/ui/Pagination";
import StatusBadge from "../../components/ui/StatusBadge";
import { paginar } from "../../components/ui/utilidades";
import {
    consultarCursosDisponibles,
    consultarPeriodos,
    consultarProgramas,
} from "../../utils/consultaCursosApi";
import { formatearHorarios } from "../../utils/matricula";
import "./consultaCursos.css";

// HU-10 - Consultar cursos disponibles de un período académico.
// A diferencia de la pantalla de matrícula, aquí se puede explorar
// cualquier programa y período, con búsqueda opcional por texto.

const TAMANO_PAGINA = 10;

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
    const [pagina, setPagina] = useState(1);

    // Carga de períodos y programas. El estado se actualiza cuando la
    // petición termina (no de forma síncrona dentro del efecto).
    useEffect(() => {
        let cancelado = false;

        Promise.all([consultarProgramas(token), consultarPeriodos(token)])
            .then(([programasData, periodosData]) => {
                if (cancelado) return;

                const listaPeriodos = Array.isArray(periodosData) ? periodosData : [];

                setProgramas(Array.isArray(programasData) ? programasData : []);
                setPeriodos(listaPeriodos);

                if (listaPeriodos.length > 0) {
                    setIdPeriodo(String(periodoPorDefecto(listaPeriodos).id));
                }
            })
            .catch((e) => {
                if (!cancelado) {
                    setErrorFiltros(e.message || "No fue posible cargar los períodos y programas académicos.");
                }
            })
            .finally(() => {
                if (!cancelado) setCargandoFiltros(false);
            });

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

    const filasPagina = paginar(cursosFiltrados, pagina, TAMANO_PAGINA);

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
            setPagina(1);
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
        setPagina(1);
    }

    function cambiarTexto(valor) {
        setTexto(valor);
        setPagina(1);
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

    function contenidoResultados() {
        if (buscando) {
            return (
                <div className="table-wrap" aria-busy="true">
                    <table className="dtable">
                        <caption className="sr-only">Buscando cursos...</caption>
                        <tbody>
                            {[0, 1, 2, 3, 4].map((i) => (
                                <tr key={i}>
                                    {[0, 1, 2, 3, 4, 5].map((j) => (
                                        <td key={j}><span className="skeleton" /></td>
                                    ))}
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            );
        }

        if (!haBuscado) {
            return (
                <EmptyState
                    icon={BookOpen}
                    title="Seleccione un período y un programa"
                    description="Luego pulse «Buscar cursos» para ver los grupos ofertados con su horario, profesor y cupos."
                />
            );
        }

        if (cursos.length === 0) {
            return (
                <EmptyState
                    icon={BookOpen}
                    title="No hay cursos disponibles"
                    description="No se encontraron cursos con los filtros seleccionados. Pruebe con otro período o programa académico."
                />
            );
        }

        if (cursosFiltrados.length === 0) {
            return (
                <EmptyState
                    title={`Ningún curso coincide con «${texto.trim()}»`}
                    description="Pruebe con otro nombre o código, o quite el filtro."
                    action={(
                        <button type="button" className="sg-btn sg-btn--secondary" onClick={() => cambiarTexto("")}>
                            Quitar filtro
                        </button>
                    )}
                />
            );
        }

        return (
            <>
                <div className="table-wrap">
                    <table className="dtable dtable--cards">
                        <caption className="sr-only">Cursos disponibles, página {pagina}</caption>
                        <thead>
                            <tr>
                                <th scope="col">Curso</th>
                                <th scope="col" className="num">Nivel</th>
                                <th scope="col">Grupo</th>
                                <th scope="col">Horario</th>
                                <th scope="col">Profesor</th>
                                <th scope="col">Cupos</th>
                            </tr>
                        </thead>
                        <tbody>
                            {filasPagina.map((curso) => {
                                const sinCupos = curso.cupoDisponible <= 0;

                                return (
                                    <tr key={curso.idGrupo}>
                                        <td className="c-main">
                                            <div className="who">
                                                <div>
                                                    <b>{curso.nombreCurso}</b>
                                                    <span>{curso.codigoCurso}</span>
                                                </div>
                                            </div>
                                        </td>
                                        <td className="num" data-label="Nivel">{curso.nivel ?? "—"}</td>
                                        <td data-label="Grupo">{curso.codigoGrupo}</td>
                                        <td data-label="Horario">{formatearHorarios(curso.horarios)}</td>
                                        <td data-label="Profesor">{textoProfesor(curso)}</td>
                                        <td>
                                            {sinCupos ? (
                                                <StatusBadge tone="danger">Sin cupos</StatusBadge>
                                            ) : (
                                                <StatusBadge tone="success">
                                                    {curso.cupoDisponible} de {curso.cupoMaximo}
                                                </StatusBadge>
                                            )}
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>

                <div className="table-foot">
                    <Pagination
                        page={pagina}
                        pageSize={TAMANO_PAGINA}
                        total={cursosFiltrados.length}
                        onChange={setPagina}
                    />
                </div>
            </>
        );
    }

    return (
        <>
            <PageHead
                title="Cursos disponibles"
                sub="Consulte los cursos ofertados en un período académico: horario, grupo, profesor asignado y cupos disponibles antes de solicitar matrícula."
            />

            {errorFiltros && (
                <Alert tone="danger" title="No se pudieron cargar los filtros" className="consulta-cursos-alert">
                    {errorFiltros}
                </Alert>
            )}
            {sinPeriodos && (
                <Alert tone="info" className="consulta-cursos-alert">
                    No hay períodos académicos disponibles para consulta en este momento.
                </Alert>
            )}
            {error && (
                <Alert tone="danger" className="consulta-cursos-alert">
                    {error}
                </Alert>
            )}

            <div className="panel">
                <form className="filters" role="search" onSubmit={handleBuscar} noValidate>
                    <Field id="cmbPeriodoConsulta" label="Período académico" select>
                        <select
                            id="cmbPeriodoConsulta"
                            className="sg-input sg-select"
                            value={idPeriodo}
                            onChange={handleCambiarPeriodo}
                            disabled={cargandoFiltros}
                            aria-required="true"
                        >
                            <option value="">{cargandoFiltros ? "Cargando…" : "Seleccione un período"}</option>
                            {periodos.map((periodo) => (
                                <option key={periodo.id} value={periodo.id}>
                                    {periodo.nombre} ({periodo.codigo})
                                </option>
                            ))}
                        </select>
                    </Field>

                    <Field id="cmbProgramaConsulta" label="Programa académico" select>
                        <select
                            id="cmbProgramaConsulta"
                            className="sg-input sg-select"
                            value={idPrograma}
                            onChange={handleCambiarPrograma}
                            disabled={cargandoFiltros}
                            aria-required="true"
                        >
                            <option value="">{cargandoFiltros ? "Cargando…" : "Seleccione un programa"}</option>
                            {programas.map((programa) => (
                                <option key={programa.id} value={programa.id}>
                                    {programa.nombre}
                                </option>
                            ))}
                        </select>
                    </Field>

                    <Field id="txtFiltroCurso" label="Filtrar" optional icon={Search} className="f-search">
                        <input
                            id="txtFiltroCurso"
                            className="sg-input"
                            type="search"
                            value={texto}
                            onChange={(event) => cambiarTexto(event.target.value)}
                            placeholder="Nombre o código del curso"
                            disabled={cargandoFiltros}
                        />
                    </Field>

                    <div className="f-actions">
                        <button
                            type="button"
                            className="sg-btn sg-btn--secondary"
                            onClick={handleLimpiar}
                            disabled={buscando}
                        >
                            <RotateCcw size={18} strokeWidth={1.75} aria-hidden="true" />
                            Limpiar
                        </button>
                        <button
                            type="submit"
                            className={`sg-btn sg-btn--primary${buscando ? " is-loading" : ""}`}
                            disabled={!puedeBuscar}
                            aria-busy={buscando || undefined}
                        >
                            {buscando
                                ? <span className="sg-spinner" aria-hidden="true" />
                                : <Search size={18} strokeWidth={1.75} aria-hidden="true" />}
                            {buscando ? "Buscando…" : "Buscar cursos"}
                        </button>
                    </div>
                </form>

                {haBuscado && !buscando && cursos.length > 0 && (
                    <div className="chips">
                        <span className="result-count" aria-live="polite">
                            <b>{cursosFiltrados.length}</b>
                            {cursosFiltrados.length === 1 ? " curso disponible" : " cursos disponibles"}
                            {" · "}cupos consultados a las {horaConsulta}
                        </span>
                        {texto.trim() && (
                            <span className="chip">
                                Filtro: {texto.trim()}
                                <button type="button" aria-label="Quitar filtro de texto" onClick={() => cambiarTexto("")}>
                                    <X size={14} strokeWidth={1.75} aria-hidden="true" />
                                </button>
                            </span>
                        )}
                        <button type="button" className="link-btn chips-clear" onClick={handleBuscar}>
                            Actualizar cupos
                        </button>
                    </div>
                )}

                {contenidoResultados()}
            </div>
        </>
    );
}

export default ConsultaCursos;
