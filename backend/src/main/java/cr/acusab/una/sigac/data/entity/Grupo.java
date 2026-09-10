package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "grupos")
public class Grupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_grupo", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @NotNull
    @Column(name = "id_periodo", nullable = false)
    private Long idPeriodo;

    @NotNull
    @Column(name = "id_curso", nullable = false)
    private Long idCurso;

    @Column(name = "id_profesor")
    private Long idProfesor;

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
}

