import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { Agente } from '../../core/api/agentes.service';
import { ApiError } from '../../core/api/api.types';
import { Broker, BrokersService } from '../../core/api/brokers.service';
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

/**
 * Ficha del broker y su equipo vigente.
 *
 * **Rareza del cable que esta pantalla NO debe disimular**: los agentes que
 * devuelve `GET /brokers/{id}/agentes` llegan **sin contadores comerciales** —
 * `captacionesActivas` y `operacionesActivas` viajan en `0` aunque el agente
 * tenga captaciones y oportunidades abiertas—. Pintar esos ceros sería informar
 * mal, así que no se muestran aquí y se enlaza a **Agentes**, que es donde el
 * cable los calcula de verdad.
 *
 * Leer es de cualquier sesión; editar, solo de ADMIN.
 */
@Component({
  selector: 'app-broker-detail',
  imports: [EstadoListado, RouterLink],
  templateUrl: './broker-detail.html',
  styleUrl: './broker-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BrokerDetail implements OnInit {
  private readonly api = inject(BrokersService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly broker = signal<Broker | null>(null);
  protected readonly equipo = signal<Agente[]>([]);
  protected readonly idBroker = signal<number>(0);

  protected readonly esAdmin = computed(() => this.auth.sesion()?.rol === 'TENANT_ADMIN');
  protected readonly activo = computed(() => this.broker()?.estadoAdministrativo === 'A');

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.error.set('El broker indicado no es válido.');
      this.cargando.set(false);
      return;
    }
    this.idBroker.set(id);
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    forkJoin({
      broker: this.api.obtener$(this.idBroker()),
      equipo: this.api.agentes$(this.idBroker()),
    }).subscribe({
      next: ({ broker, equipo }) => {
        this.broker.set(broker);
        this.equipo.set(equipo ?? []);
        this.cargando.set(false);
      },
      error: (error: unknown) => {
        this.error.set(
          error instanceof ApiError ? error.message : 'No se pudo cargar la ficha del broker.',
        );
        this.cargando.set(false);
      },
    });
  }

  protected editar(): void {
    void this.router.navigate(['/brokers', this.idBroker(), 'editar']);
  }

  protected volver(): void {
    void this.router.navigate(['/brokers']);
  }

  protected verAgentes(): void {
    void this.router.navigate(['/agentes']);
  }

  // -- presentación --------------------------------------------------------

  protected texto(valor: string | undefined | null): string {
    return textoDe(valor);
  }

  protected documento(): string {
    const broker = this.broker();
    if (!broker) return SIN_DATO;
    const tipo = describir(TIPO_DOCUMENTO, broker.tipoDocumento);
    const numero = textoDe(broker.numeroDocumento);
    return tipo ? `${tipo} ${numero}` : numero;
  }

  protected operativo(agente: Agente): string {
    return ESTADO_OPERATIVO[agente.estadoOperativo ?? ''] ?? SIN_DATO;
  }

  protected agenteActivo(agente: Agente): boolean {
    return agente.estadoAdministrativo === 'A';
  }
}
