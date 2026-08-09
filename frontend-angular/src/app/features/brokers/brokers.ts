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
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { catchError, distinctUntilChanged, map, Observable, of, startWith, Subject, switchMap } from 'rxjs';

import { ApiError, paginaVacia, PageResponse } from '../../core/api/api.types';
import { Broker, BrokersService } from '../../core/api/brokers.service';
import { AuthService } from '../../core/auth/auth.service';
import { texto as textoDe } from '../../core/formato';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { Paginacion } from '../../shared/paginacion/paginacion';

const POR_PAGINA = 10;

type ResultadoCarga = { pagina: PageResponse<Broker> } | { error: string };

/**
 * Catálogo de brokers.
 *
 * **Leer es de cualquier sesión** —el catálogo de brokers no lleva alcance, los
 * tres roles ven lo mismo— pero **crear y editar es solo de ADMIN**. Por eso el
 * módulo aparece para todos y los botones no.
 *
 * `agentesACargo` cuenta supervisiones **vigentes**, no históricas: un broker al
 * que le reasignaron todo el equipo aparece con 0 aunque haya supervisado a
 * media corredora. El histórico está en Asignaciones.
 *
 * Regla del cable que la pantalla anticipa: **solo puede existir un broker
 * administrador por organización**. Si ya lo hay, el alta con esa marca no falla
 * en el formulario, falla con un 400 del backend, así que la lista lo señala.
 */
@Component({
  selector: 'cl-brokers',
  imports: [EstadoListado, Paginacion],
  templateUrl: './brokers.html',
  styleUrl: './brokers.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Brokers implements OnInit {
  private readonly servicio = inject(BrokersService);
  private readonly auth = inject(AuthService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly datos = signal<PageResponse<Broker>>(paginaVacia<Broker>());
  protected readonly pagina = signal(1);
  protected readonly porPagina = POR_PAGINA;

  private readonly recargar$ = new Subject<void>();

  protected readonly esAdmin = computed(() => this.auth.sesion()?.rol === 'TENANT_ADMIN');
  protected readonly filas = computed(() => this.datos().items ?? []);
  protected readonly total = computed(() => this.datos().totalRecords ?? 0);
  /** Si ya hay administrador, el alta con esa marca responderá 400. */
  protected readonly yaHayAdministrador = computed(() =>
    this.filas().some((broker) => broker.esAdministrador === true),
  );

  ngOnInit(): void {
    this.ruta.queryParamMap
      .pipe(
        map((params) => this.paginaDe(params)),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((pagina) => this.pagina.set(pagina));

    this.recargar$
      .pipe(
        startWith(undefined),
        switchMap(() => this.leer$()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => this.aplicar(resultado));
  }

  private paginaDe(params: ParamMap): number {
    const valor = Number(params.get('page'));
    return Number.isFinite(valor) && valor >= 1 ? Math.floor(valor) : 1;
  }

  private leer$(): Observable<ResultadoCarga> {
    this.cargando.set(true);
    this.error.set(null);
    return this.servicio.pagina$(this.pagina(), POR_PAGINA).pipe(
      map((pagina) => ({ pagina })),
      catchError((fallo: ApiError) =>
        of({ error: fallo.message || 'No se pudo cargar el catálogo de brokers.' }),
      ),
    );
  }

  private aplicar(resultado: ResultadoCarga): void {
    this.cargando.set(false);
    if ('error' in resultado) {
      this.error.set(resultado.error);
      this.datos.set(paginaVacia<Broker>());
      return;
    }
    this.datos.set(resultado.pagina);
  }

  protected irAPagina(pagina: number): void {
    void this.router.navigate([], {
      relativeTo: this.ruta,
      queryParams: pagina === 1 ? { page: null } : { page: pagina },
      queryParamsHandling: 'merge',
    });
    this.pagina.set(pagina);
    this.recargar$.next();
  }

  protected reintentar(): void {
    this.recargar$.next();
  }

  protected nuevo(): void {
    void this.router.navigate(['/brokers/nuevo']);
  }

  protected abrir(broker: Broker): void {
    void this.router.navigate(['/brokers', broker.id]);
  }

  protected editar(broker: Broker): void {
    void this.router.navigate(['/brokers', broker.id, 'editar']);
  }

  protected texto(valor: string | undefined | null): string {
    return textoDe(valor);
  }

  protected activo(broker: Broker): boolean {
    return broker.estadoAdministrativo === 'A';
  }
}
