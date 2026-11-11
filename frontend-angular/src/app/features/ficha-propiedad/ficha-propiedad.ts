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
  TIPO_DOCUMENTO,
  TIPO_PERSONA,
} from '../../core/api/codigos';
import {
  FichaPropiedad as PropiedadFicha,
  FotoPropiedad,
  HitoDeLaHistoria,
  PropiedadesService,
} from '../../core/api/propiedades.service';
import { motivoDeBloqueo, puedeEscribir } from '../../core/api/propiedades.service';
import { Propietario, PropietariosService } from '../../core/api/propietarios.service';
import { ArchivoPreparado, ArchivosService } from '../../core/archivos/archivos.service';
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
import { BroxPageHeader } from '../../shared/page-header/page-header';

/** Tope de fotos por propiedad; lo impone el contrato universal. */
export const MAXIMO_FOTOS = 12;

interface Complementos {
  propiedad: Bloque<PropiedadFicha | null>;
  fotos: Bloque<readonly FotoPropiedad[]>;
}

/**
 * Resumen comercial de una captación: galería, características, condiciones
 * comerciales, propietario y agente responsable.
 *
 * Porta `FichaPropiedad.razor`. **La diferencia grande está en cómo se arma el
 * modelo**, no en lo que se ve: el Blazor descargaba la bandeja de captaciones,
 * la de propiedades y la de propietarios enteras, y luego emparejaba la propiedad por
 * *coincidencia difusa de dirección* y el propietario por *coincidencia difusa
 * de nombre* (`Loose()`: igualdad, o que una cadena contenga a la otra). Eso es
 * carga masiva, filtrado en memoria y una identificación que puede acertarle al
 * registro equivocado —dos locales en la misma avenida bastan—.
 *
 * Aquí la ficha se resuelve **encadenando identificadores**, que es lo que el
 * contrato ya permitía: la captación trae `idLocal` como identificador de propiedad, y la ficha trae
 * `idPropietario`. Son tres saltos, todos por id, con el alcance resuelto en el
 * backend.
 *
 * Otras divergencias deliberadas:
 * - **No hay "Exportar PDF"** (D-F5-1): los cinco endpoints Jasper quedaron
 *   fuera del alcance de la migración. Tampoco el aviso que invitaba a
 *   exportar.
 * - **La descripción es la de la propiedad**, no un párrafo generado. El Blazor
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
  imports: [EstadoListado, ImagenSegura, SubidaArchivo, BroxPageHeader],
  templateUrl: './ficha-propiedad.html',
  styleUrl: './ficha-propiedad.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FichaPropiedad implements OnInit {
  private readonly captaciones = inject(CaptacionesService);
  private readonly propiedades = inject(PropiedadesService);
  private readonly propietarios = inject(PropietariosService);
  private readonly archivos = inject(ArchivosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargar$ = new Subject<void>();

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly captacion = signal<Captacion | null>(null);
  protected readonly propiedad = signal<Bloque<PropiedadFicha | null>>(bloque(null));
  protected readonly precios = signal<Bloque<readonly HitoDeLaHistoria[]>>(bloque([]));
  protected readonly fotos = signal<Bloque<readonly FotoPropiedad[]>>(bloque([]));
  protected readonly propietario = signal<Bloque<Propietario | null>>(bloque(null));

  protected readonly subiendo = signal(false);
  protected readonly eliminandoFoto = signal<number | null>(null);
  protected readonly mensajeFoto = signal<string | null>(null);
  protected readonly errorFoto = signal<string | null>(null);

  protected readonly SIN_DATO = SIN_DATO;
  protected readonly maximoFotos = MAXIMO_FOTOS;
  protected readonly extensionesFoto = ['.png', '.jpg', '.jpeg'] as const;

  /**
   * **Si esta persona puede escribir las fotos de este inmueble** (P0).
   *
   * Lo dice el backend en la ficha de la propiedad. Antes lo decidía esta línea:
   *
   * ```
   * this.auth.sesion()?.rol === 'AGENTE'
   * ```
   *
   * y dejó de ser cierta con V87: las fotos son la ficha del inmueble, así que
   * `POST`/`DELETE /locales/{id}/fotos` los escribe **su responsable**, no
   * cualquier agente. Con la copia vieja, «Agregar fotos» y «Eliminar» se
   * ofrecían a todo agente sobre toda propiedad y respondían **403 siempre** —
   * con las 26 propiedades de `dev` en FALTANTE, siempre de verdad.
   *
   * Es la misma regla, el mismo campo y el mismo texto que usa la ficha
   * universal: no hay dos copias porque no hay nada que copiar. La resuelve el
   * mismo método del Core que después deniega la escritura.
   *
   * Mientras la ficha carga vale `false`: no se ofrece una acción que todavía
   * no se sabe si existe.
   */
  protected readonly puedeEditarFotos = computed(() =>
    puedeEscribir(this.propiedad().datos?.responsabilidad),
  );

  /** Quién responde por el inmueble hoy, o `null` si está FALTANTE. */
  protected readonly responsableFotos = computed(
    () => this.propiedad().datos?.responsabilidad?.nombre ?? null,
  );

  /**
   * Por qué no se pueden tocar las fotos, **en las palabras del Core**.
   *
   * Se pinta. Esconder los botones sin decir por qué obliga a adivinar si falta
   * un permiso o falta un dato — que es exactamente lo que `exigirEdicion`
   * evita en el backend, y sería incoherente que la pantalla lo deshiciera.
   */
  protected readonly motivoSinFotos = computed(() =>
    motivoDeBloqueo(this.propiedad().datos?.responsabilidad),
  );

  protected readonly totalFotos = computed(() => this.fotos().datos.length);
  protected readonly quedanFotos = computed(() =>
    Math.max(0, MAXIMO_FOTOS - this.totalFotos()),
  );
  protected readonly galeriaLlena = computed(() => this.quedanFotos() === 0);
  protected readonly fotoOcupada = computed(
    () => this.subiendo() || this.eliminandoFoto() !== null,
  );

  /** Área de la captación; si no la trae, la de la propiedad. */
  protected readonly area = computed(
    () => this.captacion()?.areaM2 ?? valorNumerico(this.propiedad().datos, 'metraje_total'),
  );

  protected readonly rubro = computed(
    () => this.atributo('rubro_permitido')?.valor || this.captacion()?.rubro || 'Por definir',
  );

  /** Renta mensual de referencia, con su moneda pegada al valor. */
  protected readonly renta = computed<Importe | null>(() => {
    const captacion = this.captacion();
    const encargo = this.propiedad().datos?.encargos.find((item) => item.idEncargo === captacion?.id);
    const valor = captacion?.importeReferencia ?? encargo?.importe;
    return valor === null || valor === undefined
      ? null
      : { valor, moneda: captacion?.monedaReferencia ?? encargo?.moneda ?? '' };
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
    const l = this.propiedad().datos?.ubicacion;
    const lat = l?.latitud;
    const lon = l?.longitud;
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
    void this.router.navigate(['/propiedades']);
  }

  protected recargar(): void {
    this.recargar$.next();
  }

  protected verPropiedad(): void {
    const idPropiedad = this.captacion()?.idLocal;
    if (idPropiedad) {
      void this.router.navigate(['/propiedades', idPropiedad]);
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

  protected etiquetaTipoPersona(codigo: string | undefined): string {
    return describir(TIPO_PERSONA, codigo) || SIN_DATO;
  }

  /** `D`/`R`/`C`/`P` -> DNI/RUC/… El cable manda la letra, no el nombre. */
  protected documento(propietario: Propietario): string {
    const tipo = describir(TIPO_DOCUMENTO, propietario.tipoDocumento);
    const numero = texto(propietario.numeroDocumento);
    return tipo ? `${tipo} ${numero}` : numero;
  }

  protected fecha(valor: string | null | undefined): string {
    return fechaCorta(valor);
  }

  protected importe(valor: number | null | undefined, moneda?: string | null): string {
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

  protected valor(valor: string | null | undefined): string {
    return texto(valor);
  }

  /** El catálogo ya entregó el texto del atributo; esta pantalla solo lo muestra. */
  protected caracteristica(atributo: { valor?: string | null; moneda?: string | null; unidad?: string | null; tipoDato?: string | null }): string {
    const valor = atributo.valor?.trim();
    if (!valor) return SIN_DATO;
    if (atributo.moneda) return `${atributo.moneda} ${valor}`;
    return atributo.unidad ? `${valor} ${atributo.unidad}` : valor;
  }

  protected atributo(clave: string): { valor?: string | null } | undefined {
    return this.propiedad().datos?.atributos.find((item) => item.clave === clave);
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
    const idPropiedad = this.captacion()?.idLocal;
    if (!idPropiedad) {
      this.errorFoto.set('La captación no tiene una propiedad asociada.');
      return;
    }
    if (elegidas.length > this.quedanFotos()) {
      this.errorFoto.set(
        `Solo caben ${this.quedanFotos()} foto(s) más: el máximo por propiedad es ${MAXIMO_FOTOS}.`,
      );
      return;
    }

    this.subiendo.set(true);
    this.limpiarAvisosFoto();
    let subidas = 0;
    try {
      // En serie a propósito: el tope lo comprueba el backend por
      // petición, así que en paralelo dos altas podrían pasar la validación a
      // la vez y una fallaría a mitad de tanda.
      for (const elegida of elegidas) {
        const contenido = await this.archivos.base64(elegida.archivo);
        await this.propiedades.subirFoto(idPropiedad, elegida.nombreSeguro, contenido);
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
      await this.refrescarFotos(idPropiedad);
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
    const idPropiedad = this.captacion()?.idLocal;
    if (!idPropiedad) {
      return;
    }

    this.eliminandoFoto.set(idFoto);
    this.limpiarAvisosFoto();
    try {
      await this.propiedades.eliminarFoto(idPropiedad, idFoto);
      this.mensajeFoto.set('Foto eliminada del registro y del almacén.');
      await this.refrescarFotos(idPropiedad);
    } catch (error) {
      this.errorFoto.set(
        error instanceof ApiError ? error.message : 'No se pudo eliminar la foto.',
      );
    } finally {
      this.eliminandoFoto.set(null);
    }
  }

  private async refrescarFotos(idPropiedad: number): Promise<void> {
    try {
      this.fotos.set(bloque(await this.propiedades.fotos(idPropiedad)));
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
  // Carga: captación (fatal) -> propiedad universal (incluye la galería) -> propietario
  // =====================================================================

  private cargar(codigo: string): Observable<unknown> {
    if (!codigo) {
      this.publicar(null, bloque(null), bloque([]), bloque(null));
      this.error.set('No se indicó el código de la captación.');
      return of(null);
    }

    return this.captaciones.obtenerPorCodigo$(codigo).pipe(
      switchMap((captacion) =>
        this.complementosDe(captacion).pipe(
          switchMap((complementos) =>
            this.propietarioDe(complementos.propiedad.datos).pipe(
              tap((propietario) =>
                this.publicar(
                  captacion,
                  complementos.propiedad,
                  complementos.fotos,
                  propietario,
                ),
              ),
            ),
          ),
        ),
      ),
      catchError((error) => {
        this.publicar(null, bloque(null), bloque([]), bloque(null));
        this.error.set(mensajeDeCarga(error, codigo));
        return of(null);
      }),
    );
  }

  /** La ficha universal ya trae la propiedad y su galería en una sola lectura. */
  private complementosDe(captacion: Captacion): Observable<Complementos> {
    const idPropiedad = captacion.idLocal;
    if (!idPropiedad) {
      return of({
        propiedad: bloque<PropiedadFicha | null>(
          null,
          'La captación no tiene una propiedad asociada, así que no hay ficha técnica.',
        ),
        fotos: bloque<readonly FotoPropiedad[]>([]),
      });
    }
    return forkJoin({
      propiedad: complementario<PropiedadFicha | null>(
        this.propiedades.consultar$(idPropiedad),
        null,
        'No se pudo leer la ficha técnica de la propiedad.',
      ),
      fotos: complementario<readonly FotoPropiedad[]>(
        this.propiedades.fotos$(idPropiedad),
        [],
        'No se pudo leer la galería.',
      ),
    });
  }

  /** El contacto titular sale de la relación universal, no de un registro legado. */
  private propietarioDe(propiedad: PropiedadFicha | null): Observable<Bloque<Propietario | null>> {
    const titular = propiedad?.titulares.find((item) => item.representante)
      ?? propiedad?.titulares[0];
    const idPropietario = titular?.idPropietario;
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
    propiedad: Bloque<PropiedadFicha | null>,
    fotos: Bloque<readonly FotoPropiedad[]>,
    propietario: Bloque<Propietario | null>,
  ): void {
    this.captacion.set(captacion);
    this.propiedad.set(propiedad);
    this.precios.set(bloque(propiedad.datos?.historia?.linea ?? []));
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

function valorNumerico(ficha: PropiedadFicha | null | undefined, clave: string): number | null {
  const valor = ficha?.atributos.find((atributo) => atributo.clave === clave)?.valor;
  if (valor == null || valor.trim() === '') return null;
  const numero = Number(valor);
  return Number.isFinite(numero) ? numero : null;
}
