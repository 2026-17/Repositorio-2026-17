# Contrato de entidades para el registro de estudiantes

Las entidades de esta rama las implementa el responsable del modelo. El resto del registro ya consume estos nombres de propiedades.

## Estudiante
Debe mapear `estudiantes` y exponer:
- `Long id`
- `TipoIdentificacion idTipoIdentificacion`
- `String numeroIdentificacion`
- `String nombres`
- `String primerApellido`
- `String segundoApellido`
- `LocalDate fechaNacimiento`
- `String genero`
- `String telefono`
- `String correo`
- `String direccion`
- `String nivelEscolar`
- `CentroEducativo idCentroEducativo`
- `String condicionesRelevantes`
- `LocalDate fechaIngreso`
- `Boolean activo`

## Encargado
Debe mapear `encargados` y exponer:
- `Long id`
- `TipoIdentificacion idTipoIdentificacion`
- `String numeroIdentificacion`
- `String nombres`
- `String primerApellido`
- `String segundoApellido`
- `String telefonoPrincipal`
- `String telefonoSecundario`
- `String correo`
- `String ocupacion`
- `Boolean activo`

## EstudianteEncargado
Debe mapear `estudiantes_encargados` y exponer:
- `Estudiante estudiante`
- `Encargado encargado`
- `String parentesco`
- `Boolean esResponsablePrincipal`
- `Boolean esContactoEmergencia`

El ID compuesto puede ser `@EmbeddedId`; el repositorio de esta rama no depende del tipo concreto del ID.

## Instrumento
- `Integer id`
- `String codigo`
- `String nombre`
- `Boolean activo`

## EstudianteInstrumento
Debe mapear `estudiantes_instrumentos` y exponer:
- `Long id`
- `Estudiante estudiante`
- `Instrumento instrumento`
- `LocalDate fechaInicio`
- `LocalDate fechaFin`
- `Boolean esPrincipal`
- `String observaciones`

## DocumentoEstudiante
Debe mapear `documentos_estudiantes` y exponer:
- `Long id`
- `Estudiante estudiante`
- `TipoDocumento idTipoDocumento`
- `String nombreOriginal`
- `String nombreAlmacenado`
- `String ubicacionArchivo`
- `String tipoMime`
- `Long tamanoBytes`
- `String sha256`
- `Boolean esActual`

## TipoDocumento
- `Integer id`
- `String codigo`
- `String nombre`
- `Boolean activo`

El servicio busca el tipo documental con código `POLIZA`.

## TipoIdentificacion
- `Integer id`
- `String codigo`
- `String nombre`
- `Boolean activo`

## CentroEducativo
- `Long id`
- `String nombre`
- `String tipo`
- `Boolean activo`
