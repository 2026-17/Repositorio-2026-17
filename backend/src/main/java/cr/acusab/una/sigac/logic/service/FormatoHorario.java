package cr.acusab.una.sigac.logic.service;

import java.time.LocalTime;

/*
 * Textos de horario para los mensajes: días en español y horas en formato
 * de 12 h de Costa Rica ("4:00 p. m.").
 */
final class FormatoHorario {

    private static final String[] DIAS = {
            "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"
    };

    private FormatoHorario() {
    }

    static String dia(Short diaSemana) {
        if (diaSemana == null || diaSemana < 1 || diaSemana > 7) {
            return "día " + diaSemana;
        }
        return DIAS[diaSemana - 1];
    }

    static String hora(LocalTime hora) {
        int h = hora.getHour() % 12 == 0 ? 12 : hora.getHour() % 12;
        String sufijo = hora.getHour() < 12 ? "a. m." : "p. m.";
        return h + ":" + String.format("%02d", hora.getMinute()) + " " + sufijo;
    }

    // "jueves de 4:00 p. m. a 4:40 p. m."
    static String bloque(Short diaSemana, LocalTime inicio, LocalTime fin) {
        return dia(diaSemana) + " de " + hora(inicio) + " a " + hora(fin);
    }
}
