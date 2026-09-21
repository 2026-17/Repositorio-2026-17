package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/*
 * Horario semanal de un grupo. dia_semana: 1 = lunes ... 7 = domingo.
 */
@Getter
@Setter
@Entity
@Table(name = "horarios_grupos")
public class HorarioGrupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_horario", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @NotNull
    @Column(name = "id_grupo", nullable = false)
    private Long idGrupo;

    @NotNull
    @Column(name = "dia_semana", nullable = false)
    private Short diaSemana;

    @NotNull
    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @NotNull
    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Size(max = 80)
    @Column(name = "aula", length = 80)
    private String aula;
}
