import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { Captacion, CaptacionesService } from '../../core/api/captaciones.service';
import { Cliente, ClientesService } from '../../core/api/clientes.service';
import { describir, ESTADO_PUBLICACION, CANAL_PUBLICACION } from '../../core/api/codigos';
import { LocalesService, Publicacion } from '../../core/api/locales.service';
import { OportunidadesService } from '../../core/api/oportunidades.service';
import { AuthService } from '../../core/auth/auth.service';
import { fechaCorta, SIN_DATO, texto as textoDe } from '../../core/formato';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

/** Cuántos candidatos se piden al buscar. Nunca se descarga la cartera entera. */
const CANDIDATOS = 20;

/**
 * Alta de oportunidad: **vincular un cliente interesado con una captación
 * activa**. Es el punto en que la conversación con alguien pasa a ser una
 * propuesta sobre un inmueble concreto.
 *
 * Lo que cambia respecto del Blazor y no se debe volver atrás: aquél
 * **descargaba el catálogo entero de clientes y el de captaciones** y los
 * filtraba en memoria (`TextoFiltro.Contiene`). Aquí los dos selectores
 * **buscan en el servidor** —`GET /clientes?texto=&estado=A` y
 * `GET /captaciones?q=&estado=A`—, piden 20 candidatos y avisan cuando hay más.
 * Es la misma regla que ya siguen `CaptacionForm` con los locales y toda
 * bandeja desde RC-003.
 *
 * Tres reglas del cable que la pantalla hace visibles antes de enviar:
 * - **La captación tiene que ser del agente que registra**, o el backend
 *   responde **403** (no 400). Como el listado ya viene con el alcance
 *   aplicado, un AGENTE solo ve las suyas y el caso no debería darse; si se da
 *   —por un id puesto a mano en la URL—, el mensaje del backend se muestra tal
 *   cual.
 * - Solo se ofrecen captaciones **ACTIVAS**: sobre una pendiente de revisión no
 *   se puede abrir oportunidad.
 * - `codigoOportunidad` no se pide: si va vacío el backend lo autogenera como
 *   `OP-yyMMddHHmmss`. Inventarlo en el cliente solo serviría para chocar.
 */
@Component({
  selector: 'app-oportunidad-form',
  imports: [EstadoListado, ReactiveFormsModule],
  templateUrl: './oportunidad-form.html',
  styleUrl: './oportunidad-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OportunidadForm implements OnInit {
  private readonly api = inject(OportunidadesService);
  private readonly clientesApi = inject(ClientesService);
  private readonly captacionesApi = inject(CaptacionesService);
  private readonly localesApi = inject(LocalesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly errorGuardado = signal<string | null>(null);
  protected readonly guardando = signal(false);

  protected readonly clientes = signal<readonly Cliente[]>([]);
  protected readonly totalClientes = signal(0);
  protected readonly buscandoClientes = signal(false);
  protected readonly clienteActual = signal<Cliente | null>(null);

  protected readonly captaciones = signal<readonly Captacion[]>([]);
  protected readonly totalCaptaciones = signal(0);
  protected readonly buscandoCaptaciones = signal(false);
  protected readonly captacionActual = signal<Captacion | null>(null);

  protected readonly publicaciones = signal<readonly Publicacion[]>([]);

  protected readonly busquedaCliente = new FormControl('', { nonNullable: true });
  protected readonly busquedaCaptacion = new FormControl('', { nonNullable: true });

  protected readonly formulario = this.fb.group({
    idCliente: this.fb.control(0, [Validators.required, Validators.min(1)]),
    idCaptacion: this.fb.control(0, [Validators.required, Validators.min(1)]),
    idPublicacionOrigen: this.fb.control(0),
    observaciones: this.fb.control('', [Validators.maxLength(2000)]),
  });

  protected readonly puedeGuardar = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  /** El cliente llega fijado desde su bitácora: ahí no se cambia de interesado. */
  protected readonly clienteFijo = signal(false);
  protected readonly pasoCliente = computed(() => this.clienteActual() !== null);
  protected readonly pasoCaptacion = computed(() => this.captacionActual() !== null);

  ngOnInit(): void {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.errorCarga.set(null);
    try {
      const params = this.route.snapshot.queryParamMap;
      const idCliente = idPositivo(params.get('cliente'));
      const idCaptacion = idPositivo(params.get('captacion'));
      const codigoCaptacion = (params.get('captacionCodigo') ?? '').trim();

      if (idCliente) {
        await this.fijarCliente(await this.clientesApi.obtener(idCliente));
        this.clienteFijo.set(true);
      } else {
        await this.buscarClientes();
      }

      if (codigoCaptacion) {
        await this.fijarCaptacion(await this.captacionesApi.obtenerPorCodigo(codigoCaptacion));
      } else if (idCaptacion) {
        await this.fijarCaptacion(await this.captacionesApi.obtener(idCaptacion));
      } else {
        await this.buscarCaptaciones();
      }

      const idPublicacion = idPositivo(params.get('publicacion'));
      if (idPublicacion && this.publicaciones().some((p) => p.id === idPublicacion)) {
        this.formulario.controls.idPublicacionOrigen.setValue(idPublicacion);
      }
    } catch (error) {
      this.errorCarga.set(mensajeError(error, 'No se pudo preparar el formulario.'));
    } finally {
      this.cargando.set(false);
    }
  }

  protected async buscarClientes(): Promise<void> {
    this.buscandoClientes.set(true);
    try {
      const pagina = await this.clientesApi.pagina({
        pagina: 1,
        tamano: CANDIDATOS,
        // Solo activos: proponerle una propiedad a un cliente dado de baja no
        // tiene sentido, y el catálogo puede ser grande.
        estado: 'A',
        texto: this.busquedaCliente.value.trim() || undefined,
      });
      this.clientes.set(pagina.items);
      this.totalClientes.set(pagina.totalRecords);
      // Si el seleccionado ya no está en los candidatos, se conserva aparte
      // para no perder la selección al escribir otra búsqueda.
      const actual = this.clienteActual();
      if (actual && !pagina.items.some((c) => c.id === actual.id)) {
        this.clientes.set([actual, ...pagina.items]);
      }
    } catch (error) {
      this.errorGuardado.set(mensajeError(error, 'No se pudieron buscar los clientes.'));
    } finally {
      this.buscandoClientes.set(false);
    }
  }

  protected async buscarCaptaciones(): Promise<void> {
    this.buscandoCaptaciones.set(true);
    try {
      const pagina = await this.captacionesApi.pagina({
        pagina: 1,
        tamano: CANDIDATOS,
        // ACTIVA: sobre una pendiente de revisión no se abre oportunidad.
        estado: 'A',
        q: this.busquedaCaptacion.value.trim() || undefined,
      });
      this.captaciones.set(pagina.items);
      this.totalCaptaciones.set(pagina.totalRecords);
      const actual = this.captacionActual();
      if (actual && !pagina.items.some((c) => c.id === actual.id)) {
        this.captaciones.set([actual, ...pagina.items]);
      }
    } catch (error) {
      this.errorGuardado.set(mensajeError(error, 'No se pudieron buscar las captaciones.'));
    } finally {
      this.buscandoCaptaciones.set(false);
    }
  }

  protected seleccionarCliente(): void {
    const id = Number(this.formulario.controls.idCliente.value);
    this.clienteActual.set(this.clientes().find((c) => c.id === id) ?? null);
  }

  protected async seleccionarCaptacion(): Promise<void> {
    const id = Number(this.formulario.controls.idCaptacion.value);
    const captacion = this.captaciones().find((c) => c.id === id) ?? null;
    this.captacionActual.set(captacion);
    this.formulario.controls.idPublicacionOrigen.setValue(0);
    await this.cargarPublicaciones(captacion);
  }

  protected async guardar(): Promise<void> {
    if (this.guardando() || !this.puedeGuardar()) return;
    if (this.formulario.invalid || !this.clienteActual() || !this.captacionActual()) {
      this.formulario.markAllAsTouched();
      this.errorGuardado.set('Selecciona un cliente interesado y una captación activa.');
      return;
    }
    this.guardando.set(true);
    this.errorGuardado.set(null);
    try {
      const datos = this.formulario.getRawValue();
      const creada = await this.api.registrar({
        idCliente: datos.idCliente,
        idCaptacion: datos.idCaptacion,
        observaciones: datos.observaciones.trim() || undefined,
        // 0 = "sin atribuir": no se manda, porque el cable distingue ausente de cero.
        idPublicacionOrigen: datos.idPublicacionOrigen || undefined,
      });
      void this.router.navigate(['/oportunidades', creada.id]);
    } catch (error) {
      this.errorGuardado.set(mensajeError(error, 'No se pudo crear la oportunidad.'));
    } finally {
      this.guardando.set(false);
    }
  }

  protected cancelar(): void {
    const idCliente = this.clienteFijo() ? this.clienteActual()?.id : null;
    if (idCliente) {
      void this.router.navigate(['/clientes', idCliente, 'contacto']);
    } else {
      void this.router.navigate(['/oportunidades']);
    }
  }

  protected descripcionCaptacion(captacion: Captacion): string {
    return `${textoDe(captacion.codigoCaptacion)} · ${textoDe(captacion.direccionLocal)} (${textoDe(captacion.distritoLocal)})`;
  }

  protected descripcionPublicacion(publicacion: Publicacion): string {
    const canal = describir(CANAL_PUBLICACION, publicacion.canal) || SIN_DATO;
    const titulo = (publicacion.tituloAnuncio ?? '').trim();
    const estado = describir(ESTADO_PUBLICACION, publicacion.estado);
    return `${canal}${titulo ? ` · ${titulo}` : ''}${estado ? ` (${estado})` : ''}`;
  }

  protected invalido(campo: 'idCliente' | 'idCaptacion'): boolean {
    const control = this.formulario.controls[campo];
    return control.invalid && (control.touched || control.dirty);
  }

  protected fecha(valor: string | undefined): string {
    return valor ? fechaCorta(valor) : SIN_DATO;
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }

  private async fijarCliente(cliente: Cliente): Promise<void> {
    this.clienteActual.set(cliente);
    this.clientes.set([cliente]);
    this.totalClientes.set(1);
    this.formulario.controls.idCliente.setValue(cliente.id);
  }

  private async fijarCaptacion(captacion: Captacion): Promise<void> {
    this.captacionActual.set(captacion);
    this.captaciones.set([captacion]);
    this.totalCaptaciones.set(1);
    this.formulario.controls.idCaptacion.setValue(captacion.id);
    await this.cargarPublicaciones(captacion);
  }

  /**
   * Publicaciones del local de la captación, para atribuir la oportunidad al
   * anuncio por el que llegó el cliente. Es **opcional y no bloqueante**: si la
   * lectura falla, el alta sigue siendo posible sin atribución.
   */
  private async cargarPublicaciones(captacion: Captacion | null): Promise<void> {
    if (!captacion?.idLocal) {
      this.publicaciones.set([]);
      return;
    }
    try {
      this.publicaciones.set(await this.localesApi.publicaciones(captacion.idLocal));
    } catch {
      this.publicaciones.set([]);
    }
  }
}

function idPositivo(valor: string | null): number | null {
  const numero = Number(valor);
  return Number.isSafeInteger(numero) && numero > 0 ? numero : null;
}

function mensajeError(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
