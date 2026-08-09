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
import { ActivatedRoute, Router } from '@angular/router';
import {
  catchError,
  combineLatest,
  distinctUntilChanged,
  forkJoin,
  map,
  Observable,
  of,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';

import { ApiError } from '../../core/api/api.types';
import {
  describir,
  ESTADO_LOCAL,
  ESTADO_PROSPECCION,
  ESTADO_PUBLICACION,
  CANAL_PUBLICACION,
  HITO_PRECIO,
  RESULTADO_PROPUESTA,
  TIPO_INMUEBLE,
  USO_INMUEBLE,
} from '../../core/api/codigos';
import {
  Local,
  LocalesService,
  PrecioLocal,
  Publicacion,
} from '../../core/api/locales.service';
import {
  masAvanzada,
  Prospeccion,
  ProspeccionesService,
} from '../../core/api/prospecciones.service';
import { AuthService } from '../../core/auth/auth.service';
import { Bloque, bloque, complementario } from '../../core/bloque';
import { fechaCorta, fechaHora, monto, numero, siNo, SIN_DATO, texto } from '../../core/formato';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { EditorPublicacion } from './editor-publicacion';

/** Un paso de la línea de tiempo previa a la captación. */
export interface PasoProspeccion {
  titulo: string;
  fecha: string;
  detalle: string;
  completado: boolean;
  activo: boolean;
}

/**
 * Ficha de un local: datos comerciales, avance de la prospección previa a
 * captar, ficha técnica, histórico de precios y publicaciones.
 *
 * Porta `LocalDetail.razor`, con tres diferencias deliberadas:
 *
 * 1. **La prospección se pide filtrada por `idLocal`**, no descargando la
 *    bandeja entera para filtrarla en memoria (RC-003). Con eso se pierde el
 *    emparejamiento por dirección que hacía el Blazor como respaldo, que era
 *    un parche para filas antiguas sin `localId`: en la v2 el alta del local
 *    crea siempre su prospección inicial enlazada.
 * 2. **Los bloques complementarios fallan por separado**, en vez del
 *    `try/catch` que dejaba precios y publicaciones vacíos sin decir nada.
 * 3. **No hay enlace "Crear captación"**: esa pantalla todavía no está
 *    migrada y un botón que no lleva a ninguna parte es peor que su ausencia.
 */
@Component({
  selector: 'app-local-detail',
  imports: [EstadoListado, EditorPublicacion],
  templateUrl: './local-detail.html',
  styleUrl: './local-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LocalDetail implements OnInit {
  private readonly locales = inject(LocalesService);
  private readonly prospecciones = inject(ProspeccionesService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargar$ = new Subject<void>();

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly local = signal<Local | null>(null);
  protected readonly prospeccion = signal<Bloque<Prospeccion | null>>(bloque(null));
  protected readonly precios = signal<Bloque<readonly PrecioLocal[]>>(bloque([]));
  protected readonly publicaciones = signal<Bloque<readonly Publicacion[]>>(bloque([]));

  protected readonly editorAbierto = signal(false);
  protected readonly publicacionEnEdicion = signal<Publicacion | null>(null);
  protected readonly cambiandoEstado = signal<number | null>(null);
  protected readonly errorAccion = signal<string | null>(null);

  protected readonly SIN_DATO = SIN_DATO;
  protected readonly idLocal = signal(0);

  /**
   * Solo el AGENTE escribe. Es el mismo gate del backend para publicaciones
   * (`matriz-operacion-rol.md`), y también lo que el Blazor llamaba
   * "solo lectura" para broker y administrador.
   */
  protected readonly puedeEditar = computed(() => this.auth.sesion()?.rol === 'AGENTE');

  /** La etapa más avanzada alcanzada; sin prospección cae al estado del local. */
  protected readonly etapa = computed(() => {
    const estado = this.prospeccion().datos?.estado;
    return estado
      ? describir(ESTADO_PROSPECCION, estado)
      : describir(ESTADO_LOCAL, this.local()?.estado) || SIN_DATO;
  });

  protected readonly estadoProspeccion = computed(() => {
    const actual = this.prospeccion().datos;
    return actual ? describir(ESTADO_PROSPECCION, actual.estado) : 'Sin prospección vinculada';
  });

  /**
   * Estado de la propuesta al propietario.
   *
   * Se mira `fechaPropuesta`, **no** el estado `E`: la v1 nunca lo emite —
   * entregar la propuesta deja la prospección en `S` y marca la fecha—, así
   * que esperar ese estado dejaría la propuesta como pendiente para siempre.
   */
  protected readonly estadoPropuesta = computed(() => {
    const actual = this.prospeccion().datos;
    if (!actual?.fechaPropuesta) {
      return 'Pendiente';
    }
    const resultado = describir(RESULTADO_PROPUESTA, actual.resultadoPropuesta);
    const entregada = `Entregada ${fechaCorta(actual.fechaPropuesta)}`;
    return resultado ? `${entregada} · ${resultado}` : entregada;
  });

  protected readonly linea = computed<PasoProspeccion[]>(() => {
    const local = this.local();
    const p = this.prospeccion().datos;
    const nivel = nivelProspeccion(p?.estado);
    return [
      {
        titulo: 'Local registrado',
        fecha: fechaCorta(local?.fechaRegistro),
        detalle: texto(local?.propietarioNombre),
        completado: true,
        activo: false,
      },
      {
        titulo: 'Contacto con propietario',
        fecha: fechaCorta(p?.fechaContacto),
        detalle: texto(p?.propietarioNombre),
        completado: nivel >= 1 || !!p?.fechaContacto,
        activo: p?.estado === 'C',
      },
      {
        titulo: 'Reunión registrada',
        fecha: fechaCorta(p?.fechaReunion),
        detalle: texto(local?.direccion),
        completado: nivel >= 2 || !!p?.fechaReunion,
        activo: p?.estado === 'R',
      },
      {
        titulo: 'Propuesta entregada',
        fecha: fechaCorta(p?.fechaPropuesta),
        detalle: describir(RESULTADO_PROPUESTA, p?.resultadoPropuesta) || SIN_DATO,
        completado: nivel >= 3 || !!p?.fechaPropuesta,
        activo: p?.estado === 'S' || p?.estado === 'E',
      },
      {
        titulo: 'Captación',
        fecha: texto(p?.captacionCodigo),
        detalle: texto(p?.captacionCodigo),
        completado: nivel >= 4 || !!p?.captacionCodigo,
        activo: p?.estado === 'T',
      },
    ];
  });

  ngOnInit(): void {
    const id$ = this.route.paramMap.pipe(
      map((params) => Number(params.get('id'))),
      distinctUntilChanged(),
    );

    combineLatest([id$, this.recargar$.pipe(startWith(undefined))])
      .pipe(
        map(([id]) => id),
        tap((id) => {
          this.idLocal.set(id);
          this.cargando.set(true);
          this.error.set(null);
          this.errorAccion.set(null);
        }),
        switchMap((id) => this.cargar(id)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  protected volver(): void {
    void this.router.navigate(['/locales']);
  }

  protected editarLocal(): void {
    void this.router.navigate(['/locales', this.idLocal(), 'editar']);
  }

  /**
   * Resumen comercial de la captación vinculada. Muestra galería,
   * condiciones pactadas y responsables; no es el registro técnico del local.
   */
  protected verFichaPropiedad(): void {
    const codigo = this.prospeccion().datos?.captacionCodigo;
    if (codigo) {
      void this.router.navigate(['/captaciones', codigo, 'ficha']);
    }
  }

  protected recargar(): void {
    this.recargar$.next();
  }

  protected etiquetaEstadoLocal(codigo: string | undefined): string {
    return describir(ESTADO_LOCAL, codigo);
  }

  protected etiquetaHito(codigo: string | undefined): string {
    return describir(HITO_PRECIO, codigo);
  }

  protected etiquetaTipo(codigo: string | undefined): string {
    return describir(TIPO_INMUEBLE, codigo) || SIN_DATO;
  }

  protected etiquetaUso(codigo: string | undefined): string {
    return describir(USO_INMUEBLE, codigo) || SIN_DATO;
  }

  protected etiquetaCanal(codigo: string | undefined): string {
    return describir(CANAL_PUBLICACION, codigo);
  }

  protected etiquetaEstadoPublicacion(codigo: string | undefined): string {
    return describir(ESTADO_PUBLICACION, codigo);
  }

  /**
   * Tono del badge, por dominio y nunca por la letra: el mismo código
   * significa cosas distintas según de qué hable (`P` es "pendiente" en
   * captación y "publicado" en publicación).
   */
  protected tonoLocal(codigo: string | undefined): string {
    return codigo === 'D' ? 'bien' : codigo === 'N' ? 'aviso' : '';
  }

  protected tonoPublicacion(codigo: string | undefined): string {
    return codigo === 'P' ? 'bien' : codigo === 'S' ? 'aviso' : '';
  }

  protected fecha(valor: string | undefined): string {
    return fechaCorta(valor);
  }

  protected fechaConHora(valor: string | undefined): string {
    return fechaHora(valor);
  }

  protected importe(valor: number | undefined, moneda?: string): string {
    return monto(valor, moneda);
  }

  protected cifra(valor: number | undefined, decimales = 2): string {
    return numero(valor, decimales);
  }

  protected booleano(valor: boolean | undefined): string {
    return siNo(valor);
  }

  protected valor(valor: string | undefined): string {
    return texto(valor);
  }

  // =====================================================================
  // Publicaciones
  // =====================================================================

  protected nuevaPublicacion(): void {
    this.publicacionEnEdicion.set(null);
    this.editorAbierto.set(true);
  }

  protected editarPublicacion(publicacion: Publicacion): void {
    this.publicacionEnEdicion.set(publicacion);
    this.editorAbierto.set(true);
  }

  protected cerrarEditor(): void {
    this.editorAbierto.set(false);
  }

  protected async publicacionGuardada(): Promise<void> {
    this.editorAbierto.set(false);
    await this.refrescarPublicaciones();
  }

  /** Pausar (`S`), publicar (`P`) o cerrar (`C`) el anuncio. */
  protected async cambiarEstado(publicacion: Publicacion, estado: string): Promise<void> {
    if (this.cambiandoEstado() !== null) {
      return;
    }
    this.cambiandoEstado.set(publicacion.id);
    this.errorAccion.set(null);
    try {
      await this.locales.cambiarEstadoPublicacion(this.idLocal(), publicacion.id, estado);
      await this.refrescarPublicaciones();
    } catch (error) {
      this.errorAccion.set(
        error instanceof ApiError
          ? error.message
          : 'No se pudo cambiar el estado de la publicación.',
      );
    } finally {
      this.cambiandoEstado.set(null);
    }
  }

  private async refrescarPublicaciones(): Promise<void> {
    try {
      const lista = await this.locales.publicaciones(this.idLocal());
      this.publicaciones.set(bloque(lista));
    } catch (error) {
      // Se conserva la lista visible: el cambio ya se aplicó en el servidor y
      // vaciarla haría creer que se perdió.
      this.errorAccion.set(
        error instanceof ApiError
          ? error.message
          : 'No se pudo actualizar la lista de publicaciones.',
      );
    }
  }

  // =====================================================================
  // Carga
  // =====================================================================

  private cargar(id: number): Observable<unknown> {
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.publicar(null, bloque(null), bloque([]), bloque([]));
      this.error.set('El identificador del local no es válido.');
      return of(null);
    }

    // Los tres complementarios no propagan su error al `forkJoin`: cada uno
    // se queda con el suyo y el resto de la ficha se dibuja igual.
    return forkJoin({
      local: this.locales.obtener$(id),
      prospeccion: complementario<Prospeccion | null>(
        this.prospecciones.porLocal$(id).pipe(map((pagina) => masAvanzada(pagina.items))),
        null,
        'No se pudo leer la prospección del local.',
      ),
      precios: complementario<readonly PrecioLocal[]>(
        this.locales.precios$(id),
        [],
        'No se pudo leer el histórico de precios.',
      ),
      publicaciones: complementario<readonly Publicacion[]>(
        this.locales.publicaciones$(id),
        [],
        'No se pudieron leer las publicaciones.',
      ),
    }).pipe(
      tap(({ local, prospeccion, precios, publicaciones }) =>
        this.publicar(local, prospeccion, precios, publicaciones),
      ),
      catchError((error) => {
        this.publicar(null, bloque(null), bloque([]), bloque([]));
        this.error.set(
          error instanceof ApiError ? error.message : 'No se pudo cargar el local.',
        );
        return of(null);
      }),
    );
  }

  private publicar(
    local: Local | null,
    prospeccion: Bloque<Prospeccion | null>,
    precios: Bloque<readonly PrecioLocal[]>,
    publicaciones: Bloque<readonly Publicacion[]>,
  ): void {
    this.local.set(local);
    this.prospeccion.set(prospeccion);
    this.precios.set(precios);
    this.publicaciones.set(publicaciones);
    this.cargando.set(false);
  }
}

/**
 * Nivel alcanzado en la línea de tiempo. Es una escala DISTINTA de la que
 * ordena prospecciones (`avance`), y no hay que unificarlas: aquí `E` y `S`
 * comparten nivel porque son el mismo hito —la v1 no emite `E`—, mientras
 * que al ordenar sí conviene distinguirlas.
 *
 * Si una etapa posterior ya se cumplió, las anteriores se dan por completas
 * aunque el backend no haya fijado cada fecha intermedia.
 */
export function nivelProspeccion(estado: string | null | undefined): number {
  switch (estado) {
    case 'C':
      return 1;
    case 'R':
      return 2;
    case 'E':
    case 'S':
      return 3;
    case 'T':
      return 4;
    default:
      return 0;
  }
}
