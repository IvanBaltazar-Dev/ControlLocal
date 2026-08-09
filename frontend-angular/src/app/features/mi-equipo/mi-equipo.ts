import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { Agente } from '../../core/api/agentes.service';
import { ApiError } from '../../core/api/api.types';
import { Broker, BrokersService } from '../../core/api/brokers.service';
import { AuthService } from '../../core/auth/auth.service';
import { SIN_DATO, texto as textoDe } from '../../core/formato';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

const ESTADO_OPERATIVO: Readonly<Record<string, string>> = {
  D: 'Disponible',
  O: 'Ocupado',
  V: 'Vacaciones',
  S: 'Suspendido',
};

/**
 * Panel del broker sobre su propio equipo. Porta `BrokerProfile.razor`, que en
 * el legado se abría por CÓDIGO de broker; aquí no hace falta parámetro: el
 * equipo es el de quien tiene la sesión abierta, que es el único caso que la
 * pantalla resuelve mejor que el catálogo.
 *
 * **No duplica `BrokerDetail`**: aquella es la ficha de *un* broker desde el
 * catálogo —la abre cualquiera, incluido el admin— y esta es "mi equipo", con
 * el atajo a cada agente y el aviso de quién no puede recibir encargos ahora
 * mismo, que es lo que un supervisor necesita antes de repartir trabajo.
 *
 * Rareza del cable que se respeta: los agentes de `/brokers/{id}/agentes`
 * llegan **sin contadores comerciales** —viajan en 0 aunque tengan carga real—,
 * así que aquí no se muestran y se enlaza a la ficha de cada agente, que sí los
 * calcula.
 *
 * Al ADMIN no se le ofrece: no supervisa a nadie, y su vista del organigrama es
 * **Asignaciones**.
 */
@Component({
  selector: 'cl-mi-equipo',
  imports: [EstadoListado],
  templateUrl: './mi-equipo.html',
  styleUrl: './mi-equipo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MiEquipo implements OnInit {
  private readonly api = inject(BrokersService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly broker = signal<Broker | null>(null);
  protected readonly equipo = signal<Agente[]>([]);

  /**
   * `idDominio` es el `persona_rol.id` del rol operativo de la sesión — para un
   * BROKER, su id de broker. Es el mismo que viaja como `idBroker` en el resto
   * del cable.
   */
  protected readonly idBroker = computed(() => this.auth.sesion()?.idDominio ?? 0);
  protected readonly esBroker = computed(() => this.auth.sesion()?.rol === 'BROKER');

  protected readonly disponibles = computed(
    () => this.equipo().filter((a) => a.estadoOperativo === 'D').length,
  );
  protected readonly noDisponibles = computed(
    () => this.equipo().filter((a) => a.estadoAdministrativo === 'A' && a.estadoOperativo !== 'D'),
  );
  protected readonly inactivos = computed(
    () => this.equipo().filter((a) => a.estadoAdministrativo !== 'A').length,
  );

  ngOnInit(): void {
    if (!this.esBroker()) {
      this.cargando.set(false);
      return;
    }
    this.cargar();
  }

  protected cargar(): void {
    const id = this.idBroker();
    if (!id) {
      this.error.set('Tu sesión no tiene un rol de broker asociado.');
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    forkJoin({
      broker: this.api.obtener$(id),
      equipo: this.api.agentes$(id).pipe(catchError(() => of([] as Agente[]))),
    }).subscribe({
      next: ({ broker, equipo }) => {
        this.broker.set(broker);
        this.equipo.set(equipo ?? []);
        this.cargando.set(false);
      },
      error: (error: unknown) => {
        this.error.set(
          error instanceof ApiError ? error.message : 'No se pudo cargar tu equipo.',
        );
        this.cargando.set(false);
      },
    });
  }

  protected abrirAgente(agente: Agente): void {
    void this.router.navigate(['/agentes', agente.id]);
  }

  protected nuevoAgente(): void {
    void this.router.navigate(['/agentes/nuevo']);
  }

  protected verCaptaciones(): void {
    void this.router.navigate(['/captaciones/pendientes']);
  }

  protected verSolicitudes(): void {
    void this.router.navigate(['/solicitudes/revisar']);
  }

  // -- presentación --------------------------------------------------------

  protected texto(valor: string | undefined | null): string {
    return textoDe(valor);
  }

  protected operativo(agente: Agente): string {
    return ESTADO_OPERATIVO[agente.estadoOperativo ?? ''] ?? SIN_DATO;
  }

  protected activo(agente: Agente): boolean {
    return agente.estadoAdministrativo === 'A';
  }
}
