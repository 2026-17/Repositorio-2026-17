import {
    Bell,
    BookOpen,
    BookPlus,
    BookX,
    CalendarCog,
    CalendarDays,
    CalendarPlus,
    ChartColumn,
    ClipboardList,
    GraduationCap,
    Music,
    UserPlus,
    Users,
    UserRoundCog,
    Wallet,
} from "lucide-react";

import { ROLES } from "./roles";

// Pantalla de inicio de todos los roles: explica cada opción del menú.
export const RUTA_INICIO = "/inicio";

/*
 * Menú lateral agrupado por rol.
 * Cada grupo tiene un título y sus enlaces; cada enlace lleva el ícono
 * que se ve en el menú (y es lo único visible cuando está contraído)
 * y la descripción que se muestra en la pantalla de inicio.
 */
export const MENU_POR_ROL = {
    [ROLES.ADMINISTRADOR]: [
        {
            titulo: "Administración",
            items: [
                {
                    to: "/admin/usuarios/crear",
                    label: "Crear usuario",
                    icon: UserPlus,
                    descripcion: "Registre una cuenta de acceso a SIGAC con su rol y estado. La contraseña temporal se envía al correo del usuario.",
                },
            ],
        },
    ],

    [ROLES.DIRECTOR_ACADEMICO]: [
        {
            titulo: "Período académico",
            items: [
                {
                    to: "/director-academico/periodos-academicos/crear",
                    label: "Crear período",
                    icon: CalendarPlus,
                    descripcion: "Defina el nombre, las fechas y el estado de un nuevo período para organizar cursos, grupos, matrícula y pagos.",
                },
                {
                    to: "/director-academico/periodos-academicos/modificar",
                    label: "Consultar / Modificar",
                    icon: CalendarCog,
                    descripcion: "Busque un período académico y actualice sus fechas o su estado sin perder su información histórica.",
                },
            ],
        },
        {
            titulo: "Cursos",
            items: [
                {
                    to: "/director-academico/cursos/crear",
                    label: "Crear curso",
                    icon: BookPlus,
                    descripcion: "Incorpore un nuevo curso a la malla curricular con su información y requisitos.",
                },
                {
                    to: "/director-academico/cursos/modificar",
                    label: "Modificar curso",
                    icon: BookOpen,
                    descripcion: "Seleccione un curso activo y actualice los datos permitidos de la malla curricular.",
                },
                {
                    to: "/director-academico/cursos/desactivar",
                    label: "Desactivar curso",
                    icon: BookX,
                    descripcion: "Retire un curso de la malla curricular sin eliminar su información histórica.",
                },
                {
                    to: "/director-academico/grupos/crear",
                    label: "Crear grupo",
                    icon: Users,
                    descripcion: "Abra un grupo de un curso en un período académico: cupo, profesor, modalidad y horario semanal.",
                },
                {
                    to: "/director-academico/asignar-profesores",
                    label: "Asignar profesores",
                    icon: UserRoundCog,
                    descripcion: "Asigne un profesor responsable a cada grupo y revise que los horarios no choquen antes de guardar.",
                },
            ],
        },
        {
            titulo: "Académico",
            items: [
                {
                    to: "/director-academico/profesores",
                    label: "Profesores",
                    icon: GraduationCap,
                    descripcion: "Consulte y administre la información académica y de contacto de los profesores.",
                },
                {
                    to: "/director-academico/profesores/registrar",
                    label: "Registrar profesor",
                    icon: UserPlus,
                    descripcion: "Ingrese los datos personales y académicos de un profesor y cree su cuenta de usuario.",
                },
                {
                    to: "/director-academico/estudiantes/instrumentos",
                    label: "Asignar instrumento",
                    icon: Music,
                    descripcion: "Registre o cambie el instrumento musical asociado al expediente académico de un estudiante.",
                },
            ],
        },
    ],

    [ROLES.ADMINISTRATIVO]: [
        {
            titulo: "Académico",
            items: [
                {
                    to: "/admin/administracion/estudiantes",
                    label: "Estudiantes",
                    icon: Users,
                    descripcion: "Consulte, edite y administre la información académica, de contacto y los documentos de los estudiantes.",
                },
                {
                    to: "/admin/administracion/estudiantes/crear",
                    label: "Registrar estudiante",
                    icon: UserPlus,
                    descripcion: "Ingrese los datos personales, académicos y de contacto de un estudiante nuevo junto con su documentación.",
                },
                {
                    to: "/admin/administracion/estudiantes/instrumentos",
                    label: "Asignar instrumento",
                    icon: Music,
                    descripcion: "Registre o cambie el instrumento musical asociado al expediente académico de un estudiante.",
                },
            ],
        },
        {
            titulo: "Matrícula y pagos",
            items: [
                {
                    to: "/admin/administracion/matricula",
                    label: "Matrícula",
                    icon: ClipboardList,
                    descripcion: "Revise datos, mensualidades y pago de matrícula de cada estudiante y habilite o rechace su preaprobación.",
                },
                {
                    to: "/admin/administracion/becas-pagos",
                    label: "Becas y pagos",
                    icon: Wallet,
                    descripcion: "Busque un estudiante para asignarle una beca o registrar sus pagos.",
                },
            ],
        },
        {
            titulo: "Informes",
            items: [
                {
                    to: "/admin/administracion/reportes",
                    label: "Reportes",
                    icon: ChartColumn,
                    descripcion: "Consulte indicadores y listados de la escuela.",
                },
            ],
        },
    ],

    [ROLES.PROFESOR]: [],

    [ROLES.JUNTA_DIRECTIVA]: [],

    /*
     * HU-10 - Consultar cursos disponibles de un período académico.
     * HU-002 - Solicitud de matrícula.
     * HU-012 - Notificaciones del estudiante.
     */
    [ROLES.ESTUDIANTE]: [
        {
            titulo: "Mi matrícula",
            items: [
                {
                    to: "/estudiante/cursos-disponibles",
                    label: "Cursos disponibles",
                    icon: BookOpen,
                    descripcion: "Consulte los cursos de un período académico: horario, grupo, profesor y cupos disponibles.",
                },
                {
                    to: "/estudiante/horario",
                    label: "Mi horario",
                    icon: CalendarDays,
                    descripcion: "Vea su horario semanal con los cursos en los que está matriculado.",
                },
                {
                    to: "/estudiante/matricula",
                    label: "Matrícula",
                    icon: ClipboardList,
                    descripcion: "Seleccione los cursos autorizados del período vigente y confirme su matrícula.",
                },
                {
                    to: "/estudiante/notificaciones",
                    label: "Notificaciones",
                    icon: Bell,
                    descripcion: "Consulte las novedades relacionadas con sus solicitudes de matrícula.",
                },
            ],
        },
    ],
};
