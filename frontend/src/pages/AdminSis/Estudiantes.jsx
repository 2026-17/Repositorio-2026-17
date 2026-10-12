import { useEffect, useMemo, useState } from "react";
import { ChevronsUpDown, FileText, Pencil, Plus, Search, UserCheck, UserX, Users, X } from "lucide-react";
import { API_URL } from "../../config/api.js";
import CreateStudent from "./createStudent";
import EditarEstudiante from "./EditarEstudiante";
import DocumentosEstudiante from "./DocumentosEstudiante";
import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import Pagination from "../../components/ui/Pagination";
import StatusBadge from "../../components/ui/StatusBadge";
import { iniciales, paginar } from "../../components/ui/utilidades";
import "./styles/admin_Sis.css";
import "./styles/Estudiantes.css";

import { useDialog } from "../../components/dialog/useDialog";
import { lanzarError, mensajeDeExcepcion } from "../../utils/api";

const TAMANO_PAGINA = 10;
const ETIQUETA_ESTADO = { true: "Activos", false: "Inactivos", "": "Todos" };

function getHeaders() {
    const token = localStorage.getItem("token");
    return token ? { Authorization: `Bearer ${token}` } : {};
}

async function obtenerEstudiantes(filtroActivo) {
    const params = new URLSearchParams();
    if (filtroActivo) params.set("activo", filtroActivo);

    const response = await fetch(
        `${API_URL}/estudiantes/listado${params.toString() ? `?${params.toString()}` : ""}`,
        { headers: getHeaders() }
    );

    if (!response.ok) await lanzarError(response, "No se pudo cargar la lista de estudiantes.");
    const datos = await response.json();
    return Array.isArray(datos) ? datos : [];
}

function Estudiantes() {
    const dialog = useDialog();
    const [estudiantes, setEstudiantes] = useState([]);
    const [busqueda, setBusqueda] = useState("");
    const [filtroActivo, setFiltroActivo] = useState("true");
    const [orden, setOrden] = useState("asc");
    const [pagina, setPagina] = useState(1);
    const [cargando, setCargando] = useState(true);
    const [formAbierto, setFormAbierto] = useState(false);
    const [editingId, setEditingId] = useState(null);
    const [bajaEstudiante, setBajaEstudiante] = useState(null);
    const [motivoBaja, setMotivoBaja] = useState("");
    const [bajaError, setBajaError] = useState("");
    const [bajaCargando, setBajaCargando] = useState(false);
    // HU-022: estudiante cuyo expediente documental se está administrando.
    const [estudianteDocumentos, setEstudianteDocumentos] = useState(null);

    // Carga inicial y al cambiar el filtro. El estado se actualiza cuando la
    // petición termina (no de forma síncrona dentro del efecto).
    useEffect(() => {
        let activo = true;

        obtenerEstudiantes(filtroActivo)
            .then((datos) => { if (activo) setEstudiantes(datos); })
            .catch((e) => { if (activo) dialog.error(mensajeDeExcepcion(e, "No se pudo cargar la lista de estudiantes.")); })
            .finally(() => { if (activo) setCargando(false); });

        return () => { activo = false; };
    }, [filtroActivo, dialog]);

    // Recarga desde acciones del usuario (guardar, dar de baja, reactivar).
    async function recargarEstudiantes() {
        setCargando(true);
        try {
            setEstudiantes(await obtenerEstudiantes(filtroActivo));
        } catch (e) {
            dialog.error(mensajeDeExcepcion(e, "No se pudo cargar la lista de estudiantes."));
        } finally {
            setCargando(false);
        }
    }

    const estudiantesFiltrados = useMemo(() => {
        const texto = busqueda.trim().toLowerCase();
        const lista = !texto ? [...estudiantes] : estudiantes.filter((e) =>
            (e.nombreCompleto || "").toLowerCase().includes(texto) ||
            (e.numeroIdentificacion || "").toLowerCase().includes(texto)
        );

        lista.sort((a, b) => {
            const comparacion = (a.nombreCompleto || "").localeCompare(b.nombreCompleto || "", "es");
            return orden === "asc" ? comparacion : -comparacion;
        });

        return lista;
    }, [estudiantes, busqueda, orden]);

    const filasPagina = paginar(estudiantesFiltrados, pagina, TAMANO_PAGINA);

    function cambiarBusqueda(valor) {
        setBusqueda(valor);
        setPagina(1);
    }

    function cambiarEstado(valor) {
        // El efecto recarga la lista; el indicador solo se muestra si cambia el filtro.
        if (valor !== filtroActivo) setCargando(true);
        setFiltroActivo(valor);
        setPagina(1);
    }

    function quitarFiltros() {
        cambiarBusqueda("");
        cambiarEstado("true");
    }

    function abrirNuevo() {
        setBajaEstudiante(null);
        setEstudianteDocumentos(null);
        setEditingId(null);
        setFormAbierto(true);
        requestAnimationFrame(() =>
            document.getElementById("form-estudiante")?.scrollIntoView({
                behavior: "smooth",
                block: "start",
            })
        );
    }

    function abrirEditar(estudiante) {
        setBajaEstudiante(null);
        setEstudianteDocumentos(null);
        setEditingId(estudiante.id);
        setFormAbierto(true);
        requestAnimationFrame(() =>
            document.getElementById("form-estudiante")?.scrollIntoView({
                behavior: "smooth",
                block: "start",
            })
        );
    }

    function cerrarFormulario() {
        setFormAbierto(false);
        setEditingId(null);
    }

    // HU-022: abre la gestión documental del estudiante seleccionado.
    function abrirDocumentos(estudiante) {
        setFormAbierto(false);
        setEditingId(null);
        setBajaEstudiante(null);
        setEstudianteDocumentos(estudiante);
        requestAnimationFrame(() =>
            document.getElementById("documentos-estudiante")?.scrollIntoView({
                behavior: "smooth",
                block: "start",
            })
        );
    }

    function cerrarDocumentos() {
        setEstudianteDocumentos(null);
    }

    function abrirBaja(estudiante) {
        setFormAbierto(false);
        setEditingId(null);
        setEstudianteDocumentos(null);
        setBajaEstudiante(estudiante);
        setMotivoBaja("");
        setBajaError("");

        // El formulario se renderiza al inicio de la página; llevarlo a la vista.
        requestAnimationFrame(() => {
            document.getElementById("form-baja-estudiante")?.scrollIntoView({
                behavior: "smooth",
                block: "start",
            });
            document.getElementById("txtMotivoBaja")?.focus({ preventScroll: true });
        });
    }

    function cerrarBaja() {
        if (bajaCargando) return;
        setBajaEstudiante(null);
        setMotivoBaja("");
        setBajaError("");
    }

    async function confirmarBaja() {
        const motivo = motivoBaja.trim();

        if (!motivo) {
            setBajaError("El motivo de baja es obligatorio.");
            return;
        }

        if (motivo.length > 500) {
            setBajaError("El motivo de baja no puede superar 500 caracteres.");
            return;
        }

        const confirmado = await dialog.confirmar(
            `¿Confirma la baja de ${bajaEstudiante.nombreCompleto}? Se cancelarán sus matrículas activas.`,
            {
                peligro: true,
                textoAceptar: "Dar de baja",
            }
        );

        if (!confirmado) return;

        setBajaCargando(true);
        setBajaError("");

        try {
            const response = await fetch(
                `${API_URL}/estudiantes/${bajaEstudiante.id}/baja`,
                {
                    method: "PATCH",
                    headers: {
                        ...getHeaders(),
                        "Content-Type": "application/json",
                    },
                    body: JSON.stringify({ motivoBaja: motivo }),
                }
            );

            const data = await response.json().catch(() => ({}));

            if (!response.ok) {
                throw new Error(data.message || "No se pudo dar de baja al estudiante.");
            }

            setBajaEstudiante(null);
            setMotivoBaja("");
            void recargarEstudiantes();

            await dialog.exito(
                `Baja realizada correctamente. Matrículas canceladas: ${data.matriculasCanceladas}. Cursos retirados: ${data.cursosRetirados}.`
            );
        } catch (e) {
            setBajaError(e.message);
        } finally {
            setBajaCargando(false);
        }
    }

    async function confirmarReactivacion(estudiante) {
        const confirmado = await dialog.confirmar(
            `¿Desea reactivar a ${estudiante.nombreCompleto}?`,
            {
                textoAceptar: "Reactivar estudiante",
            }
        );

        if (!confirmado) return;

        try {
            const response = await fetch(
                `${API_URL}/estudiantes/${estudiante.id}/reactivar`,
                {
                    method: "PATCH",
                    headers: getHeaders(),
                }
            );
            const data = await response.json().catch(() => ({}));

            if (!response.ok) {
                throw new Error(data.message || "No se pudo reactivar al estudiante.");
            }

            void recargarEstudiantes();
            await dialog.exito(
                data.mensaje ||
                "Estudiante reactivado. Las matrículas y cursos cancelados no se restauran automáticamente."
            );
        } catch (e) {
            dialog.error(e.message);
        }
    }

    function formularioGuardado() {
        setFormAbierto(false);
        setEditingId(null);
        dialog.exito(editingId
            ? "Información del estudiante actualizada correctamente."
            : "Estudiante registrado correctamente.");
        void recargarEstudiantes();
        window.scrollTo({ top: 0, behavior: "smooth" });
    }

    const hayFiltros = Boolean(busqueda.trim()) || filtroActivo !== "true";

    return (
        <>
            <PageHead
                title="Estudiantes"
                sub="Registre, consulte y administre la información académica y de contacto de los estudiantes."
                actions={(
                    <button type="button" className="sg-btn sg-btn--primary" onClick={abrirNuevo}>
                        <Plus size={18} strokeWidth={1.75} aria-hidden="true" />
                        Nuevo estudiante
                    </button>
                )}
            />

            {bajaEstudiante && (
                <section
                    id="form-baja-estudiante"
                    className="panel form-incrustado"
                    aria-labelledby="titulo-baja-estudiante"
                >
                    <div className="panel-head">
                        <div>
                            <h2 id="titulo-baja-estudiante">Dar de baja estudiante</h2>
                            <p className="panel-head-sub">
                                {bajaEstudiante.nombreCompleto} — {bajaEstudiante.numeroIdentificacion || "Sin identificación"}
                            </p>
                        </div>
                    </div>
                    <div className="panel-body">
                        <Field
                            id="txtMotivoBaja"
                            label="Motivo de baja"
                            hint="Se cancelarán sus matrículas activas y se retirarán sus cursos. Máximo 500 caracteres."
                            error={bajaError}
                        >
                            <textarea
                                id="txtMotivoBaja"
                                className="sg-input"
                                value={motivoBaja}
                                onChange={(e) => {
                                    setMotivoBaja(e.target.value);
                                    if (bajaError) setBajaError("");
                                }}
                                maxLength={500}
                                rows={4}
                                placeholder="Indique el motivo de la baja"
                                disabled={bajaCargando}
                                aria-invalid={bajaError ? true : undefined}
                                aria-describedby={`txtMotivoBaja-hint${bajaError ? " txtMotivoBaja-error" : ""}`}
                            />
                        </Field>
                        <p className="baja-contador" aria-live="polite">{motivoBaja.length}/500</p>
                    </div>
                    <div className="panel-foot">
                        <button
                            type="button"
                            className="sg-btn sg-btn--secondary"
                            onClick={cerrarBaja}
                            disabled={bajaCargando}
                        >
                            Cancelar
                        </button>
                        <button
                            type="button"
                            className={`sg-btn sg-btn--danger${bajaCargando ? " is-loading" : ""}`}
                            onClick={confirmarBaja}
                            disabled={bajaCargando}
                            aria-busy={bajaCargando || undefined}
                        >
                            {bajaCargando
                                ? <span className="sg-spinner" aria-hidden="true" />
                                : <UserX size={18} strokeWidth={1.75} aria-hidden="true" />}
                            {bajaCargando ? "Procesando…" : "Dar de baja"}
                        </button>
                    </div>
                </section>
            )}

            {formAbierto && (
                <section id="form-estudiante" className="form-incrustado">
                    {editingId ? (
                        <EditarEstudiante
                            studentId={editingId}
                            onCancel={cerrarFormulario}
                            onSuccess={formularioGuardado}
                        />
                    ) : (
                        <CreateStudent
                            onCancel={cerrarFormulario}
                            onSuccess={formularioGuardado}
                        />
                    )}
                </section>
            )}

            {estudianteDocumentos && (
                <div id="documentos-estudiante" className="form-incrustado">
                    <DocumentosEstudiante
                        estudiante={estudianteDocumentos}
                        onClose={cerrarDocumentos}
                    />
                </div>
            )}

            <div className="panel">
                <div className="filters" role="search">
                    <Field id="txtBuscarEstudiante" label="Buscar" icon={Search} className="f-search">
                        <input
                            id="txtBuscarEstudiante"
                            className="sg-input"
                            type="search"
                            value={busqueda}
                            onChange={(e) => cambiarBusqueda(e.target.value)}
                            placeholder="Nombre o número de identificación"
                        />
                    </Field>
                    <Field id="cmbEstadoEstudiante" label="Estado" select>
                        <select
                            id="cmbEstadoEstudiante"
                            className="sg-input sg-select"
                            value={filtroActivo}
                            onChange={(e) => cambiarEstado(e.target.value)}
                        >
                            <option value="true">Activos</option>
                            <option value="false">Inactivos</option>
                            <option value="">Todos</option>
                        </select>
                    </Field>
                </div>

                <div className="chips">
                    <span className="result-count" aria-live="polite">
                        <b>{estudiantesFiltrados.length}</b>
                        {estudiantesFiltrados.length === 1 ? " resultado" : " resultados"}
                    </span>
                    {busqueda.trim() && (
                        <span className="chip">
                            Búsqueda: {busqueda.trim()}
                            <button type="button" aria-label="Quitar filtro de búsqueda" onClick={() => cambiarBusqueda("")}>
                                <X size={14} strokeWidth={1.75} aria-hidden="true" />
                            </button>
                        </span>
                    )}
                    {filtroActivo !== "true" && (
                        <span className="chip">
                            Estado: {ETIQUETA_ESTADO[filtroActivo]}
                            <button type="button" aria-label="Quitar filtro de estado" onClick={() => cambiarEstado("true")}>
                                <X size={14} strokeWidth={1.75} aria-hidden="true" />
                            </button>
                        </span>
                    )}
                    {hayFiltros && (
                        <button type="button" className="link-btn chips-clear" onClick={quitarFiltros}>
                            Quitar filtros
                        </button>
                    )}
                </div>

                {cargando ? (
                    <div className="table-wrap" aria-busy="true">
                        <table className="dtable">
                            <caption className="sr-only">Cargando estudiantes...</caption>
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
                ) : estudiantesFiltrados.length === 0 ? (
                    <EmptyState
                        icon={Users}
                        title={busqueda.trim()
                            ? `Ningún estudiante coincide con «${busqueda.trim()}»`
                            : "No hay estudiantes con estos filtros"}
                        description="Revise la ortografía, busque por número de identificación o quite algún filtro."
                        action={hayFiltros && (
                            <button type="button" className="sg-btn sg-btn--secondary" onClick={quitarFiltros}>
                                Quitar filtros
                            </button>
                        )}
                    />
                ) : (
                    <div className="table-wrap">
                        <table className="dtable dtable--cards">
                            <caption className="sr-only">Lista de estudiantes, página {pagina}</caption>
                            <thead>
                                <tr>
                                    <th scope="col" aria-sort={orden === "asc" ? "ascending" : "descending"}>
                                        <button type="button" onClick={() => setOrden(orden === "asc" ? "desc" : "asc")}>
                                            Estudiante
                                            <ChevronsUpDown size={14} strokeWidth={1.75} aria-hidden="true" />
                                        </button>
                                    </th>
                                    <th scope="col" className="num">Edad</th>
                                    <th scope="col">Nivel escolar</th>
                                    <th scope="col">Instrumento</th>
                                    <th scope="col">Centro educativo</th>
                                    <th scope="col">Estado</th>
                                    <th scope="col" className="actions"><span className="sr-only">Acciones</span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {filasPagina.map((e) => (
                                    <tr key={e.id}>
                                        <td className="c-main">
                                            <div className="who">
                                                <span className="avatar avatar--sm" aria-hidden="true">{iniciales(e.nombreCompleto)}</span>
                                                <div>
                                                    <b>{e.nombreCompleto}</b>
                                                    <span>{e.numeroIdentificacion || "—"}</span>
                                                </div>
                                            </div>
                                        </td>
                                        <td className="num" data-label="Edad">{e.edad}</td>
                                        <td data-label="Nivel">{e.nivelEscolar || "—"}</td>
                                        <td data-label="Instrumento">{e.instrumento ? <span className="inst">{e.instrumento}</span> : "—"}</td>
                                        <td data-label="Centro">{e.centroEducativo || "—"}</td>
                                        <td>
                                            <StatusBadge tone={e.activo ? "success" : "neutral"}>
                                                {e.activo ? "Activo" : "Inactivo"}
                                            </StatusBadge>
                                        </td>
                                        <td className="actions">
                                            <div className="row-actions">
                                                <button
                                                    type="button"
                                                    className="sg-btn sg-btn--ghost sg-btn--sm"
                                                    onClick={() => abrirDocumentos(e)}
                                                    disabled={!e.activo}
                                                    title={e.activo
                                                        ? "Administrar documentos del estudiante"
                                                        : "Solo se pueden administrar documentos de estudiantes activos"}
                                                    aria-label={`Administrar documentos de ${e.nombreCompleto}`}
                                                >
                                                    <FileText size={16} strokeWidth={1.75} aria-hidden="true" />
                                                    Documentos
                                                </button>
                                                <button
                                                    type="button"
                                                    className="sg-btn sg-btn--ghost sg-btn--sm"
                                                    onClick={() => abrirEditar(e)}
                                                    aria-label={`Editar a ${e.nombreCompleto}`}
                                                >
                                                    <Pencil size={16} strokeWidth={1.75} aria-hidden="true" />
                                                    Editar
                                                </button>
                                                {e.activo ? (
                                                    <button
                                                        type="button"
                                                        className="sg-btn sg-btn--ghost sg-btn--sm btn-baja"
                                                        onClick={() => abrirBaja(e)}
                                                        aria-label={`Dar de baja a ${e.nombreCompleto}`}
                                                    >
                                                        <UserX size={16} strokeWidth={1.75} aria-hidden="true" />
                                                        Dar de baja
                                                    </button>
                                                ) : (
                                                    <button
                                                        type="button"
                                                        className="sg-btn sg-btn--ghost sg-btn--sm btn-reactivar"
                                                        onClick={() => confirmarReactivacion(e)}
                                                        aria-label={`Reactivar a ${e.nombreCompleto}`}
                                                    >
                                                        <UserCheck size={16} strokeWidth={1.75} aria-hidden="true" />
                                                        Reactivar
                                                    </button>
                                                )}
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {!cargando && estudiantesFiltrados.length > 0 && (
                    <div className="table-foot">
                        <Pagination
                            page={pagina}
                            pageSize={TAMANO_PAGINA}
                            total={estudiantesFiltrados.length}
                            onChange={setPagina}
                        />
                    </div>
                )}
            </div>
        </>
    );
}

export default Estudiantes;
