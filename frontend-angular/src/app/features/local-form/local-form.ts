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
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { CapturaService, RestriccionesCampo } from '../../core/api/captura.service';
import {
  Local,
  LocalesService,
  LocalRequest,
  PosibleDuplicadoLocal,
} from '../../core/api/locales.service';
import {
  DatosPropietario,
  Propietario,
  PropietariosService,
} from '../../core/api/propietarios.service';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import {
  DISTRITOS_LIMA,
  ESTADOS_LOCAL_FORM,
  RUBROS_COMERCIALES,
  TIPOS_INMUEBLE,
} from './catalogos-local';

const PROPIETARIOS_POR_PAGINA = 50;

/**
 * La operacion de este formulario. `/locales` es el alta heredada: un local
 * comercial EN ALQUILER, igual que declara `LocalComercialServiceImpl`.
 */
const OPERACION_DEL_ALTA = 'ALQUILER';

/**
 * Campo del formulario -> clave del catalogo, para los que el catalogo gobierna.
 *
 * <p>Nombrar la clave NO es saber donde vive: es la unica forma de pedir un
 * dato. Lo que este mapa evita es lo contrario — que el formulario decida el
 * RANGO de cada campo, que es una regla y ya tiene dueno (D-E4-3).
 *
 * <p>`numeroEstacionamientos` se llama `estacionamientos` en el catalogo: el
 * nombre del campo del cable y el de la clave no tienen por que coincidir, y
 * por eso esto es un mapa y no una lista.
 */
const CLAVES_DEL_CATALOGO: ReadonlyArray<readonly [string, string]> = [
  ['ambientes', 'ambientes'],
  ['antiguedadAnios', 'antiguedad_anios'],
  ['frente', 'frente'],
  ['numeroEstacionamientos', 'estacionamientos'],
  ['cuotaMantenimiento', 'cuota_mantenimiento'],
];

/** Los que se piden como enteros; el resto admite decimales. */
const ENTEROS_DEL_CATALOGO: ReadonlySet<string> = new Set([
  'ambientes',
  'antiguedadAnios',
  'numeroEstacionamientos',
]);

/** Mismo criterio que el alta completa: DNI 8, RUC 11. */
const LARGO_DOCUMENTO: Readonly<Record<string, number>> = { D: 8, R: 11 };

@Component({
  selector: 'app-local-form',
  imports: [ReactiveFormsModule, EstadoListado],
  templateUrl: './local-form.html',
  styleUrl: './local-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LocalForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly locales = inject(LocalesService);
  private readonly captura = inject(CapturaService);
  private readonly propietariosApi = inject(PropietariosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private localOriginal: Local | null = null;
  private paginaPropietarios = 0;

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly cargandoPropietarios = signal(false);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly errorGuardado = signal<string | null>(null);
  protected readonly posiblesDuplicados = signal<readonly PosibleDuplicadoLocal[]>([]);
  protected readonly duplicadosRevisados = signal(false);
  protected readonly idLocal = signal<number | null>(null);
  protected readonly propietarios = signal<readonly Propietario[]>([]);
  protected readonly totalPropietarios = signal(0);
  protected readonly busquedaPropietario = signal('');

  protected readonly esEdicion = computed(() => this.idLocal() !== null);
  protected readonly hayMasPropietarios = computed(
    () => this.propietarios().length < this.totalPropietarios(),
  );
  protected readonly propietariosVisibles = computed(() => {
    const texto = normalizar(this.busquedaPropietario());
    if (!texto) {
      return this.propietarios();
    }
    return this.propietarios().filter((propietario) =>
      normalizar(
        `${propietario.nombre} ${propietario.numeroDocumento} ${propietario.tipoPersona}`,
      ).includes(texto),
    );
  });
  protected propietarioSeleccionado(): Propietario | null {
    const id = this.formulario.controls.idPropietario.value;
    return this.propietarios().find((propietario) => propietario.id === id) ?? null;
  }

  protected readonly estados = ESTADOS_LOCAL_FORM;
  protected readonly tiposInmueble = TIPOS_INMUEBLE;
  protected readonly distritos = DISTRITOS_LIMA;
  protected readonly rubros = RUBROS_COMERCIALES;

  protected readonly formulario = this.fb.group({
    idPropietario: this.fb.nonNullable.control(0, [Validators.min(1)]),
    direccion: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(255)]),
    zonaUrbanizacion: this.fb.nonNullable.control('', Validators.maxLength(160)),
    distrito: this.fb.nonNullable.control('Miraflores', Validators.required),
    nombreEdificioGaleria: this.fb.nonNullable.control('', Validators.maxLength(160)),
    interiorUnidad: this.fb.nonNullable.control('', Validators.maxLength(80)),
    piso: this.fb.nonNullable.control('', Validators.maxLength(40)),
    referenciaInterna: this.fb.nonNullable.control('', Validators.maxLength(120)),
    geoLat: this.fb.control<number | null>(null, [Validators.min(-90), Validators.max(90)]),
    geoLong: this.fb.control<number | null>(null, [Validators.min(-180), Validators.max(180)]),
    metraje: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    rubroPermitido: this.fb.nonNullable.control(RUBROS_COMERCIALES[0], Validators.required),
    precioReferencial: this.fb.control<number | null>(null, [
      Validators.required,
      Validators.min(0),
    ]),
    monedaReferencial: this.fb.nonNullable.control('', Validators.required),
    estado: this.fb.nonNullable.control('D', Validators.required),
    tipoInmueble: this.fb.nonNullable.control('L', Validators.required),
    // Los gobernados nacen SIN minimo: se lo pone el catalogo (D-E4-3).
    //
    // Aqui vivian `enteroOpcional(0)`, `enteroOpcional(1)` y `Validators.min(0)`,
    // una copia a mano de reglas que el catalogo ya declara. Desde que las
    // declara el catalogo, esta copia era una segunda autoridad sobre la misma
    // regla; ahora el limite llega por contrato y lo aplica
    // `aplicarRestricciones()`.
    //
    // `entero()` si se queda: que un numero no admita decimales es el TIPO DE
    // DATO funcional, y eso el cliente si puede conocerlo.
    antiguedadAnios: this.fb.control<number | null>(null, [entero()]),
    ambientes: this.fb.control<number | null>(null, [entero()]),
    descripcion: this.fb.nonNullable.control('', Validators.maxLength(2000)),
    frente: this.fb.control<number | null>(null),
    zonificacion: this.fb.nonNullable.control('', Validators.maxLength(80)),
    numeroEstacionamientos: this.fb.control<number | null>(null, [entero()]),
    cargaElectricaKw: this.fb.control<number | null>(null, Validators.min(0)),
    cuotaMantenimiento: this.fb.control<number | null>(null),
    aptoLicencia: this.fb.nonNullable.control(''),
  });

  /* ---- Alta en contexto (D-E2-3 §3.1) ----
     Cuatro campos y ni uno mas. El resto de la ficha se completa despues en
     el catalogo: aqui el objetivo es no romper el hilo de quien esta
     registrando el local. */
  protected readonly altaAbierta = signal(false);
  protected readonly altaGuardando = signal(false);
  protected readonly altaError = signal<string | null>(null);
  protected readonly altaJuridica = signal(false);

  protected readonly formAlta = this.fb.group({
    tipoPersona: this.fb.nonNullable.control<'N' | 'J'>('N', Validators.required),
    tipoDocumento: this.fb.nonNullable.control<'D' | 'R' | 'C' | 'P'>('D', Validators.required),
    numeroDocumento: this.fb.nonNullable.control('', Validators.required),
    nombre: this.fb.nonNullable.control('', Validators.required),
    telefono: this.fb.nonNullable.control('', [Validators.required, Validators.pattern(/^\d{9}$/)]),
  });

  /** La busqueda no encontro a nadie y hay algo escrito: hay que ofrecerlo. */
  protected readonly sinCoincidencias = computed(
    () => this.busquedaPropietario().trim().length > 0 && this.propietariosVisibles().length === 0,
  );

  protected readonly pasoPropietario = signal(false);
  protected readonly pasoUbicacion = signal(false);
  protected readonly pasoCaracteristicas = signal(false);

  ngOnInit(): void {
    this.formulario.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.posiblesDuplicados.set([]);
        this.duplicadosRevisados.set(false);
        this.actualizarPasos();
      });

    // El minimo de cada campo lo declara el catalogo, y el catalogo se consulta
    // por tipo de propiedad: se vuelve a pedir cuando el tipo cambia.
    this.formulario.controls.tipoInmueble.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((tipo) => void this.aplicarRestricciones(tipo));
    void this.aplicarRestricciones(this.formulario.controls.tipoInmueble.value);

    void this.cargar();
  }

  /** Lo que el catalogo declaro, por clave. Vacio hasta que responde. */
  protected readonly restricciones = signal<ReadonlyMap<string, RestriccionesCampo>>(new Map());

  /** El `min` del input, para que la plantilla tampoco lo lleve escrito. */
  protected minimoDe(campo: string): number | null {
    const clave = CLAVES_DEL_CATALOGO.find(([formulario]) => formulario === campo)?.[1];
    return clave ? (this.restricciones().get(clave)?.minimo ?? null) : null;
  }

  /**
   * Los minimos, tomados del catalogo en vez de escritos aqui.
   *
   * El formulario llevaba `ambientes >= 1` y `>= 0` en otros cuatro: una copia
   * a mano de reglas que hoy declara el catalogo y el motor de captura publica
   * en `restricciones`. Mantener la copia dejaba la regla con dos duenos, y
   * divergiendo desde el primer tenant que cambiara la suya.
   *
   * Si la peticion falla NO se inventa un minimo. El campo se queda sin
   * validacion de rango en el cliente y el backend rechaza igual, con su
   * mensaje. Es preferible a validar con un numero adivinado: un minimo
   * equivocado bloquea un dato correcto, y eso el usuario no puede resolverlo.
   */
  private async aplicarRestricciones(tipoInmueble: string): Promise<void> {
    let declaradas: ReadonlyMap<string, RestriccionesCampo>;
    try {
      declaradas = await this.captura.restriccionesPorClave(tipoInmueble, OPERACION_DEL_ALTA);
    } catch {
      return;
    }

    for (const [campo, clave] of CLAVES_DEL_CATALOGO) {
      const control = this.formulario.get(campo);
      const minimo = declaradas.get(clave)?.minimo;
      if (!control) {
        continue;
      }
      const validadores = ENTEROS_DEL_CATALOGO.has(campo) ? [entero()] : [];
      if (minimo !== null && minimo !== undefined) {
        validadores.push(Validators.min(minimo));
      }
      control.setValidators(validadores);
      control.updateValueAndValidity({ emitEvent: false });
    }
    this.restricciones.set(declaradas);
  }

  protected seleccionarPropietario(id: number): void {
    if (this.esEdicion()) {
      return;
    }
    this.formulario.controls.idPropietario.setValue(id);
    this.formulario.controls.idPropietario.markAsTouched();
  }

  protected buscarPropietario(evento: Event): void {
    this.busquedaPropietario.set((evento.target as HTMLInputElement).value);
  }

  /**
   * Abre el alta en contexto con lo que el agente ya escribio en la
   * busqueda: si tecleo un nombre va al nombre, y si tecleo digitos va al
   * documento. No se le hace escribirlo dos veces.
   */
  protected abrirAlta(): void {
    const escrito = this.busquedaPropietario().trim();
    this.formAlta.reset({
      tipoPersona: 'N',
      tipoDocumento: 'D',
      numeroDocumento: /^\d+$/.test(escrito) ? escrito : '',
      nombre: /^\d+$/.test(escrito) ? '' : escrito,
      telefono: '',
    });
    this.altaJuridica.set(false);
    this.altaError.set(null);
    this.altaAbierta.set(true);
  }

  protected cerrarAlta(): void {
    this.altaAbierta.set(false);
    this.altaError.set(null);
  }

  /** Persona juridica: el documento es RUC y deja de elegirse. */
  protected cambiarTipoPersona(tipo: 'N' | 'J'): void {
    this.formAlta.controls.tipoPersona.setValue(tipo);
    this.altaJuridica.set(tipo === 'J');
    const documento = this.formAlta.controls.tipoDocumento;
    if (tipo === 'J') {
      documento.setValue('R');
      documento.disable();
    } else {
      if (documento.value === 'R') documento.setValue('D');
      documento.enable();
    }
  }

  protected altaInvalido(nombre: keyof typeof this.formAlta.controls): boolean {
    const control = this.formAlta.controls[nombre];
    return control.invalid && (control.touched || control.dirty);
  }

  protected etiquetaDocumentoAlta(): string {
    return { D: 'DNI', R: 'RUC', C: 'Carné de extranjería', P: 'Pasaporte' }[
      this.formAlta.controls.tipoDocumento.value
    ]!;
  }

  /**
   * Guarda y **deja el propietario seleccionado**. Si esto no seleccionara,
   * el agente tendria que volver a buscarlo y el desvio seguiria ahi, solo
   * que mas corto.
   */
  protected async guardarAlta(): Promise<void> {
    if (this.altaGuardando()) {
      return;
    }
    if (this.formAlta.invalid) {
      this.formAlta.markAllAsTouched();
      this.altaError.set('Revisa los campos obligatorios.');
      return;
    }
    const largo = LARGO_DOCUMENTO[this.formAlta.controls.tipoDocumento.value];
    const numero = this.formAlta.controls.numeroDocumento.value.trim();
    if (largo && numero.length !== largo) {
      this.formAlta.controls.numeroDocumento.markAsTouched();
      this.altaError.set(`El ${this.etiquetaDocumentoAlta()} debe tener ${largo} dígitos.`);
      return;
    }
    /* Duplicado por documento: la misma persona dos veces ensucia la
       busqueda de toda captacion futura, y el backend no lo impide. */
    const yaEsta = this.propietarios().find(
      (propietario) => propietario.numeroDocumento?.trim() === numero,
    );
    if (yaEsta) {
      this.seleccionarPropietario(yaEsta.id);
      this.busquedaPropietario.set(yaEsta.nombre);
      this.cerrarAlta();
      return;
    }

    this.altaGuardando.set(true);
    this.altaError.set(null);
    try {
      const v = this.formAlta.getRawValue();
      const datos: DatosPropietario = {
        tipoPersona: v.tipoPersona,
        tipoDocumento: v.tipoDocumento,
        numeroDocumento: numero,
        nombre: v.nombre.trim(),
        telefono: v.telefono.trim(),
      };
      const creado = await this.propietariosApi.registrar(datos);
      this.agregarPropietarios([creado]);
      this.totalPropietarios.update((total) => total + 1);
      this.seleccionarPropietario(creado.id);
      this.busquedaPropietario.set(creado.nombre);
      this.altaAbierta.set(false);
    } catch (error) {
      this.altaError.set(
        error instanceof ApiError ? error.message : 'No se pudo registrar el propietario.',
      );
    } finally {
      this.altaGuardando.set(false);
    }
  }

  protected cargarMasPropietarios(): void {
    if (!this.cargandoPropietarios() && this.hayMasPropietarios()) {
      void this.cargarPaginaPropietarios(this.paginaPropietarios + 1);
    }
  }

  protected invalido(nombre: keyof typeof this.formulario.controls): boolean {
    const control = this.formulario.controls[nombre];
    return control.invalid && (control.touched || control.dirty);
  }

  protected cancelar(): void {
    void this.router.navigate(['/propiedades']);
  }

  protected reintentar(): void {
    void this.cargar();
  }

  protected async guardar(): Promise<void> {
    if (this.guardando()) {
      return;
    }
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.errorGuardado.set('Revisa los campos obligatorios y los valores fuera de rango.');
      return;
    }

    this.guardando.set(true);
    this.errorGuardado.set(null);
    try {
      const datos = this.datosParaGuardar();
      if (!this.duplicadosRevisados()) {
        const candidatos = await this.locales.posiblesDuplicados(
          datos,
          this.idLocal() ?? undefined,
        );
        this.posiblesDuplicados.set(candidatos);
        if (candidatos.length > 0) {
          this.errorGuardado.set(
            'Revisa los posibles duplicados. Si corresponde a otro inmueble, puedes continuar.',
          );
          this.duplicadosRevisados.set(true);
          return;
        }
      }
      if (this.idLocal() !== null) {
        await this.locales.actualizar(this.idLocal()!, datos);
      } else {
        await this.locales.registrar(datos);
      }
      await this.router.navigate(['/propiedades'], { replaceUrl: true });
    } catch (error) {
      this.errorGuardado.set(
        error instanceof ApiError ? error.message : 'No se pudo guardar el local.',
      );
    } finally {
      this.guardando.set(false);
    }
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.errorCarga.set(null);
    this.propietarios.set([]);
    this.totalPropietarios.set(0);
    this.paginaPropietarios = 0;
    try {
      const idTexto = this.route.snapshot.paramMap.get('id');
      const id = idTexto === null ? null : Number(idTexto);
      if (id !== null && (!Number.isSafeInteger(id) || id <= 0)) {
        throw new Error('El identificador del local no es válido.');
      }
      this.idLocal.set(id);

      const [pagina, local] = await Promise.all([
        this.propietariosApi.pagina(1, PROPIETARIOS_POR_PAGINA),
        id === null ? Promise.resolve(null) : this.locales.obtener(id),
      ]);
      this.aplicarPaginaPropietarios(pagina.items, pagina.totalRecords, pagina.page);
      if (local) {
        this.localOriginal = local;
        if (
          local.idPropietario &&
          !this.propietarios().some((propietario) => propietario.id === local.idPropietario)
        ) {
          const propietario = await this.propietariosApi.obtener(local.idPropietario);
          this.agregarPropietarios([propietario]);
        }
        this.aplicarLocal(local);
      } else {
        this.localOriginal = null;
        this.formulario.enable({ emitEvent: false });
        this.formulario.reset(
          {
            idPropietario: 0,
            direccion: '',
            zonaUrbanizacion: '',
            distrito: 'Miraflores',
            nombreEdificioGaleria: '',
            interiorUnidad: '',
            piso: '',
            referenciaInterna: '',
            geoLat: null,
            geoLong: null,
            metraje: null,
            rubroPermitido: RUBROS_COMERCIALES[0],
            precioReferencial: null,
            monedaReferencial: '',
            estado: 'D',
            tipoInmueble: 'L',
            antiguedadAnios: null,
            ambientes: null,
            descripcion: '',
            frente: null,
            zonificacion: '',
            numeroEstacionamientos: null,
            cargaElectricaKw: null,
            cuotaMantenimiento: null,
            aptoLicencia: '',
          },
          { emitEvent: false },
        );
      }
      this.actualizarPasos();
    } catch (error) {
      this.errorCarga.set(
        error instanceof ApiError || error instanceof Error
          ? error.message
          : 'No se pudo cargar el formulario.',
      );
    } finally {
      this.cargando.set(false);
    }
  }

  private async cargarPaginaPropietarios(pagina: number): Promise<void> {
    this.cargandoPropietarios.set(true);
    try {
      const respuesta = await this.propietariosApi.pagina(
        pagina,
        PROPIETARIOS_POR_PAGINA,
      );
      this.aplicarPaginaPropietarios(
        respuesta.items,
        respuesta.totalRecords,
        respuesta.page,
      );
    } catch (error) {
      this.errorGuardado.set(
        error instanceof ApiError ? error.message : 'No se pudieron cargar más propietarios.',
      );
    } finally {
      this.cargandoPropietarios.set(false);
    }
  }

  private aplicarPaginaPropietarios(
    propietarios: readonly Propietario[],
    total: number,
    pagina: number,
  ): void {
    this.paginaPropietarios = pagina;
    this.totalPropietarios.set(total);
    this.agregarPropietarios(propietarios);
  }

  private agregarPropietarios(nuevos: readonly Propietario[]): void {
    const porId = new Map(this.propietarios().map((propietario) => [propietario.id, propietario]));
    for (const propietario of nuevos) {
      porId.set(propietario.id, propietario);
    }
    this.propietarios.set(
      [...porId.values()].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es')),
    );
  }

  private aplicarLocal(local: Local): void {
    this.formulario.patchValue(
      {
        idPropietario: local.idPropietario ?? 0,
        direccion: local.direccion ?? '',
        zonaUrbanizacion: local.zonaUrbanizacion ?? '',
        distrito: local.distrito ?? 'Miraflores',
        nombreEdificioGaleria: local.nombreEdificioGaleria ?? '',
        interiorUnidad: local.interiorUnidad ?? '',
        piso: local.piso ?? '',
        referenciaInterna: local.referenciaInterna ?? '',
        geoLat: local.geoLat ?? null,
        geoLong: local.geoLong ?? null,
        metraje: local.metraje ?? null,
        rubroPermitido: local.rubroPermitido ?? RUBROS_COMERCIALES[0],
        precioReferencial: local.precioReferencial ?? null,
        monedaReferencial: local.monedaReferencial ?? '',
        estado: local.estado ?? 'D',
        tipoInmueble: local.tipoInmueble ?? 'L',
        antiguedadAnios: local.antiguedadAnios ?? null,
        ambientes: local.ambientes ?? null,
        descripcion: local.descripcion ?? '',
        frente: local.frente ?? null,
        zonificacion: local.zonificacion ?? '',
        numeroEstacionamientos: local.numeroEstacionamientos ?? null,
        cargaElectricaKw: local.cargaElectricaKw ?? null,
        cuotaMantenimiento: local.cuotaMantenimiento ?? null,
        aptoLicencia:
          local.aptoLicenciaFuncionamiento === undefined
            ? ''
            : String(local.aptoLicenciaFuncionamiento),
      },
      { emitEvent: false },
    );
    this.formulario.controls.idPropietario.disable({ emitEvent: false });
  }

  private datosParaGuardar(): LocalRequest {
    const valor = this.formulario.getRawValue();
    return {
      codigoLocal: this.localOriginal?.codigoLocal ?? generarCodigoLocal(),
      direccion: valor.direccion.trim(),
      distrito: valor.distrito,
      metraje: valor.metraje!,
      precioReferencial: valor.precioReferencial!,
      monedaReferencial: valor.monedaReferencial,
      rubroPermitido: valor.rubroPermitido,
      descripcion: textoOpcional(valor.descripcion),
      idPropietario: valor.idPropietario,
      estado: valor.estado,
      tipoInmueble: valor.tipoInmueble,
      uso: 'C',
      ambientes: valor.ambientes,
      antiguedadAnios: valor.antiguedadAnios,
      zonaUrbanizacion: textoOpcional(valor.zonaUrbanizacion),
      geoLat: valor.geoLat,
      geoLong: valor.geoLong,
      estadoPublicacion: this.localOriginal?.estadoPublicacion ?? 'B',
      frente: valor.frente,
      zonificacion: textoOpcional(valor.zonificacion),
      aptoLicenciaFuncionamiento:
        valor.aptoLicencia === '' ? null : valor.aptoLicencia === 'true',
      cargaElectricaKw: valor.cargaElectricaKw,
      numeroEstacionamientos: valor.numeroEstacionamientos,
      cuotaMantenimiento: valor.cuotaMantenimiento,
      interiorUnidad: textoOpcional(valor.interiorUnidad),
      piso: textoOpcional(valor.piso),
      referenciaInterna: textoOpcional(valor.referenciaInterna),
      nombreEdificioGaleria: textoOpcional(valor.nombreEdificioGaleria),
    };
  }

  private actualizarPasos(): void {
    const valor = this.formulario.getRawValue();
    this.pasoPropietario.set(valor.idPropietario > 0);
    this.pasoUbicacion.set(!!valor.direccion.trim() && !!valor.distrito);
    this.pasoCaracteristicas.set(
      valor.metraje !== null &&
        valor.metraje > 0 &&
        valor.precioReferencial !== null &&
        valor.precioReferencial >= 0 &&
        valor.monedaReferencial.length > 0,
    );
  }
}

export function generarCodigoLocal(fecha = new Date()): string {
  const partes = [
    fecha.getUTCFullYear().toString().slice(-2),
    dos(fecha.getUTCMonth() + 1),
    dos(fecha.getUTCDate()),
    dos(fecha.getUTCHours()),
    dos(fecha.getUTCMinutes()),
    dos(fecha.getUTCSeconds()),
    fecha.getUTCMilliseconds().toString().padStart(3, '0'),
  ];
  return `LC-${partes.join('')}`;
}

function dos(valor: number): string {
  return valor.toString().padStart(2, '0');
}

function textoOpcional(valor: string): string | null {
  const limpio = valor.trim();
  return limpio || null;
}

/**
 * Que el valor no lleve decimales. NO comprueba rango: el rango lo declara el
 * catalogo y llega por contrato (ver `aplicarRestricciones`).
 */
function entero() {
  return (control: AbstractControl<number | null>): ValidationErrors | null => {
    const valor = control.value;
    return valor === null || Number.isInteger(valor) ? null : { entero: true };
  };
}

function normalizar(valor: string): string {
  return valor
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLocaleLowerCase('es')
    .trim();
}
