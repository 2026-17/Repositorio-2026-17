import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { CalendarDays, ClipboardList, Clock, MapPin, User } from "lucide-react";

import Alert from "../../components/ui/Alert";
import EmptyState from "../../components/ui/EmptyState";
import Field from "../../components/ui/Field";
import PageHead from "../../components/ui/PageHead";
import StatusBadge from "../../components/ui/StatusBadge";
import { formatearHorarios } from "../../utils/matricula";
import { consultarMiHorario } from "../../utils/matriculaApi";
import "./miHorario.css";

// Horario semanal del estudiante: los cursos que tiene matriculados en un
// período, dibujados en una cuadrícula de lunes a viernes (sábado y
// domingo solo aparecen si hay clases esos días).

const DIAS = [
    { numero: 1, nombre: "Lunes", corto: "Lun" },
    { numero: 2, nombre: "Martes", corto: "Mar" },
    { numero: 3, nombre: "Miércoles", corto: "Mié" },
    { numero: 4, nombre: "Jueves", corto: "Jue" },
    { numero: 5, nombre: "Viernes", corto: "Vie" },
    { numero: 6, nombre: "Sábado", corto: "Sáb" },
    { numero: 7, nombre: "Domingo", corto: "Dom" },
];

const ESTADO_PERIODO = {
    PLANIFICADO: { texto: "Planificado", tono: "neutral" },
    MATRICULA: { texto: "En matrícula", tono: "info" },
    EN_CURSO: { texto: "En curso", tono: "success" },
    CERRADO: { texto: "Cerrado", tono: "neutral" },
    CANCELADO: { texto: "Cancelado", tono: "danger" },
};

// La cuadrícula se divide en filas de 15 minutos.
const MINUTOS_FILA = 15;
const TONOS_CURSO = 6;

function aMinutos(hora) {
    const [h, m] = String(hora).split(":").map(Number);
    return h * 60 + (m || 0);
}

function textoHora(minutos) {
    const h = Math.floor(minutos / 60);
    const m = minutos % 60;
    return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

function textoFecha(fecha) {
    if (!fecha) return "";
    return new Date(`${fecha}T00:00:00`).toLocaleDateString("es-CR", {
        day: "numeric",
        month: "short",
        year: "numeric",
    });
}

// getDay() cuenta desde el domingo (0); el servidor usa 1 = lunes ... 7 = domingo.
function diaDeHoy() {
    const dia = new Date().getDay();
    return dia === 0 ? 7 : dia;
}

function MiHorario() {
    const token = localStorage.getItem("token");

    const [idPeriodo, setIdPeriodo] = useState("");
    const [datos, setDatos] = useState(null);
    const [cargando, setCargando] = useState(true);
    const [error, setError] = useState("");

    // El estado se actualiza cuando la petición termina (no de forma
    // síncrona dentro del efecto).
    useEffect(() => {
        let cancelado = false;

        consultarMiHorario(token, idPeriodo)
            .then((respuesta) => {
                if (!cancelado) {
                    setDatos(respuesta);
                    setError("");
                }
            })
            .catch((e) => {
                if (!cancelado) setError(e.message || "No fue posible consultar su horario.");
            })
            .finally(() => {
                if (!cancelado) setCargando(false);
            });

        return () => {
            cancelado = true;
        };
    }, [token, idPeriodo]);

    function cambiarPeriodo(valor) {
        if (valor === String(datos?.periodo?.id ?? "")) return;
        setCargando(true);
        setIdPeriodo(valor);
    }

    const clases = useMemo(() => datos?.clases ?? [], [datos]);

    // Cada curso conserva su color en la cuadrícula, la agenda y la tabla.
    const tonoPorGrupo = useMemo(() => {
        const mapa = {};
        clases.forEach((clase, i) => {
            mapa[clase.idGrupo] = i % TONOS_CURSO;
        });
        return mapa;
    }, [clases]);

    const bloques = useMemo(() => clases.flatMap((clase) =>
        (clase.horarios ?? []).map((h, i) => ({
            clave: `${clase.idGrupo}-${i}`,
            clase,
            dia: h.diaSemana,
            inicio: aMinutos(h.horaInicio),
            fin: aMinutos(h.horaFin),
            aula: h.aula || clase.aula,
        }))
    ), [clases]);

    const sinHorario = clases.filter((clase) => !clase.horarios || clase.horarios.length === 0);

    const dias = DIAS.filter((d) => d.numero <= 5 || bloques.some((b) => b.dia === d.numero));

    // Rango visible: desde la hora en punto antes de la primera clase
    // hasta la hora en punto después de la última.
    const inicioDia = bloques.length ? Math.floor(Math.min(...bloques.map((b) => b.inicio)) / 60) * 60 : 0;
    const finDia = bloques.length ? Math.ceil(Math.max(...bloques.map((b) => b.fin)) / 60) * 60 : 0;
    const horas = [];
    for (let m = inicioDia; m < finDia; m += 60) horas.push(m);

    const minutosSemana = bloques.reduce((total, b) => total + (b.fin - b.inicio), 0);
    const hoy = diaDeHoy();

    const periodo = datos?.periodo;
    const periodos = datos?.periodos ?? [];
    const estado = ESTADO_PERIODO[periodo?.estado];

    function filaDe(minutos, redondeo) {
        return 2 + redondeo((minutos - inicioDia) / MINUTOS_FILA);
    }

    const selectorPeriodo = periodos.length > 1 && (
        <Field id="cmbPeriodoHorario" label="Período" select className="mi-horario__periodo">
            <select
                id="cmbPeriodoHorario"
                className="sg-input sg-select"
                value={String(periodo?.id ?? "")}
                onChange={(e) => cambiarPeriodo(e.target.value)}
                disabled={cargando}
            >
                {periodos.map((p) => (
                    <option key={p.id} value={p.id}>
                        {p.nombre} ({p.codigo})
                    </option>
                ))}
            </select>
        </Field>
    );

    function contenido() {
        if (cargando) {
            return (
                <div className="panel" aria-busy="true">
                    <div className="panel-body">
                        <span className="skeleton" />
                        <div className="mi-horario__skeleton">
                            {[0, 1, 2, 3, 4].map((i) => <span key={i} className="skeleton" />)}
                        </div>
                    </div>
                </div>
            );
        }

        if (error) {
            return <Alert tone="danger" title="No se pudo cargar su horario">{error}</Alert>;
        }

        if (!periodo) {
            return (
                <div className="panel">
                    <EmptyState
                        icon={CalendarDays}
                        title="Aún no tiene cursos matriculados"
                        description="Cuando matricule cursos en un período, aquí verá sus clases de la semana con su horario, aula y profesor."
                        action={(
                            <Link className="sg-btn sg-btn--primary" to="/estudiante/matricula">
                                <ClipboardList size={18} strokeWidth={1.75} aria-hidden="true" />
                                Ir a matrícula
                            </Link>
                        )}
                    />
                </div>
            );
        }

        return (
            <>
                {sinHorario.length > 0 && (
                    <Alert tone="info" title="Cursos sin horario definido" className="mi-horario__aviso">
                        {sinHorario.map((c) => c.nombreCurso).join(", ")}
                        {sinHorario.length === 1 ? " todavía no tiene" : " todavía no tienen"} días y horas
                        asignados. Aparecerán en la cuadrícula cuando la dirección académica los defina.
                    </Alert>
                )}

                {bloques.length > 0 && (
                    <section className="panel" aria-labelledby="titulo-semana">
                        <div className="panel-head">
                            <div>
                                <h2 id="titulo-semana">Semana</h2>
                                <p className="panel-head-sub">
                                    {clases.length} {clases.length === 1 ? "curso" : "cursos"}
                                    {" · "}
                                    {Math.round((minutosSemana / 60) * 10) / 10} h de clase por semana
                                </p>
                            </div>
                        </div>

                        {/* Escritorio: cuadrícula. Es solo visual; la misma
                            información está en la agenda y en la tabla. */}
                        <div className="mi-horario__scroll" aria-hidden="true">
                            <div
                                className="mi-horario__grid"
                                style={{
                                    gridTemplateColumns: `56px repeat(${dias.length}, minmax(120px, 1fr))`,
                                    gridTemplateRows: `auto repeat(${(finDia - inicioDia) / MINUTOS_FILA}, var(--hor-fila))`,
                                }}
                            >
                                <div className="mi-horario__esquina" style={{ gridColumn: 1, gridRow: 1 }} />
                                {dias.map((d, i) => (
                                    <div
                                        key={d.numero}
                                        className={`mi-horario__dia${d.numero === hoy ? " is-hoy" : ""}`}
                                        style={{ gridColumn: i + 2, gridRow: 1 }}
                                    >
                                        {d.nombre}
                                        {d.numero === hoy && <span className="mi-horario__hoy">Hoy</span>}
                                    </div>
                                ))}

                                {horas.map((m) => (
                                    <div
                                        key={`h-${m}`}
                                        className="mi-horario__hora"
                                        style={{ gridColumn: 1, gridRow: `${filaDe(m, Math.floor)} / span ${60 / MINUTOS_FILA}` }}
                                    >
                                        {textoHora(m)}
                                    </div>
                                ))}

                                {dias.map((d, i) => horas.map((m) => (
                                    <div
                                        key={`c-${d.numero}-${m}`}
                                        className={`mi-horario__celda${d.numero === hoy ? " is-hoy" : ""}`}
                                        style={{ gridColumn: i + 2, gridRow: `${filaDe(m, Math.floor)} / span ${60 / MINUTOS_FILA}` }}
                                    />
                                )))}

                                {bloques.map((b) => {
                                    const columna = dias.findIndex((d) => d.numero === b.dia) + 2;
                                    return (
                                        <div
                                            key={b.clave}
                                            className={`mi-horario__bloque tono-${tonoPorGrupo[b.clase.idGrupo]}`}
                                            style={{
                                                gridColumn: columna,
                                                gridRow: `${filaDe(b.inicio, Math.floor)} / ${filaDe(b.fin, Math.ceil)}`,
                                            }}
                                            title={`${b.clase.nombreCurso} · ${textoHora(b.inicio)}-${textoHora(b.fin)}`}
                                        >
                                            <b>{b.clase.nombreCurso}</b>
                                            <span>{textoHora(b.inicio)}–{textoHora(b.fin)}</span>
                                            {b.aula && <span>{b.aula}</span>}
                                        </div>
                                    );
                                })}
                            </div>
                        </div>

                        {/* Celular: agenda por día */}
                        <div className="mi-horario__agenda">
                            {dias.map((d) => {
                                const delDia = bloques
                                    .filter((b) => b.dia === d.numero)
                                    .sort((a, b) => a.inicio - b.inicio);
                                if (delDia.length === 0) return null;

                                return (
                                    <div key={d.numero} className="mi-horario__agenda-dia">
                                        <h3>
                                            {d.nombre}
                                            {d.numero === hoy && <StatusBadge tone="primary" icon={null}>Hoy</StatusBadge>}
                                        </h3>
                                        <ul>
                                            {delDia.map((b) => (
                                                <li key={b.clave} className={`tono-${tonoPorGrupo[b.clase.idGrupo]}`}>
                                                    <b>{b.clase.nombreCurso}</b>
                                                    <span>
                                                        <Clock size={14} strokeWidth={1.75} aria-hidden="true" />
                                                        {textoHora(b.inicio)}–{textoHora(b.fin)}
                                                    </span>
                                                    {b.aula && (
                                                        <span>
                                                            <MapPin size={14} strokeWidth={1.75} aria-hidden="true" />
                                                            {b.aula}
                                                        </span>
                                                    )}
                                                    <span>
                                                        <User size={14} strokeWidth={1.75} aria-hidden="true" />
                                                        {b.clase.profesor ?? "Profesor por definir"}
                                                    </span>
                                                </li>
                                            ))}
                                        </ul>
                                    </div>
                                );
                            })}
                        </div>
                    </section>
                )}

                <section className="panel" aria-labelledby="titulo-cursos">
                    <div className="panel-head">
                        <h2 id="titulo-cursos">Mis cursos</h2>
                    </div>
                    <div className="table-wrap">
                        <table className="dtable dtable--cards">
                            <caption className="sr-only">Cursos matriculados en {periodo.nombre}</caption>
                            <thead>
                                <tr>
                                    <th scope="col">Curso</th>
                                    <th scope="col">Grupo</th>
                                    <th scope="col">Horario</th>
                                    <th scope="col">Aula</th>
                                    <th scope="col">Profesor</th>
                                    <th scope="col">Modalidad</th>
                                </tr>
                            </thead>
                            <tbody>
                                {clases.map((clase) => (
                                    <tr key={clase.idGrupo}>
                                        <td className="c-main">
                                            <div className="who">
                                                <span
                                                    className={`mi-horario__muestra tono-${tonoPorGrupo[clase.idGrupo]}`}
                                                    aria-hidden="true"
                                                />
                                                <div>
                                                    <b>{clase.nombreCurso}</b>
                                                    <span>{clase.codigoCurso}</span>
                                                </div>
                                            </div>
                                        </td>
                                        <td data-label="Grupo">{clase.codigo}</td>
                                        <td data-label="Horario">{formatearHorarios(clase.horarios)}</td>
                                        <td data-label="Aula">{clase.aula || "—"}</td>
                                        <td data-label="Profesor">{clase.profesor ?? "Por definir"}</td>
                                        <td data-label="Modalidad">{clase.modalidad ? clase.modalidad.charAt(0) + clase.modalidad.slice(1).toLowerCase() : "—"}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </section>
            </>
        );
    }

    return (
        <>
            <PageHead
                title="Mi horario"
                sub={periodo
                    ? (
                        <>
                            {periodo.nombre}
                            {periodo.fechaInicio && ` · del ${textoFecha(periodo.fechaInicio)} al ${textoFecha(periodo.fechaFin)}`}
                            {estado && <> {" "}<StatusBadge tone={estado.tono}>{estado.texto}</StatusBadge></>}
                        </>
                    )
                    : "Consulte sus clases de la semana: día, hora, aula y profesor de cada curso matriculado."}
                actions={selectorPeriodo || null}
            />

            {contenido()}
        </>
    );
}

export default MiHorario;
