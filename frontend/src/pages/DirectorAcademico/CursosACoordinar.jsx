import { useEffect, useMemo, useState } from "react";
import { Handshake, Save } from "lucide-react";

import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import StatusBadge from "../../components/ui/StatusBadge";
import { useDialog } from "../../components/dialog/useDialog";
import { mensajeDeExcepcion } from "../../utils/api";
import { nombreProfesor, solicitar } from "./espaciosApi";
import { ETIQUETA_ESTADO_GRUPO, TONO_ESTADO_GRUPO } from "./gruposComun";
import { guardarCoordinacion, listarCursosACoordinar, listarProfesoresActivosGrupo } from "./cursosACoordinarApi";
import "./espacios.css";
import "./agrupaciones.css";
import "./cursosACoordinar.css";

const MAXIMO_NOTAS = 500;

// Por defecto, el período en matrícula; si no hay, el que está en curso.
function periodoPorDefecto(periodos) {
    return periodos.find((p) => p.estado === "MATRICULA")
        ?? periodos.find((p) => p.estado === "EN_CURSO")
        ?? periodos[0]
        ?? null;
}

// Borrador editable de una fila a partir de lo guardado.
function borradorDesde(estudiante) {
    return {
        idProfesorAsignado: estudiante.idProfesorAsignado ? String(estudiante.idProfesorAsignado) : "",
        notasCoordinacion: estudiante.notasCoordinacion ?? "",
    };
}

/*
 * Cursos a coordinar en Dirección (Música de Cámara I a VI).
 *
 * Sus grupos se publican sin horario ni profesor. Aquí la Dirección
 * Académica ve quién los matriculó y, estudiante por estudiante, le asigna
 * (o no) un profesor y anota cómo lo cumplirá.
 */
export default function CursosACoordinar() {
    const dialog = useDialog();

    const [periodos, setPeriodos] = useState([]);
    const [profesores, setProfesores] = useState([]);
    const [idPeriodo, setIdPeriodo] = useState("");

    const [grupos, setGrupos] = useState({ clave: null, lista: [] });
    // idDetalle -> { idProfesorAsignado, notasCoordinacion } (lo que se está editando)
    const [borradores, setBorradores] = useState({});
    const [guardando, setGuardando] = useState(null);
    const [guardados, setGuardados] = useState({});

    // Catálogos: períodos y profesores activos.
    useEffect(() => {
        let activo = true;
        Promise.all([
            solicitar("/periodos-academicos", {}, "No fue posible cargar los períodos."),
            listarProfesoresActivosGrupo(),
        ])
            .then(([listaPeriodos, listaProfesores]) => {
                if (!activo) return;
                const lista = listaPeriodos ?? [];
                setPeriodos(lista);
                setProfesores((listaProfesores ?? []).filter((p) => p.activo !== false));
                const porDefecto = periodoPorDefecto(lista);
                if (porDefecto) setIdPeriodo((actual) => actual || String(porDefecto.id));
            })
            .catch((e) => dialog.error(mensajeDeExcepcion(e, "No fue posible cargar la información necesaria.")));
        return () => { activo = false; };
    }, [dialog]);

    // Grupos del período elegido.
    useEffect(() => {
        if (!idPeriodo) return undefined;
        let activo = true;
        listarCursosACoordinar(idPeriodo)
            .then((lista) => {
                if (!activo) return;
                setGrupos({ clave: idPeriodo, lista: lista ?? [] });
                setBorradores({});
                setGuardados({});
            })
            .catch((e) => {
                if (!activo) return;
                setGrupos({ clave: idPeriodo, lista: [] });
                dialog.error(mensajeDeExcepcion(e, "No fue posible cargar los cursos a coordinar."));
            });
        return () => { activo = false; };
    }, [idPeriodo, dialog]);

    const cargando = Boolean(idPeriodo) && grupos.clave !== idPeriodo;
    const listaGrupos = useMemo(() => (cargando ? [] : grupos.lista), [cargando, grupos]);
    const hayEstudiantes = listaGrupos.some((g) => g.estudiantes.length > 0);

    function valorFila(estudiante) {
        return borradores[estudiante.idDetalle] ?? borradorDesde(estudiante);
    }

    function cambiarFila(estudiante, campo, valor) {
        setBorradores((prev) => ({
            ...prev,
            [estudiante.idDetalle]: { ...valorFila(estudiante), [campo]: valor },
        }));
        setGuardados((prev) => ({ ...prev, [estudiante.idDetalle]: false }));
    }

    function filaModificada(estudiante) {
        const borrador = borradores[estudiante.idDetalle];
        if (!borrador) return false;
        const original = borradorDesde(estudiante);
        return borrador.idProfesorAsignado !== original.idProfesorAsignado
            || borrador.notasCoordinacion.trim() !== original.notasCoordinacion.trim();
    }

    async function guardarFila(grupo, estudiante) {
        const fila = valorFila(estudiante);
        if (fila.notasCoordinacion.length > MAXIMO_NOTAS) {
            dialog.error(`La nota no puede superar los ${MAXIMO_NOTAS} caracteres.`);
            return;
        }

        setGuardando(estudiante.idDetalle);
        try {
            const actualizado = await guardarCoordinacion(estudiante.idDetalle, {
                idProfesorAsignado: fila.idProfesorAsignado ? Number(fila.idProfesorAsignado) : null,
                notasCoordinacion: fila.notasCoordinacion.trim() || null,
            });
            setGrupos((prev) => ({
                ...prev,
                lista: prev.lista.map((g) => (g.idGrupo !== grupo.idGrupo ? g : {
                    ...g,
                    estudiantes: g.estudiantes.map((e) => (e.idDetalle === actualizado.idDetalle ? actualizado : e)),
                })),
            }));
            setBorradores((prev) => {
                const siguiente = { ...prev };
                delete siguiente[estudiante.idDetalle];
                return siguiente;
            });
            setGuardados((prev) => ({ ...prev, [estudiante.idDetalle]: true }));
        } catch (e) {
            dialog.error(mensajeDeExcepcion(e, "No fue posible guardar la coordinación."));
        } finally {
            setGuardando(null);
        }
    }

    return (
        <div className="espacios">
            <PageHead
                title="Cursos a coordinar"
                sub="Cursos como Música de Cámara, que se publican sin horario ni profesor. Asigne un profesor a cada estudiante y anote cómo cumplirá el curso."
            />

            <section className="panel" aria-labelledby="t-coordinar" aria-busy={cargando || undefined}>
                <div className="panel-head agrupaciones-cabecera">
                    <h2 id="t-coordinar">Estudiantes matriculados</h2>
                    <Field id="f-periodo-coordinar" label="Período" select className="agrupaciones-filtro">
                        <select
                            id="f-periodo-coordinar"
                            className="sg-input sg-select"
                            value={idPeriodo}
                            onChange={(e) => setIdPeriodo(e.target.value)}
                        >
                            <option value="">Seleccione un período</option>
                            {periodos.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
                        </select>
                    </Field>
                </div>

                <div className="panel-body">
                    {cargando ? (
                        <p className="muted" role="status">Cargando…</p>
                    ) : !hayEstudiantes ? (
                        <EmptyState
                            icon={Handshake}
                            title="Sin estudiantes por coordinar"
                            description="Todavía nadie ha matriculado cursos a coordinar en este período."
                        />
                    ) : listaGrupos.map((g) => (
                        <section key={g.idGrupo} className="coordinar-grupo" aria-labelledby={`t-grupo-${g.idGrupo}`}>
                            <h3 id={`t-grupo-${g.idGrupo}`} className="form-section-title">
                                {g.nombreCurso} · {g.codigo} · {g.matriculados} de {g.cupoMaximo}{" "}
                                <StatusBadge tone={TONO_ESTADO_GRUPO[g.estado] ?? "neutral"}>
                                    {ETIQUETA_ESTADO_GRUPO[g.estado] ?? g.estado}
                                </StatusBadge>
                            </h3>

                            {g.estudiantes.length === 0 ? (
                                <p className="muted">Todavía nadie ha matriculado este grupo.</p>
                            ) : (
                                <div className="table-wrap">
                                    <table className="dtable dtable--cards coordinar-tabla">
                                        <caption className="sr-only">Estudiantes de {g.nombreCurso} ({g.codigo})</caption>
                                        <thead>
                                            <tr>
                                                <th scope="col">Estudiante</th>
                                                <th scope="col">Cédula</th>
                                                <th scope="col">Instrumento</th>
                                                <th scope="col">Profesor</th>
                                                <th scope="col">Cómo lo cumplirá</th>
                                                <th scope="col"><span className="sr-only">Acciones</span></th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {g.estudiantes.map((e) => {
                                                const fila = valorFila(e);
                                                const idProfesor = `prof-${e.idDetalle}`;
                                                const idNotas = `notas-${e.idDetalle}`;
                                                const ocupado = guardando === e.idDetalle;
                                                // Un profesor asignado que ya no está activo se sigue mostrando.
                                                const asignadoInactivo = e.idProfesorAsignado
                                                    && !profesores.some((p) => p.id === e.idProfesorAsignado);
                                                return (
                                                    <tr key={e.idDetalle}>
                                                        <td className="c-main"><b>{e.nombreCompleto}</b></td>
                                                        <td className="tnum" data-label="Cédula">{e.cedula}</td>
                                                        <td data-label="Instrumento">{e.instrumento || <span className="muted">—</span>}</td>
                                                        <td className="coordinar-profesor" data-label="Profesor">
                                                            <label className="sr-only" htmlFor={idProfesor}>Profesor de {e.nombreCompleto}</label>
                                                            <select
                                                                id={idProfesor}
                                                                className="sg-input sg-select"
                                                                value={fila.idProfesorAsignado}
                                                                onChange={(ev) => cambiarFila(e, "idProfesorAsignado", ev.target.value)}
                                                                disabled={ocupado}
                                                            >
                                                                <option value="">Sin asignar</option>
                                                                {asignadoInactivo && (
                                                                    <option value={e.idProfesorAsignado}>{e.nombreProfesorAsignado} (inactivo)</option>
                                                                )}
                                                                {profesores.map((p) => (
                                                                    <option key={p.id} value={p.id}>{nombreProfesor(p)}</option>
                                                                ))}
                                                            </select>
                                                        </td>
                                                        <td className="coordinar-notas" data-label="Cómo lo cumplirá">
                                                            <label className="sr-only" htmlFor={idNotas}>Cómo cumplirá {e.nombreCompleto} el curso</label>
                                                            <textarea
                                                                id={idNotas}
                                                                className="sg-input"
                                                                maxLength={MAXIMO_NOTAS}
                                                                value={fila.notasCoordinacion}
                                                                onChange={(ev) => cambiarFila(e, "notasCoordinacion", ev.target.value)}
                                                                placeholder="Ej.: En Orquesta · Acompaña a violín · Cuarteto con compañeros"
                                                                disabled={ocupado}
                                                            />
                                                            <span className="coordinar-contador tnum" aria-hidden="true">
                                                                {fila.notasCoordinacion.length}/{MAXIMO_NOTAS}
                                                            </span>
                                                        </td>
                                                        <td className="coordinar-acciones">
                                                            <button
                                                                type="button"
                                                                className={`sg-btn sg-btn--primary sg-btn--sm${ocupado ? " is-loading" : ""}`}
                                                                onClick={() => guardarFila(g, e)}
                                                                disabled={guardando !== null || !filaModificada(e)}
                                                                aria-label={`Guardar la coordinación de ${e.nombreCompleto}`}
                                                            >
                                                                {ocupado
                                                                    ? <span className="sg-spinner" aria-hidden="true" />
                                                                    : <Save size={16} strokeWidth={1.75} aria-hidden="true" />}
                                                                {ocupado ? "Guardando…" : "Guardar"}
                                                            </button>
                                                            {guardados[e.idDetalle] && (
                                                                <span className="coordinar-guardado" role="status">Se guardó.</span>
                                                            )}
                                                        </td>
                                                    </tr>
                                                );
                                            })}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </section>
                    ))}
                </div>
            </section>
        </div>
    );
}
