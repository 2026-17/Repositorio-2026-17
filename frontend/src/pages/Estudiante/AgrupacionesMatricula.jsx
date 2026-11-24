import { CircleCheck, CircleX, Music, TriangleAlert, Users } from "lucide-react";

import Alert from "../../components/ui/Alert";
import EmptyState from "../../components/ui/EmptyState";
import StatusBadge from "../../components/ui/StatusBadge";
import { formatearHorarios } from "../../utils/matricula";

/*
 * Matrícula - Paso "Agrupaciones" (mockup 8 · Estudiante: elegir agrupaciones).
 *
 * El estudiante elige una o varias agrupaciones (son opcionales). Solo ve el
 * nombre: el nivel lo calcula el sistema y es interno.
 *
 * Props:
 * - datos: respuesta de /matriculas-estudiante/agrupaciones.
 * - elegidas: ids de grupo marcados, en el orden en que se marcaron.
 * - motivos: { [idGrupo]: texto } por qué una agrupación no se puede marcar
 *   ahora (choque con algo ya elegido).
 * - errores: { [idGrupo]: [textos] } problemas de una agrupación marcada.
 * - onToggle(agrupacion).
 */
const SECCIONES = [
    { tipo: "INSTRUMENTAL", titulo: "Agrupaciones instrumentales", sub: "Orquestas, bandas y ensambles" },
    { tipo: "CORAL", titulo: "Agrupaciones corales", sub: "Coros y talleres vocales" },
];

function textoCupos(a) {
    if (a.cupoDisponible <= 0) return <StatusBadge tone="neutral">Sin cupos</StatusBadge>;
    if (a.cupoDisponible <= 3) {
        return <StatusBadge tone="warning">{a.cupoDisponible === 1 ? "Queda 1 cupo" : `Quedan ${a.cupoDisponible} cupos`}</StatusBadge>;
    }
    return <StatusBadge tone="success" icon={null}>{a.cupoDisponible} de {a.cupoMaximo} cupos</StatusBadge>;
}

export default function AgrupacionesMatricula({ datos, elegidas, motivos, errores, onToggle, numero = 3 }) {
    const agrupaciones = datos?.agrupaciones ?? [];

    return (
        <section className="panel bloque" aria-labelledby="t-agrupaciones">
            <div className="panel-head">
                <div>
                    <h2 id="t-agrupaciones">{numero}. Agrupaciones</h2>
                    <p className="panel-head-sub">Son opcionales: puede continuar sin elegir ninguna.</p>
                </div>
            </div>

            <div className="panel-body agrupaciones-matricula">
                {datos && !datos.habilitado && datos.motivoNoHabilitado && (
                    <Alert tone="info" title="No puede elegir agrupaciones">{datos.motivoNoHabilitado}</Alert>
                )}

                {datos?.habilitado && agrupaciones.length === 0 && (
                    <EmptyState
                        icon={Users}
                        title="Sin agrupaciones publicadas"
                        description="Este período todavía no tiene orquestas, bandas ni coros abiertos para matrícula."
                        compact
                    />
                )}

                {agrupaciones.length > 0 && (
                    <>
                        <Alert tone="info" title="Puede elegir varias.">
                            En cada una el profesor le pone una nota aparte.
                        </Alert>

                        {SECCIONES.map((seccion) => {
                            const lista = agrupaciones.filter((a) => a.tipo === seccion.tipo);
                            if (lista.length === 0) return null;
                            const idLeyenda = `leyenda-${seccion.tipo.toLowerCase()}`;

                            return (
                                <fieldset className="fieldset agrupaciones-seccion" key={seccion.tipo} aria-describedby={`${idLeyenda}-sub`}>
                                    <legend className="leccion-legend" id={idLeyenda}>
                                        <Music size={18} strokeWidth={1.75} aria-hidden="true" />
                                        {seccion.titulo}
                                    </legend>
                                    <p className="muted agrupaciones-seccion__sub" id={`${idLeyenda}-sub`}>{seccion.sub}</p>

                                    <div className="choice-list espacios-grid">
                                        {lista.map((a) => {
                                            const marcada = a.matriculada || elegidas.includes(a.idGrupo);
                                            const motivo = a.matriculada ? null : (a.motivoBloqueo || motivos[a.idGrupo]);
                                            const deshabilitada = a.matriculada || (Boolean(motivo) && !marcada);
                                            const listaErrores = marcada && !a.matriculada ? (errores[a.idGrupo] ?? []) : [];

                                            return (
                                                <label
                                                    key={a.idGrupo}
                                                    className={`choice opcion-espacio opcion-agrupacion${marcada ? " is-checked" : ""}${deshabilitada ? " is-disabled" : ""}${listaErrores.length > 0 ? " has-error" : ""}`}
                                                >
                                                    <input
                                                        type="checkbox"
                                                        checked={marcada}
                                                        disabled={deshabilitada}
                                                        onChange={() => onToggle(a)}
                                                       
                                                    />
                                                    <span className="choice-main">
                                                        <b>{a.nombre}</b>
                                                        <span className="tnum">{formatearHorarios(a.horarios)}</span>
                                                        <span>
                                                            {a.profesores?.length > 0
                                                                ? `${a.profesores.length === 1 ? "Profesor" : "Profesores"}: ${a.profesores.join(", ")}`
                                                                : "Profesor por definir"}
                                                        </span>
                                                        {a.aula && <span>{a.aula}</span>}
                                                        <span className="agrupacion-cupos">{textoCupos(a)}</span>

                                                        {motivo && !marcada && (
                                                            <span className="espacio-motivo">
                                                                <TriangleAlert size={16} strokeWidth={1.75} aria-hidden="true" />
                                                                {motivo}
                                                            </span>
                                                        )}
                                                        {listaErrores.map((msg) => (
                                                            <span className="sg-field__error" key={msg}>
                                                                <CircleX size={16} strokeWidth={1.75} aria-hidden="true" />
                                                                {msg}
                                                            </span>
                                                        ))}
                                                        {a.matriculada && (
                                                            <span className="espacio-elegido">
                                                                <CircleCheck size={16} strokeWidth={1.75} aria-hidden="true" />
                                                                Ya matriculada en este período
                                                            </span>
                                                        )}
                                                        {marcada && !a.matriculada && (
                                                            <span className="espacio-elegido">
                                                                <CircleCheck size={16} strokeWidth={1.75} aria-hidden="true" />
                                                                Elegida
                                                            </span>
                                                        )}
                                                    </span>
                                                </label>
                                            );
                                        })}
                                    </div>
                                </fieldset>
                            );
                        })}
                    </>
                )}
            </div>
        </section>
    );
}
