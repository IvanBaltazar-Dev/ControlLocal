import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import {
  AgentesService,
  ComisionesAgente,
  ConteoEstado,
  FichaAgente,
  ImportePorMoneda,
} from '../../core/api/agentes.service';
import { ApiError } from '../../core/api/api.types';
import { describir, TIPO_DOCUMENTO } from '../../core/api/codigos';
import { AuthService } from '../../core/auth/auth.service';
import { SIN_DATO, texto as textoDe } from '../../core/formato';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

const ESTADO_OPERATIVO: Readonly<Record<string, string>> = {
  D: 'Disponible',
  O: 'Ocupado',
  V: 'Vacaciones',
  S: 'Suspendido',
};

/** Las cuatro magnitudes del dinero, en el orden en que se leen. */
interface BloqueDinero {
  readonly etiqueta: string;
  readonly explicacion: string;
  readonly importes: ImportePorMoneda[];
  readonly tono: 'neutro' | 'ok' | 'aviso';
}

/**
 * Ficha del agente inmobiliario.
 *
 * **Se arma con UNA llamada a `GET /agentes/{id}`**, no combinando páginas de
 * las bandejas de captaciones, oportunidades, solicitudes y cierres. Eso último
 * no es una cuestión de eficiencia sino de corrección: cada listado pagina, y
 * contar sobre la página visible da un número falso en cuanto el agente tiene
 * más de una página de trabajo.
 *
 * Dos cosas que la pantalla dice explícitamente porque son fáciles de
 * malinterpretar:
 *
 * 1. **Los cierres y el dinero son históricos.** Salen de la atribución que se
 *    congeló al cerrar cada alquiler (V27), no de quién supervisa hoy al
 *    agente: si cambió de equipo, su historia se va con él.
 * 2. **Generado, cobrado y pagado son tres cosas distintas.** Lo pactado no es
 *    lo que entró en caja, y lo que le toca al agente no es lo que ya se le
 *    pagó. Se muestran separados y por moneda, nunca sumados entre sí.
 *
 * El alcance lo impone el backend: el BROKER solo abre la ficha de los agentes
 * que supervisa hoy y fuera de su equipo recibe 403, que aquí se explica como
 * alcance y no como error del sistema.
 */
@Component({
  selector: 'app-agente-detail',
  imports: [EstadoListado, RouterLink],
  templateUrl: './agente-detail.html',
  styleUrl: './agente-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgenteDetail implements OnInit {
  private readonly api = inject(AgentesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly fueraDeAlcance = signal(false);
  protected readonly ficha = signal<FichaAgente | null>(null);
  protected readonly idAgente = signal<number>(0);

  /** Fila 18: editar la ficha de un agente es gobierno, no supervisión. */
  protected readonly puedeEditar = computed(
    () => this.auth.sesion()?.rol === 'TENANT_ADMIN',
  );
  protected readonly agente = computed(() => this.ficha()?.agente ?? null);
  protected readonly activo = computed(
    () => this.agente()?.estadoAdministrativo === 'A',
  );

  protected readonly captaciones = computed(() => this.ficha()?.captaciones ?? []);
  protected readonly oportunidades = computed(() => this.ficha()?.oportunidades ?? []);
  protected readonly solicitudes = computed(() => this.ficha()?.solicitudes ?? []);
  protected readonly ultimosCierres = computed(() => this.ficha()?.ultimosCierres ?? []);

  protected readonly totalCaptaciones = computed(() => suma(this.captaciones()));
  protected readonly totalOportunidades = computed(() => suma(this.oportunidades()));
  protected readonly totalSolicitudes = computed(() => suma(this.solicitudes()));

  /**
   * El dinero en cuatro bloques, con la explicación al lado. Sin ella, "500 y
   * 200" no dice si faltan 300 por cobrar o si hubo una anulación.
   */
  protected readonly dinero = computed<BloqueDinero[]>(() => {
    const c: ComisionesAgente | undefined = this.ficha()?.comisiones;
    if (!c) {
      return [];
    }
    return [
      {
        etiqueta: 'Comisión generada',
        explicacion: 'Lo pactado en sus cierres, sin contar liquidaciones anuladas.',
        importes: c.generada,
        tono: 'neutro',
      },
      {
        etiqueta: 'Cobrado por la corredora',
        explicacion: 'Lo que entró de verdad, descontando reversiones.',
        importes: c.cobrada,
        tono: 'ok',
      },
      {
        etiqueta: 'Pendiente de cobro',
        explicacion: 'Diferencia entre lo generado y lo cobrado.',
        importes: c.pendienteCobro,
        tono: 'aviso',
      },
      {
        etiqueta: 'Asignado al agente',
        explicacion: 'La parte que el broker le adjudicó del bruto.',
        importes: c.asignadaAgente,
        tono: 'neutro',
      },
      {
        etiqueta: 'Pagado al agente',
        explicacion: 'Lo que ya se le pagó.',
        importes: c.pagadaAgente,
        tono: 'ok',
      },
      {
        etiqueta: 'Pendiente de pagarle',
        explicacion: 'Diferencia entre lo asignado y lo pagado.',
        importes: c.pendientePagoAgente,
        tono: 'aviso',
      },
    ];
  });

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.error.set('El agente indicado no es válido.');
      this.cargando.set(false);
      return;
    }
    this.idAgente.set(id);
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.fueraDeAlcance.set(false);
    this.api.ficha$(this.idAgente()).subscribe({
      next: (ficha) => {
        this.ficha.set(ficha);
        this.cargando.set(false);
      },
      error: (error: unknown) => {
        // Un 403 aquí no es un fallo: es que el agente no es de su equipo.
        if (error instanceof ApiError && error.sinPermiso) {
          this.fueraDeAlcance.set(true);
        } else {
          this.error.set(
            error instanceof ApiError ? error.message : 'No se pudo cargar la ficha del agente.',
          );
        }
        this.cargando.set(false);
      },
    });
  }

  protected editar(): void {
    void this.router.navigate(['/agentes', this.idAgente(), 'editar']);
  }

  protected volver(): void {
    void this.router.navigate(['/agentes']);
  }

  protected verBroker(): void {
    const id = this.ficha()?.supervision?.idBroker;
    if (id) {
      void this.router.navigate(['/brokers', id]);
    }
  }

  protected verCierres(): void {
    void this.router.navigate(['/propiedades-alquiladas'], {
      queryParams: { idAgente: this.idAgente() },
    });
  }

  // -- presentación --------------------------------------------------------

  protected texto(valor: string | undefined | null): string {
    return textoDe(valor);
  }

  protected documento(): string {
    const agente = this.agente();
    if (!agente) return SIN_DATO;
    const tipo = describir(TIPO_DOCUMENTO, agente.tipoDocumento);
    const numero = textoDe(agente.numeroDocumento);
    return tipo ? `${tipo} ${numero}` : numero;
  }

  protected operativo(): string {
    return ESTADO_OPERATIVO[this.agente()?.estadoOperativo ?? ''] ?? SIN_DATO;
  }

  /** Un bloque sin importes se dibuja como guion, no como cero. */
  protected vacio(bloque: BloqueDinero): boolean {
    return bloque.importes.length === 0;
  }

  protected importe(valor: ImportePorMoneda): string {
    return `${valor.moneda} ${Number(valor.monto ?? 0).toLocaleString('es-PE', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  }
}

function suma(conteos: ConteoEstado[]): number {
  return conteos.reduce((total, c) => total + (c.total ?? 0), 0);
}
