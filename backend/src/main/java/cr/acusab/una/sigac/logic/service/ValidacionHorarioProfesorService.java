package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.HorarioGrupo;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.HorarioGrupoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class ValidacionHorarioProfesorService {

    public record BloqueHorario(
            Short diaSemana,
            LocalTime horaInicio,
            LocalTime horaFin
    ) {
    }

    private final GrupoRepository grupoRepository;
    private final HorarioGrupoRepository horarioRepository;

    public ValidacionHorarioProfesorService(
            GrupoRepository grupoRepository,
            HorarioGrupoRepository horarioRepository
    ) {
        this.grupoRepository = grupoRepository;
        this.horarioRepository = horarioRepository;
    }

    public void validarDisponibilidad(
            Long idProfesor,
            Long idPeriodo,
            LocalDate fechaInicioDestino,
            LocalDate fechaFinDestino,
            Long idGrupoExcluido,
            List<BloqueHorario> horariosPropuestos
    ) {
        if (horariosPropuestos == null || horariosPropuestos.isEmpty()) {
            throw new IllegalArgumentException(
                    "El grupo debe tener al menos un horario registrado antes de asignarle un profesor."
            );
        }

        List<Grupo> gruposDelProfesor = grupoRepository.findGruposProfesorEnPeriodo(
                idProfesor,
                idPeriodo,
                fechaInicioDestino,
                fechaFinDestino
        );

        List<Grupo> gruposComparables = gruposDelProfesor.stream()
                .filter(grupo -> idGrupoExcluido == null
                        || !grupo.getId().equals(idGrupoExcluido))
                .toList();

        List<Long> idsGrupos = gruposComparables.stream()
                .map(Grupo::getId)
                .toList();

        if (idsGrupos.isEmpty()) {
            return;
        }

        List<HorarioGrupo> horariosOcupados =
                horarioRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(idsGrupos);

        for (BloqueHorario propuesto : horariosPropuestos) {
            for (HorarioGrupo ocupado : horariosOcupados) {
                boolean mismoDia = propuesto.diaSemana().equals(ocupado.getDiaSemana());
                boolean traslape = propuesto.horaInicio().isBefore(ocupado.getHoraFin())
                        && ocupado.getHoraInicio().isBefore(propuesto.horaFin());

                if (mismoDia && traslape) {
                    Grupo grupoConflicto = gruposComparables.stream()
                            .filter(grupo -> grupo.getId().equals(ocupado.getIdGrupo()))
                            .findFirst()
                            .orElse(null);

                    String detalleGrupo = grupoConflicto == null
                            ? ""
                            : " (grupo " + grupoConflicto.getCodigo() + ")";

                    throw new IllegalArgumentException(
                            "Conflicto de horario: el profesor ya tiene un grupo asignado en ese horario"
                                    + detalleGrupo + "."
                    );
                }
            }
        }
    }
}
