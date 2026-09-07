package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "estudiantes_encargados")
public class EstudiantesEncargado {
    @EmbeddedId
    private EstudiantesEncargadoId id;

    @MapsId("idEstudiante")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_estudiante", nullable = false)
    private Estudiante idEstudiante;

    @MapsId("idEncargado")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_encargado", nullable = false)
    private Encargado idEncargado;

    @Size(max = 30)
    @NotNull
    @Column(name = "parentesco", nullable = false, length = 30)
    private String parentesco;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "es_responsable_principal", nullable = false)
    private Boolean esResponsablePrincipal = false;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "es_contacto_emergencia", nullable = false)
    private Boolean esContactoEmergencia = false;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "autorizado_retiro", nullable = false)
    private Boolean autorizadoRetiro = false;

    @Size(max = 255)
    @Column(name = "observaciones")
    private String observaciones;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    // Bridge getters/setters for compatibility with service
    public void setEstudiante(Estudiante estudiante) { this.idEstudiante = estudiante; }
    public Estudiante getEstudiante() { return this.idEstudiante; }

    public void setEncargado(Encargado encargado) { this.idEncargado = encargado; }
    public Encargado getEncargado() { return this.idEncargado; }
}