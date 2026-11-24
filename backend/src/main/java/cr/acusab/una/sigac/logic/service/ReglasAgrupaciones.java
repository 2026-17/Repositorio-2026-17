package cr.acusab.una.sigac.logic.service;

import java.util.Set;

/*
 * Decisiones de negocio de las agrupaciones, en un solo lugar para que
 * sea fácil cambiarlas. Los códigos de los niveles están en
 * FamiliaAgrupacion.
 *
 * D1. Nivel en una agrupación = el siguiente después del último aprobado
 *     en ESA agrupación (calificación de SIGAC o reconocido con esa
 *     agrupación). Es la regla base; no tiene interruptor.
 * D2. Sin avance propio en la agrupación, se parte del nivel más alto
 *     aprobado de la familia SIN agrupación (cursos_reconocidos de la
 *     carga inicial con id_agrupacion NULL). Si no hay, se empieza en I.
 * D3. Quien ya aprobó el nivel máximo en la agrupación no la puede
 *     matricular ("Ya completó esta agrupación").
 * D4. Si en el período matricula DOS o más agrupaciones y NINGUNA es
 *     CORAL, la SEGUNDA que eligió (matriculas_detalles.orden_seleccion) se
 *     le califica como coro: ese detalle guarda su siguiente nivel de Coro
 *     en ESA agrupación (D1 a D3). Es automática: el estudiante no elige.
 *     Con una sola agrupación no aplica.
 * D5. Conjunto Instrumental (o Coro) queda completo con TODOS sus niveles
 *     aprobados en UNA MISMA agrupación, contando el historial sin
 *     agrupación de D2. El plan usa la agrupación más adelantada.
 * D6. Una agrupación "solo inclusión" solo la matriculan estudiantes de
 *     tipo INCLUSION.
 * D7. Edad mínima y máxima opcionales (agrupaciones y cursos), en años
 *     cumplidos al inicio del período (ReglasEdad).
 */
public final class ReglasAgrupaciones {

    // D2: usar el historial sin agrupación como punto de partida.
    public static final boolean PARTIR_DEL_HISTORIAL_SIN_AGRUPACION = true;

    // D3: bloquear la agrupación cuando ya se aprobó su nivel máximo.
    public static final boolean BLOQUEAR_AGRUPACION_COMPLETADA = true;

    // D4: calificar como coro una agrupación de quien no lleva ninguna coral.
    public static final boolean CALIFICAR_INSTRUMENTAL_COMO_CORO = true;

    // D4: desde cuántas agrupaciones aplica; la que ocupa ese lugar es la que se califica como coro.
    public static final int AGRUPACIONES_PARA_CORO = 2;

    // D6: código de tipos_estudiante.
    public static final String TIPO_ESTUDIANTE_INCLUSION = "INCLUSION";

    // Estado de grupo que se ofrece a los estudiantes.
    public static final String ESTADO_GRUPO_OFERTADO = "PUBLICADO";

    // Mismo estado académico que cuenta como aprobado en los instrumentos.
    public static final String ESTADO_ACADEMICO_APROBADO = "APROBADO";

    /*
     * Estados académicos que todavía no son un resultado: no cuentan como
     * "no aprobó" en el motivo interno ("No aprobó Orquesta V: la repite").
     */
    public static final Set<String> ESTADOS_ACADEMICOS_SIN_RESULTADO =
            Set.of("EN_CURSO", "PENDIENTE", "INSCRITO");

    private ReglasAgrupaciones() {
    }
}
