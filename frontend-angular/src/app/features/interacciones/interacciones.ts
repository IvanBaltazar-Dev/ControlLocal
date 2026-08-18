import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import {
  catchError,
  combineLatest,
  distinctUntilChanged,
  EMPTY,
  map,
  Observable,
  of,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';
import { RESULTADOS_POR_PAGINA } from '../../shared/paginacion/tamano-pagina';

import { ApiError, paginaVacia, PageResponse } from '../../core/api/api.types';
import {
  CANAL_CONTACTO,
  CONTEXTO_INTERACCION,
  describir,
  opcionesDe,
  RESULTADO_INTERACCION,
  resultadosDe,
} from '../../core/api/codigos';
import { Interaccion, InteraccionesService } from '../../core/api/interacciones.service';
import { AuthService } from '../../core/auth/auth.service';
import { fechaHora, SIN_DATO, texto as textoDe } from '../../core/formato';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect, OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';

const POR_PAGINA = RESULTADOS_POR_PAGINA;
const GRUPOS_VALIDOS = new Set(['TODAS', 'PROPIETARIO', 'CLIENTE']);

interface Pestana {
  clave: string;
  etiqueta: string;
  descripcion: string;
}

/**
 * Las dos conversaciones del negocio. `grupo=PROPIETARIO` significa contexto
 * PROSPECCION **o** CAPTACION; cualquier otro valor distinto de `TODAS`
 * devuelve el complemento — o sea, las del lado del cliente—. No es un filtro
 * por contexto: es una partición en dos, y por eso la pestaña "Cliente" incluye
 * también las de oportunidad.
 */
const PESTANAS: readonly Pestana[] = [
  { clave: 'TODAS', etiqueta: 'Todas', descripcion: 'Los dos lados de la relación.' },
  {
    clave: 'CLIENTE',
    etiqueta: 'Lado cliente',
    descripcion: 'Contexto cliente y oportunidad: la conversación con quien busca local.',
  },
  {
    clave: 'PROPIETARIO',
    etiqueta: 'Lado propietario',
    descripcion: 'Contexto prospección y captación: la conversación con quien tiene el local.',
  },
];

export interface FiltrosInteraccionesUrl {
  texto: string;
  grupo: string;
  canal: string;
  resultado: string;
  page: number;
}

type ResultadoCarga = PageResponse<Interaccion> | { error: string };

/**
 * Bitácora comercial completa: **la única pantalla que ve las cuatro
 * conversaciones juntas**.
 *
 * Tres cosas del cable que la pantalla hace visibles:
 * - **El alcance del BROKER es por AGENTE SUPERVISADO**, no por captación como
 *   en oportunidades y visitas. Se filtra por el agente responsable de la
 *   interacción, no por la entidad de la que cuelga. No unificar: son dos
 *   reglas distintas a propósito.
 * - **`grupo` parte el universo en dos**, no filtra por contexto. Ver
 *   `PESTANAS`.
 * - **El catálogo de `resultado` depende del contexto** y el filtro de la
 *   barra no lo sabe: por eso ofrece la unión de los cuatro sub-catálogos
 *   acotada a la pestaña activa, en vez de una lista fija que devolvería vacío.
 */
@Component({
  selector: 'app-interacciones',
  imports: [BarraFiltros, EstadoListado, FiltroSelect, Paginacion],
  templateUrl: './interacciones.html',
  styleUrl: './interacciones.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Interacciones implements OnInit {
  private readonly api = inject(InteraccionesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargar$ = new Subject<void>();

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly paginaDatos = signal<PageResponse<Interaccion>>(paginaVacia(POR_PAGINA));
  protected readonly filtros = signal<FiltrosInteraccionesUrl>({
    texto: '',
    grupo: 'TODAS',
    canal: '',
    resultado: '',
    page: 1,
  });

  protected readonly pestanas = PESTANAS;
  protected readonly porPagina = POR_PAGINA;
  protected readonly puedeOperar = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  protected readonly opcionesCanal: OpcionFiltro[] = opcionesDe(CANAL_CONTACTO);
  protected readonly hayFiltros = computed(() => {
    const f = this.filtros();
    return !!f.texto || !!f.canal || !!f.resultado || f.grupo !== 'TODAS';
  });
  protected readonly mensajeVacio = computed(() =>
    this.hayFiltros()
      ? 'Ninguna interacción coincide con los filtros.'
      : 'Todavía no hay interacciones registradas en tu alcance.',
  );

  /**
   * Resultados ofrecidos, acotados a la pestaña. En "lado propietario" solo
   * tienen sentido los de prospección y captación; en "lado cliente", los de
   * cliente y oportunidad. En "todas" se ofrece la unión, deduplicada —
   * `PROPUESTA_ENVIADA` y `DESCARTADO` viven en dos contextos.
   */
  protected readonly opcionesResultado = computed<OpcionFiltro[]>(() => {
    const contextos =
      this.filtros().grupo === 'PROPIETARIO'
        ? ['PROSPECCION', 'CAPTACION']
        : this.filtros().grupo === 'CLIENTE'
          ? ['CLIENTE', 'OPORTUNIDAD']
          : ['PROSPECCION', 'CAPTACION', 'CLIENTE', 'OPORTUNIDAD'];
    const vistos = new Set<string>();
    return contextos
      .flatMap((contexto) => resultadosDe(contexto))
      .filter((opcion) => !vistos.has(opcion.valor) && vistos.add(opcion.valor));
  });

  protected readonly descripcionPestana = computed(
    () => PESTANAS.find((p) => p.clave === this.filtros().grupo)?.descripcion ?? '',
  );

  ngOnInit(): void {
    const filtrosUrl$ = this.route.queryParamMap.pipe(
      map(filtrosInteraccionesDesdeUrl),
      distinctUntilChanged(mismosFiltros),
    );

    combineLatest([filtrosUrl$, this.recargar$.pipe(startWith(undefined))])
      .pipe(
        map(([filtros]) => filtros),
        tap((filtros) => {
          this.filtros.set(filtros);
          this.cargando.set(true);
          this.error.set(null);
        }),
        switchMap((filtros) =>
          this.api
            .pagina$({
              pagina: filtros.page,
              tamano: POR_PAGINA,
              grupo: filtros.grupo,
              canal: filtros.canal || undefined,
              resultado: filtros.resultado || undefined,
              q: filtros.texto || undefined,
            })
            .pipe(
              switchMap((pagina) => this.corregirPaginaFueraDeRango(filtros, pagina)),
              catchError((error) =>
                of({
                  error:
                    error instanceof ApiError
                      ? error.message
                      : 'No se pudo cargar la bitácora comercial.',
                }),
              ),
            ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => this.publicar(resultado));
  }

  protected nueva(): void {
    void this.router.navigate(['/interacciones/nueva']);
  }

  protected ver(interaccion: Interaccion): void {
    void this.router.navigate(['/interacciones', interaccion.id]);
  }

  protected cambiarGrupo(grupo: string): void {
    // El resultado se limpia al cambiar de pestaña: su catálogo depende del
    // contexto, y conservarlo dejaría un filtro que ya no devuelve nada.
    this.navegar({ grupo, resultado: '', page: 1 });
  }

  protected cambiarTexto(texto: string): void {
    const normalizado = texto.trim();
    if (normalizado !== this.filtros().texto) {
      this.navegar({ texto: normalizado, page: 1 });
    }
  }

  protected cambiarCanal(canal: string): void {
    this.navegar({ canal, page: 1 });
  }

  protected cambiarResultado(resultado: string): void {
    this.navegar({ resultado, page: 1 });
  }

  protected cambiarPagina(page: number): void {
    this.navegar({ page });
  }

  protected limpiar(): void {
    this.navegar({ texto: '', grupo: 'TODAS', canal: '', resultado: '', page: 1 });
  }

  protected recargar(): void {
    this.recargar$.next();
  }

  protected contexto(codigo: string | undefined): string {
    return describir(CONTEXTO_INTERACCION, codigo) || SIN_DATO;
  }

  /** Prospección y captación son la conversación con el propietario. */
  protected esDePropietario(interaccion: Interaccion): boolean {
    return interaccion.contexto === 'PROSPECCION' || interaccion.contexto === 'CAPTACION';
  }

  protected canal(codigo: string | undefined): string {
    return describir(CANAL_CONTACTO, codigo) || SIN_DATO;
  }

  protected resultado(codigo: string | undefined): string {
    return describir(RESULTADO_INTERACCION, codigo) || SIN_DATO;
  }

  /**
   * Con quién se habló. El cable ya lo resuelve en `personaNombre`; se cae a
   * los nombres específicos solo si viniera vacío.
   */
  protected persona(interaccion: Interaccion): string {
    return textoDe(
      interaccion.personaNombre || interaccion.clienteNombre || interaccion.propietarioNombre,
    );
  }

  /** El código de la entidad de la que cuelga, sea cual sea el contexto. */
  protected referencia(interaccion: Interaccion): string {
    return textoDe(interaccion.codigoProspeccion || interaccion.codigoCaptacion);
  }

  protected momento(valor: string | undefined): string {
    return valor ? fechaHora(valor) : SIN_DATO;
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }

  private corregirPaginaFueraDeRango(
    filtros: FiltrosInteraccionesUrl,
    pagina: PageResponse<Interaccion>,
  ): Observable<PageResponse<Interaccion>> {
    const ultima = Math.max(1, Math.ceil(pagina.totalRecords / POR_PAGINA));
    if (filtros.page > ultima) {
      this.navegar({ page: ultima }, true);
      return EMPTY;
    }
    return of(pagina);
  }

  private publicar(resultado: ResultadoCarga): void {
    if ('error' in resultado) {
      this.paginaDatos.set(paginaVacia(POR_PAGINA));
      this.error.set(resultado.error);
    } else {
      this.paginaDatos.set(resultado);
    }
    this.cargando.set(false);
  }

  private navegar(cambios: Partial<FiltrosInteraccionesUrl>, replaceUrl = false): void {
    const siguiente = { ...this.filtros(), ...cambios };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        texto: siguiente.texto || null,
        grupo: siguiente.grupo === 'TODAS' ? null : siguiente.grupo,
        canal: siguiente.canal || null,
        resultado: siguiente.resultado || null,
        page: siguiente.page,
      },
      replaceUrl,
    });
  }
}

export function filtrosInteraccionesDesdeUrl(params: ParamMap): FiltrosInteraccionesUrl {
  const grupoSolicitado = (params.get('grupo') ?? 'TODAS').trim().toUpperCase();
  const canalSolicitado = (params.get('canal') ?? '').trim().toUpperCase();
  const solicitada = Number(params.get('page') ?? '1');
  return {
    texto: (params.get('texto') ?? '').trim(),
    grupo: GRUPOS_VALIDOS.has(grupoSolicitado) ? grupoSolicitado : 'TODAS',
    // Un canal inventado en la URL no viaja al backend.
    canal: CANAL_CONTACTO[canalSolicitado] ? canalSolicitado : '',
    resultado: (params.get('resultado') ?? '').trim().toUpperCase(),
    page: Number.isSafeInteger(solicitada) && solicitada > 0 ? solicitada : 1,
  };
}

function mismosFiltros(a: FiltrosInteraccionesUrl, b: FiltrosInteraccionesUrl): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}
