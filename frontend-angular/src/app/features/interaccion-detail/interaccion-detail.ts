import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import {
  CANAL_CONTACTO,
  CONTEXTO_INTERACCION,
  describir,
  RESULTADO_INTERACCION,
  resultadosDe,
} from '../../core/api/codigos';
import { Interaccion, InteraccionesService } from '../../core/api/interacciones.service';
import { AuthService } from '../../core/auth/auth.service';
import { fechaHora, SIN_DATO, texto as textoDe } from '../../core/formato';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { OpcionFiltro } from '../../shared/filtro-select/filtro-select';

/**
 * Ficha de una interacción, con la **única edición que el cable permite**.
 *
 * `PUT /interacciones/{id}` toca exactamente dos campos —`resultado` y
 * `observaciones`— y **descarta el resto en silencio**: ni el contexto, ni la
 * entidad de la que cuelga, ni el canal, ni la fecha. Por eso la pantalla
 * muestra todo lo demás como dato fijo, con el motivo a la vista, en vez de
 * ofrecer controles que prometerían un cambio que el backend no hace. Es la
 * misma decisión que `ClienteForm` con la identidad del cliente.
 *
 * El resultado se ofrece acotado a la allow-list **del contexto de esta
 * interacción**, no del contexto que el usuario quiera: cambiar de conversación
 * no es editar, es registrar otra.
 */
@Component({
  selector: 'app-interaccion-detail',
  imports: [EstadoListado, ReactiveFormsModule],
  templateUrl: './interaccion-detail.html',
  styleUrl: './interaccion-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InteraccionDetail implements OnInit {
  private readonly api = inject(InteraccionesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly guardando = signal(false);
  protected readonly editando = signal(false);
  protected readonly interaccion = signal<Interaccion | null>(null);
  protected readonly idInteraccion = signal(0);

  protected readonly resultado = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required],
  });
  protected readonly observaciones = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(2000)],
  });

  protected readonly puedeEditar = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  protected readonly opcionesResultado = computed<OpcionFiltro[]>(() =>
    resultadosDe(this.interaccion()?.contexto),
  );
  protected readonly esDePropietario = computed(() => {
    const contexto = this.interaccion()?.contexto;
    return contexto === 'PROSPECCION' || contexto === 'CAPTACION';
  });

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.error.set('La interacción indicada no es válida.');
      this.cargando.set(false);
      return;
    }
    this.idInteraccion.set(id);
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const interaccion = await this.api.obtener(this.idInteraccion());
      this.interaccion.set(interaccion);
      this.resultado.setValue(interaccion.resultado ?? '');
      this.observaciones.setValue(interaccion.observaciones ?? '');
    } catch (error) {
      this.interaccion.set(null);
      this.error.set(mensajeError(error, 'No se pudo cargar la interacción.'));
    } finally {
      this.cargando.set(false);
    }
  }

  protected editar(): void {
    this.mensaje.set(null);
    this.editando.set(true);
  }

  protected cancelarEdicion(): void {
    if (this.guardando()) return;
    const interaccion = this.interaccion();
    this.resultado.setValue(interaccion?.resultado ?? '');
    this.observaciones.setValue(interaccion?.observaciones ?? '');
    this.editando.set(false);
  }

  protected async guardar(): Promise<void> {
    if (this.guardando() || !this.puedeEditar()) return;
    if (this.resultado.invalid) {
      this.resultado.markAsTouched();
      return;
    }
    this.guardando.set(true);
    this.error.set(null);
    try {
      this.interaccion.set(
        await this.api.actualizar(
          this.idInteraccion(),
          this.resultado.value,
          this.observaciones.value.trim() || undefined,
        ),
      );
      this.editando.set(false);
      this.mensaje.set('Interacción actualizada.');
    } catch (error) {
      this.error.set(mensajeError(error, 'No se pudo actualizar la interacción.'));
    } finally {
      this.guardando.set(false);
    }
  }

  protected volver(): void {
    void this.router.navigate(['/interacciones']);
  }

  /**
   * Vuelve a la entidad de la que cuelga, **si su pantalla existe**. La
   * captación se abre por código y aquí solo se tiene el suyo cuando el cable
   * lo trae; si no, no se ofrece el enlace (misma regla que en `ClienteDetail`).
   */
  protected irAlOrigen(): void {
    const i = this.interaccion();
    if (!i) return;
    if (i.idOportunidad) void this.router.navigate(['/oportunidades', i.idOportunidad]);
    else if (i.idCliente) void this.router.navigate(['/clientes', i.idCliente, 'contacto']);
    else if (i.idProspeccion) void this.router.navigate(['/prospecciones', i.idProspeccion]);
    else if (i.codigoCaptacion) void this.router.navigate(['/captaciones', i.codigoCaptacion]);
  }

  protected tieneOrigen(): boolean {
    const i = this.interaccion();
    return !!(i?.idOportunidad || i?.idCliente || i?.idProspeccion || i?.codigoCaptacion);
  }

  protected etiquetaOrigen(): string {
    const i = this.interaccion();
    if (i?.idOportunidad) return 'Ver la oportunidad';
    if (i?.idCliente) return 'Ver la bitácora del cliente';
    if (i?.idProspeccion) return 'Ver la prospección';
    if (i?.codigoCaptacion) return 'Ver el expediente';
    return '';
  }

  protected contexto(): string {
    return describir(CONTEXTO_INTERACCION, this.interaccion()?.contexto) || SIN_DATO;
  }

  protected canal(): string {
    return describir(CANAL_CONTACTO, this.interaccion()?.canalContacto) || SIN_DATO;
  }

  protected resultadoActual(): string {
    return describir(RESULTADO_INTERACCION, this.interaccion()?.resultado) || SIN_DATO;
  }

  protected persona(): string {
    const i = this.interaccion();
    return textoDe(i?.personaNombre || i?.clienteNombre || i?.propietarioNombre);
  }

  protected referencia(): string {
    const i = this.interaccion();
    return textoDe(i?.codigoProspeccion || i?.codigoCaptacion);
  }

  protected momento(valor: string | undefined): string {
    return valor ? fechaHora(valor) : SIN_DATO;
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }
}

function mensajeError(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
