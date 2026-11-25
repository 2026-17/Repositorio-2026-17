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
 * Grupo incluido en una matrícula. Mientras "retirado" sea falso, el
 * grupo ocupa un cupo.
 */
@Getter
@Setter
@Entity
@Table(name = "matriculas_detalles")
public class MatriculaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_matricula", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_matricula", nullable = false)
    private Matricula matricula;

    @NotNull
    @Column(name = "id_grupo", nullable = false)
    private Long idGrupo;

    /*
     * Nivel que el estudiante cursa cuando el grupo es un espacio de
     * instrumento (Instrumento I..XIV). En un grupo normal queda NULL y el
     * curso es el del grupo.
     */
    @Column(name = "id_curso")
    private Long idCurso;

    /*
     * Instrumento que el estudiante cursa cuando el grupo es un espacio de
     * instrumento. Se guarda siempre en los espacios; es indispensable en
     * los de "cualquier instrumento del profesor" (grupos.id_instrumento
     * NULL). En un grupo normal queda NULL.
     */
    @Column(name = "id_instrumento")
    private Integer idInstrumento;

    /*
     * Agrupaciones: orden en que el estudiante las eligió en el período
     * (1, 2, 3...). Regla D4: la segunda se califica como coro cuando no
     * lleva ninguna coral. NULL en grupos normales y espacios.
     */
    @Column(name = "orden_seleccion", columnDefinition = "smallint UNSIGNED")
    private Short ordenSeleccion;

    /*
     * Cursos a coordinar en Dirección: profesor que la Dirección Académica
     * le asigna a ESTE estudiante (NULL = sin asignar) y cómo cumplirá el
     * curso (en qué agrupación, a quién acompaña, con quiénes, horario).
     * En los demás grupos quedan NULL.
     */
    @Column(name = "id_profesor_asignado", columnDefinition = "int UNSIGNED")
    private Long idProfesorAsignado;

    @Size(max = 500)
    @Column(name = "notas_coordinacion", length = 500)
    private String notasCoordinacion;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "retirado", nullable = false)
    private Boolean retirado = false;

    @Column(name = "fecha_retiro")
    private LocalDate fechaRetiro;

    @Size(max = 500)
    @Column(name = "motivo_retiro", length = 500)
    private String motivoRetiro;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @PrePersist
    protected void alPersistir() {

        Instant ahora = Instant.now();

        creadoEn = ahora;
        actualizadoEn = ahora;
    }

    @PreUpdate
    protected void alActualizar() {
        actualizadoEn = Instant.now();
    }
}
