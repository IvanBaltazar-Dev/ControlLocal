import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { CapturaService, PreguntaCaptura } from '../../core/api/captura.service';
import {
  FilaPropiedad,
  FiltrosPropiedades,
  PropiedadesService,
} from '../../core/api/propiedades.service';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { FiltroSelect } from '../../shared/filtro-select/filtro-select';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { Paginacion } from '../../shared/paginacion/paginacion';

const POR_PAGINA = 20;

/** Los cuatro estados del contrato. Son de la propiedad, no del tipo ni de la operación. */
const ESTADOS = [
  { valor: 'D', etiqueta: 'Disponible' },
  { valor: 'N', etiqueta: 'No disponible' },
  { valor: 'I', etiqueta: 'Inactiva' },
];

/**
 * **La cartera, para los siete tipos y las dos operaciones.**
 *
 * ## Qué cambia respecto del listado que sustituye
 *
 * El anterior era una tabla de locales comerciales en alquiler: una columna
 * «Renta» y otra «Rubro», y ninguna que dijera qué se hace con la propiedad —
 * porque sólo se podía hacer una cosa.
 *
 * Aquí cada fila lleva **sus encargos**. Una propiedad en venta y en alquiler
 * enseña las dos operaciones con sus dos importes, sin que ninguno se llame
 * «el precio».
 *
 * ## «Venta + alquiler» se compone aquí, no viaja
 *
 * No existe como valor en ninguna parte: es lo que se ve cuando una propiedad
 * tiene los dos encargos vivos. Esta pantalla lo **compone** al pintar, que es
 * exactamente lo que le toca — presentación—; y el filtro correspondiente lo
 * resuelve el backend con dos EXISTS, no con una igualdad.
 *
 * ## Ni tipos ni operaciones escritos aquí
 *
 * Los dos catálogos salen de `GET /captura/apertura`, que es su único dueño.
 * Con la lista copiada en esta pantalla, añadir un tipo —que el modelo
 * universal prometió que sería una fila— volvería a ser un cambio en dos
 * repositorios (D-A-1 §6), y hay un gate que rompe el build por eso.
 */
@Component({
  selector: 'app-propiedades',
  imports: [RouterLink, BarraFiltros, FiltroSelect, EstadoListado, Paginacion],
  templateUrl: './propiedades.html',
  styleUrl: './propiedades.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Propiedades implements OnInit {
  private readonly api = inject(PropiedadesService);
  private readonly captura = inject(CapturaService);
  private readonly router = inject(Router);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly filas = signal<readonly FilaPropiedad[]>([]);
  protected readonly total = signal(0);
  protected readonly pagina = signal(1);

  protected readonly texto = signal('');
  protected readonly tipo = signal('');
  protected readonly operacion = signal('');
  protected readonly distrito = signal('');
  protected readonly estado = signal('');

  protected readonly distritos = signal<readonly string[]>([]);
  /** Tipos y operaciones, tal como los publica el motor. */
  protected readonly opcionesTipo = signal<readonly { valor: string; etiqueta: string }[]>([]);
  protected readonly opcionesOperacion = signal<readonly { valor: string; etiqueta: string }[]>([]);
  protected readonly estados = ESTADOS;

  protected readonly porPagina = POR_PAGINA;

  protected readonly hayFiltros = computed(
    () =>
      !!this.texto() || !!this.tipo() || !!this.operacion() || !!this.distrito() || !!this.estado(),
  );

  protected readonly vacio = computed(() => !this.cargando() && this.filas().length === 0);

  async ngOnInit(): Promise<void> {
    await Promise.all([this.cargarOpciones(), this.cargar()]);
  }

  /**
   * Las opciones del filtro, de sus dos dueños: el vocabulario del dominio lo
   * publica el motor de captura y los distritos salen de la cartera real.
   *
   * La operación gana un valor compuesto —«las dos»— que **no existe en el
   * dominio**: es un filtro, y filtrar por «tiene los dos encargos» es una
   * pregunta legítima que se responde con la lista de operaciones que el motor
   * ya declaró.
   */
  private async cargarOpciones(): Promise<void> {
    try {
      const [apertura, filtros] = await Promise.all([
        this.captura.apertura(),
        this.api.filtros(),
      ]);
      this.distritos.set(filtros.distritos);
      this.opcionesTipo.set(opcionesDe(apertura, 'tipoPropiedad'));

      const operaciones = opcionesDe(apertura, 'operaciones');
      this.opcionesOperacion.set([
        ...operaciones,
        ...(operaciones.length > 1
          ? [
              {
                valor: operaciones.map((opcion) => opcion.valor).join(','),
                etiqueta: enUnaFrase(
                  operaciones.map((opcion) => opcion.valor),
                  'y',
                ),
              },
            ]
          : []),
      ]);
    } catch {
      // Sin opciones la lista sigue sirviendo: los filtros quedan vacíos y se
      // puede buscar por texto. Peor sería no poder ver la cartera.
    }
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    const filtros: FiltrosPropiedades = {
      pagina: this.pagina(),
      tamano: POR_PAGINA,
      texto: this.texto() || undefined,
      tipoPropiedad: this.tipo() || undefined,
      operaciones: this.operacion() || undefined,
      distrito: this.distrito() || undefined,
      estado: this.estado() || undefined,
    };
    try {
      const pagina = await this.api.listar(filtros);
      this.filas.set(pagina.items);
      this.total.set(pagina.totalRecords);
    } catch (error) {
      this.error.set(
        error instanceof ApiError || error instanceof Error
          ? error.message
          : 'No se pudo cargar la cartera.',
      );
    } finally {
      this.cargando.set(false);
    }
  }

  /** Cualquier cambio de filtro vuelve a la primera página: si no, se ve vacío. */
  protected async filtrar(): Promise<void> {
    this.pagina.set(1);
    await this.cargar();
  }

  protected async limpiar(): Promise<void> {
    this.texto.set('');
    this.tipo.set('');
    this.operacion.set('');
    this.distrito.set('');
    this.estado.set('');
    await this.filtrar();
  }

  protected async irAPagina(pagina: number): Promise<void> {
    this.pagina.set(pagina);
    await this.cargar();
  }

  /**
   * **La etiqueta de operación, compuesta aquí.** Con dos encargos vivos dice
   * «Venta + alquiler»; con uno, el que sea; con ninguno, lo dice también —una
   * propiedad sin encargo vivo no está ni en venta ni en alquiler, y callarlo
   * la haría parecer disponible.
   */
  protected operacionesDe(fila: FilaPropiedad): string {
    if (fila.encargos.length === 0) {
      return 'Sin encargo';
    }
    return enUnaFrase(fila.encargos.map((encargo) => encargo.operacion), '+');
  }

  /** Cada encargo con su importe. Dos encargos, dos renglones: nunca un total. */
  protected importesDe(fila: FilaPropiedad): string[] {
    return fila.encargos
      .filter((encargo) => encargo.importe != null)
      .map(
        (encargo) =>
          `${enFrase(encargo.operacion)}: ${encargo.moneda ?? ''} ${formatear(encargo.importe!)}`.trim(),
      );
  }

  protected async abrir(fila: FilaPropiedad): Promise<void> {
    await this.router.navigate(['/propiedades', fila.id]);
  }
}

/** Las opciones de una pregunta del motor, listas para un `cl-filtro-select`. */
function opcionesDe(
  apertura: PreguntaCaptura[],
  clave: string,
): { valor: string; etiqueta: string }[] {
  const pregunta = apertura.find((candidata) => candidata.clave === clave);
  // El rótulo lo pone el Core («Local comercial», «Venta»); aquí no se compone.
  return (pregunta?.opciones ?? []).map((opcion) => ({
    valor: opcion.valor,
    etiqueta: opcion.rotulo,
  }));
}

/** `VENTA` → `Venta`. El valor viaja en mayúsculas; la persona lo lee en frase. */
function enFrase(valor: string): string {
  return valor.charAt(0) + valor.slice(1).toLowerCase();
}

/**
 * Varias operaciones en una sola frase: «Venta + alquiler», «Venta y alquiler».
 *
 * Sólo la primera va en mayúscula, porque es **una frase** y no una lista de
 * etiquetas. «Venta + Alquiler» se lee como dos rótulos pegados.
 */
function enUnaFrase(valores: readonly string[], union: string): string {
  return valores
    .map((valor, indice) => (indice === 0 ? enFrase(valor) : valor.toLowerCase()))
    .join(` ${union} `);
}

function formatear(importe: number): string {
  return new Intl.NumberFormat('es-PE', { maximumFractionDigits: 0 }).format(importe);
}
