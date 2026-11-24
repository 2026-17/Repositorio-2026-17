package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.time.LocalDate;

/*
 * Avance académico que no viene de una calificación de SIGAC:
 * - CERTIFICACION: cursos aprobados antes de SIGAC (importación de las
 *   certificaciones 2025, tarea aparte).
 * - NIVEL_INGRESO: niveles de instrumento anteriores al nivel de ingreso
 *   que la dirección académica asigna tras una prueba de ubicación.
 *
 * id_instrumento solo se llena en los cursos de instrumento: aprobar
 * Instrumento V en piano no lo aprueba en guitarra.
 *
 * La columna id_instrumento_clave (COALESCE(id_instrumento, 0)) la calcula
 * MySQL para la llave única; no se mapea.
 */
@Getter
@Setter
@Entity
@Table(name = "cursos_reconocidos")
public class CursoReconocido {

    public static final String CONDICION_APROBADO = "APROBADO";
    public static final String CONDICION_CONVALIDADO = "CONVALIDADO";

    public static final String ORIGEN_CERTIFICACION = "CERTIFICACION";
    public static final String ORIGEN_NIVEL_INGRESO = "NIVEL_INGRESO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_curso_reconocido", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @NotNull
    @Column(name = "id_estudiante", nullable = false)
    private Long idEstudiante;

    @NotNull
    @Column(name = "id_curso", nullable = false)
    private Long idCurso;

    @Column(name = "id_instrumento")
    private Integer idInstrumento;

    /*
     * Agrupación en que se aprobó (Orquesta IV). NULL = solo el nivel de la
     * familia (Conjunto Instrumental o Coro), sin agrupación: es el punto de
     * partida en una agrupación nueva (AvanceAgrupacionService, regla D2).
     * La columna id_agrupacion_clave la calcula MySQL; no se mapea.
     */
    @Column(name = "id_agrupacion")
    private Long idAgrupacion;

    @NotNull
    @Size(max = 20)
    @Column(name = "condicion", nullable = false, length = 20)
    private String condicion;

    @NotNull
    @Size(max = 20)
    @Column(name = "origen", nullable = false, length = 20)
    private String origen;

    @NotNull
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Size(max = 500)
    @Column(name = "motivo", length = 500)
    private String motivo;

    @Column(name = "id_usuario_registra")
    private Long idUsuarioRegistra;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @PrePersist
    protected void alPersistir() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
    }
}
