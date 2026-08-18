import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { rolGuard } from './core/auth/rol.guard';

/**
 * Rutas del SPA. Dos guardas encadenadas y con papeles distintos:
 * `authGuard` decide si hay sesión, `rolGuard` si ese rol alcanza la pantalla
 * (leyendo el mismo mapa que dibuja el menú, `core/auth/acceso.ts`).
 *
 * Ninguna de las dos es autorización: el backend la impone en cada request
 * (RC-001). Aquí solo se evita el viaje y el 403 en pantalla.
 *
 * Al añadir una pantalla al menú: declarar su ruta hija con
 * `canActivate: [rolGuard]` y su fila en `MODULOS`. Toda fila de `MODULOS`
 * tiene que tener ruta — no hay entradas "pendientes", porque una entrada que
 * no se puede pulsar es ruido. Si la ruta no está declarada en `MODULOS`
 * (detalles, formularios), el guard la deja pasar y manda el backend.
 */
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    // PUBLICA (D-27): el titular de los datos tiene que poder leer el aviso
    // SIN cuenta. Va fuera del shell y sin `authGuard` a proposito; el enlace
    // de la casilla de autorización apunta aquí.
    path: 'privacidad',
    loadComponent: () => import('./features/privacidad/privacidad').then((m) => m.Privacidad),
  },
  {
    // PUBLICA (§4.3): quien recupera su acceso NO tiene sesión — es justo lo
    // que viene a recuperar. Acepta `?token=…` para entrar directa al canje.
    path: 'recuperar',
    loadComponent: () =>
      import('./features/recuperar-acceso/recuperar-acceso').then((m) => m.RecuperarAcceso),
  },
  {
    // Con sesión, pero FUERA del shell (§4.2/§4.5). Es la única forma de que
    // funcione con la sesión capada por contraseña temporal: dentro del shell,
    // la campana y el menú llaman a endpoints que ese 403 bloquea.
    path: 'cambiar-contrasena',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/cambiar-contrasena/cambiar-contrasena').then((m) => m.CambiarContrasena),
  },
  {
    // Gemela de la anterior y fuera del shell por lo mismo (D-S0-25): con la
    // sesión capada por segundo factor pendiente, el backend solo deja pasar
    // el perfil, este flujo y el logout — el armazón no llegaría a pintarse.
    path: 'enrolar-mfa',
    canActivate: [authGuard],
    loadComponent: () => import('./features/enrolar-mfa/enrolar-mfa').then((m) => m.EnrolarMfa),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    children: [
      {
        // Home del sistema. Sin `data.roles`: los tres entran y lo que cambia
        // es el alcance — y, para BROKER y ADMIN, que la bandeja llega vacía
        // porque `/tareas` es solo del agente (no es un 403).
        path: '',
        canActivate: [rolGuard],
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'propiedades/nueva',
        canActivate: [rolGuard],
        data: { roles: ['AGENTE'] },
        loadComponent: () =>
          import('./features/local-form/local-form').then((m) => m.LocalForm),
      },
      {
        path: 'propiedades/:id/editar',
        canActivate: [rolGuard],
        data: { roles: ['AGENTE'] },
        loadComponent: () =>
          import('./features/local-form/local-form').then((m) => m.LocalForm),
      },
      {
        // Cierres exitosos. Sin `data.roles`: los tres roles entran y lo que
        // cambia es el ALCANCE, que resuelve el backend.
        path: 'propiedades-alquiladas',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/propiedades-alquiladas/propiedades-alquiladas').then(
            (m) => m.PropiedadesAlquiladas,
          ),
      },
      {
        // Cartera del equipo. A diferencia de la ficha, esta SÍ lleva gate de
        // rol: el backend responde 403 a un AGENTE
        // (`/captaciones/propiedades-equipo` es de BROKER/ADMIN), y la fila de
        // `MODULOS` que dibuja el menú es la misma que lee el guard.
        path: 'propiedades-equipo',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/propiedades-equipo/propiedades-equipo').then(
            (m) => m.PropiedadesEquipo,
          ),
      },
      {
        // Catálogo de propietarios. El listado no lleva gate —los tres roles
        // entran y lo que cambia es el alcance— pero escribir es de AGENTE, y
        // eso lo decide la pantalla con el rol de sesión, no el guard.
        path: 'propietarios',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/propietarios/propietarios').then((m) => m.Propietarios),
      },
      {
        // Antes de `propietarios/:id/...`: el router resuelve por orden y `:id`
        // capturaría también el literal "nuevo".
        path: 'propietarios/nuevo',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/propietario-form/propietario-form').then((m) => m.PropietarioForm),
      },
      {
        // Sin `data.roles`, igual que el formulario de clientes: escribir es de
        // AGENTE, pero el gate lo pone la PANTALLA en solo lectura, no el
        // router. Un broker que llegue por URL ve el formulario bloqueado y el
        // motivo, no un "acceso denegado" sin explicación.
        path: 'propietarios/:id/editar',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/propietario-form/propietario-form').then((m) => m.PropietarioForm),
      },
      {
        path: 'propietarios/:id',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/propietario-detail/propietario-detail').then(
            (m) => m.PropietarioDetail,
          ),
      },
      {
        path: 'agentes',
        canActivate: [rolGuard],
        loadComponent: () => import('./features/agentes/agentes').then((m) => m.Agentes),
      },
      {
        // Antes de `agentes/:id/...`, como en el resto: `:id` capturaría "nuevo".
        path: 'agentes/nuevo',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/agente-form/agente-form').then((m) => m.AgenteForm),
      },
      {
        path: 'agentes/:id/editar',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/agente-form/agente-form').then((m) => m.AgenteForm),
      },
      {
        path: 'agentes/:id',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/agente-detail/agente-detail').then((m) => m.AgenteDetail),
      },
      {
        path: 'brokers',
        canActivate: [rolGuard],
        loadComponent: () => import('./features/brokers/brokers').then((m) => m.Brokers),
      },
      {
        path: 'brokers/nuevo',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/broker-form/broker-form').then((m) => m.BrokerForm),
      },
      {
        path: 'brokers/:id/editar',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/broker-form/broker-form').then((m) => m.BrokerForm),
      },
      {
        path: 'brokers/:id',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/broker-detail/broker-detail').then((m) => m.BrokerDetail),
      },
      {
        // Todo el recurso `/asignaciones` es de ADMIN, así que aquí el gate SÍ
        // va en el router: no hay nada que enseñar a los demás roles.
        path: 'asignaciones',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/asignaciones/asignaciones').then((m) => m.Asignaciones),
      },
      {
        // Gobierno de accesos. Mismo criterio que `asignaciones`: los dos
        // endpoints que consume son TENANT_ADMIN enteros, así que el gate va
        // también en el router — no hay nada que enseñarle a los demás roles.
        path: 'seguridad',
        canActivate: [rolGuard],
        loadComponent: () => import('./features/seguridad/seguridad').then((m) => m.Seguridad),
      },
      {
        // Antes de `brokers/:id`… no: cuelga de su propia ruta porque no es la
        // ficha de "un" broker sino la del que tiene la sesión abierta.
        path: 'mi-equipo',
        canActivate: [rolGuard],
        loadComponent: () => import('./features/mi-equipo/mi-equipo').then((m) => m.MiEquipo),
      },
      {
        // Los tres roles leen; las tres operaciones de comisión son de BROKER
        // sin ADMIN, y eso lo resuelve la pantalla mostrando o no los botones.
        path: 'comisiones',
        canActivate: [rolGuard],
        loadComponent: () => import('./features/comisiones/comisiones').then((m) => m.Comisiones),
      },
      {
        // Alcance por UNIÓN de agente propio y agente de la captación, y esa
        // segunda rama existe solo para el BROKER. Lo impone el backend.
        path: 'seguimiento-comercial',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/seguimiento-comercial/seguimiento-comercial').then(
            (m) => m.SeguimientoComercial,
          ),
      },
      {
        // Sin `data.roles`: los tres entran y lo que cambia es el alcance y el
        // `ambito` que el propio backend rotula.
        path: 'indicadores',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/indicadores/indicadores').then((m) => m.Indicadores),
      },
      {
        // RF-017. Sin gate: alcanza por agente responsable, como indicadores.
        path: 'reportes',
        canActivate: [rolGuard],
        loadComponent: () => import('./features/reportes/reportes').then((m) => m.Reportes),
      },
      {
        path: 'perfil',
        canActivate: [rolGuard],
        loadComponent: () => import('./features/perfil/perfil').then((m) => m.Perfil),
      },
      {
        path: 'prospecciones',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/prospecciones/prospecciones').then((m) => m.Prospecciones),
      },
      {
        path: 'clientes',
        canActivate: [rolGuard],
        loadComponent: () => import('./features/clientes/clientes').then((m) => m.Clientes),
      },
      {
        path: 'clientes/nuevo',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/cliente-form/cliente-form').then((m) => m.ClienteForm),
      },
      {
        path: 'clientes/:id/editar',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/cliente-form/cliente-form').then((m) => m.ClienteForm),
      },
      {
        // Bitácora de contacto: la conversación con el cliente, con y sin
        // propiedad de por medio. Va antes que `clientes/:id` por claridad;
        // el router no las confunde porque tienen distinto número de tramos.
        path: 'clientes/:id/contacto',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/cliente-contacto-detail/cliente-contacto-detail').then(
            (m) => m.ClienteContactoDetail,
          ),
      },
      {
        path: 'clientes/:id',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/cliente-detail/cliente-detail').then((m) => m.ClienteDetail),
      },
      {
        // Antes de `oportunidades/:id`: el router resuelve por orden y `:id`
        // capturaría también el literal "nueva".
        path: 'oportunidades/nueva',
        canActivate: [rolGuard],
        data: { roles: ['AGENTE'] },
        loadComponent: () =>
          import('./features/oportunidad-form/oportunidad-form').then((m) => m.OportunidadForm),
      },
      {
        path: 'oportunidades/:id',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/oportunidad-detail/oportunidad-detail').then(
            (m) => m.OportunidadDetail,
          ),
      },
      {
        // Sin `data.roles`: los tres roles entran y lo que cambia es el
        // alcance —el BROKER alcanza por CAPTACIÓN, no por agente—, que
        // resuelve el backend.
        path: 'oportunidades',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/oportunidades/oportunidades').then((m) => m.Oportunidades),
      },
      {
        // Programar exige que la oportunidad sea del PROPIO agente, sin
        // alcance de broker: por eso esta sí lleva gate de rol.
        path: 'visitas/nueva',
        canActivate: [rolGuard],
        data: { roles: ['AGENTE'] },
        loadComponent: () =>
          import('./features/visita-form/visita-form').then((m) => m.VisitaForm),
      },
      {
        path: 'visitas',
        canActivate: [rolGuard],
        loadComponent: () => import('./features/visitas/visitas').then((m) => m.Visitas),
      },
      {
        path: 'interacciones/nueva',
        canActivate: [rolGuard],
        data: { roles: ['AGENTE'] },
        loadComponent: () =>
          import('./features/interaccion-form/interaccion-form').then((m) => m.InteraccionForm),
      },
      {
        path: 'interacciones/:id',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/interaccion-detail/interaccion-detail').then(
            (m) => m.InteraccionDetail,
          ),
      },
      {
        // El BROKER alcanza aquí por AGENTE SUPERVISADO, no por captación.
        path: 'interacciones',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/interacciones/interacciones').then((m) => m.Interacciones),
      },
      {
        // Antes de `solicitudes/:codigo`: el router resuelve por orden y
        // `:codigo` capturaría también los literales "nueva" y "revisar".
        path: 'solicitudes/nueva',
        canActivate: [rolGuard],
        data: { roles: ['AGENTE'] },
        loadComponent: () =>
          import('./features/solicitud-form/solicitud-form').then((m) => m.SolicitudForm),
      },
      {
        // Cola del broker. Lleva gate porque la decisión a la que conduce
        // (`POST /evaluaciones`) es de BROKER/ADMIN: ofrecérsela al agente
        // sería prometerle un 403.
        path: 'solicitudes/revisar',
        // Fila 13: firmar la evaluación es del broker. El admin ya no entra.
        canActivate: [rolGuard],
        data: { roles: ['BROKER'] },
        loadComponent: () =>
          import('./features/solicitudes-revisar/solicitudes-revisar').then(
            (m) => m.SolicitudesRevisar,
          ),
      },
      {
        path: 'solicitudes/:codigo/documentos',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/documentos-solicitud/documentos-solicitud').then(
            (m) => m.DocumentosSolicitud,
          ),
      },
      {
        path: 'solicitudes/:codigo/evaluar',
        canActivate: [rolGuard],
        data: { roles: ['BROKER'] },
        loadComponent: () =>
          import('./features/evaluacion-solicitud/evaluacion-solicitud').then(
            (m) => m.EvaluacionSolicitud,
          ),
      },
      {
        path: 'solicitudes/:codigo',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/solicitud-detail/solicitud-detail').then((m) => m.SolicitudDetail),
      },
      {
        // Sin `data.roles`: los tres roles entran y lo que cambia es el
        // ALCANCE —el BROKER alcanza por AGENTE SUPERVISADO, distinto de
        // oportunidades—, que resuelve el backend.
        path: 'solicitudes',
        canActivate: [rolGuard],
        loadComponent: () => import('./features/solicitudes/solicitudes').then((m) => m.Solicitudes),
      },
      {
        path: 'prospecciones/:id',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/prospeccion-detail/prospeccion-detail').then(
            (m) => m.ProspeccionDetail,
          ),
      },
      {
        path: 'captaciones/pendientes',
        // Fila 1: supervisión, y el admin la conserva en lectura.
        canActivate: [rolGuard],
        data: { roles: ['BROKER', 'TENANT_ADMIN'] },
        loadComponent: () =>
          import('./features/bandeja-captaciones/bandeja-captaciones').then(
            (m) => m.BandejaCaptaciones,
          ),
      },
      {
        path: 'captaciones/reasignaciones',
        canActivate: [rolGuard],
        data: { roles: ['BROKER', 'TENANT_ADMIN'] },
        loadComponent: () =>
          import('./features/reasignaciones-captacion/reasignaciones-captacion').then(
            (m) => m.ReasignacionesCaptacion,
          ),
      },
      {
        path: 'captaciones/nueva',
        canActivate: [rolGuard],
        data: { roles: ['AGENTE'] },
        loadComponent: () =>
          import('./features/captacion-form/captacion-form').then((m) => m.CaptacionForm),
      },
      {
        path: 'captaciones/:codigo/editar',
        canActivate: [rolGuard],
        data: { roles: ['AGENTE'] },
        loadComponent: () =>
          import('./features/captacion-form/captacion-form').then((m) => m.CaptacionForm),
      },
      {
        path: 'captaciones/:codigo/revisar',
        // Fila 5: decidir un encargo es del broker.
        canActivate: [rolGuard],
        data: { roles: ['BROKER'] },
        loadComponent: () =>
          import('./features/captacion-review/captacion-review').then(
            (m) => m.CaptacionReview,
          ),
      },
      {
        path: 'captaciones',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/captaciones/captaciones').then((m) => m.Captaciones),
      },
      {
        // Resumen comercial de una captación, por su correlativo.
        // Sin `data.roles`: los tres roles la leen y lo que cambia es el
        // ALCANCE, que resuelve el backend (`GET /captaciones/codigo/{codigo}`
        // responde 403 fuera de él). Lo que sí es de AGENTE —escribir fotos—
        // se gatea dentro de la pantalla y en el backend, no por la ruta.
        path: 'captaciones/:codigo/ficha',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/ficha-propiedad/ficha-propiedad').then((m) => m.FichaPropiedad),
      },
      {
        path: 'captaciones/:codigo',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/captacion-detail/captacion-detail').then((m) => m.CaptacionDetail),
      },
      {
        // Después de `locales/nuevo`: el router resuelve por orden y `:id`
        // capturaría también el literal "nuevo".
        path: 'propiedades/:id',
        canActivate: [rolGuard],
        loadComponent: () =>
          import('./features/local-detail/local-detail').then((m) => m.LocalDetail),
      },
      {
        path: 'propiedades',
        canActivate: [rolGuard],
        loadComponent: () => import('./features/locales/locales').then((m) => m.Locales),
      },

      // ----------------------------------------------------------------
      // Compatibilidad: `/locales` fue la ruta hasta el 2026-08-18.
      //
      // El dominio se generalizó —siete tipos de propiedad, venta y alquiler—
      // y la URL tenía que seguirlo: mantener `/locales` como nombre principal
      // habría dejado el producto diciendo una cosa en el menú y otra en la
      // barra de direcciones. La canónica es `/propiedades`.
      //
      // Estos redirects existen para los enlaces ya guardados y para el
      // historial del navegador de quien lleva meses usando BROX. **Son
      // temporales**: código nuevo, navegación y pruebas apuntan a la
      // canónica, y estas cuatro filas se retiran cuando deje de haber
      // tráfico hacia ellas.
      // ----------------------------------------------------------------
      { path: 'locales/nuevo', redirectTo: 'propiedades/nueva', pathMatch: 'full' },
      { path: 'locales/:id/editar', redirectTo: 'propiedades/:id/editar', pathMatch: 'full' },
      { path: 'locales/:id', redirectTo: 'propiedades/:id', pathMatch: 'full' },
      { path: 'locales', redirectTo: 'propiedades', pathMatch: 'full' },
      {
        // Sin rolGuard a propósito: es el destino cuando el guard rechaza, y
        // protegerla provocaría un bucle de redirecciones.
        path: 'acceso-denegado',
        loadComponent: () =>
          import('./features/acceso-denegado/acceso-denegado').then((m) => m.AccesoDenegado),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
