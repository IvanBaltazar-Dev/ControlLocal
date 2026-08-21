import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { IndicadoresResumen, IndicadoresService } from '../../core/api/indicadores.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { Indicadores } from './indicadores';

const RESUMEN: IndicadoresResumen = {
  ambito: 'Reportes de equipo',
  captacionesPorRevisar: 1,
  solicitudesPorEvaluar: 1,
  captacionesTotales: 4,
  captacionesActivas: 3,
  captacionesObservadas: 0,
  oportunidadesActivas: 2,
  interacciones: 6,
  visitas: 3,
  cierres: 2,
  cierresCohorte: 1,
  conversionPropia: 25,
  agentesActivos: 2,
  brokersActivos: 1,
  propiedadesEquipo: 3,
  mesesEtiquetas: ['Jun 26', 'Jul 26'],
  cierresPorMes: [1, 1],
  conversionPorPeriodo: [50, 0],
  captacionesPorPeriodo: [2, 2],
  etapas: [
    { nombre: 'Captacion activa', valor: 1 },
    { nombre: 'Clientes interesados', valor: 1 },
    { nombre: 'Con solicitud', valor: 0 },
    { nombre: 'En evaluacion', valor: 0 },
    { nombre: 'Alquilada', valor: 2 },
  ],
  captacionesSalud: [{ nombre: 'Activas', valor: 3 }],
  embudo: [
    { etapa: 'Oportunidades activas', valor: 0, porcentaje: 100 },
    { etapa: 'Con visita realizada', valor: 0, porcentaje: 0 },
  ],
  desempeno: [{ nombre: 'Ana Perez', captaciones: 2, cierres: 1, conversion: 50 }],
  operativo: {
    recontactosVencidos: 1,
    recontactosAlDia: 2,
    diasPromedioSinSeguimiento: 8,
    visitasPendientes: 1,
    solicitudesSinCierre: 0,
    conversionProspeccionCaptacion: 40,
  },
  // Esta pantalla no las pinta todavía; viajan en la respuesta desde E1.
  senales: [
    {
      concepto: 'RECONTACTO_VENCIDO',
      valor: 1,
      nivelAtencion: 'ALTO',
      requiereAtencion: true,
      prioridad: 2,
    },
  ],
  pendientesDeAtencion: 1,
  /**
   * El bloque de E2.6. Los cuatro KPI van con el rótulo del backend —**la
   * pantalla no los escribe**— y con casos distintos a propósito: uno en ritmo,
   * uno en atención, uno fuera y uno sin meta, que es la combinación que rompe
   * si alguien vuelve a suponer que todos traen meta.
   */
  rendimiento: {
    periodo: {
      codigo: '2026-08',
      desde: '2026-08-01',
      hasta: '2026-08-31',
      diasTranscurridos: 19,
      diasTotales: 31,
      enCurso: true,
    },
    generadoEn: '2026-08-19T12:00:00Z',
    kpis: [
      {
        codigo: 'C',
        rotulo: 'Propietarios contactados',
        hecho: 'prospeccion con fecha de contacto dentro del mes',
        actual: 19,
        metaPeriodo: 24,
        metaEsperadaAHoy: 15,
        porcentajeMeta: 79,
        faltante: 5,
        proyeccionCierre: 31,
        porcentajeProyectado: 129,
        estadoRitmo: 'EN_RITMO',
        motivoSinBase: 'NINGUNO',
        sinCadencia: false,
        variacionComparable: 4,
      },
      {
        codigo: 'P',
        rotulo: 'Propiedades captadas',
        hecho: 'transicion de captacion a ACTIVA dentro del mes',
        actual: 9,
        metaPeriodo: 15,
        metaEsperadaAHoy: 9,
        porcentajeMeta: 60,
        faltante: 6,
        proyeccionCierre: 14,
        porcentajeProyectado: 98,
        estadoRitmo: 'ATENCION',
        motivoSinBase: 'NINGUNO',
        sinCadencia: false,
        variacionComparable: -1,
      },
      {
        codigo: 'S',
        rotulo: 'Solicitudes ingresadas',
        hecho: 'solicitud registrada dentro del mes',
        actual: 2,
        metaPeriodo: 8,
        metaEsperadaAHoy: 5,
        porcentajeMeta: 25,
        faltante: 6,
        proyeccionCierre: 3,
        porcentajeProyectado: 41,
        estadoRitmo: 'FUERA_DE_RITMO',
        motivoSinBase: 'NINGUNO',
        sinCadencia: false,
        variacionComparable: 0,
      },
      {
        // Sin meta: los seis derivados NO viajan. El backend omite los nulos,
        // así que llegan como `undefined` y no como `null`.
        codigo: 'F',
        rotulo: 'Contratos firmados',
        hecho: 'contrato con fecha de cierre dentro del mes',
        actual: 4,
        estadoRitmo: 'SIN_BASE',
        motivoSinBase: 'SIN_META',
        sinCadencia: false,
      },
    ],
    puedeCerrarse: {
      operaciones: 2,
      importe: 12000,
      moneda: 'PEN',
      variasMonedas: false,
      esperanDecision: 1,
    },
    pulso: null,
  },
};

function sesion(rol: RolSesion): Sesion {
  return {
    token: 't',
    expiraEnSegundos: 3600,
    rol,
    idUsuario: 1,
    idDominio: 20,
    nombre: 'Ricardo Salas',
    usuario: 'rsalas',
    expiraEn: '2026-12-31T00:00:00',
  };
}

describe('Indicadores', () => {
  let api: jasmine.SpyObj<IndicadoresService>;
  let fixture: ComponentFixture<Indicadores>;

  function raiz(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function montar(rol: RolSesion = 'BROKER', periodo?: string): Promise<Indicadores> {
    await TestBed.configureTestingModule({
      imports: [Indicadores],
      providers: [
        { provide: IndicadoresService, useValue: api },
        { provide: AuthService, useValue: { sesion: signal(sesion(rol)) } },
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap(periodo ? { periodo } : {}) },
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Indicadores);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  /** El mismo resumen con un trozo cambiado, para no repetir el fixture entero. */
  function conResumen(parcial: Partial<IndicadoresResumen>): void {
    api.resumen.and.resolveTo({ ...RESUMEN, ...parcial });
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    api = jasmine.createSpyObj<IndicadoresService>('IndicadoresService', [
      'resumen',
      'metas',
      'propuestasDeMeta',
    ]);
    api.resumen.and.resolveTo(RESUMEN);
    api.metas.and.resolveTo([]);
    api.propuestasDeMeta.and.resolveTo([]);
  });

  // ==================================================================
  // Carga y periodo
  // ==================================================================

  it('pide el periodo por defecto y respeta el de la URL', async () => {
    await montar('BROKER');
    expect(api.resumen).toHaveBeenCalledOnceWith('6m');

    TestBed.resetTestingModule();
    api.resumen.calls.reset();
    await montar('BROKER', '3m');
    expect(api.resumen).toHaveBeenCalledOnceWith('3m');
  });

  it('descarta un periodo inventado en vez de mandarlo al backend', async () => {
    await montar('BROKER', 'trimestre');

    expect(api.resumen).toHaveBeenCalledOnceWith('6m');
  });

  it('un fallo deja la pantalla en error y sin datos a medias', async () => {
    api.resumen.and.rejectWith(new Error('boom'));
    const pantalla = await montar();

    expect(pantalla['datos']()).toBeNull();
    expect(pantalla['error']()).toBeTruthy();
  });

  it('el ambito lo rotula el backend, no la pantalla', async () => {
    await montar('BROKER');

    expect(raiz().querySelector('.cab .sub')?.textContent).toContain('Reportes de equipo');
  });

  // ==================================================================
  // LOS CUATRO KPI Y SU ESFERA
  // ==================================================================

  /**
   * Los mismos cuatro nombres que el pie del Inicio, **letra por letra**. Los
   * dos leen el rótulo del cable, así que no pueden divergir: si esta prueba y
   * la del pie dejaran de coincidir, sería porque alguien escribió el texto a
   * mano en una de las dos.
   */
  it('los cuatro KPI canonicos salen del cable, en el orden del embudo', async () => {
    await montar('AGENTE');
    const nombres = Array.from(raiz().querySelectorAll('.tira .kpi .rot')).map((n) =>
      n.textContent?.trim(),
    );

    expect(nombres).toEqual([
      'Propietarios contactados',
      'Propiedades captadas',
      'Solicitudes ingresadas',
      'Contratos firmados',
    ]);
  });

  it('no añade un quinto indicador', async () => {
    await montar('AGENTE');

    expect(raiz().querySelectorAll('.tira .kpi').length).toBe(4);
  });

  /**
   * El estado lo decide el dominio. La pantalla lo traduce a un atributo y el
   * color sale de ahí; nunca al revés.
   */
  it('el estado de ritmo viaja al DOM sin traducir', async () => {
    await montar('AGENTE');
    const estados = Array.from(raiz().querySelectorAll('.tira .kpi')).map((k) =>
      k.getAttribute('data-ritmo'),
    );

    expect(estados).toEqual(['EN_RITMO', 'ATENCION', 'FUERA_DE_RITMO', 'SIN_BASE']);
  });

  /**
   * La esfera contesta cinco cosas: cuánto llevas (arco y manija), hasta dónde
   * llegarás (la prolongación), cuál es la meta (la marca del final) y dónde
   * tocaría estar hoy (la marca de dentro).
   */
  it('cada esfera responde las cinco preguntas', async () => {
    await montar('AGENTE');
    const primera = raiz().querySelector('cl-esfera');

    expect(primera?.querySelector('.arco')).not.toBeNull();
    expect(primera?.querySelector('.proy')).not.toBeNull();
    expect(primera?.querySelector('.manija')).not.toBeNull();
    expect(primera?.querySelector('.mk-meta')).not.toBeNull();
    expect(primera?.querySelector('.mk-hoy')).not.toBeNull();
  });

  /**
   * **Sin meta no hay recorrido que dibujar.** Una esfera vacía dice «aquí no
   * hay nada que medir» mejor que un cero, que diría que el objetivo era cero.
   */
  it('un KPI sin meta deja la esfera sin arco ni marcas', async () => {
    await montar('AGENTE');
    const esferas = raiz().querySelectorAll('cl-esfera');
    const sinMeta = esferas[esferas.length - 1];

    expect(sinMeta.querySelector('.pista')).not.toBeNull();
    expect(sinMeta.querySelector('.arco')).toBeNull();
    expect(sinMeta.querySelector('.manija')).toBeNull();
    expect(sinMeta.querySelector('.mk-meta')).toBeNull();
  });

  /** Primero la meta y lo que falta; el ritmo va debajo, en su propia línea. */
  it('cada KPI dice la meta y lo que falta antes que el ritmo', async () => {
    await montar('AGENTE');
    const primero = raiz().querySelector('.tira .kpi');

    expect(primero?.querySelector('.avance')?.textContent?.trim()).toBe('Meta 24 · te faltan 5');
    expect(primero?.querySelector('.esperado')?.textContent?.trim()).toBe(
      'Hoy deberías ir por 15',
    );
  });

  /**
   * **Instrucción 4 de D-E2-2**: al broker no se le atribuye una producción
   * personal que él no hace. Los números son los mismos; la voz, no.
   */
  it('al broker se le habla del equipo, no de lo que deberia llevar el', async () => {
    await montar('BROKER');
    const texto = raiz().querySelector('.tira')?.textContent ?? '';

    expect(texto).not.toContain('te faltan');
    expect(texto).not.toContain('Hoy deberías ir por');
    expect(texto).toContain('Hoy el equipo debería ir por');
  });

  /**
   * La marca de adelanto contesta «¿y esto es bueno?» sin que el lector reste,
   * y **convive con «te faltan 5»**: una habla del calendario y la otra del
   * objetivo.
   */
  it('la marca de adelanto sale de la resta contra donde tocaba estar hoy', async () => {
    await montar('AGENTE');
    const marcas = Array.from(raiz().querySelectorAll('.tira .kpi .marca')).map((m) => ({
      clase: m.className,
      texto: m.textContent?.trim(),
    }));

    // 19 contra 15 esperados, 9 contra 9, 2 contra 5. El cuarto no tiene meta.
    expect(marcas.length).toBe(3);
    expect(marcas[0]).toEqual({ clase: 'marca mas', texto: '+4' });
    expect(marcas[1]).toEqual({ clase: 'marca eq', texto: 'al día' });
    expect(marcas[2]).toEqual({ clase: 'marca menos', texto: '−3' });
  });

  /** Qué fila cuenta exactamente: es lo que separa un indicador de una impresión. */
  it('cada KPI dice que hecho cuenta', async () => {
    await montar('AGENTE');

    expect(raiz().querySelector('.tira .kpi .sub')?.textContent).toContain(
      'prospeccion con fecha de contacto dentro del mes',
    );
  });

  /** El calendario se dice UNA vez para los cuatro, y lo cuenta el backend. */
  it('la cabecera ensena el mes de calendario y su corte', async () => {
    await montar('AGENTE');
    const cabecera = raiz().querySelector('.t-cab .der')?.textContent ?? '';

    expect(cabecera).toContain('agosto de 2026');
    expect(cabecera).toContain('día 19 de 31');
    expect(cabecera).toContain('quedan 12 días');
  });

  // ==================================================================
  // PULSO DEL EQUIPO
  // ==================================================================

  /** Su pulso sería su propio ritmo contado otra vez (instrucción 14). */
  it('el agente no ve pulso de equipo', async () => {
    await montar('AGENTE');

    expect(raiz().querySelector('.pulso')).toBeNull();
  });

  it('el broker ve el pulso una sola vez y sin nombres', async () => {
    conResumen({
      rendimiento: {
        ...RESUMEN.rendimiento,
        pulso: { enRitmo: 6, atencion: 1, fueraDeRitmo: 1, sinBase: 0, agentes: 8 },
      },
    });
    await montar('BROKER');
    const pulsos = raiz().querySelectorAll('.pulso');

    expect(pulsos.length).toBe(1);
    expect(pulsos[0].textContent).toContain('6 en ritmo');
    expect(pulsos[0].textContent).toContain('1 fuera de ritmo');
    expect(pulsos[0].textContent).toContain('sobre 8 agentes');
    // Los grupos vacíos no ocupan sitio: «0 sin meta fijada» no es un dato.
    expect(pulsos[0].textContent).not.toContain('0 sin meta');
  });

  /**
   * **La pantalla no lleva tabla de posiciones.** Un agente con cartera recién
   * asignada y otro con expedientes maduros no compiten, y ordenarlos por
   * cierres es exactamente lo que prohíbe la instrucción 13 de D-E2-2.
   */
  it('no hay tabla de posiciones del equipo', async () => {
    await montar('BROKER');
    const texto = raiz().textContent ?? '';

    expect(texto).not.toContain('Desempeño por agente');
    expect(texto).not.toContain('Ana Perez');
  });

  // ==================================================================
  // LA LECTURA
  // ==================================================================

  /**
   * **La clasificación llega hecha** (R-07, E1): la pantalla agrupa por el
   * nivel que manda el dominio y no decide cuándo algo pasa a ser grave.
   */
  it('agrupa las senales por el nivel que manda el dominio', async () => {
    conResumen({
      senales: [
        {
          concepto: 'RECONTACTO_VENCIDO',
          valor: 3,
          nivelAtencion: 'ALTO',
          requiereAtencion: true,
          prioridad: 1,
        },
        {
          concepto: 'VISITA_PENDIENTE',
          valor: 2,
          nivelAtencion: 'INFORMATIVO',
          requiereAtencion: false,
          prioridad: 6,
        },
        {
          concepto: 'CAPTACION_POR_REVISAR',
          valor: 0,
          nivelAtencion: 'SIN_PENDIENTES',
          requiereAtencion: false,
          prioridad: 3,
        },
      ],
    });
    await montar('BROKER');
    const columnas = Array.from(raiz().querySelectorAll('.lec .lec-cab span:last-child')).map(
      (c) => c.textContent?.trim(),
    );

    // «Vigilar» no sale: ninguna señal llegó en nivel MEDIO, y un rótulo sobre
    // el vacío no informa.
    expect(columnas).toEqual(['Atender ya', 'Al día', 'Para mirar']);
  });

  /**
   * `DEMORA_DE_SEGUIMIENTO` vale **días**, no cosas. Sin decirlo, la columna
   * sumaría peras con manzanas — el mismo error que hacía decir «17 cosas»
   * donde había 8 pendientes y 9 días de atraso.
   */
  it('la senal que se mide en dias lo dice', async () => {
    conResumen({
      senales: [
        {
          concepto: 'DEMORA_DE_SEGUIMIENTO',
          valor: 9,
          nivelAtencion: 'MEDIO',
          requiereAtencion: true,
          prioridad: 5,
        },
      ],
    });
    await montar('BROKER');
    const senal = raiz().querySelector('.ind');

    expect(senal?.querySelector('.n')?.textContent?.trim()).toBe('Demora de seguimiento');
    expect(senal?.querySelector('.c small')?.textContent?.trim()).toBe('días');
  });

  // ==================================================================
  // EN JUEGO
  // ==================================================================

  /** El importe conserva su moneda: no se convierte para redondear. */
  it('la cifra en juego conserva su moneda', async () => {
    await montar('BROKER');

    expect(raiz().querySelector('.juego .v')?.textContent).toContain('PEN');
  });

  /**
   * Cero operaciones **no se esconde**: es información, y esconderla dejaría el
   * hueco a que alguien lo leyera como un fallo de carga.
   */
  it('sin operaciones que cerrar lo dice, en vez de callarse', async () => {
    conResumen({
      rendimiento: {
        ...RESUMEN.rendimiento,
        puedeCerrarse: {
          operaciones: 0,
          importe: 0,
          moneda: null,
          variasMonedas: false,
          esperanDecision: 0,
        },
      },
    });
    await montar('BROKER');

    expect(raiz().querySelector('.juego')?.textContent).toContain(
      'Ninguna operación puede cerrarse este mes',
    );
    expect(raiz().querySelector('.juego .v')).toBeNull();
  });

  /** La palanca es del broker: al agente no se le ofrece una decisión que no es suya. */
  it('solo quien decide ve la palanca de las que esperan decision', async () => {
    await montar('BROKER');
    expect(raiz().querySelector('.palanca')?.getAttribute('href')).toBe('/solicitudes/revisar');

    TestBed.resetTestingModule();
    await montar('AGENTE');
    expect(raiz().querySelector('.palanca')).toBeNull();
  });

  // ==================================================================
  // CARTERA
  // ==================================================================

  /**
   * **El riel es la cartera entera, no la fila más grande.** Normalizado contra
   * el máximo, la etapa mayor salía a barra llena y se leía «toda mi cartera
   * está en Activa» cuando eran 5 de 13.
   */
  it('reparte las etapas sobre su propio total, no sobre la fila mayor', async () => {
    const pantalla = await montar();

    expect(pantalla['etapas']().map((e) => e.ancho)).toEqual([25, 25, 0, 0, 50]);
  });

  it('la salud tiñe lo que reclama una mirada y deja el resto en tinta', async () => {
    conResumen({
      captacionesSalud: [
        { nombre: 'Activas', valor: 3 },
        { nombre: 'Por revisar', valor: 2 },
      ],
    });
    await montar();
    const fichas = raiz().querySelectorAll('.sf');

    expect(fichas[0].classList.contains('ojo')).toBeFalse();
    expect(fichas[1].classList.contains('ojo')).toBeTrue();
  });

  // ==================================================================
  // EMBUDO
  // ==================================================================

  /**
   * El cable trae NIVELES y el embudo se lee en SALTOS: de las 24 que entraron,
   * 16 llegaron a visita. Así el porcentaje dice lo que retiene ese tramo, en
   * vez de repetir la proporción contra la primera fila.
   */
  it('el embudo se lee en saltos, con su origen y su destino', async () => {
    conResumen({
      embudo: [
        { etapa: 'Oportunidades activas', valor: 24, porcentaje: 100 },
        { etapa: 'Con visita realizada', valor: 16, porcentaje: 67 },
        { etapa: 'Con solicitud creada', valor: 4, porcentaje: 17 },
      ],
    });
    await montar();
    const saltos = raiz().querySelectorAll('.salto');

    expect(saltos.length).toBe(2);
    expect(saltos[0].querySelector('.par')?.textContent?.trim()).toBe(
      'Oportunidades activas → con visita realizada',
    );
    expect(saltos[0].querySelector('.de')?.textContent?.trim()).toBe('24');
    expect(saltos[0].querySelector('.a')?.textContent?.trim()).toBe('16');
    expect(saltos[0].querySelector('.pc')?.textContent?.trim()).toBe('67 %');
    expect(saltos[0].querySelector('.ns')?.textContent?.trim()).toBe('8 se quedaron');
  });

  /**
   * **Un tramo sin casos no tiene tasa.** Con 0 de origen el porcentaje era
   * 0/0 = NaN y se colaba en pantalla. No se calcula: se dice.
   */
  it('un salto sin casos no inventa una tasa', async () => {
    await montar();
    const salto = raiz().querySelector('.salto');

    expect(salto?.classList.contains('magro')).toBeTrue();
    expect(salto?.querySelector('.pc')?.textContent?.trim()).toBe('—');
    expect(salto?.querySelector('.ns')?.textContent?.trim()).toBe('sin casos');
  });

  // ==================================================================
  // EVOLUCIÓN
  // ==================================================================

  /**
   * Una serie, no cuatro. Cuatro líneas simultáneas son ruido (D-E2-2 §10), y
   * mezclar conteos con un porcentaje obligaría a dos escalas en el mismo marco.
   */
  it('la evolucion ensena una sola metrica por vez', async () => {
    const pantalla = await montar();

    expect(pantalla['serie']().length).toBe(1);
    expect(pantalla['serie']()[0].nombre).toBe('Captaciones');

    pantalla['cambiarMetrica']('conversion');
    expect(pantalla['serie']()[0].nombre).toBe('Conversión');
    expect(pantalla['serie']()[0].valores).toEqual(RESUMEN.conversionPorPeriodo);
  });

  it('descarta una metrica que no existe', async () => {
    const pantalla = await montar();

    pantalla['cambiarMetrica']('inventada');

    expect(pantalla['metrica']()).toBe('captaciones');
  });

  /**
   * La meta se dice con palabras y no se dibuja como línea sobre los seis
   * tramos: el cable no publica metas históricas, y una línea de lado a lado
   * afirmaría que ese objetivo rigió los seis periodos.
   */
  it('la meta de la serie es la del mes en curso, y solo si existe', async () => {
    const pantalla = await montar();

    // Captaciones ↔ «Propiedades captadas», que en el fixture tiene meta 15.
    expect(pantalla['metaDeLaSerie']()).toBe(15);

    pantalla['cambiarMetrica']('conversion');
    expect(pantalla['metaDeLaSerie']()).toBeNull();
  });

  // ==================================================================
  // METAS
  // ==================================================================

  /** La meta vive donde se mide el rendimiento, no en un módulo aparte. */
  it('la gestion de metas vive en esta pantalla', async () => {
    await montar('BROKER');

    expect(raiz().querySelector('#metas')).not.toBeNull();
    expect(raiz().querySelector('#metas .tabla-metas')).not.toBeNull();
  });

  /** El agente PROPONE; si pudiera fijarla, el indicador sería manipulable. */
  it('el agente no recibe la tabla con la que se fijan metas', async () => {
    await montar('AGENTE');

    expect(raiz().querySelector('#metas .tabla-metas')).toBeNull();
    expect(raiz().querySelector('#metas .mis-objetivos')).not.toBeNull();
  });
});
