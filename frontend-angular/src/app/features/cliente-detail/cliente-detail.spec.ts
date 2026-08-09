import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ApiError } from '../../core/api/api.types';
import { ClientesService } from '../../core/api/clientes.service';
import { Coincidencias, CoincidenciasService } from '../../core/api/coincidencias.service';
import {
  FichaCliente,
  FichaComercialService,
  SeccionFicha,
} from '../../core/api/ficha-comercial.service';
import { Requerimiento, RequerimientosService } from '../../core/api/requerimientos.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { ConstanciaAutorizacion } from '../../core/autorizacion';
import { ClienteDetail } from './cliente-detail';

/** Marcador de sección PENDIENTE del cable: total negativo, sin filas. */
function pendiente(section: string): SeccionFicha {
  return { section, totalRecords: -1, page: 0, pageSize: 8, items: [] };
}

function resuelta(section: string, total: number, items: SeccionFicha['items'] = []): SeccionFicha {
  return { section, totalRecords: total, page: 1, pageSize: 8, items };
}

const FICHA: FichaCliente = {
  cliente: {
    id: 5,
    tipoPersona: 'J',
    tipoDocumento: 'R',
    numeroDocumento: '20551234567',
    nombre: 'Retail Andino SAC',
    telefono: '014567890',
    correo: 'contacto@retailandino.test',
    rubroComercial: 'Retail',
    estado: 'A',
    consentimientoContacto: true,
    consentimientoUsoDato: false,
  },
  requerimientoActivo: true,
  ctaRuta: '/oportunidad-form?clienteId=5',
  sections: {
    requerimientos: resuelta('requerimientos', 1, [
      {
        id: '11',
        codigo: 'REQ-0011',
        proceso: 'Requerimiento',
        titulo: 'Local en Miraflores',
        subtitulo: 'Hasta PEN 9,000',
        estado: 'Activo',
        fecha: '2026-07-20',
        ruta: '',
      },
    ]),
    propiedades: pendiente('propiedades'),
    oportunidades: resuelta('oportunidades', 4),
    interacciones: pendiente('interacciones'),
    visitas: pendiente('visitas'),
    solicitudes: resuelta('solicitudes', 2),
    cierres: resuelta('cierres', 1),
    agentes: pendiente('agentes'),
  },
};

const REQUERIMIENTO: Requerimiento = {
  id: 11,
  idCliente: 5,
  rubro: 'Retail',
  tipoInmueble: 'LOCAL_COMERCIAL',
  rentaMin: 3000,
  rentaMax: 9000,
  moneda: 'PEN',
  metrajeMin: 60,
  estado: 'A',
  distritos: ['Miraflores', 'San Isidro'],
  observaciones: 'Prefiere esquina.',
};

const COINCIDENCIAS: Coincidencias = {
  origen: 'cliente',
  total: 1,
  page: 1,
  pageSize: 6,
  items: [
    {
      tipo: 'PROPIEDAD',
      // Ojo: en esta dirección el `id` es el de la CAPTACIÓN, no el del local.
      id: 77,
      codigo: 'CAP-0077',
      titulo: 'Av. Larco 123',
      subtitulo: 'Miraflores · PEN 8,000',
      distrito: 'Miraflores',
      renta: 'PEN 8,000',
      area: '80 m²',
      frente: '6 m',
      puntaje: 83,
      cumple: ['Distrito coincide', 'Renta dentro del rango'],
      noCumple: ['Frente menor al pedido'],
      captacionId: 77,
      proponerRuta: '/oportunidad-form?clienteId=5&captacionId=77',
    },
  ],
};

/** D-27: constancia por defecto — vigente y contra el aviso vigente. */
const AUTORIZACION: ConstanciaAutorizacion = {
  estado: 'VIGENTE',
  registradaEn: '2026-08-05T10:30:00',
  registradaPor: 'Valeria Mora',
  versionAviso: '1.0',
  versionVigente: '1.0',
};

describe('ClienteDetail', () => {
  let api: jasmine.SpyObj<FichaComercialService>;
  let clientes: jasmine.SpyObj<ClientesService>;
  let requerimientos: jasmine.SpyObj<RequerimientosService>;
  let coincidencias: jasmine.SpyObj<CoincidenciasService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    clientes = jasmine.createSpyObj<ClientesService>('ClientesService', ['autorizacion']);
    clientes.autorizacion.and.resolveTo(AUTORIZACION);
    api = jasmine.createSpyObj<FichaComercialService>('FichaComercialService', [
      'fichaCliente$',
      'seccionCliente$',
    ]);
    api.fichaCliente$.and.returnValue(of(FICHA));
    api.seccionCliente$.and.returnValue(of(resuelta('visitas', 0)));
    requerimientos = jasmine.createSpyObj<RequerimientosService>('RequerimientosService', [
      'porCliente',
      'crear',
      'actualizar',
      'cambiarEstado',
    ]);
    requerimientos.porCliente.and.resolveTo([REQUERIMIENTO]);
    requerimientos.crear.and.resolveTo(REQUERIMIENTO);
    requerimientos.actualizar.and.resolveTo(REQUERIMIENTO);
    requerimientos.cambiarEstado.and.resolveTo({ ...REQUERIMIENTO, estado: 'P' });
    coincidencias = jasmine.createSpyObj<CoincidenciasService>('CoincidenciasService', [
      'paraCliente$',
    ]);
    coincidencias.paraCliente$.and.returnValue(of(COINCIDENCIAS));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('la carga inicial trae la cabecera y NO pide ninguna sección', async () => {
    const fixture = await montar();

    expect(api.fichaCliente$).toHaveBeenCalledWith(5);
    expect(api.seccionCliente$).not.toHaveBeenCalled();
    const contenido = texto(fixture);
    expect(contenido).toContain('Retail Andino SAC');
    expect(contenido).toContain('RUC 20551234567');
    expect(contenido).toContain('Local en Miraflores');
  });

  /** El total negativo es "pendiente", no un dato: nunca se muestra. */
  it('las secciones pendientes no muestran contador', async () => {
    const fixture = await montar();

    const pestanas = botonesPestana(fixture);
    expect(pestanas).toContain('Oportunidades 4');
    expect(pestanas).toContain('Propiedades');
    expect(pestanas).not.toContain('Propiedades -1');
  });

  it('abrir una pestaña pendiente la pide una sola vez', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);

    acceso.abrir('visitas');
    fixture.detectChanges();
    expect(api.seccionCliente$).toHaveBeenCalledWith(5, 'visitas', 1);

    // Volver a ella no repite la llamada: ya está resuelta.
    acceso.abrir('requerimientos');
    acceso.abrir('visitas');
    expect(api.seccionCliente$).toHaveBeenCalledTimes(1);
  });

  it('una sección vacía resuelta tampoco se vuelve a pedir', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);

    // 'cierres' vino con total 1 y sin items: está resuelta, no pendiente.
    acceso.abrir('cierres');
    expect(api.seccionCliente$).not.toHaveBeenCalled();
  });

  it('el agente no ve la pestaña de agentes', async () => {
    const agente = await montar('AGENTE');
    expect(botonesPestana(agente).some((t) => t.startsWith('Agentes'))).toBeFalse();

    const broker = await montar('BROKER');
    expect(botonesPestana(broker).some((t) => t.startsWith('Agentes'))).toBeTrue();
  });

  /**
   * La ficha devuelve rutas del cable. Una que apunta a pantalla sin migrar no
   * se ofrece: un botón que no lleva a ninguna parte es peor que su ausencia.
   */
  it('solo ofrece "Ver detalle" cuando la ruta tiene pantalla migrada', async () => {
    const conRutas: FichaCliente = {
      ...FICHA,
      sections: {
        ...FICHA.sections,
        requerimientos: resuelta('requerimientos', 2, [
          { id: '1', titulo: 'Con pantalla', ruta: 'local-detail/9', estado: 'A' },
          { id: '2', titulo: 'Sin pantalla', ruta: 'solicitud-detail/7', estado: 'A' },
        ]),
      },
    };
    api.fichaCliente$.and.returnValue(of(conRutas));
    const fixture = await montar();

    expect(botonesTabla(fixture)).toEqual(['Ver detalle']);
    const acceso = acceder(fixture);
    acceso.navegar({ ruta: 'local-detail/9' });
    expect(router.navigate).toHaveBeenCalledWith(['/locales', '9']);
  });

  it('las métricas salen de los totales de las secciones', async () => {
    const fixture = await montar();

    const contenido = texto(fixture);
    expect(contenido).toContain('Oportunidades');
    expect(contenido).toContain('Solicitudes');
    expect(contenido).toContain('Cierres');
    expect(acceder(fixture).metricas().map((m) => m.valor)).toEqual([4, 2, 1]);
  });

  /**
   * Un cero afirmaría que no hay ninguna; la carga inicial es parcial, así que
   * "todavía no lo sé" tiene que verse distinto.
   */
  it('una métrica de sección no pedida se muestra como guion, no como cero', async () => {
    api.fichaCliente$.and.returnValue(
      of({
        ...FICHA,
        sections: { ...FICHA.sections, oportunidades: pendiente('oportunidades') },
      }),
    );
    const fixture = await montar();

    expect(acceder(fixture).metricas()[0].valor).toBeNull();
    expect(texto(fixture)).not.toContain('Oportunidades 0');
  });

  it('avisa de la búsqueda activa y ya ofrece presentar cartera', async () => {
    const fixture = await montar();

    expect(texto(fixture)).toContain('búsqueda activa');
    // La CTA se destrabó al migrar OportunidadForm: antes no se ofrecía porque
    // no había pantalla a la que llevar.
    expect(texto(fixture)).toContain('Presentar propiedades de cartera');
  });

  /**
   * Sin requerimiento activo el matching no tiene contra qué comparar, así que
   * la CTA no se ofrece en vez de llevar a una lista siempre vacía.
   */
  it('sin búsqueda activa no ofrece presentar cartera', async () => {
    api.fichaCliente$.and.returnValue(of({ ...FICHA, requerimientoActivo: false }));
    const fixture = await montar();

    expect(texto(fixture)).not.toContain('Presentar propiedades de cartera');
  });

  it('las coincidencias se piden bajo demanda y muestran sus motivos', async () => {
    const fixture = await montar();
    expect(coincidencias.paraCliente$).not.toHaveBeenCalled();

    await acceder(fixture).verCoincidencias();
    fixture.detectChanges();

    expect(coincidencias.paraCliente$).toHaveBeenCalledWith(5, 1, 6);
    const contenido = texto(fixture);
    expect(contenido).toContain('83%');
    expect(contenido).toContain('Distrito coincide');
    expect(contenido).toContain('Frente menor al pedido');
  });

  /**
   * En `cliente → propiedades` el `id` del sobre es el de la CAPTACIÓN, que es
   * justo lo que necesita el alta de oportunidad.
   */
  it('proponer abre el alta con cliente y captación fijados', async () => {
    const fixture = await montar();
    await acceder(fixture).verCoincidencias();

    acceder(fixture).proponer(COINCIDENCIAS.items[0]);

    expect(router.navigate).toHaveBeenCalledWith(['/oportunidades/nueva'], {
      queryParams: { cliente: 5, captacion: 77 },
    });
  });

  it('los requerimientos se piden al abrir su pestaña, una sola vez', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);

    // La pestaña activa al entrar ES requerimientos, así que ya se pidieron.
    expect(requerimientos.porCliente).toHaveBeenCalledOnceWith(5);

    acceso.abrir('visitas');
    acceso.abrir('requerimientos');
    expect(requerimientos.porCliente).toHaveBeenCalledTimes(1);
  });

  it('el editor manda los distritos por nombre y guarda como alta', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);

    acceso.nuevoRequerimiento();
    acceso.formulario.patchValue({
      rubro: 'Cafetería',
      distritos: ' Miraflores , San Isidro ,, ',
    });
    await acceso.guardarRequerimiento();

    expect(requerimientos.crear).toHaveBeenCalled();
    const cuerpo = requerimientos.crear.calls.mostRecent().args[0];
    expect(cuerpo.idCliente).toBe(5);
    expect(cuerpo.rubro).toBe('Cafetería');
    expect(cuerpo.distritos).toEqual(['Miraflores', 'San Isidro']);
  });

  /**
   * Ningún límite es obligatorio: un requerimiento sin renta ni metraje es
   * válido y el matching los cuenta como NO_APLICA. Exigirlos sería una regla
   * inventada por la pantalla.
   */
  it('guarda un requerimiento sin renta ni metraje', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);

    acceso.nuevoRequerimiento();
    acceso.formulario.patchValue({ rubro: 'Retail' });
    await acceso.guardarRequerimiento();

    const cuerpo = requerimientos.crear.calls.mostRecent().args[0];
    expect(cuerpo.rentaMin).toBeUndefined();
    expect(cuerpo.metrajeMax).toBeUndefined();
  });

  /** Pausar/cerrar/reactivar tiene endpoint propio: no es parte del PUT. */
  it('pausar usa el endpoint de estado, no la edición completa', async () => {
    const fixture = await montar();

    await acceder(fixture).cambiarEstadoRequerimiento(REQUERIMIENTO, 'P');

    expect(requerimientos.cambiarEstado).toHaveBeenCalledWith(11, 'P');
    expect(requerimientos.actualizar).not.toHaveBeenCalled();
  });

  it('quien no es agente no ve las acciones del requerimiento', async () => {
    const broker = await montar('BROKER');

    const contenido = texto(broker);
    expect(contenido).toContain('Búsqueda declarada');
    expect(contenido).not.toContain('Nuevo requerimiento');
    expect(contenido).not.toContain('Pausar');
  });

  it('un error de sección no tumba la ficha y se puede reintentar', async () => {
    api.seccionCliente$.and.returnValue(throwError(() => new ApiError(500, 'Falló la sección.')));
    const fixture = await montar();
    const acceso = acceder(fixture);

    acceso.abrir('visitas');
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Falló la sección.');
    // La cabecera sigue en pie: el fallo es del bloque, no de la ficha.
    expect(texto(fixture)).toContain('Retail Andino SAC');
  });

  it('un error de la ficha completa sí es fatal y se puede reintentar', async () => {
    api.fichaCliente$.and.returnValue(throwError(() => new ApiError(404, 'Cliente no encontrado.')));
    const fixture = await montar();

    expect(texto(fixture)).toContain('Cliente no encontrado.');
  });

  // ------------------------------------------------------------------
  // D-27: constancia de la autorización en la ficha
  // ------------------------------------------------------------------

  it('la ficha muestra la autorización con fecha y con quién la registró', async () => {
    const fixture = await montar();
    const contenido = texto(fixture);

    expect(clientes.autorizacion).toHaveBeenCalledWith(5);
    expect(contenido).toContain('Autorización registrada');
    expect(contenido).toContain('Valeria Mora');
    // La fecha va formateada en es-PE, no en ISO crudo.
    expect(contenido).toContain('05 ago');
  });

  it('NO muestra el canal: lo sella el backend y siempre vale lo mismo', async () => {
    const fixture = await montar();

    expect(texto(fixture)).not.toContain('Canal');
  });

  it('la versión del aviso se calla cuando es la vigente, y aparece cuando no', async () => {
    // Coinciden: el número no aporta nada operativo y sería ruido en la ficha.
    let fixture = await montar();
    expect(texto(fixture)).not.toContain('Aviso citado');

    // Difieren: entonces sí dice algo — autorizó contra un aviso anterior.
    clientes.autorizacion.and.resolveTo({
      ...AUTORIZACION,
      estado: 'CADUCADA',
      versionAviso: '1.0',
      versionVigente: '2.0',
    });
    fixture = await montar();
    const contenido = texto(fixture);
    expect(contenido).toContain('Aviso citado');
    expect(contenido).toContain('Versión 1.0');
    expect(contenido).toContain('Autorización caducada');
  });

  it('una persona anterior a D-27 dice SIN registro, no "no autorizó"', async () => {
    clientes.autorizacion.and.resolveTo({ estado: 'SIN_REGISTRO', versionVigente: '1.0' });
    const fixture = await montar();
    const contenido = texto(fixture);

    // Son cosas distintas: nunca se le pidió, no es que la negara.
    expect(contenido).toContain('Sin registro de autorización');
    expect(contenido).toContain('antes de que la autorización se pidiera en el alta');
  });

  it('si la autorización falla, la ficha comercial se sigue viendo', async () => {
    clientes.autorizacion.and.rejectWith(new ApiError(500, 'Servicio no disponible.'));
    const fixture = await montar();
    const contenido = texto(fixture);

    // Es un dato de cumplimiento, no la historia comercial: no puede tumbarla.
    expect(contenido).toContain('Retail Andino SAC');
    expect(contenido).toContain('Servicio no disponible.');
    // Y sobre todo, no se inventa un "sin autorización" que no consta.
    expect(contenido).not.toContain('Sin registro de autorización');
  });

  async function montar(rol: RolSesion = 'AGENTE'): Promise<ComponentFixture<ClienteDetail>> {
    TestBed.resetTestingModule();
    const sesion = signal<Sesion | null>({
      token: 't',
      expiraEnSegundos: 3600,
      rol,
      idUsuario: 1,
      idDominio: 30,
      nombre: 'Prueba',
      usuario: 'prueba',
      expiraEn: '2099-01-01T00:00:00',
    });
    TestBed.configureTestingModule({
      imports: [ClienteDetail],
      providers: [
        { provide: FichaComercialService, useValue: api },
        { provide: ClientesService, useValue: clientes },
        { provide: RequerimientosService, useValue: requerimientos },
        { provide: CoincidenciasService, useValue: coincidencias },
        { provide: AuthService, useValue: { sesion } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '5' }) } },
        },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(ClienteDetail);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

interface AccesoFicha {
  abrir(seccion: string): void;
  navegar(fila: { ruta?: string }): void;
  metricas(): { etiqueta: string; valor: number | null }[];
  verCoincidencias(): Promise<void>;
  proponer(coincidencia: { id: number; captacionId?: number }): void;
  nuevoRequerimiento(): void;
  editarRequerimiento(requerimiento: Requerimiento): void;
  guardarRequerimiento(): Promise<void>;
  cambiarEstadoRequerimiento(requerimiento: Requerimiento, estado: string): Promise<void>;
  formulario: { patchValue(valores: Record<string, unknown>): void };
}

function acceder(fixture: ComponentFixture<ClienteDetail>): AccesoFicha {
  return fixture.componentInstance as unknown as AccesoFicha;
}

function texto(fixture: ComponentFixture<ClienteDetail>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}

function botonesPestana(fixture: ComponentFixture<ClienteDetail>): string[] {
  return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.cl-pestanas button')).map(
    (b) => (b.textContent ?? '').replace(/\s+/g, ' ').trim(),
  );
}

function botonesTabla(fixture: ComponentFixture<ClienteDetail>): string[] {
  return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('tbody button')).map(
    (b) => b.textContent?.trim() ?? '',
  );
}
