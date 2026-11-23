import { ChevronDown, ChevronUp, Plus, Trash2 } from "lucide-react";

import Alert from "../../components/ui/Alert";
import CedulaInput from "../../components/ui/CedulaInput";
import Field from "../../components/ui/Field";
import StatusBadge from "../../components/ui/StatusBadge";
import { ajustarIdentificacionAlTipo, esCedulaNacional, mostrarIdentificacion } from "../../utils/cedula";
import { MAX_CONTACTOS, PARENTESCOS, parentescoPorConfirmar } from "./edicionEstudiante";

// Tarjeta de un encargado: resumen siempre visible y formulario desplegable.
export default function EncargadoEdicion({
    encargado, tipos, errores, abierto, contactosDisponibles,
    onAlternar, onCampo, onPrincipal, onAgregarContacto, onCampoContacto, onQuitarContacto, onQuitar,
}) {
    const e = encargado;
    const k = campo => `enc-${e.clave}-${campo}`;
    const id = campo => `edit-${k(campo)}`;
    const tipo = tipos.find(t => String(t.id) === String(e.idTipoIdentificacion));
    const nombre = [e.nombres, e.primerApellido, e.segundoApellido].filter(Boolean).join(" ") || "Encargado nuevo";
    const etiqueta = PARENTESCOS.find(p => p.valor === e.parentesco)?.etiqueta ?? e.parentesco;
    const resumen = [mostrarIdentificacion(e.numeroIdentificacion, tipo), e.telefonoPrincipal, e.correo].filter(Boolean).join(" · ");
    const otros = e.otrosEstudiantes.map(o => o.nombreCompleto);
    const aria = campo => ({
        "aria-invalid": errores[k(campo)] ? "true" : undefined,
        "aria-describedby": errores[k(campo)] ? `${id(campo)}-error` : undefined,
    });

    const texto = (campo, label, props = {}) => {
        const { optional, maxLength = 100, ...resto } = props;
        return (
            <Field id={id(campo)} errorKey={k(campo)} label={label} optional={optional} error={errores[k(campo)]}>
                <input id={id(campo)} className="sg-input" maxLength={maxLength} value={e[campo]} onChange={ev => onCampo(campo, ev.target.value)} {...aria(campo)} {...resto} />
            </Field>
        );
    };

    return (
        <div className="subpanel">
            <div className="subpanel-head">
                <div className="edicion-encargado-resumen">
                    <span>{nombre}</span>
                    <span className="edicion-chips">
                        <StatusBadge tone="neutral" icon={null}>{etiqueta}</StatusBadge>
                        {parentescoPorConfirmar(e) && <StatusBadge tone="warning">Parentesco por confirmar</StatusBadge>}
                        {e.esResponsablePrincipal && <StatusBadge tone="primary" icon={null}>Responsable principal</StatusBadge>}
                    </span>
                    {resumen && <span className="muted edicion-linea">{resumen}</span>}
                </div>
                <button type="button" className="sg-btn sg-btn--ghost" aria-expanded={abierto} aria-controls={`panel-${e.clave}`} onClick={onAlternar}>
                    {abierto ? <ChevronUp size={18} strokeWidth={1.75} aria-hidden="true" /> : <ChevronDown size={18} strokeWidth={1.75} aria-hidden="true" />}
                    {abierto ? "Ocultar" : "Editar"}<span className="sr-only"> {nombre}</span>
                </button>
            </div>

            {abierto && (
                <div className="subpanel-body" id={`panel-${e.clave}`}>
                    {otros.length > 0 && (
                        <Alert tone="info" className="form-alert">
                            También es encargado(a) de {otros.join(", ")}. Sus datos personales se cambian para {otros.length === 1 ? "los dos" : "todos"}; el parentesco y los permisos son solo para este estudiante.
                        </Alert>
                    )}
                    {!e.idEncargado && (
                        <p className="sg-field__hint">Si esta identificación ya está registrada, se usa esa persona con los datos que ya tiene.</p>
                    )}
                    <div className="form-grid">
                        <Field id={id("parentesco")} errorKey={k("parentesco")} label="Parentesco" error={errores[k("parentesco")]} select>
                            <select id={id("parentesco")} className="sg-input sg-select" value={e.parentesco} onChange={ev => onCampo("parentesco", ev.target.value)} {...aria("parentesco")}>
                                {PARENTESCOS.map(p => <option key={p.valor} value={p.valor}>{p.etiqueta}</option>)}
                            </select>
                        </Field>
                        <div className="edicion-permisos">
                            <label className="check-line">
                                <input type="checkbox" checked={e.esResponsablePrincipal} onChange={ev => onPrincipal(ev.target.checked)} />
                                <span>Responsable principal <small className="muted">Solo uno; al marcarlo, el otro deja de serlo.</small></span>
                            </label>
                            <label className="check-line">
                                <input type="checkbox" checked={e.autorizadoRetiro} onChange={ev => onCampo("autorizadoRetiro", ev.target.checked)} />
                                <span>Autorizado para retirar al menor</span>
                            </label>
                        </div>
                        <Field id={id("idTipoIdentificacion")} errorKey={k("idTipoIdentificacion")} label="Tipo de identificación" error={errores[k("idTipoIdentificacion")]} select>
                            <select id={id("idTipoIdentificacion")} className="sg-input sg-select" value={e.idTipoIdentificacion} onChange={ev => {
                                const nuevo = tipos.find(t => String(t.id) === ev.target.value);
                                onCampo("idTipoIdentificacion", ev.target.value);
                                onCampo("numeroIdentificacion", ajustarIdentificacionAlTipo(e.numeroIdentificacion, nuevo));
                            }} {...aria("idTipoIdentificacion")}>
                                <option value="">Seleccione</option>
                                {tipos.map(t => <option key={t.id} value={t.id}>{t.nombre}</option>)}
                            </select>
                        </Field>
                        <Field id={id("numeroIdentificacion")} errorKey={k("numeroIdentificacion")} label="Cédula o identificación" error={errores[k("numeroIdentificacion")]}>
                            <CedulaInput id={id("numeroIdentificacion")} className="sg-input" mascara={esCedulaNacional(tipo)} maxLength={40} value={e.numeroIdentificacion} onChange={valor => onCampo("numeroIdentificacion", valor)} {...aria("numeroIdentificacion")} />
                        </Field>
                        {texto("nombres", "Nombre")}
                        {texto("primerApellido", "Primer apellido", { maxLength: 80 })}
                        {texto("segundoApellido", "Segundo apellido", { maxLength: 80, optional: true })}
                        {texto("telefonoPrincipal", "Teléfono principal", { maxLength: 9, type: "tel", inputMode: "tel", placeholder: "8888-4321" })}
                        {texto("telefonoSecundario", "Teléfono secundario", { maxLength: 9, type: "tel", inputMode: "tel", optional: true })}
                        {texto("correo", "Correo electrónico", { maxLength: 254, type: "email", optional: true })}
                        {texto("ocupacion", "Ocupación", { maxLength: 120, optional: true })}
                        {texto("estadoCivil", "Estado civil", { maxLength: 40, optional: true })}
                        <Field id={id("observaciones")} errorKey={k("observaciones")} label="Observaciones" optional className="span-all" error={errores[k("observaciones")]}>
                            <textarea id={id("observaciones")} className="sg-input" maxLength={255} value={e.observaciones} onChange={ev => onCampo("observaciones", ev.target.value)} {...aria("observaciones")} />
                        </Field>

                        <fieldset className="fieldset span-all">
                            <legend>Contactos de emergencia</legend>
                            <p className="sg-field__hint">
                                Máximo {MAX_CONTACTOS} por estudiante. {contactosDisponibles === 1 ? "Queda 1 disponible." : `Quedan ${contactosDisponibles} disponibles.`}
                            </p>
                            {e.contactosEmergencia.map((c, indice) => {
                                const kc = campo => `con-${c.clave}-${campo}`;
                                const campoContacto = (campo, label, maxLength, extra = {}) => (
                                    <Field id={id(kc(campo))} errorKey={k(kc(campo))} label={label} error={errores[k(kc(campo))]}>
                                        <input id={id(kc(campo))} className="sg-input" maxLength={maxLength} value={c[campo]} onChange={ev => onCampoContacto(c.clave, campo, ev.target.value)} {...aria(kc(campo))} {...extra} />
                                    </Field>
                                );
                                return (
                                    <div className="edicion-contacto" key={c.clave}>
                                        <div className="form-grid form-grid--3">
                                            {campoContacto("nombre", "Nombre", 180)}
                                            {campoContacto("telefono", "Teléfono", 30, { type: "tel", inputMode: "tel" })}
                                            {campoContacto("relacion", "Relación", 80)}
                                        </div>
                                        <button type="button" className="sg-btn sg-btn--ghost btn-peligro" onClick={() => onQuitarContacto(c.clave)}>
                                            <Trash2 size={18} strokeWidth={1.75} aria-hidden="true" /> Quitar<span className="sr-only"> contacto {indice + 1}</span>
                                        </button>
                                    </div>
                                );
                            })}
                            <button type="button" className="sg-btn sg-btn--secondary" onClick={onAgregarContacto} disabled={contactosDisponibles <= 0}>
                                <Plus size={18} strokeWidth={1.75} aria-hidden="true" /> Agregar contacto
                            </button>
                        </fieldset>
                    </div>
                    {!e.idEncargado && (
                        <button type="button" className="sg-btn sg-btn--ghost btn-peligro guardian-add" onClick={onQuitar}>
                            <Trash2 size={18} strokeWidth={1.75} aria-hidden="true" /> Quitar este encargado nuevo
                        </button>
                    )}
                </div>
            )}
        </div>
    );
}
