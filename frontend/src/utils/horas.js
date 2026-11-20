// Horas y días para mostrar al usuario, en formato de 12 h de Costa Rica
// ("7:10 a. m.", "4:00 p. m."). Los valores internos siguen en "HH:mm"
// (o "HH:mm:ss", como los manda el backend).

export const DIAS_SEMANA = [
    { valor: 1, nombre: "Lunes", corto: "Lun" },
    { valor: 2, nombre: "Martes", corto: "Mar" },
    { valor: 3, nombre: "Miércoles", corto: "Mié" },
    { valor: 4, nombre: "Jueves", corto: "Jue" },
    { valor: 5, nombre: "Viernes", corto: "Vie" },
    { valor: 6, nombre: "Sábado", corto: "Sáb" },
    { valor: 7, nombre: "Domingo", corto: "Dom" },
];

export function nombreDia(dia) {
    return DIAS_SEMANA.find((d) => d.valor === Number(dia))?.nombre ?? "Día por definir";
}

export function diaCorto(dia) {
    return DIAS_SEMANA.find((d) => d.valor === Number(dia))?.corto ?? "?";
}

// "HH:mm[:ss]" → minutos desde la medianoche.
export function aMinutos(hora) {
    if (!hora) return 0;
    const [h, m] = String(hora).split(":").map(Number);
    return h * 60 + (m || 0);
}

// Minutos desde la medianoche → "HH:mm".
export function desdeMinutos(minutos) {
    const h = Math.floor(minutos / 60);
    const m = minutos % 60;
    return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

function partes(hora) {
    const minutos = aMinutos(hora);
    const h24 = Math.floor(minutos / 60);
    const m = String(minutos % 60).padStart(2, "0");
    const h12 = h24 % 12 === 0 ? 12 : h24 % 12;
    return { texto: `${h12}:${m}`, sufijo: h24 < 12 ? "a. m." : "p. m." };
}

// "16:00" → "4:00 p. m."
export function hora12(hora) {
    if (!hora) return "";
    const { texto, sufijo } = partes(hora);
    return `${texto} ${sufijo}`;
}

// "07:10", "07:50" → "7:10 – 7:50 a. m."; "11:40", "12:20" → "11:40 a. m. – 12:20 p. m."
export function rangoHoras12(inicio, fin) {
    if (!inicio || !fin) return "";
    const a = partes(inicio);
    const b = partes(fin);
    return a.sufijo === b.sufijo
        ? `${a.texto} – ${b.texto} ${b.sufijo}`
        : `${a.texto} ${a.sufijo} – ${b.texto} ${b.sufijo}`;
}
