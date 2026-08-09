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
import { Captacion, CaptacionesService } from '../../core/api/captaciones.service';
import {
  describir,
  ESTADO_CAPTACION,
  ESTADO_LOCAL,
  HITO_PRECIO,
  TIPO_DOCUMENTO,
  TIPO_INMUEBLE,
  TIPO_PERSONA,
} from '../../core/api/codigos';
import {
  FotoLocal,
  Local,
  LocalesService,
  PrecioLocal,
} from '../../core/api/locales.service';
import { Propietario, PropietariosService } from '../../core/api/propietarios.service';
import { ArchivoPreparado, ArchivosService } from '../../core/archivos/archivos.service';
import { AuthService } from '../../core/auth/auth.service';
import { Bloque, bloque, complementario } from '../../core/bloque';
import {
  calcularCondicionComision,
  comisionSobreRenta,
  descripcionCondicionComision,
  Importe,
  importeTexto,
} from '../../core/comision';
import {
  fechaCorta,
  monto,
  numero,
  porcentaje,
  siNo,
  SIN_DATO,
  texto,
} from '../../core/formato';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { ImagenSegura } from '../../shared/imagen-segura/imagen-segura';
import { SubidaArchivo } from '../../shared/subida-archivo/subida-archivo';

/** Tope de fotos por local; lo impone el backend y aquí solo se anticipa. */
export const MAXIMO_FOTOS = 6;

interface Complementos {
  local: Bloque<Local | null>;
  precios: Bloque<readonly PrecioLocal[]>;
  fotos: Bloque<readonly FotoLocal[]>;
}

/**
 * Resumen comercial de una captación: galería, características, condiciones
 * comerciales, propietario y agente responsable.
 *
 * Porta `FichaPropiedad.razor`. **La diferencia grande está en cómo se arma el
 * modelo**, no en lo que se ve: el Blazor descargaba la bandeja de captaciones,
 * la de locales y la de propietarios enteras, y luego emparejaba el local por
 * *coincidencia difusa de dirección* y el propietario por *coincidencia difusa
 * de nombre* (`Loose()`: igualdad, o que una cadena contenga a la otra). Eso es
 * carga masiva, filtrado en memoria y una identificación que puede acertarle al
 * registro equivocado —dos locales en la misma avenida bastan—.
 *
 * Aquí la ficha se resuelve **encadenando identificadores**, que es lo que el
 * contrato ya permitía: la captación trae `idLocal`, el local trae
 * `idPropietario`. Son tres saltos, todos por id, con el alcance resuelto en el
 * backend.
 *
 * Otras divergencias deliberadas:
 * - **No hay "Exportar PDF"** (D-F5-1): los cinco endpoints Jasper quedaron
 *   fuera del alcance de la migración. Tampoco el aviso que invitaba a
 *   exportar.
 * - **La descripción es la del local**, no un párrafo generado. El Blazor
 *   fabricaba prosa comercial ("excelente afluencia peatonal…") idéntica para
 *   cualquier propiedad e ignoraba el campo `descripcion` que el usuario había
 *   escrito. Inventar contenido en una ficha que se comparte con el cliente es
 *   justo lo que no debe hacer una herramienta de gestión.
 * - **El mapa se abre al pulsarlo**, en vez de incrustar un iframe de Google en
 *   cada carga: la ficha dejaba de ser una lectura interna y enviaba la
 *   dirección del inmueble a un tercero sin que nadie lo pidiera.
 */
@Component({
  selector: 'app-ficha-propiedad',
  imports: [EstadoListado, ImagenSegura, SubidaArchivo],
  templateUrl: './ficha-propiedad.html',
  styleUrl: './ficha-propiedad.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FichaPropiedad implements OnInit {
  private readonly captaciones = inject(CaptacionesService);
  private readonly locales = inject(LocalesService);
  private readonly propietarios = inject(PropietariosService);
  private readonly archivos = inject(ArchivosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargar$ = new Subject<void>();

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly captacion = signal<Captacion | null>(null);
  protected readonly local = signal<Bloque<Local | null>>(bloque(null));
  protected readonly precios = signal<Bloque<readonly PrecioLocal[]>>(bloque([]));
  protected readonly fotos = signal<Bloque<readonly FotoLocal[]>>(bloque([]));
  protected readonly propietario = signal<Bloque<Propietario | null>>(bloque(null));

  protected readonly subiendo = signal(false);
  protected readonly eliminandoFoto = signal<number | null>(null);
  protected readonly mensajeFoto = signal<string | null>(null);
  protected readonly errorFoto = signal<string | null>(null);

  protected readonly SIN_DATO = SIN_DATO;
  protected readonly maximoFotos = MAXIMO_FOTOS;
  protected readonly extensionesFoto = ['.png', '.jpg', '.jpeg'] as const;

  /**
   * Solo el AGENTE escribe fotos: `POST`/`DELETE /locales/{id}/fotos` llevan
   * `hasRole('AGENTE')` (`matriz-operacion-rol.md`). Ver la ficha, en cambio,
   * es de los tres roles con su alcance, así que la ruta no lleva `roles`.
   */
  protected readonly puedeEditarFotos = computed(
    () => this.auth.sesion()?.rol === 'AGENTE',
  );

  protected readonly totalFotos = computed(() => this.fotos().datos.length);
  protected readonly quedanFotos = computed(() =>
    Math.max(0, MAXIMO_FOTOS - this.totalFotos()),
  );
  protected readonly galeriaLlena = computed(() => this.quedanFotos() === 0);
  protected readonly fotoOcupada = computed(
    () => this.subiendo() || this.eliminandoFoto() !== null,
  );

  /** Área de la captación; si no la trae, la del local (igual que el Blazor). */
  protected readonly area = computed(
    () => this.captacion()?.areaM2 ?? this.local().datos?.metraje ?? null,
  );

  protected readonly rubro = computed(
    () => this.local().datos?.rubroPermitido || this.captacion()?.rubro || 'Por definir',
  );

  /** Renta mensual de referencia, con su moneda pegada al valor. */
  protected readonly renta = computed<Importe | null>(() => {
    const valor = this.local().datos?.precioReferencial;
    return valor === null || valor === undefined
      ? null
      : { valor, moneda: this.local().datos?.monedaReferencial ?? '' };
  });

  /**
   * La comisión estimada, **en la moneda de la renta**: sale de
   * `comisionSobreRenta`, que la hereda del importe que recibe, así que no
   * puede rotularse con otra.
   */
  protected readonly comisionEstimada = computed(() =>
    calcularCondicionComision(this.captacion())
      ?? comisionSobreRenta(this.captacion()?.comisionPactada, this.renta()),
  );

  /** "Un mes de alquiler" o, si no es redonda, el porcentaje sobre la renta. */
  protected readonly comisionEquivalencia = computed(() =>
    descripcionCondicionComision(this.captacion()),
  );

  /**
   * El porcentaje solo se enseña **como dato secundario** cuando la
   * equivalencia ya se dijo en meses; si no, sería repetirlo dos veces.
   */
  protected readonly comisionPorcentajeSecundario = computed(() =>
    this.captacion()?.tipoComision === 'E' && this.captacion()?.valorComision !== undefined
      ? porcentaje((this.captacion()?.valorComision ?? 0) * 100)
      : '',
  );

  protected readonly vigencia = computed(() => {
    const c = this.captacion();
    if (!c?.fechaInicioVigencia || !c?.fechaFinVigencia) {
      return 'Sin vigencia definida';
    }
    return `${fechaCorta(c.fechaInicioVigencia)} — ${fechaCorta(c.fechaFinVigencia)}`;
  });

  /**
   * Días hasta el fin de vigencia, con el mismo criterio del Blazor
   * (`fechaFin - hoy`, en días completos). Se compara a medianoche local para
   * que el resultado no dependa de la hora a la que se abra la ficha.
   */
  protected readonly diasRestantes = computed(() => {
    const fin = this.captacion()?.fechaFinVigencia;
    const dias = diasHasta(fin);
    if (dias === null) {
      return SIN_DATO;
    }
    if (dias === 0) {
      return 'vence hoy';
    }
    return dias > 0 ? `vence en ${dias} días` : `venció hace ${-dias} días`;
  });

  protected readonly vigenciaVencida = computed(() => {
    const dias = diasHasta(this.captacion()?.fechaFinVigencia);
    return dias !== null && dias < 0;
  });

  /** Buscador de mapas, abierto por el usuario y no incrustado. Ver la clase. */
  protected readonly enlaceMapa = computed(() => {
    const c = this.captacion();
    const l = this.local().datos;
    const lat = l?.geoLat;
    const lon = l?.geoLong;
    const consulta =
      lat !== undefined && lat !== null && lon !== undefined && lon !== null
        ? `${lat},${lon}`
        : [c?.direccionLocal, c?.distritoLocal, 'Lima', 'Perú']
            .filter((parte) => !!parte)
            .join(', ');
    return consulta
      ? `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(consulta)}`
      : null;
  });

  ngOnInit(): void {
    const codigo$ = this.route.paramMap.pipe(
      map((params) => (params.get('codigo') ?? '').trim()),
      distinctUntilChanged(),
    );

    combineLatest([codigo$, this.recargar$.pipe(startWith(undefined))])
      .pipe(
        map(([codigo]) => codigo),
        tap(() => {
          this.cargando.set(true);
          this.error.set(null);
          this.limpiarAvisosFoto();
        }),
        switchMap((codigo) => this.cargar(codigo)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  protected volver(): void {
    void this.router.navigate(['/locales']);
  }

  protected recargar(): void {
    this.recargar$.next();
  }

  protected verLocal(): void {
    const idLocal = this.captacion()?.idLocal;
    if (idLocal) {
      void this.router.navigate(['/locales', idLocal]);
    }
  }

  // Presentación: todo delega en `core/formato` y `core/api/codigos`.

  protected etiquetaEstado(codigo: string | undefined): string {
    return describir(ESTADO_CAPTACION, codigo) || SIN_DATO;
  }

  protected tonoEstado(codigo: string | undefined): string {
    if (codigo === 'A') {
      return 'bien';
    }
    return codigo === 'P' || codigo === 'O' ? 'aviso' : codigo === 'R' ? 'mal' : '';
  }

  protected etiquetaEstadoLocal(codigo: string | undefined): string {
    return describir(ESTADO_LOCAL, codigo) || SIN_DATO;
  }

  protected etiquetaHito(codigo: string | undefined): string {
    return describir(HITO_PRECIO, codigo);
  }

  protected etiquetaTipoInmueble(codigo: string | undefined): string {
    return describir(TIPO_INMUEBLE, codigo) || SIN_DATO;
  }

  protected etiquetaTipoPersona(codigo: string | undefined): string {
    return describir(TIPO_PERSONA, codigo) || SIN_DATO;
  }

  /** `D`/`R`/`C`/`P` -> DNI/RUC/… El cable manda la letra, no el nombre. */
  protected documento(propietario: Propietario): string {
    const tipo = describir(TIPO_DOCUMENTO, propietario.tipoDocumento);
    const numero = texto(propietario.numeroDocumento);
    return tipo ? `${tipo} ${numero}` : numero;
  }

  protected fecha(valor: string | undefined): string {
    return fechaCorta(valor);
  }

  protected importe(valor: number | undefined, moneda?: string): string {
    return monto(valor, moneda);
  }

  protected cifra(valor: number | null | undefined, decimales = 2): string {
    return numero(valor, decimales);
  }

  /** Importe con su moneda; nunca se separan. */
  protected texto(importe: Importe | null): string {
    return importeTexto(importe);
  }

  protected booleano(valor: boolean | undefined): string {
    return siNo(valor);
  }

  protected valor(valor: string | undefined): string {
    return texto(valor);
  }

  protected unidad(valor: number | undefined, sufijo: string, decimales = 2): string {
    return valor === undefined || valor === null
      ? SIN_DATO
      : `${numero(valor, decimales)} ${sufijo}`;
  }

  /** Iniciales para el avatar; hasta dos, solo de palabras que empiezan por letra. */
  protected iniciales(nombre: string | undefined): string {
    return (nombre ?? '')
      .split(/\s+/)
      .filter((palabra) => palabra && /\p{L}/u.test(palabra[0]))
      .slice(0, 2)
      .map((palabra) => palabra[0].toLocaleUpperCase('es'))
      .join('');
  }

  // =====================================================================
  // Galería
  // =====================================================================

  protected async fotosElegidas(elegidas: readonly ArchivoPreparado[]): Promise<void> {
    // Doble puerta: el backend gatea por rol, y aquí no se inicia siquiera la
    // petición desde un rol que no escribe. Ocultar el botón no basta.
    if (!this.puedeEditarFotos() || this.fotoOcupada() || elegidas.length === 0) {
      return;
    }
    const idLocal = this.captacion()?.idLocal;
    if (!idLocal) {
      this.errorFoto.set('La captación no tiene un local asociado.');
      return;
    }
    if (elegidas.length > this.quedanFotos()) {
      this.errorFoto.set(
        `Solo caben ${this.quedanFotos()} foto(s) más: el máximo por local es ${MAXIMO_FOTOS}.`,
      );
      return;
    }

    this.subiendo.set(true);
    this.limpiarAvisosFoto();
    let subidas = 0;
    try {
      // En serie a propósito: el tope de 6 lo comprueba el backend por
      // petición, así que en paralelo dos altas podrían pasar la validación a
      // la vez y una fallaría a mitad de tanda.
      for (const elegida of elegidas) {
        const contenido = await this.archivos.base64(elegida.archivo);
        await this.locales.subirFoto(idLocal, elegida.nombreSeguro, contenido);
        subidas += 1;
      }
      this.mensajeFoto.set(
        subidas === 1 ? 'Foto agregada a la galería.' : `${subidas} fotos agregadas.`,
      );
    } catch (error) {
      this.errorFoto.set(
        error instanceof ApiError ? error.message : 'No se pudo guardar la foto.',
      );
    } finally {
      this.subiendo.set(false);
      // Se relee siempre: si falló a mitad, la galería debe reflejar lo que
      // de verdad quedó guardado, no lo que se intentó.
      await this.refrescarFotos(idLocal);
    }
  }

  protected fotoRechazada(mensaje: string): void {
    this.mensajeFoto.set(null);
    this.errorFoto.set(mensaje);
  }

  protected async eliminarFoto(idFoto: number): Promise<void> {
    if (!this.puedeEditarFotos() || this.fotoOcupada()) {
      return;
    }
    const idLocal = this.captacion()?.idLocal;
    if (!idLocal) {
      return;
    }

    this.eliminandoFoto.set(idFoto);
    this.limpiarAvisosFoto();
    try {
      await this.locales.eliminarFoto(idLocal, idFoto);
      this.mensajeFoto.set('Foto eliminada del registro y del almacén.');
      await this.refrescarFotos(idLocal);
    } catch (error) {
      this.errorFoto.set(
        error instanceof ApiError ? error.message : 'No se pudo eliminar la foto.',
      );
    } finally {
      this.eliminandoFoto.set(null);
    }
  }

  private async refrescarFotos(idLocal: number): Promise<void> {
    try {
      this.fotos.set(bloque(await this.locales.fotos(idLocal)));
    } catch (error) {
      this.fotos.set(
        bloque(
          this.fotos().datos,
          error instanceof ApiError ? error.message : 'No se pudo releer la galería.',
        ),
      );
    }
  }

  private limpiarAvisosFoto(): void {
    this.mensajeFoto.set(null);
    this.errorFoto.set(null);
  }

  // =====================================================================
  // Carga: captación (fatal) -> local/precios/fotos -> propietario
  // =====================================================================

  private cargar(codigo: string): Observable<unknown> {
    if (!codigo) {
      this.publicar(null, bloque(null), bloque([]), bloque([]), bloque(null));
      this.error.set('No se indicó el código de la captación.');
      return of(null);
    }

    return this.captaciones.obtenerPorCodigo$(codigo).pipe(
      switchMap((captacion) =>
        this.complementosDe(captacion).pipe(
          switchMap((complementos) =>
            this.propietarioDe(complementos.local.datos).pipe(
              tap((propietario) =>
                this.publicar(
                  captacion,
                  complementos.local,
                  complementos.precios,
                  complementos.fotos,
                  propietario,
                ),
              ),
            ),
          ),
        ),
      ),
      catchError((error) => {
        this.publicar(null, bloque(null), bloque([]), bloque([]), bloque(null));
        this.error.set(mensajeDeCarga(error, codigo));
        return of(null);
      }),
    );
  }

  /** Las tres lecturas que cuelgan del local, en paralelo y degradando solas. */
  private complementosDe(captacion: Captacion): Observable<Complementos> {
    const idLocal = captacion.idLocal;
    if (!idLocal) {
      return of({
        local: bloque<Local | null>(
          null,
          'La captación no tiene un local asociado, así que no hay ficha técnica.',
        ),
        precios: bloque<readonly PrecioLocal[]>([]),
        fotos: bloque<readonly FotoLocal[]>([]),
      });
    }
    return forkJoin({
      local: complementario<Local | null>(
        this.locales.obtener$(idLocal),
        null,
        'No se pudo leer la ficha técnica del local.',
      ),
      precios: complementario<readonly PrecioLocal[]>(
        this.locales.precios$(idLocal),
        [],
        'No se pudo leer el histórico de precios.',
      ),
      fotos: complementario<readonly FotoLocal[]>(
        this.locales.fotos$(idLocal),
        [],
        'No se pudo leer la galería.',
      ),
    });
  }

  /** El propietario cuelga del local; sin local no hay a quién pedir. */
  private propietarioDe(local: Local | null): Observable<Bloque<Propietario | null>> {
    const idPropietario = local?.idPropietario;
    if (!idPropietario) {
      return of(bloque<Propietario | null>(null));
    }
    return complementario<Propietario | null>(
      this.propietarios.obtener$(idPropietario),
      null,
      'No se pudieron leer los datos de contacto del propietario.',
    );
  }

  private publicar(
    captacion: Captacion | null,
    local: Bloque<Local | null>,
    precios: Bloque<readonly PrecioLocal[]>,
    fotos: Bloque<readonly FotoLocal[]>,
    propietario: Bloque<Propietario | null>,
  ): void {
    this.captacion.set(captacion);
    this.local.set(local);
    this.precios.set(precios);
    this.fotos.set(fotos);
    this.propietario.set(propietario);
    this.cargando.set(false);
  }
}

/**
 * Días completos entre hoy y la fecha, comparando a medianoche local para que
 * el resultado no cambie según la hora a la que se abra la ficha.
 */
export function diasHasta(fecha: string | null | undefined, hoy = new Date()): number | null {
  if (!fecha) {
    return null;
  }
  const partes = /^(\d{4})-(\d{2})-(\d{2})/.exec(fecha);
  if (!partes) {
    return null;
  }
  const fin = new Date(Number(partes[1]), Number(partes[2]) - 1, Number(partes[3]));
  const inicio = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate());
  return Math.round((fin.getTime() - inicio.getTime()) / 86_400_000);
}

/**
 * El 403 del alcance merece su propio texto: el backend responde 403 —no 404—
 * cuando la captación existe pero es de otro equipo, y decir "no encontrada"
 * ahí manda a buscar un código que sí existe.
 */
function mensajeDeCarga(error: unknown, codigo: string): string {
  if (error instanceof ApiError) {
    return error.sinPermiso
      ? `La captación ${codigo} está fuera de tu alcance.`
      : error.message;
  }
  return `No se pudo cargar la ficha de ${codigo}.`;
}
