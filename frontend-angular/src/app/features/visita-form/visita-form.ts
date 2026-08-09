import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { Oportunidad, OportunidadesService } from '../../core/api/oportunidades.service';
import { VisitasService } from '../../core/api/visitas.service';
import { AuthService } from '../../core/auth/auth.service';
import { SIN_DATO, texto as textoDe } from '../../core/formato';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

const CANDIDATOS = 20;

/**
 * Programar una visita al local.
 *
 * La regla que manda aquí y que conviene no suavizar en pantalla: **el alta
 * exige que la oportunidad sea del PROPIO agente**, comparando directamente con
 * su rol operativo y **sin** alcance de broker. Un broker que abriera esta
 * pantalla recibiría 403 aunque la oportunidad sea de su equipo, así que la
 * ruta es de AGENTE y el formulario lo dice.
 *
 * Solo se ofrecen oportunidades **abiertas** (`A`): programar una visita sobre
 * una oportunidad cerrada o ya con solicitud no es lo que pide el proceso, y el
 * selector consulta al servidor con ese filtro en vez de traerse la bandeja.
 */
@Component({
  selector: 'app-visita-form',
  imports: [EstadoListado, ReactiveFormsModule],
  templateUrl: './visita-form.html',
  styleUrl: './visita-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VisitaForm implements OnInit {
  private readonly api = inject(VisitasService);
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
  protected readonly oportunidadFija = signal(false);

  protected readonly busqueda = new FormControl('', { nonNullable: true });

  protected readonly formulario = this.fb.group({
    idOportunidad: this.fb.control(0, [Validators.required, Validators.min(1)]),
    fechaVisita: this.fb.control('', [Validators.required]),
    horaVisita: this.fb.control('', [Validators.required]),
    observaciones: this.fb.control('', [Validators.maxLength(1000)]),
  });

  protected readonly puedeGuardar = computed(() => this.auth.sesion()?.rol === 'AGENTE');

  ngOnInit(): void {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.errorCarga.set(null);
    try {
      const id = idPositivo(this.route.snapshot.queryParamMap.get('oportunidad'));
      if (id) {
        const oportunidad = await this.oportunidadesApi.obtener(id);
        this.oportunidadActual.set(oportunidad);
        this.oportunidades.set([oportunidad]);
        this.totalOportunidades.set(1);
        this.formulario.controls.idOportunidad.setValue(oportunidad.id);
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
        // Abiertas: sobre una cerrada no se programa una visita.
        estado: 'A',
        query: this.busqueda.value.trim() || undefined,
      });
      this.oportunidades.set(pagina.items);
      this.totalOportunidades.set(pagina.totalRecords);
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
      this.errorGuardado.set('Indica la oportunidad, la fecha y la hora de la visita.');
      return;
    }
    this.guardando.set(true);
    this.errorGuardado.set(null);
    try {
      const datos = this.formulario.getRawValue();
      await this.api.programar({
        idOportunidad: datos.idOportunidad,
        fechaVisita: datos.fechaVisita,
        horaVisita: datos.horaVisita,
        observaciones: datos.observaciones.trim() || undefined,
      });
      void this.router.navigate(['/oportunidades', datos.idOportunidad]);
    } catch (error) {
      this.errorGuardado.set(mensajeError(error, 'No se pudo programar la visita.'));
    } finally {
      this.guardando.set(false);
    }
  }

  protected cancelar(): void {
    const id = this.oportunidadFija() ? this.oportunidadActual()?.id : null;
    if (id) {
      void this.router.navigate(['/oportunidades', id]);
    } else {
      void this.router.navigate(['/visitas']);
    }
  }

  protected descripcion(oportunidad: Oportunidad): string {
    return `${textoDe(oportunidad.codigoOportunidad)} · ${textoDe(oportunidad.clienteNombre)} — ${textoDe(oportunidad.direccionLocal)}`;
  }

  protected invalido(campo: 'idOportunidad' | 'fechaVisita' | 'horaVisita'): boolean {
    const control = this.formulario.controls[campo];
    return control.invalid && (control.touched || control.dirty);
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }

  protected get sinDato(): string {
    return SIN_DATO;
  }
}

function idPositivo(valor: string | null): number | null {
  const numero = Number(valor);
  return Number.isSafeInteger(numero) && numero > 0 ? numero : null;
}

function mensajeError(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
