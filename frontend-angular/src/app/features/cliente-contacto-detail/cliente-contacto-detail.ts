import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { Cliente, ClientesService } from '../../core/api/clientes.service';
import {
  CANAL_CONTACTO,
  describir,
  ESTADO_OPORTUNIDAD,
  RESULTADO_INTERACCION,
  TIPO_DOCUMENTO,
} from '../../core/api/codigos';
import { Interaccion, InteraccionesService } from '../../core/api/interacciones.service';
import {
  Oportunidad,
  OPORTUNIDAD_ACTIVA,
  OportunidadesService,
} from '../../core/api/oportunidades.service';
import { AuthService } from '../../core/auth/auth.service';
import { fechaCorta, fechaHora, SIN_DATO, texto as textoDe } from '../../core/formato';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

/**
 * Bitácora de contacto del cliente: **la relación agente-cliente cuando
 * todavía no hay propiedad de por medio**.
 *
 * Es la pantalla que justifica que `interaccion_comercial` sea polimórfica. Su
 * contenido son las interacciones de contexto `CLIENTE` —las que no cuelgan de
 * ninguna oportunidad— más las propuestas que sí llegaron a serlo, para que se
 * lea de un tirón el recorrido completo: primero se habla con alguien, después
 * se le propone un local. La ficha comercial (`ClienteDetail`) responde "qué
 * hay registrado"; esta responde "cómo va la conversación".
 *
 * Tres cosas que conviene no volver a introducir al tocarla:
 * - **Los dos bloques fallan por separado.** Solo el cliente es fatal: si las
 *   oportunidades responden 500, las interacciones se siguen viendo con su
 *   aviso al lado. Es el patrón de `LocalDetail`, y aquí importa más porque son
 *   dos lecturas independientes de recursos distintos.
 * - **El "agente visible" es un dato derivado, no del cliente.** El catálogo de
 *   clientes es compartido y el rol CLIENTE no guarda agente asignado: quién lo
 *   atiende se deduce de quién registró la última interacción y, en su defecto,
 *   de quién abrió alguna oportunidad. Por eso puede decir "no asignado" para
 *   un cliente que otro agente sí atiende pero cuyo rastro no está en tu
 *   alcance.
 * - El **alcance ya viene aplicado por el backend**: dos actores distintos ven
 *   bitácoras distintas del mismo cliente, y eso se dice en pantalla en vez de
 *   dejar creer que la lista es completa.
 */
@Component({
  selector: 'app-cliente-contacto-detail',
  imports: [EstadoListado],
  templateUrl: './cliente-contacto-detail.html',
  styleUrl: './cliente-contacto-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClienteContactoDetail implements OnInit {
  private readonly clientes = inject(ClientesService);
  private readonly interaccionesApi = inject(InteraccionesService);
  private readonly oportunidadesApi = inject(OportunidadesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly cliente = signal<Cliente | null>(null);
  protected readonly idCliente = signal(0);

  protected readonly interacciones = signal<Interaccion[]>([]);
  protected readonly errorInteracciones = signal<string | null>(null);
  protected readonly oportunidades = signal<Oportunidad[]>([]);
  protected readonly errorOportunidades = signal<string | null>(null);

  /** Solo el AGENTE escribe: broker y admin leen la bitácora de su equipo. */
  protected readonly puedeOperar = computed(() => this.auth.sesion()?.rol === 'AGENTE');

  protected readonly activas = computed(
    () => this.oportunidades().filter((o) => OPORTUNIDAD_ACTIVA.has(o.estado ?? '')).length,
  );

  /**
   * La bitácora llega ordenada por `fechaHora` desc con los nulos al final, así
   * que el primero con fecha es el último contacto. No se reordena en el
   * cliente: se respeta el orden del cable.
   */
  protected readonly ultimoContacto = computed(
    () => this.interacciones().find((i) => !!i.fechaHora)?.fechaHora,
  );

  protected readonly agenteVisible = computed(() => {
    const deInteraccion = this.interacciones().find((i) => (i.agenteNombre ?? '').trim());
    if (deInteraccion?.agenteNombre) return deInteraccion.agenteNombre;
    const deOportunidad = this.oportunidades().find((o) => (o.agenteNombre ?? '').trim());
    return deOportunidad?.agenteNombre ?? 'No asignado';
  });

  // El tope se avisa en vez de recortar en silencio: una bitácora truncada sin
  // decirlo se lee como una bitácora completa.
  protected readonly bitacoraAlTope = computed(() => this.interacciones().length >= TOPE_BITACORA);
  protected readonly propuestasAlTope = computed(
    () => this.oportunidades().length >= TOPE_BITACORA,
  );

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.error.set('El cliente indicado no es válido.');
      this.cargando.set(false);
      return;
    }
    this.idCliente.set(id);
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.cliente.set(await this.clientes.obtener(this.idCliente()));
    } catch (error) {
      this.cliente.set(null);
      this.error.set(mensaje(error, 'No se pudo cargar el cliente.'));
      this.cargando.set(false);
      return;
    }
    this.cargando.set(false);
    // Los dos bloques se piden a la vez y fallan por separado: ninguno tumba
    // la pantalla ni al otro.
    await Promise.all([this.cargarInteracciones(), this.cargarOportunidades()]);
  }

  protected async cargarInteracciones(): Promise<void> {
    this.errorInteracciones.set(null);
    try {
      const pagina = await this.interaccionesApi.pagina({
        contexto: 'CLIENTE',
        idCliente: this.idCliente(),
        pagina: 1,
        tamano: TOPE_BITACORA,
      });
      this.interacciones.set(pagina.items ?? []);
    } catch (error) {
      this.interacciones.set([]);
      this.errorInteracciones.set(mensaje(error, 'No se pudo cargar la bitácora.'));
    }
  }

  protected async cargarOportunidades(): Promise<void> {
    this.errorOportunidades.set(null);
    try {
      const pagina = await this.oportunidadesApi.pagina({
        idCliente: this.idCliente(),
        pagina: 1,
        tamano: TOPE_BITACORA,
      });
      this.oportunidades.set(pagina.items ?? []);
    } catch (error) {
      this.oportunidades.set([]);
      this.errorOportunidades.set(mensaje(error, 'No se pudieron cargar las propuestas.'));
    }
  }

  protected verFicha(): void {
    void this.router.navigate(['/clientes', this.idCliente()]);
  }

  protected editarCliente(): void {
    void this.router.navigate(['/clientes', this.idCliente(), 'editar']);
  }

  protected registrarInteraccion(): void {
    void this.router.navigate(['/interacciones/nueva'], {
      queryParams: { contexto: 'CLIENTE', cliente: this.idCliente() },
    });
  }

  protected crearOportunidad(): void {
    void this.router.navigate(['/oportunidades/nueva'], {
      queryParams: { cliente: this.idCliente() },
    });
  }

  protected verOportunidad(oportunidad: Oportunidad): void {
    void this.router.navigate(['/oportunidades', oportunidad.id]);
  }

  protected documento(): string {
    const cliente = this.cliente();
    const tipo = describir(TIPO_DOCUMENTO, cliente?.tipoDocumento);
    const numero = textoDe(cliente?.numeroDocumento);
    return tipo ? `${tipo} ${numero}` : numero;
  }

  protected canal(codigo: string | undefined): string {
    return describir(CANAL_CONTACTO, codigo) || SIN_DATO;
  }

  protected resultado(codigo: string | undefined): string {
    return describir(RESULTADO_INTERACCION, codigo) || SIN_DATO;
  }

  protected estadoOportunidad(codigo: string | undefined): string {
    return describir(ESTADO_OPORTUNIDAD, codigo) || SIN_DATO;
  }

  /** Verde solo lo que cerró bien; rojo lo que no continuó. */
  protected tonoOportunidad(codigo: string | undefined): string {
    if (codigo === 'F') return 'bien';
    if (codigo === 'N' || codigo === 'X') return 'mal';
    return 'aviso';
  }

  protected momento(valor: string | undefined): string {
    return valor ? fechaHora(valor) : SIN_DATO;
  }

  protected fecha(valor: string | undefined): string {
    return valor ? fechaCorta(valor) : SIN_DATO;
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }
}

/**
 * Tope de las dos listas. Es la vista de UN cliente, no una bandeja: el mismo
 * 100 del Blazor. Si un cliente llegara a superarlo, lo que hace falta es
 * paginación real aquí, no un tope mayor — y por eso la pantalla avisa cuando
 * lo alcanza en vez de recortar en silencio.
 */
const TOPE_BITACORA = 100;

function mensaje(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
