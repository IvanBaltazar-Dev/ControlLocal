import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import {
  CANAL_CONTACTO,
  describir,
  ESTADO_OPORTUNIDAD,
  ESTADO_VISITA,
  MOTIVO_NO_CONTINUIDAD,
  opcionesDe,
  RESULTADO_INTERACCION,
  RESULTADO_VISITA,
} from '../../core/api/codigos';
import { Interaccion, InteraccionesService } from '../../core/api/interacciones.service';
import { Oportunidad, OportunidadesService } from '../../core/api/oportunidades.service';
import { Visita, VisitasService } from '../../core/api/visitas.service';
import { AuthService } from '../../core/auth/auth.service';
import {
  fechaCorta,
  fechaHora,
  hora as horaDe,
  SIN_DATO,
  texto as textoDe,
} from '../../core/formato';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { OpcionFiltro } from '../../shared/filtro-select/filtro-select';

interface Etapa {
  clave: string;
  etiqueta: string;
}

/**
 * Las cinco etapas del seguimiento de UN cliente sobre UNA propiedad. No son
 * los estados del cable uno a uno: `A` cubre las dos primeras (interesado y en
 * seguimiento) y lo que separa la segunda de la primera es que ya haya pasado
 * algo — una interacción o una visita—.
 */
const ETAPAS: readonly Etapa[] = [
  { clave: 'interes', etiqueta: 'Interesado' },
  { clave: 'seguimiento', etiqueta: 'En seguimiento' },
  { clave: 'solicitud', etiqueta: 'Solicitud creada' },
  { clave: 'evaluacion', etiqueta: 'En evaluación' },
  { clave: 'cierre', etiqueta: 'Alquilada' },
];

/**
 * Seguimiento de una oportunidad: **un cliente sobre una propiedad**. Una misma
 * propiedad puede tener varias oportunidades vivas, cada una con su
 * conversación, sus visitas y su desenlace; por eso la barra de etapas vive
 * aquí y no en la ficha de la captación.
 *
 * Cosas que hay que entender antes de tocarla:
 * - **Los tres bloques fallan por separado.** Solo la oportunidad es fatal: si
 *   las visitas responden 500, las interacciones se siguen viendo. Patrón de
 *   `LocalDetail`.
 * - **No hay botón de "cerrar exitosa" y es correcto.** El endpoint existe y
 *   responde **400 siempre**, con un mensaje que lo explica: el cierre
 *   favorable lo produce la cascada de `POST /contratos` (F4). Un botón ahí
 *   solo serviría para enseñar un error.
 * - **Tampoco hay "crear solicitud" todavía**: F4 no está migrada al SPA, y la
 *   casa no ofrece enlaces a pantallas que no existen (misma decisión que
 *   `LocalDetail` con "Crear captación"). El estado `S` ya dice que la
 *   solicitud existe; el expediente se lee, por ahora, en el legado.
 * - **El cierre por no continuidad exige razón tipificada.** El backend guarda
 *   la *descripción* de la razón en `motivoCierre`, no su código: por eso la
 *   pantalla muestra ese texto tal cual y no intenta traducirlo.
 */
@Component({
  selector: 'app-oportunidad-detail',
  imports: [DialogoConfirmacion, EstadoListado, ReactiveFormsModule],
  templateUrl: './oportunidad-detail.html',
  styleUrl: './oportunidad-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OportunidadDetail implements OnInit {
  private readonly api = inject(OportunidadesService);
  private readonly interaccionesApi = inject(InteraccionesService);
  private readonly visitasApi = inject(VisitasService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly oportunidad = signal<Oportunidad | null>(null);
  protected readonly idOportunidad = signal(0);

  protected readonly interacciones = signal<Interaccion[]>([]);
  protected readonly errorInteracciones = signal<string | null>(null);
  protected readonly visitas = signal<Visita[]>([]);
  protected readonly errorVisitas = signal<string | null>(null);

  protected readonly dialogoCierre = signal(false);
  protected readonly procesando = signal(false);
  protected readonly razon = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required],
  });
  protected readonly observacionesCierre = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(1000)],
  });

  protected readonly etapas = ETAPAS;
  protected readonly opcionesRazon: OpcionFiltro[] = opcionesDe(MOTIVO_NO_CONTINUIDAD);

  protected readonly puedeOperar = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  protected readonly noContinua = computed(() => this.oportunidad()?.estado === 'N');
  protected readonly cerradaExitosa = computed(() => this.oportunidad()?.estado === 'F');
  protected readonly cerradaNoFavorable = computed(() => this.oportunidad()?.estado === 'X');
  protected readonly cerrada = computed(
    () => this.noContinua() || this.cerradaExitosa() || this.cerradaNoFavorable(),
  );

  /**
   * Índice de la etapa actual. `A` avanza de "interesado" a "en seguimiento"
   * en cuanto hay algo registrado: es la única etapa que no sale del estado del
   * cable, porque el cable no distingue esos dos momentos.
   */
  protected readonly etapaActual = computed(() => {
    const estado = this.oportunidad()?.estado;
    if (estado === 'F') return 4;
    if (estado === 'S') return 2;
    if (estado === 'A') {
      return this.interacciones().length > 0 || this.visitas().length > 0 ? 1 : 0;
    }
    return 0;
  });

  protected readonly visitasRealizadas = computed(
    () => this.visitas().filter((v) => v.estado === 'R').length,
  );
  protected readonly visitaProxima = computed(() =>
    this.visitas().find((v) => v.estado === 'P' || v.estado === 'G'),
  );
  protected readonly ultimoContacto = computed(
    () => this.interacciones().find((i) => !!i.fechaHora)?.fechaHora,
  );

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.error.set('La oportunidad indicada no es válida.');
      this.cargando.set(false);
      return;
    }
    this.idOportunidad.set(id);
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.oportunidad.set(await this.api.obtener(this.idOportunidad()));
    } catch (error) {
      this.oportunidad.set(null);
      this.error.set(mensajeError(error, 'No se pudo cargar la oportunidad.'));
      this.cargando.set(false);
      return;
    }
    this.cargando.set(false);
    await Promise.all([this.cargarInteracciones(), this.cargarVisitas()]);
  }

  protected async cargarInteracciones(): Promise<void> {
    this.errorInteracciones.set(null);
    try {
      const pagina = await this.interaccionesApi.pagina({
        contexto: 'OPORTUNIDAD',
        idOportunidad: this.idOportunidad(),
        pagina: 1,
        tamano: 100,
      });
      this.interacciones.set(pagina.items ?? []);
    } catch (error) {
      this.interacciones.set([]);
      this.errorInteracciones.set(mensajeError(error, 'No se pudieron cargar las interacciones.'));
    }
  }

  protected async cargarVisitas(): Promise<void> {
    this.errorVisitas.set(null);
    try {
      const pagina = await this.visitasApi.pagina({
        idOportunidad: this.idOportunidad(),
        pagina: 1,
        tamano: 100,
      });
      this.visitas.set(pagina.items ?? []);
    } catch (error) {
      this.visitas.set([]);
      this.errorVisitas.set(mensajeError(error, 'No se pudieron cargar las visitas.'));
    }
  }

  protected volver(): void {
    void this.router.navigate(['/oportunidades']);
  }

  protected verPropiedad(): void {
    const codigo = this.oportunidad()?.codigoCaptacion;
    if (codigo) void this.router.navigate(['/captaciones', codigo, 'ficha']);
  }

  protected verCliente(): void {
    const id = this.oportunidad()?.idCliente;
    if (id) void this.router.navigate(['/clientes', id, 'contacto']);
  }

  protected registrarInteraccion(): void {
    void this.router.navigate(['/interacciones/nueva'], {
      queryParams: { contexto: 'OPORTUNIDAD', oportunidad: this.idOportunidad() },
    });
  }

  protected programarVisita(): void {
    void this.router.navigate(['/visitas/nueva'], {
      queryParams: { oportunidad: this.idOportunidad() },
    });
  }

  protected verVisita(visita: Visita): void {
    void this.router.navigate(['/visitas'], { queryParams: { texto: visita.codigoOportunidad } });
  }

  protected verInteraccion(interaccion: Interaccion): void {
    void this.router.navigate(['/interacciones', interaccion.id]);
  }

  protected abrirCierre(): void {
    this.razon.reset('');
    this.observacionesCierre.reset('');
    this.mensaje.set(null);
    this.dialogoCierre.set(true);
  }

  protected cerrarDialogo(): void {
    if (!this.procesando()) this.dialogoCierre.set(false);
  }

  protected async confirmarCierre(): Promise<void> {
    const razon = this.razon.value.trim();
    if (this.procesando()) return;
    if (!razon) {
      this.razon.markAsTouched();
      return;
    }
    this.procesando.set(true);
    this.error.set(null);
    try {
      this.oportunidad.set(
        await this.api.noContinuidad(
          this.idOportunidad(),
          razon,
          this.observacionesCierre.value.trim() || undefined,
        ),
      );
      this.dialogoCierre.set(false);
      this.mensaje.set(
        `Oportunidad cerrada. Motivo: ${describir(MOTIVO_NO_CONTINUIDAD, razon)}.`,
      );
    } catch (error) {
      this.error.set(mensajeError(error, 'No se pudo cerrar la oportunidad.'));
    } finally {
      this.procesando.set(false);
    }
  }

  protected etapaCumplida(indice: number): boolean {
    return !this.noContinua() && indice < this.etapaActual();
  }

  protected etapaEsActual(indice: number): boolean {
    return !this.noContinua() && indice === this.etapaActual();
  }

  protected etiquetaEstado(): string {
    return describir(ESTADO_OPORTUNIDAD, this.oportunidad()?.estado) || SIN_DATO;
  }

  protected tonoEstado(): string {
    const estado = this.oportunidad()?.estado;
    if (estado === 'F') return 'bien';
    if (estado === 'N' || estado === 'X') return 'mal';
    return 'aviso';
  }

  protected canal(codigo: string | undefined): string {
    return describir(CANAL_CONTACTO, codigo) || SIN_DATO;
  }

  protected resultadoInteraccion(codigo: string | undefined): string {
    return describir(RESULTADO_INTERACCION, codigo) || SIN_DATO;
  }

  protected estadoVisita(codigo: string | undefined): string {
    return describir(ESTADO_VISITA, codigo) || SIN_DATO;
  }

  protected tonoVisita(codigo: string | undefined): string {
    if (codigo === 'R') return 'bien';
    if (codigo === 'C' || codigo === 'N') return 'mal';
    return 'aviso';
  }

  protected resultadoVisita(codigo: string | undefined): string {
    return codigo ? describir(RESULTADO_VISITA, codigo) : SIN_DATO;
  }

  protected momento(valor: string | undefined): string {
    return valor ? fechaHora(valor) : SIN_DATO;
  }

  protected fecha(valor: string | undefined): string {
    return valor ? fechaCorta(valor) : SIN_DATO;
  }

  /** `16:00:00` del cable -> `16:00`: la agenda no se cita al segundo. */
  protected hora(valor: string | undefined): string {
    return horaDe(valor);
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }
}

function mensajeError(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
