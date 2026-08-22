import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import {
  Captacion,
  CaptacionesService,
  CaptacionRequest,
} from '../../core/api/captaciones.service';
import { Local, LocalesService } from '../../core/api/locales.service';
import { Prospeccion, ProspeccionesService } from '../../core/api/prospecciones.service';
import { AuthService } from '../../core/auth/auth.service';
import {
  calcularCondicionComision,
  comisionPactadaCompatible,
  descripcionCondicionComision,
  importeTexto,
} from '../../core/comision';
import { monto, numero, SIN_DATO, texto } from '../../core/formato';
import { POLITICA_COMERCIAL } from '../../core/politica-comercial';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

const LOCALES_POR_BUSQUEDA = 20;
type ModalidadComision = 'E05' | 'E1' | 'E15' | 'E2' | 'P' | 'F' | 'S';

/** `''` es «todavia no se ha dicho», y no vale para guardar. */
type Operacion = '' | 'A' | 'V';

/**
 * Las modalidades que caben en cada operacion.
 *
 * <p>Una venta no se comisiona en mensualidades: no hay renta que contar, y
 * «un mes de alquiler» sobre un precio de venta multiplicaria el precio entero
 * por uno. El backend lo rechaza desde V76 (`CondicionesEconomicas.exigirBaseCoherente`);
 * la lista de aqui existe para que no se pueda ni elegir, no para sustituir a
 * esa validacion.
 */
const MODALIDADES: Readonly<Record<'A' | 'V', readonly { valor: ModalidadComision; rotulo: string }[]>> = {
  A: [
    { valor: 'E05', rotulo: 'Medio mes de alquiler' },
    { valor: 'E1', rotulo: 'Un mes de alquiler' },
    { valor: 'E15', rotulo: 'Un mes y medio de alquiler' },
    { valor: 'E2', rotulo: 'Dos meses de alquiler' },
    { valor: 'P', rotulo: 'Porcentaje sobre la renta mensual' },
    { valor: 'F', rotulo: 'Monto fijo' },
    { valor: 'S', rotulo: 'Sin comisión' },
  ],
  V: [
    { valor: 'P', rotulo: 'Porcentaje sobre el precio de venta' },
    { valor: 'F', rotulo: 'Monto fijo' },
    { valor: 'S', rotulo: 'Sin comisión' },
  ],
};

/** Lo que la pantalla PROPONE al declarar la operacion; se confirma aparte. */
const MODALIDAD_HABITUAL: Readonly<Record<'A' | 'V', ModalidadComision>> = { A: 'E1', V: 'P' };

@Component({
  selector: 'app-captacion-form',
  imports: [EstadoListado, ReactiveFormsModule],
  templateUrl: './captacion-form.html',
  styleUrl: './captacion-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaptacionForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(CaptacionesService);
  private readonly prospecciones = inject(ProspeccionesService);
  private readonly localesApi = inject(LocalesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected captacionOriginal: Captacion | null = null;
  private captacionCreadaPendienteEnlace: Captacion | null = null;

  protected readonly cargando = signal(true);
  protected readonly cargandoLocales = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly errorGuardado = signal<string | null>(null);
  protected readonly idCaptacion = signal<number | null>(null);
  protected readonly prospeccionOrigen = signal<Prospeccion | null>(null);
  protected readonly localActual = signal<Local | null>(null);
  protected readonly locales = signal<readonly Local[]>([]);
  protected readonly totalLocales = signal(0);
  protected readonly busquedaLocal = this.fb.nonNullable.control('');

  protected readonly esEdicion = computed(() => this.idCaptacion() !== null);
  protected readonly localFijo = computed(
    () =>
      this.esEdicion() ||
      this.prospeccionOrigen() !== null ||
      idPositivo(this.route.snapshot.queryParamMap.get('local')) !== null,
  );
  protected readonly esSubsanacion = computed(
    () => this.esEdicion() && this.captacionOriginal?.estado === 'O',
  );
  protected readonly titulo = computed(() =>
    this.esEdicion()
      ? `Editar ${texto(this.captacionOriginal?.codigoCaptacion)}`
      : this.prospeccionOrigen()
        ? `Crear captación desde ${texto(this.prospeccionOrigen()?.codigoProspeccion)}`
        : 'Nueva captación',
  );
  protected readonly textoGuardar = computed(() => {
    if (this.captacionCreadaPendienteEnlace) return 'Reintentar vínculo';
    if (this.esSubsanacion()) return 'Guardar y reenviar a revisión';
    if (this.esEdicion()) return 'Guardar cambios';
    return 'Crear y enviar a revisión';
  });

  /**
   * Espejo en signal de lo que dice el control `operacion`.
   *
   * <p>El rotulo del importe, las modalidades ofrecidas y la base de calculo
   * dependen de ella, y con OnPush una lectura directa del FormControl no
   * repinta. Se actualiza en los dos unicos sitios donde la operacion cambia:
   * cuando el agente la elige y cuando se carga un encargo existente.
   */
  protected readonly operacionSel = signal<Operacion>('');

  protected readonly modalidades = computed(() => {
    const operacion = this.operacionSel();
    return operacion === '' ? [] : MODALIDADES[operacion];
  });

  protected readonly formulario = this.fb.group(
    {
      idLocal: this.fb.nonNullable.control(0, Validators.min(1)),
      // Sin defecto. Antes iba fija a alquiler --`motivoOperacion: 'A'`-- y esa
      // constante convertia cualquier encargo de venta en un alquiler con el
      // precio de venta metido en la casilla de la renta. El mismo importe es
      // una cosa u otra segun esta letra, asi que no se supone: se declara.
      operacion: this.fb.nonNullable.control<Operacion>('', Validators.required),
      fechaCaptacion: this.fb.nonNullable.control(hoyIso(), Validators.required),
      fechaInicioVigencia: this.fb.nonNullable.control(hoyIso(), Validators.required),
      fechaFinVigencia: this.fb.nonNullable.control(fechaEnMeses(POLITICA_COMERCIAL.encargoMesesPorDefecto), Validators.required),
      // El importe del encargo se ESCRIBE aquí (V75). Antes era un espejo
      // deshabilitado de `propiedad.precioReferencial`, y desde que una
      // propiedad puede existir sin encargo ese espejo llega vacío: la pantalla
      // no tenía dónde poner el precio que el propietario acaba de aceptar. Y
      // aunque lo tuviera, el importe pertenece al encargo: dos encargos de la
      // misma propiedad pueden pactar cifras distintas.
      importeReferencia: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
      monedaReferencia: this.fb.nonNullable.control<'PEN' | 'USD'>('PEN', Validators.required),
      modalidadComision: this.fb.nonNullable.control<ModalidadComision>('E1', Validators.required),
      valorComision: this.fb.control<number | null>(null, Validators.min(0)),
      monedaComision: this.fb.nonNullable.control<'PEN' | 'USD'>('PEN', Validators.required),
      tratamientoIgv: this.fb.nonNullable.control<'I' | 'A' | 'N'>('N', Validators.required),
      motivoSinComision: this.fb.nonNullable.control('', Validators.maxLength(300)),
      comisionConfirmada: this.fb.nonNullable.control(false, Validators.requiredTrue),
      urgencia: this.fb.nonNullable.control(3, [Validators.min(1), Validators.max(5)]),
      exclusividad: this.fb.nonNullable.control(false),
      observaciones: this.fb.nonNullable.control('', Validators.maxLength(2000)),
    },
    { validators: [vigenciaValida, condicionComisionValida] },
  );

  ngOnInit(): void {
    void this.cargar();
  }

  protected invalido(nombre: keyof typeof this.formulario.controls): boolean {
    const control = this.formulario.controls[nombre];
    return control.invalid && (control.touched || control.dirty);
  }

  protected cancelar(): void {
    const origen = this.prospeccionOrigen()?.id;
    void this.router.navigate(origen ? ['/prospecciones', origen] : ['/captaciones']);
  }

  protected reintentar(): void {
    void this.cargar();
  }

  protected async buscarLocales(): Promise<void> {
    if (!this.localFijo()) await this.cargarLocales(this.busquedaLocal.value.trim());
  }

  /**
   * La operacion la elige quien capta, y arrastra la modalidad.
   *
   * <p>Si la modalidad que estaba puesta no cabe en la nueva operacion se
   * sustituye por la habitual de esa operacion; dejarla seria ofrecer «dos
   * meses de alquiler» como comision de una venta.
   */
  protected elegirOperacion(): void {
    const operacion = this.formulario.controls.operacion.value;
    this.operacionSel.set(operacion);
    if (operacion === '') return;
    const admitidas = MODALIDADES[operacion].map((modalidad) => modalidad.valor);
    if (!admitidas.includes(this.formulario.controls.modalidadComision.value)) {
      this.formulario.controls.modalidadComision.setValue(MODALIDAD_HABITUAL[operacion]);
    }
  }

  protected seleccionarLocal(): void {
    const id = this.formulario.controls.idLocal.value;
    this.localActual.set(this.locales().find((local) => local.id === id) ?? null);
  }

  protected descripcionLocal(): string {
    const local = this.localActual();
    if (!local) return SIN_DATO;
    return `${texto(local.codigoLocal)} · ${texto(local.direccion)} (${texto(local.distrito)})`;
  }

  protected areaLocal(): string {
    const metraje = this.localActual()?.metraje;
    return metraje === undefined ? SIN_DATO : `${numero(metraje)} m²`;
  }

  protected precioLocal(): string {
    const local = this.localActual();
    return monto(local?.precioReferencial, local?.monedaReferencial);
  }

  /**
   * El rótulo del importe dice **qué** número es, y lo dice la OPERACIÓN.
   *
   * <p>Un precio de venta rotulado «renta mensual» es un error de bulto: son
   * dos órdenes de magnitud de diferencia y la comisión se calcula sobre él.
   * Mientras no se haya declarado la operación el rótulo no adivina ninguna de
   * las dos.
   */
  protected rotuloDelImporte(): string {
    const operacion = this.operacionSel();
    if (operacion === 'A') return 'Renta mensual';
    if (operacion === 'V') return 'Precio de venta';
    return 'Importe del encargo';
  }

  /** «renta» / «precio de venta», para los textos que hablan de la base. */
  protected baseEnPalabras(): string {
    return this.operacionSel() === 'V' ? 'el precio de venta' : 'la renta mensual';
  }

  protected requiereValorComision(): boolean {
    return ['P', 'F'].includes(this.formulario.controls.modalidadComision.value);
  }

  protected sinComision(): boolean {
    return this.formulario.controls.modalidadComision.value === 'S';
  }

  protected descripcionComision(): string {
    return descripcionCondicionComision(this.condicionFormulario());
  }

  protected importeComision(): string {
    return importeTexto(calcularCondicionComision(this.condicionFormulario()));
  }

  protected valor(valor: string | undefined): string {
    return texto(valor);
  }

  protected pasoLocal(): boolean {
    return this.formulario.controls.idLocal.valid && this.localActual() !== null;
  }

  protected pasoCondiciones(): boolean {
    return this.formulario.controls.operacion.valid && this.formulario.controls.urgencia.valid;
  }

  protected pasoVigencia(): boolean {
    return (
      this.formulario.controls.fechaCaptacion.valid &&
      this.formulario.controls.fechaInicioVigencia.valid &&
      this.formulario.controls.fechaFinVigencia.valid &&
      this.formulario.controls.modalidadComision.valid &&
      this.formulario.controls.valorComision.valid &&
      this.formulario.controls.monedaComision.valid &&
      this.formulario.controls.tratamientoIgv.valid &&
      this.formulario.controls.motivoSinComision.valid &&
      this.formulario.controls.comisionConfirmada.valid &&
      !this.formulario.hasError('vigencia') &&
      !this.formulario.hasError('comision')
    );
  }

  protected async guardar(): Promise<void> {
    if (this.guardando()) return;
    if (this.formulario.invalid || !this.localActual()) {
      this.formulario.markAllAsTouched();
      this.errorGuardado.set('Revisa el local, las fechas y los valores obligatorios.');
      return;
    }

    this.guardando.set(true);
    this.errorGuardado.set(null);
    try {
      if (this.captacionCreadaPendienteEnlace) {
        await this.enlazarProspeccion(this.captacionCreadaPendienteEnlace);
      } else if (this.esEdicion()) {
        await this.api.actualizar(this.idCaptacion()!, this.datosParaGuardar());
      } else {
        const creada = await this.api.registrar(this.datosParaGuardar());
        if (this.prospeccionOrigen()) {
          this.captacionCreadaPendienteEnlace = creada;
          await this.enlazarProspeccion(creada);
        }
      }
      await this.router.navigate(['/captaciones'], {
        queryParams: { estado: 'P' },
        replaceUrl: true,
      });
    } catch (error) {
      const creada = this.captacionCreadaPendienteEnlace;
      this.errorGuardado.set(
        creada
          ? `La captación ${texto(creada.codigoCaptacion)} se creó, pero no se pudo vincular a la prospección. Reintenta el vínculo sin volver a crearla. ${mensajeError(error, '')}`.trim()
          : mensajeError(error, 'No se pudo guardar la captación.'),
      );
    } finally {
      this.guardando.set(false);
    }
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.errorCarga.set(null);
    this.errorGuardado.set(null);
    this.captacionCreadaPendienteEnlace = null;
    try {
      const codigo = this.route.snapshot.paramMap.get('codigo');
      const idProspeccion = idPositivo(this.route.snapshot.queryParamMap.get('prospeccion'));
      const idLocal = idPositivo(this.route.snapshot.queryParamMap.get('local'));
      if (codigo) {
        await this.cargarEdicion(codigo);
      } else if (idProspeccion) {
        await this.cargarDesdeProspeccion(idProspeccion);
      } else if (idLocal) {
        await this.fijarLocal(await this.localesApi.obtener(idLocal));
      } else {
        await this.cargarLocales('');
      }
    } catch (error) {
      this.errorCarga.set(mensajeError(error, 'No se pudo cargar el formulario.'));
    } finally {
      this.cargando.set(false);
    }
  }

  private async cargarEdicion(codigo: string): Promise<void> {
    const captacion = await this.api.obtenerPorCodigo(codigo);
    if (!['P', 'O'].includes(captacion.estado ?? '')) {
      throw new Error('Solo se puede editar una captación pendiente u observada.');
    }
    if (!captacion.idLocal) throw new Error('La captación no tiene un local asociado.');
    this.captacionOriginal = captacion;
    this.idCaptacion.set(captacion.id);
    await this.fijarLocal(await this.localesApi.obtener(captacion.idLocal));
    // La operacion del encargo que se edita, tal como la guardo el backend. El
    // espejo heredado `motivoOperacion` solo se consulta si el tipado no vino.
    const operacion = operacionValida(captacion.tipoOperacion)
      ?? operacionValida(captacion.motivoOperacion)
      ?? '';
    this.operacionSel.set(operacion);
    this.formulario.patchValue({
      operacion,
      fechaCaptacion: captacion.fechaCaptacion ?? hoyIso(),
      fechaInicioVigencia: captacion.fechaInicioVigencia ?? hoyIso(),
      fechaFinVigencia: captacion.fechaFinVigencia ?? fechaEnMeses(POLITICA_COMERCIAL.encargoMesesPorDefecto),
      // Al editar se recupera lo que el encargo pacto, no lo que el inmueble
      // proyecta: son cosas distintas desde V75 y la propiedad puede no tener
      // ninguna.
      importeReferencia: captacion.importeReferencia
        ?? this.localActual()?.precioReferencial
        ?? null,
      monedaReferencia: monedaValida(captacion.monedaReferencia)
        ?? monedaValida(this.localActual()?.monedaReferencial)
        ?? 'PEN',
      modalidadComision: modalidadDesde(captacion),
      valorComision: valorEditableDesde(captacion),
      monedaComision: monedaValida(captacion.monedaComision)
        ?? monedaValida(captacion.monedaReferencia)
        ?? monedaValida(this.localActual()?.monedaReferencial)
        ?? 'PEN',
      tratamientoIgv: tratamientoIgvValido(captacion.tratamientoIgv),
      motivoSinComision: captacion.motivoSinComision ?? '',
      comisionConfirmada: false,
      urgencia: captacion.urgencia ?? 3,
      exclusividad: captacion.exclusividad ?? false,
      observaciones: captacion.observaciones ?? '',
    });
  }

  private async cargarDesdeProspeccion(id: number): Promise<void> {
    const prospeccion = await this.prospecciones.obtener(id);
    if (!['E', 'S'].includes(prospeccion.estado ?? '')) {
      throw new Error('La prospección debe tener una propuesta entregada y estar en seguimiento.');
    }
    if (!prospeccion.localId) throw new Error('La prospección no tiene un local asociado.');
    this.prospeccionOrigen.set(prospeccion);
    await this.fijarLocal(await this.localesApi.obtener(prospeccion.localId));
  }

  private async fijarLocal(local: Local): Promise<void> {
    this.localActual.set(local);
    this.locales.set([local]);
    this.totalLocales.set(1);
    this.formulario.controls.idLocal.setValue(local.id);
    const moneda = monedaValida(local.monedaReferencial);
    if (moneda && !this.esEdicion()) {
      this.formulario.controls.monedaComision.setValue(moneda);
      this.formulario.controls.monedaReferencia.setValue(moneda);
    }
    // El último precio registrado se PROPONE como punto de partida; no se
    // hereda. Si la propiedad nunca tuvo encargo llega vacío y el campo se
    // queda esperando lo que el propietario acaba de aceptar (V75).
    if (!this.esEdicion() && local.precioReferencial != null) {
      this.formulario.controls.importeReferencia.setValue(local.precioReferencial);
    }
  }

  private async cargarLocales(textoBusqueda: string): Promise<void> {
    this.cargandoLocales.set(true);
    try {
      const pagina = await this.localesApi.pagina({
        page: 1,
        tamano: LOCALES_POR_BUSQUEDA,
        texto: textoBusqueda || undefined,
        estado: 'D',
      });
      this.locales.set(pagina.items);
      this.totalLocales.set(pagina.totalRecords);
      const idActual = this.formulario.controls.idLocal.value;
      this.localActual.set(pagina.items.find((local) => local.id === idActual) ?? null);
    } catch (error) {
      this.errorGuardado.set(mensajeError(error, 'No se pudieron buscar los locales.'));
    } finally {
      this.cargandoLocales.set(false);
    }
  }

  private datosParaGuardar(): CaptacionRequest {
    const valor = this.formulario.getRawValue();
    const sesion = this.auth.sesion();
    if (!sesion) throw new Error('La sesión ya no está disponible.');
    const operacion = valor.operacion;
    if (operacion === '') throw new Error('Declara si el encargo es de alquiler o de venta.');
    const condicion = this.condicionFormulario();
    if (!condicion.tipoComision || !condicion.baseCalculo
        || condicion.valorComision === null || !condicion.monedaComision
        || condicion.importeReferencia === null || !condicion.monedaReferencia) {
      throw new Error('La condición económica no está completa.');
    }
    return {
      codigoCaptacion: this.captacionOriginal?.codigoCaptacion ?? generarCodigoCaptacion(),
      fechaCaptacion: valor.fechaCaptacion,
      fechaInicioVigencia: valor.fechaInicioVigencia,
      fechaFinVigencia: valor.fechaFinVigencia,
      comisionPactada: comisionPactadaCompatible(
        condicion.tipoComision,
        condicion.valorComision,
      ),
      observaciones: textoOpcional(valor.observaciones),
      idLocal: valor.idLocal,
      idAgente: this.captacionOriginal?.idAgente ?? sesion.idDominio,
      // Las dos columnas dicen lo mismo porque el backend exige que coincidan;
      // lo que ya no hacen es decir 'A' pase lo que pase.
      motivoOperacion: operacion,
      urgencia: valor.urgencia,
      exclusividad: valor.exclusividad,
      tipoOperacion: operacion,
      importeReferencia: condicion.importeReferencia,
      monedaReferencia: condicion.monedaReferencia,
      tipoComision: condicion.tipoComision,
      baseCalculo: condicion.baseCalculo,
      valorComision: condicion.valorComision,
      monedaComision: condicion.monedaComision,
      tratamientoIgv: valor.tratamientoIgv,
      motivoSinComision: condicion.motivoSinComision ?? null,
    };
  }

  private condicionFormulario(): {
    tipoComision: 'E' | 'P' | 'F' | null;
    baseCalculo: 'R' | 'V' | 'N' | null;
    valorComision: number | null;
    monedaComision: 'PEN' | 'USD' | null;
    importeReferencia: number | null;
    monedaReferencia: 'PEN' | 'USD' | null;
    motivoSinComision: string | null;
  } {
    const valor = this.formulario.getRawValue();
    // Lo que se escribió en el formulario, no lo que proyecta el inmueble: el
    // importe es del ENCARGO (V75).
    const monedaReferencia = monedaValida(valor.monedaReferencia);
    const importeReferencia = valor.importeReferencia;
    const modalidad = valor.modalidadComision;
    // La base la DERIVA la operación, no se elige por separado: un porcentaje
    // sobre «renta mensual» en un encargo de venta apuntaría a un importe que
    // ese encargo no pactó. Espeja `CondicionesEconomicas.basePorDefecto`.
    const base = valor.operacion === 'V' ? 'V' : 'R';
    if (modalidad.startsWith('E')) {
      return {
        tipoComision: 'E', baseCalculo: 'R',
        valorComision: ({ E05: 0.5, E1: 1, E15: 1.5, E2: 2 } as const)[modalidad as 'E05' | 'E1' | 'E15' | 'E2'],
        monedaComision: monedaReferencia, importeReferencia, monedaReferencia,
        motivoSinComision: null,
      };
    }
    if (modalidad === 'P') {
      return {
        tipoComision: 'P', baseCalculo: base, valorComision: valor.valorComision,
        monedaComision: monedaReferencia, importeReferencia, monedaReferencia,
        motivoSinComision: null,
      };
    }
    return {
      tipoComision: 'F', baseCalculo: 'N',
      valorComision: modalidad === 'S' ? 0 : valor.valorComision,
      monedaComision: valor.monedaComision,
      importeReferencia, monedaReferencia,
      motivoSinComision: modalidad === 'S' ? textoOpcional(valor.motivoSinComision) : null,
    };
  }

  private async enlazarProspeccion(captacion: Captacion): Promise<void> {
    const prospeccion = this.prospeccionOrigen();
    if (!prospeccion || !captacion.codigoCaptacion) return;
    await this.prospecciones.marcarCaptada(
      prospeccion.id,
      captacion.id,
      captacion.codigoCaptacion,
    );
    this.captacionCreadaPendienteEnlace = null;
  }
}

export function generarCodigoCaptacion(fecha = new Date()): string {
  return `CAP-${fecha.getUTCFullYear().toString().slice(-2)}${dos(fecha.getUTCMonth() + 1)}${dos(fecha.getUTCDate())}${dos(fecha.getUTCHours())}${dos(fecha.getUTCMinutes())}${dos(fecha.getUTCSeconds())}${fecha.getUTCMilliseconds().toString().padStart(3, '0')}`;
}

function hoyIso(): string {
  return isoLocal(new Date());
}

function fechaEnMeses(meses: number): string {
  const fecha = new Date();
  fecha.setMonth(fecha.getMonth() + meses);
  return isoLocal(fecha);
}

function isoLocal(fecha: Date): string {
  return `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}`;
}

function dos(valor: number): string {
  return String(valor).padStart(2, '0');
}

function idPositivo(valor: string | null): number | null {
  const id = Number(valor);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

function textoOpcional(valor: string): string | null {
  const limpio = valor.trim();
  return limpio || null;
}

/**
 * El backend exige fin ESTRICTAMENTE posterior al inicio (`validarEncargo`:
 * "La fecha final del encargo debe ser posterior a la inicial."). Con `<` el
 * formulario dejaba pasar fin == inicio y el 400 llegaba desde el servidor.
 */
function vigenciaValida(control: AbstractControl): ValidationErrors | null {
  const inicio = control.get('fechaInicioVigencia')?.value as string | undefined;
  const fin = control.get('fechaFinVigencia')?.value as string | undefined;
  return inicio && fin && fin <= inicio ? { vigencia: true } : null;
}

function condicionComisionValida(control: AbstractControl): ValidationErrors | null {
  const modalidad = control.get('modalidadComision')?.value as ModalidadComision | undefined;
  const operacion = control.get('operacion')?.value as Operacion | undefined;
  const valor = control.get('valorComision')?.value as number | null | undefined;
  const motivo = control.get('motivoSinComision')?.value as string | undefined;
  if ((modalidad === 'P' || modalidad === 'F')
      && (valor === null || valor === undefined || !Number.isFinite(valor) || valor <= 0)) {
    return { comision: true };
  }
  if (modalidad === 'S' && !motivo?.trim()) return { comision: true };
  // Una venta no se comisiona en mensualidades. La lista de opciones ya no la
  // ofrece, pero un formulario cargado desde un encargo antiguo si puede traer
  // la combinacion: se detiene aqui y no en el 400 del servidor.
  if (operacion === 'V' && modalidad?.startsWith('E')) return { comision: true };
  return null;
}

function modalidadDesde(captacion: Captacion): ModalidadComision {
  const tipo = captacion.tipoComision;
  const valor = captacion.valorComision;
  if (tipo === 'E') {
    const encontrada = ({ 0.5: 'E05', 1: 'E1', 1.5: 'E15', 2: 'E2' } as const)[
      valor as 0.5 | 1 | 1.5 | 2
    ];
    return encontrada ?? 'P';
  }
  if (tipo === 'F') return valor === 0 && !!captacion.motivoSinComision ? 'S' : 'F';
  return tipo === 'P' ? 'P' : 'E1';
}

function valorEditableDesde(captacion: Captacion): number | null {
  if (captacion.tipoComision === 'E'
      && ![0.5, 1, 1.5, 2].includes(captacion.valorComision ?? Number.NaN)) {
    return (captacion.valorComision ?? 0) * 100;
  }
  return captacion.tipoComision === 'P' || captacion.tipoComision === 'F'
    ? captacion.valorComision ?? null
    : null;
}

function monedaValida(valor: string | null | undefined): 'PEN' | 'USD' | null {
  return valor === 'PEN' || valor === 'USD' ? valor : null;
}

/** `null` cuando no dice nada reconocible; nunca «pues alquiler». */
function operacionValida(valor: string | null | undefined): 'A' | 'V' | null {
  return valor === 'A' || valor === 'V' ? valor : null;
}

function tratamientoIgvValido(valor: string | null | undefined): 'I' | 'A' | 'N' {
  return valor === 'I' || valor === 'A' ? valor : 'N';
}

function mensajeError(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
