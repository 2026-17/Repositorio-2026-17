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
 * Oferta de un período. Hay dos tipos:
 * - CURSO: grupo normal de un curso (id_curso obligatorio).
 * - ESPACIO_INSTRUMENTO: lección individual semanal de un profesor en un
 *   instrumento (id_curso NULL, cupo 1). El nivel (Instrumento I..XIV)
 *   lo trae el estudiante y queda en matriculas_detalles.id_curso.
 */
@Getter
@Setter
@Entity
@Table(name = "grupos")
public class Grupo {

    public static final String TIPO_CURSO = "CURSO";
    public static final String TIPO_ESPACIO_INSTRUMENTO = "ESPACIO_INSTRUMENTO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_grupo", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @NotNull
    @Column(name = "id_periodo", nullable = false)
    private Long idPeriodo;

    @NotNull
    @Size(max = 30)
    @ColumnDefault("'CURSO'")
    @Column(name = "tipo_grupo", nullable = false, length = 30)
    private String tipoGrupo = TIPO_CURSO;

    // NULL en los espacios de instrumento.
    @Column(name = "id_curso")
    private Long idCurso;

    // Solo en los espacios de instrumento.
    @Column(name = "id_instrumento")
    private Integer idInstrumento;

    @Column(name = "id_profesor")
    private Long idProfesor;

    @Column(name = "id_usuario_ultima_modificacion")
    private Long idUsuarioUltimaModificacion;

    @Size(max = 40)
    @NotNull
    @Column(name = "codigo", nullable = false, length = 40)
    private String codigo;

    @NotNull
    @Column(name = "cupo_maximo", nullable = false)
    private Short cupoMaximo;

    @Size(max = 20)
    @NotNull
    @ColumnDefault("'PRESENCIAL'")
    @Column(name = "modalidad", nullable = false, length = 20)
    private String modalidad;

    @Column(name = "aula")
    private String aula;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Size(max = 20)
    @NotNull
    @ColumnDefault("'PLANIFICADO'")
    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    public boolean esEspacioInstrumento() {
        return TIPO_ESPACIO_INSTRUMENTO.equals(tipoGrupo);
    }
}

