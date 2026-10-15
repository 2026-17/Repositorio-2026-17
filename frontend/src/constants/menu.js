import {
    Bell,
    BookOpen,
    CalendarDays,
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
 * proximamente: la opción se muestra deshabilitada con la etiqueta "Próximamente".
 *
 * Este menú también define los permisos: un rol solo puede abrir las
 * rutas de sus propias opciones (ver tieneAcceso).
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
                    to: "/director-academico/periodos-academicos",
                    label: "Períodos académicos",
                    icon: CalendarDays,
                    descripcion: "Cree períodos con el botón \"Nuevo período\" y consulte o modifique las fechas y el estado de los existentes sin perder su información histórica.",
                },
            ],
        },
        {
            titulo: "Cursos",
            items: [
                {
                    to: "/director-academico/cursos",
                    label: "Cursos",
                    icon: BookOpen,
                    descripcion: "Incorpore cursos a la malla curricular con el botón \"Nuevo curso\" y modifique o desactive los existentes sin eliminar su información histórica.",
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
                    descripcion: "Registre profesores nuevos con el botón \"Nuevo profesor\" (se crea su cuenta de usuario) y consulte, edite, dé de baja o reactive a los existentes.",
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
                    descripcion: "Registre estudiantes nuevos con el botón \"Nuevo estudiante\" y consulte, edite, dé de baja o revise los documentos de los existentes.",
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
                    // Todavía no está disponible: se ve en el menú, pero deshabilitado
                    proximamente: true,
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

// La ruta pertenece a la opción si es la misma o una subruta (p. ej. ".../registrar").
function rutaDeOpcion(pathname, to) {
    return pathname === to || pathname.startsWith(`${to}/`);
}

/*
 * Indica si el rol puede abrir la ruta. Se deriva del menú para no mantener
 * otra lista de permisos: la pantalla de inicio es de todos los roles y el
 * resto de rutas solo de los roles que tienen la opción en su menú.
 */
export function tieneAcceso(rol, pathname) {
    if (pathname === RUTA_INICIO) return true;
    const grupos = MENU_POR_ROL[rol] || [];
    return grupos.some((grupo) => grupo.items.some((item) => rutaDeOpcion(pathname, item.to)));
}
