import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { FORMA_PAGO, opcionesDe } from '../../core/api/codigos';
import { Oportunidad, OportunidadesService } from '../../core/api/oportunidades.service';
import { Solicitud, SolicitudesService } from '../../core/api/solicitudes.service';
import { AuthService } from '../../core/auth/auth.service';
import { monto as montoDe, SIN_DATO, texto as textoDe } from '../../core/formato';
import { OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

/** Cuántas oportunidades se piden al buscar. Nunca se descarga la bandeja entera. */
const CANDIDATOS = 20;

/** Las dos monedas del modelo económico; ninguna es opcional donde hay importe. */
const MONEDAS: OpcionFiltro[] = [
  { valor: 'PEN', etiqueta: 'PEN — Soles' },
  { valor: 'USD', etiqueta: 'USD — Dólares' },
];

/**
 * Alta de la solicitud de alquiler: **convertir una oportunidad abierta en una
 * oferta formal** con sus condiciones económicas.
 *
 * Tres cosas que conviene entender antes de tocarla:
 *
 * - **Las condiciones que se capturan aquí son las que el broker aprueba y las
 *   que el contrato hereda al cerrar.** No hay una segunda pantalla donde
 *   corregirlas: el cierre no las vuelve a pedir.
 * - **El alta y el envío a evaluación son dos pasos**, y así se ofrecen. El
 *   Blazor los encadenaba en un solo botón subiendo documentos por el medio;
 *   aquí la solicitud nace REGISTRADA, el agente completa su expediente en la
 *   pantalla de documentos y desde allí la envía. Encadenarlo escondía el estado
 *   intermedio y, cuando algo fallaba a mitad, dejaba la solicitud creada sin
 *   que el usuario supiera en qué punto estaba.
 * - **El selector busca en el servidor** (20 candidatos, `estado=A` abiertas):
 *   el Blazor pedía 100 oportunidades y las filtraba en memoria. Solo se ofrecen
 *   ABIERTAS porque una que ya tiene solicitud está en `S` y el backend la
 *   rechazaría con *"La oportunidad comercial debe estar ABIERTA."*.
 */
@Component({
  selector: 'app-solicitud-form',
  imports: [EstadoListado, ReactiveFormsModule],
  templateUrl: './solicitud-form.html',
  styleUrl: './solicitud-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SolicitudForm implements OnInit {
  private readonly api = inject(SolicitudesService);
  private readonly oportunidadesApi = inject(OportunidadesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly errorGuardado = signal<string | null>(null);
  protected readonly guardando = signal(false);

  protected readonly oportunidades = signal<readonly Oportunidad[]>([]);
  protected readonly totalOportunidades = signal(0);
  protected readonly buscando = signal(false);
  protected readonly oportunidadActual = signal<Oportunidad | null>(null);
  /** Llega fijada desde el seguimiento de una oportunidad: ahí no se cambia. */
  protected readonly oportunidadFija = signal(false);

  protected readonly busqueda = new FormControl('', { nonNullable: true });

  protected readonly formulario = this.fb.group({
    idOportunidad: this.fb.control(0, [Validators.required, Validators.min(1)]),
    montoPropuesto: this.fb.control<number | null>(null, [
      Validators.required,
      Validators.min(0.01),
    ]),
    moneda: this.fb.control('PEN', [Validators.required]),
    plazoMeses: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
    fechaInicio: this.fb.control(hoy(), [Validators.required]),
    formaPago: this.fb.control('TRANSFERENCIA'),
    mesesGarantia: this.fb.control<number | null>(null, [Validators.min(0)]),
    mesesAdelanto: this.fb.control<number | null>(null, [Validators.min(0)]),
    fechaVigenciaOferta: this.fb.control(''),
    observaciones: this.fb.control('', [Validators.maxLength(2000)]),
  });

  protected readonly puedeGuardar = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  protected readonly opcionesFormaPago: OpcionFiltro[] = opcionesDe(FORMA_PAGO);
  protected readonly monedas = MONEDAS;

  ngOnInit(): void {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.errorCarga.set(null);
    try {
      const idOportunidad = idPositivo(this.route.snapshot.queryParamMap.get('oportunidad'));
      if (idOportunidad) {
        this.fijar(await this.oportunidadesApi.obtener(idOportunidad));
        this.oportunidadFija.set(true);
      } else {
        await this.buscar();
      }
    } catch (error) {
      this.errorCarga.set(mensajeError(error, 'No se pudo preparar el formulario.'));
    } finally {
      this.cargando.set(false);
    }
  }

  protected async buscar(): Promise<void> {
    this.buscando.set(true);
    try {
      const pagina = await this.oportunidadesApi.pagina({
        pagina: 1,
        tamano: CANDIDATOS,
        // Solo ABIERTAS: una con solicitud ya está en `S` y el alta fallaría.
        estado: 'A',
        query: this.busqueda.value.trim() || undefined,
      });
      this.oportunidades.set(pagina.items);
      this.totalOportunidades.set(pagina.totalRecords);
      // Si la seleccionada ya no está entre los candidatos, se conserva aparte
      // para no perder la selección al escribir otra búsqueda.
      const actual = this.oportunidadActual();
      if (actual && !pagina.items.some((o) => o.id === actual.id)) {
        this.oportunidades.set([actual, ...pagina.items]);
      }
    } catch (error) {
      this.errorGuardado.set(mensajeError(error, 'No se pudieron buscar las oportunidades.'));
    } finally {
      this.buscando.set(false);
    }
  }

  protected seleccionar(): void {
    const id = Number(this.formulario.controls.idOportunidad.value);
    this.oportunidadActual.set(this.oportunidades().find((o) => o.id === id) ?? null);
  }

  protected async guardar(): Promise<void> {
    if (this.guardando() || !this.puedeGuardar()) return;
    if (this.formulario.invalid || !this.oportunidadActual()) {
      this.formulario.markAllAsTouched();
      this.errorGuardado.set('Revisa la oportunidad y las condiciones del alquiler.');
      return;
    }
    this.guardando.set(true);
    this.errorGuardado.set(null);
    try {
      const datos = this.formulario.getRawValue();
      const creada: Solicitud = await this.api.registrar({
        idOportunidad: datos.idOportunidad,
        montoPropuesto: datos.montoPropuesto ?? undefined,
        moneda: datos.moneda,
        plazoMeses: datos.plazoMeses ?? undefined,
        fechaInicio: datos.fechaInicio || undefined,
        formaPago: datos.formaPago || undefined,
        // Cero es un dato: "sin garantía" no es lo mismo que "no indicado".
        mesesGarantia: datos.mesesGarantia ?? undefined,
        mesesAdelanto: datos.mesesAdelanto ?? undefined,
        fechaVigenciaOferta: datos.fechaVigenciaOferta || undefined,
        observaciones: datos.observaciones.trim() || undefined,
        // `codigoSolicitud` NO se manda: el backend genera SOL-yyMMddHHmmss.
      });
      // Al expediente documental, que es el paso siguiente real: completar los
      // seis documentos y desde ahí enviar a evaluación.
      void this.router.navigate(['/solicitudes', creada.codigoSolicitud, 'documentos']);
    } catch (error) {
      this.errorGuardado.set(mensajeError(error, 'No se pudo registrar la solicitud.'));
    } finally {
      this.guardando.set(false);
    }
  }

  protected cancelar(): void {
    const oportunidad = this.oportunidadActual();
    if (this.oportunidadFija() && oportunidad) {
      void this.router.navigate(['/oportunidades', oportunidad.id]);
    } else {
      void this.router.navigate(['/solicitudes']);
    }
  }

  protected descripcion(oportunidad: Oportunidad): string {
    return `${textoDe(oportunidad.codigoOportunidad)} · ${textoDe(oportunidad.clienteNombre)} — ${textoDe(oportunidad.direccionLocal)}`;
  }

  protected invalido(campo: keyof typeof this.formulario.controls): boolean {
    const control = this.formulario.controls[campo];
    return control.invalid && (control.touched || control.dirty);
  }

  /** Vista previa de la oferta, con la moneda delante y como código. */
  protected ofertaResumen(): string {
    const { montoPropuesto, moneda } = this.formulario.getRawValue();
    return montoPropuesto ? montoDe(montoPropuesto, moneda) : SIN_DATO;
  }

  protected plazoResumen(): string {
    const meses = this.formulario.getRawValue().plazoMeses;
    return meses && meses > 0 ? `${meses} meses` : SIN_DATO;
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }

  private fijar(oportunidad: Oportunidad): void {
    this.oportunidadActual.set(oportunidad);
    this.oportunidades.set([oportunidad]);
    this.totalOportunidades.set(1);
    this.formulario.controls.idOportunidad.setValue(oportunidad.id);
  }
}

function hoy(): string {
  return new Date().toISOString().slice(0, 10);
}

function idPositivo(valor: string | null): number | null {
  const numero = Number(valor);
  return Number.isSafeInteger(numero) && numero > 0 ? numero : null;
}

function mensajeError(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
