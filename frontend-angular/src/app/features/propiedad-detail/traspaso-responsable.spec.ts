import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ApiError, PageResponse } from '../../core/api/api.types';
import {
  CandidatoResponsable,
  PropiedadesService,
  Responsabilidad,
} from '../../core/api/propiedades.service';
import { TraspasoResponsable } from './traspaso-responsable';

const ID_PROPIEDAD = 3259;

/** Quién responde hoy. El traspaso se ofrece porque lo dice el Core (C7). */
const RESPONSABILIDAD: Responsabilidad = {
  idResponsable: 30,
  nombre: 'Valeria Mora',
  puedeEditar: false,
  puedeTraspasar: true,
};

function candidato(parcial: Partial<CandidatoResponsable>): CandidatoResponsable {
  return { idAgente: 41, nombre: 'Marco Díaz', codigoAgente: 'AGE-0041', ...parcial };
}

function pagina(
  items: CandidatoResponsable[],
  totalRecords = items.length,
): PageResponse<CandidatoResponsable> {
  return { items, totalRecords, page: 1, pageSize: 50 };
}

/**
 * **A quién puedo traspasarla lo contesta el Core** (D-P0-7 + D-P0-12).
 *
 * Antes esta pantalla pedía `agentes.pagina(1, 100)` y depuraba en el cliente:
 * con más de cien agentes la lista llegaba **truncada sin decirlo**, y el
 * filtro local era la lista de condiciones de elegibilidad escrita por segunda
 * vez. Ahora pide `candidatos` y pinta lo que le devuelven.
 *
 * El `TestBed` **no provee `AgentesService` ni `HttpClient`**: si alguien
 * volviera a inyectar el catálogo de agentes, el componente ni siquiera se
 * construiría. Es el control positivo de que esa puerta está cerrada.
 */
describe('TraspasoResponsable', () => {
  let api: jasmine.SpyObj<PropiedadesService>;
  let fixture: ComponentFixture<TraspasoResponsable>;

  beforeEach(() => {
    api = jasmine.createSpyObj<PropiedadesService>('PropiedadesService', [
      'candidatos',
      'asignarResponsable',
    ]);
    api.candidatos.and.resolveTo(pagina([candidato({})]));
    api.asignarResponsable.and.resolveTo({
      id: 1,
      idPropiedad: ID_PROPIEDAD,
      idResponsableAnterior: 30,
      idResponsableNuevo: 41,
      idPersonaActor: 9,
      rolActor: 'BROKER',
      origen: 'TRASPASO',
      motivo: 'Reparto de la cartera del sur',
      fecha: '2026-09-01T10:00:00',
    });
    TestBed.configureTestingModule({
      imports: [TraspasoResponsable],
      providers: [{ provide: PropiedadesService, useValue: api }],
    });
  });

  // ------------------------------------------------------------------
  // Los destinos
  // ------------------------------------------------------------------

  it('al abrir pide los candidatos al Core, con el id de ESTA propiedad', async () => {
    await abrir();

    expect(api.candidatos).toHaveBeenCalledTimes(1);
    // Página y tamaño explícitos: la lista es del tenant y se pagina en el
    // servidor, que es lo que impide volver a truncarla en silencio.
    expect(api.candidatos.calls.mostRecent().args).toEqual([ID_PROPIEDAD, undefined, 1, 50]);
  });

  it('pinta EXACTAMENTE lo que devolvio el Core, sin depurar nada aqui', async () => {
    api.candidatos.and.resolveTo(
      pagina([
        candidato({ idAgente: 41, nombre: 'Marco Díaz', codigoAgente: 'AGE-0041' }),
        candidato({ idAgente: 52, nombre: 'Rosa Gómez', codigoAgente: 'AGE-0052', zonaAsignada: 'Sur' }),
        // El Core NO devolvería al responsable actual —un traspaso «de A a A» no
        // cuenta ningún hecho y responde 400—, así que aparece aquí a propósito:
        // es la única forma de comprobar que la pantalla no vuelve a filtrar por
        // su cuenta. Si alguien reintrodujera el `filter`, esto se pone rojo.
        candidato({ idAgente: 30, nombre: 'Valeria Mora', codigoAgente: 'AGE-0030' }),
      ]),
    );
    await abrir();

    expect(opciones()).toEqual([
      'Elige un agente…',
      'Marco Díaz · AGE-0041',
      'Rosa Gómez · AGE-0052 · Sur',
      'Valeria Mora · AGE-0030',
    ]);
  });

  it('al escribir vuelve a preguntar al Core con el texto', async () => {
    await abrir();
    api.candidatos.and.resolveTo(
      pagina([candidato({ idAgente: 52, nombre: 'Rosa Gómez', codigoAgente: 'AGE-0052' })]),
    );

    await escribir('gom');

    expect(api.candidatos).toHaveBeenCalledTimes(2);
    const [id, termino] = api.candidatos.calls.mostRecent().args;
    expect(id).toBe(ID_PROPIEDAD);
    expect(termino).toBe('gom');
    // Y la lista es la que devolvió el Core para ese texto, no una criba local.
    expect(opciones()).toEqual(['Elige un agente…', 'Rosa Gómez · AGE-0052']);
  });

  /**
   * La página no es la lista. Callarlo es exactamente lo que convertía un
   * catálogo truncado en «ese agente no existe».
   */
  it('cuando quedan candidatos fuera de la pagina lo dice', async () => {
    api.candidatos.and.resolveTo(pagina([candidato({})], 137));
    await abrir();

    expect(texto()).toContain('Hay más agentes: acota por nombre o código');
  });

  it('con la pagina completa no inventa que falten agentes', async () => {
    await abrir();

    expect(texto()).not.toContain('Hay más agentes');
  });

  /**
   * **El vacío no se explica.** El Core descartó por tenant, rol, cuenta,
   * relación organizacional, disponibilidad o supervisión, y no publica cuál:
   * la frase vieja —«no supervisas a ningún otro agente»— le inventaba una
   * causa a la lista vacía, y encima era falsa para el gobierno del tenant.
   */
  it('sin candidatos lo dice sin atribuirle una causa', async () => {
    api.candidatos.and.resolveTo(pagina([]));
    await abrir();

    expect(texto()).toContain('No hay agentes que puedan recibirla hoy');
    expect(texto()).not.toContain('no supervisas');
  });

  it('el rechazo del Core se muestra tal cual', async () => {
    api.candidatos.and.rejectWith(
      new ApiError(403, 'No puedes traspasar esta propiedad: no responde ante ninguno de tus agentes.'),
    );
    await abrir();

    expect(texto()).toContain('no responde ante ninguno de tus agentes');
  });

  // ------------------------------------------------------------------
  // El traspaso
  // ------------------------------------------------------------------

  it('traspasa al agente elegido, con el motivo que queda en el expediente', async () => {
    await abrir();
    elegir(1);
    escribirMotivo('Reparto de la cartera del sur');

    boton('Confirmar traspaso').click();
    await estable();

    expect(api.asignarResponsable).toHaveBeenCalledOnceWith(
      ID_PROPIEDAD,
      41,
      'Reparto de la cartera del sur',
      // El responsable que se estaba viendo (D-P0-9). El traspaso es «cambia
      // este por aquel»: sin el cuarto argumento, el Core no puede distinguir
      // este comando de otro que saliera de un estado distinto.
      30,
    );
    // Y el panel se cierra: la ficha se relee entera desde el padre.
    expect(boton('Traspasar')).toBeDefined();
  });

  /**
   * **FALTANTE también se declara** (D-P0-9). `null` no es «no lo mando»: es
   * «la vi sin responsable». Si esta pantalla omitiera el dato, el Core
   * respondería 400 — y con razón, porque un traspaso no puede partir de un
   * estado que nadie miró.
   */
  it('con la ficha FALTANTE declara que no habia responsable', async () => {
    await abrir({ ...RESPONSABILIDAD, idResponsable: null, nombre: null });
    elegir(1);
    escribirMotivo('Sale del inventario sin dueño');

    boton('Confirmar traspaso').click();
    await estable();

    expect(api.asignarResponsable).toHaveBeenCalledOnceWith(
      ID_PROPIEDAD,
      41,
      'Sale del inventario sin dueño',
      null,
    );
  });

  /**
   * **El 409 no se reintenta: se vuelve a mirar** (D-P0-9).
   *
   * El responsable cambió entre que se cargó la ficha y se pulsó el botón. Ni
   * hubo traspaso —así que `traspasado` no se emite— ni tiene sentido repetir
   * el mismo comando, que partiría de un estado que ya no existe.
   */
  it('ante un 409 ofrece recargar la ficha y NO dice que haya traspasado', async () => {
    api.asignarResponsable.and.rejectWith(
      new ApiError(
        409,
        'El responsable de esta propiedad cambio desde que lo miraste: hoy responde el agente 52.',
      ),
    );
    await abrir();
    const traspasos: number[] = [];
    const recargas: number[] = [];
    fixture.componentInstance.traspasado.subscribe(() => traspasos.push(1));
    fixture.componentInstance.recargar.subscribe(() => recargas.push(1));
    elegir(1);
    escribirMotivo('Reparto de la cartera del sur');

    boton('Confirmar traspaso').click();
    await estable();

    expect(texto()).toContain('hoy responde el agente 52');
    expect(traspasos).withContext('no hubo traspaso: no se anuncia uno').toEqual([]);

    boton('Volver a cargar la ficha').click();
    await estable();

    expect(recargas.length).withContext('y se pide releer la ficha').toBe(1);
    // El formulario se cierra: los datos con los que se rellenó son los que el
    // Core acaba de declarar caducados.
    expect(boton('Traspasar')).toBeDefined();
  });


  it('sin motivo suficiente no deja confirmar', async () => {
    await abrir();
    elegir(1);
    escribirMotivo('corto');

    boton('Confirmar traspaso').click();
    await estable();

    expect(api.asignarResponsable).not.toHaveBeenCalled();
  });

  it('el rechazo del POST se muestra tal cual y no se cierra el formulario', async () => {
    api.asignarResponsable.and.rejectWith(
      new ApiError(400, 'El agente ya responde por esta propiedad.'),
    );
    await abrir();
    elegir(1);
    escribirMotivo('Reparto de la cartera del sur');

    boton('Confirmar traspaso').click();
    await estable();

    expect(texto()).toContain('El agente ya responde por esta propiedad.');
    // Y un rechazo que NO es 409 no ofrece recargar: aquí el estado no cambió,
    // el comando estaba mal. Releer la ficha no arreglaría nada.
    expect(boton('Volver a cargar la ficha')).toBeUndefined();
  });

  // ------------------------------------------------------------------

  async function abrir(responsabilidad: Responsabilidad = RESPONSABILIDAD): Promise<void> {
    fixture = TestBed.createComponent(TraspasoResponsable);
    fixture.componentRef.setInput('idPropiedad', ID_PROPIEDAD);
    fixture.componentRef.setInput('responsabilidad', responsabilidad);
    fixture.detectChanges();
    // Con la propiedad FALTANTE el botón se llama distinto: lo dice el propio
    // componente, y pedirlo por su texto es como lo pulsa una persona.
    boton(responsabilidad.idResponsable == null ? 'Asignar responsable' : 'Traspasar').click();
    await estable();
  }

  /** Escribe en el buscador y espera al retardo de 250 ms del componente. */
  async function escribir(termino: string): Promise<void> {
    const campo = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    campo.value = termino;
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await new Promise((listo) => setTimeout(listo, 400));
    await estable();
  }

  /** Elige la opción n del `<select>`, por el camino que usa una persona. */
  function elegir(indice: number): void {
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    select.value = select.options[indice].value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function escribirMotivo(valor: string): void {
    const campo = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  async function estable(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function opciones(): string[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('option'),
    ).map((opcion) => opcion.textContent?.trim() ?? '');
  }

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function boton(etiqueta: string): HTMLButtonElement {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.trim() === etiqueta) as HTMLButtonElement;
  }
});
