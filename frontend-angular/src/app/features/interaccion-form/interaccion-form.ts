import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { CaptacionesService } from '../../core/api/captaciones.service';
import { ClientesService } from '../../core/api/clientes.service';
import {
  CANAL_CONTACTO,
  CONTEXTO_INTERACCION,
  opcionesDe,
  resultadosDe,
} from '../../core/api/codigos';
import { DatosInteraccion, InteraccionesService } from '../../core/api/interacciones.service';
import { OportunidadesService } from '../../core/api/oportunidades.service';
import { ProspeccionesService } from '../../core/api/prospecciones.service';
import { AuthService } from '../../core/auth/auth.service';
import { texto as textoDe } from '../../core/formato';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { OpcionFiltro } from '../../shared/filtro-select/filtro-select';

const CANDIDATOS = 20;

/** Una entidad candidata, ya normalizada: la pantalla no necesita su forma real. */
interface Candidato {
  id: number;
  etiqueta: string;
}

/** El parámetro de URL y el campo del request que corresponden a cada contexto. */
const CLAVE_DE_CONTEXTO: Readonly<Record<string, keyof DatosInteraccion>> = {
  OPORTUNIDAD: 'idOportunidad',
  PROSPECCION: 'idProspeccion',
  CAPTACION: 'idCaptacion',
  CLIENTE: 'idCliente',
};

const PARAMETRO_DE_CONTEXTO: Readonly<Record<string, string>> = {
  OPORTUNIDAD: 'oportunidad',
  PROSPECCION: 'prospeccion',
  CAPTACION: 'captacion',
  CLIENTE: 'cliente',
};

/**
 * Registrar una interacción: **una fila de la bitácora polimórfica**.
 *
 * Lo que define esta pantalla es que la interacción cuelga de UNA de cuatro
 * entidades, y eso cambia dos cosas a la vez:
 * - **de dónde salen los candidatos** (oportunidades, prospecciones,
 *   captaciones o clientes), y
 * - **qué resultados se pueden elegir**. El backend valida el resultado contra
 *   una allow-list por contexto y responde *"Resultado no valido para
 *   {contexto}: {codigo}"*; el selector ofrece exactamente esa lista, así que
 *   ese 400 no debería llegar nunca desde aquí.
 *
 * El contexto se envía **explícito** aunque el backend sepa inferirlo por el id
 * presente: depender de esa inferencia haría que un cambio de orden en el
 * backend cambiara el significado del formulario.
 *
 * `canalContacto` y `resultado` son **los dos obligatorios** (*"La interaccion
 * debe tener canal y resultado."*), aunque el DTO declare el resultado
 * opcional. La fecha no se pide: la pone el servidor.
 */
@Component({
  selector: 'app-interaccion-form',
  imports: [EstadoListado, ReactiveFormsModule],
  templateUrl: './interaccion-form.html',
  styleUrl: './interaccion-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InteraccionForm implements OnInit {
  private readonly api = inject(InteraccionesService);
  private readonly oportunidadesApi = inject(OportunidadesService);
  private readonly prospeccionesApi = inject(ProspeccionesService);
  private readonly captacionesApi = inject(CaptacionesService);
  private readonly clientesApi = inject(ClientesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly errorGuardado = signal<string | null>(null);
  protected readonly guardando = signal(false);

  protected readonly candidatos = signal<readonly Candidato[]>([]);
  protected readonly totalCandidatos = signal(0);
  protected readonly buscando = signal(false);
  /** La entidad llegó por la URL: el contexto y el destino no se cambian aquí. */
  protected readonly entidadFija = signal(false);

  protected readonly busqueda = new FormControl('', { nonNullable: true });

  protected readonly formulario = this.fb.group({
    contexto: this.fb.control('OPORTUNIDAD', [Validators.required]),
    idEntidad: this.fb.control(0, [Validators.required, Validators.min(1)]),
    canalContacto: this.fb.control('', [Validators.required]),
    resultado: this.fb.control('', [Validators.required]),
    observaciones: this.fb.control('', [Validators.maxLength(2000)]),
    transcripcionNota: this.fb.control('', [Validators.maxLength(4000)]),
  });

  protected readonly puedeGuardar = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  protected readonly opcionesContexto: OpcionFiltro[] = opcionesDe(CONTEXTO_INTERACCION);
  protected readonly opcionesCanal: OpcionFiltro[] = opcionesDe(CANAL_CONTACTO);

  /** Estado del contexto elegido, para que la plantilla reaccione al cambio. */
  protected readonly contexto = signal('OPORTUNIDAD');
  /** Allow-list del backend, replicada: solo se ofrece lo que acepta. */
  protected readonly opcionesResultado = computed<OpcionFiltro[]>(() =>
    resultadosDe(this.contexto()),
  );
  protected readonly etiquetaEntidad = computed(() => {
    switch (this.contexto()) {
      case 'PROSPECCION':
        return 'Prospección';
      case 'CAPTACION':
        return 'Captación';
      case 'CLIENTE':
        return 'Cliente';
      default:
        return 'Oportunidad';
    }
  });
  protected readonly marcadorBusqueda = computed(() => {
    switch (this.contexto()) {
      case 'PROSPECCION':
        return 'Código, dirección o distrito';
      case 'CAPTACION':
        return 'Código, dirección o distrito';
      case 'CLIENTE':
        return 'Nombre, razón social, documento o rubro';
      default:
        return 'Código, captación, dirección o cliente';
    }
  });

  ngOnInit(): void {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.errorCarga.set(null);
    try {
      const params = this.route.snapshot.queryParamMap;
      const solicitado = (params.get('contexto') ?? '').trim().toUpperCase();
      const contexto = CONTEXTO_INTERACCION[solicitado] ? solicitado : 'OPORTUNIDAD';
      this.formulario.controls.contexto.setValue(contexto);
      this.contexto.set(contexto);

      const id = idPositivo(params.get(PARAMETRO_DE_CONTEXTO[contexto]));
      if (id) {
        await this.fijarEntidad(contexto, id);
      } else {
        await this.buscar();
      }
    } catch (error) {
      this.errorCarga.set(mensajeError(error, 'No se pudo preparar el formulario.'));
    } finally {
      this.cargando.set(false);
    }
  }

  protected async cambiarContexto(): Promise<void> {
    const contexto = this.formulario.controls.contexto.value;
    this.contexto.set(contexto);
    // El resultado se limpia: su catálogo depende del contexto y conservarlo
    // enviaría un código que el backend rechaza con 400.
    this.formulario.controls.resultado.setValue('');
    this.formulario.controls.idEntidad.setValue(0);
    this.busqueda.setValue('');
    this.candidatos.set([]);
    await this.buscar();
  }

  protected async buscar(): Promise<void> {
    this.buscando.set(true);
    this.errorGuardado.set(null);
    const termino = this.busqueda.value.trim() || undefined;
    try {
      switch (this.contexto()) {
        case 'PROSPECCION': {
          const pagina = await this.prospeccionesApi.pagina({
            pagina: 1,
            tamano: CANDIDATOS,
            q: termino,
          });
          this.publicar(
            pagina.items.map((p) => ({
              id: p.id,
              etiqueta: `${textoDe(p.codigoProspeccion)} · ${textoDe(p.direccion)} (${textoDe(p.distrito)})`,
            })),
            pagina.totalRecords,
          );
          break;
        }
        case 'CAPTACION': {
          const pagina = await this.captacionesApi.pagina({
            pagina: 1,
            tamano: CANDIDATOS,
            q: termino,
          });
          this.publicar(
            pagina.items.map((c) => ({
              id: c.id,
              etiqueta: `${textoDe(c.codigoCaptacion)} · ${textoDe(c.direccionLocal)} (${textoDe(c.distritoLocal)})`,
            })),
            pagina.totalRecords,
          );
          break;
        }
        case 'CLIENTE': {
          const pagina = await this.clientesApi.pagina({
            pagina: 1,
            tamano: CANDIDATOS,
            estado: 'A',
            texto: termino,
          });
          this.publicar(
            pagina.items.map((c) => ({
              id: c.id,
              etiqueta: `${textoDe(c.nombre)} — ${textoDe(c.rubroComercial)}`,
            })),
            pagina.totalRecords,
          );
          break;
        }
        default: {
          const pagina = await this.oportunidadesApi.pagina({
            pagina: 1,
            tamano: CANDIDATOS,
            estado: 'A',
            query: termino,
          });
          this.publicar(
            pagina.items.map((o) => ({
              id: o.id,
              etiqueta: `${textoDe(o.codigoOportunidad)} · ${textoDe(o.clienteNombre)} — ${textoDe(o.direccionLocal)}`,
            })),
            pagina.totalRecords,
          );
        }
      }
    } catch (error) {
      this.errorGuardado.set(mensajeError(error, 'No se pudieron buscar los registros.'));
    } finally {
      this.buscando.set(false);
    }
  }

  protected async guardar(): Promise<void> {
    if (this.guardando() || !this.puedeGuardar()) return;
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.errorGuardado.set('Indica el registro, el canal de contacto y el resultado.');
      return;
    }
    this.guardando.set(true);
    this.errorGuardado.set(null);
    try {
      const datos = this.formulario.getRawValue();
      const creada = await this.api.registrar({
        contexto: datos.contexto,
        // Solo viaja el id de SU contexto: los otros tres quedan ausentes, que
        // es lo que el CHECK de la base exige.
        [CLAVE_DE_CONTEXTO[datos.contexto]]: datos.idEntidad,
        canalContacto: datos.canalContacto,
        resultado: datos.resultado,
        observaciones: datos.observaciones.trim() || undefined,
        transcripcionNota: datos.transcripcionNota.trim() || undefined,
      });
      void this.router.navigate(['/interacciones', creada.id]);
    } catch (error) {
      this.errorGuardado.set(mensajeError(error, 'No se pudo registrar la interacción.'));
    } finally {
      this.guardando.set(false);
    }
  }

  protected cancelar(): void {
    void this.router.navigate([...this.origen()]);
  }

  protected invalido(campo: 'idEntidad' | 'canalContacto' | 'resultado'): boolean {
    const control = this.formulario.controls[campo];
    return control.invalid && (control.touched || control.dirty);
  }

  private origen(): string[] {
    if (!this.entidadFija()) return ['/interacciones'];
    const id = this.formulario.controls.idEntidad.value;
    switch (this.contexto()) {
      case 'CLIENTE':
        return ['/clientes', String(id), 'contacto'];
      case 'PROSPECCION':
        return ['/prospecciones', String(id)];
      case 'OPORTUNIDAD':
        return ['/oportunidades', String(id)];
      default:
        // La captación se abre por su código, que aquí no se tiene: la bandeja
        // es el destino honesto.
        return ['/captaciones'];
    }
  }

  /**
   * Llegó por la URL: contexto y entidad quedan **deshabilitados**, no solo
   * ocultos. `getRawValue()` los sigue leyendo, así que el request va completo;
   * dejarlos editables prometería cambiar de conversación a mitad del registro.
   */
  private async fijarEntidad(contexto: string, id: number): Promise<void> {
    const etiqueta = await this.describirEntidad(contexto, id);
    this.candidatos.set([{ id, etiqueta }]);
    this.totalCandidatos.set(1);
    this.formulario.controls.idEntidad.setValue(id);
    this.formulario.controls.contexto.disable();
    this.formulario.controls.idEntidad.disable();
    this.entidadFija.set(true);
  }

  private async describirEntidad(contexto: string, id: number): Promise<string> {
    switch (contexto) {
      case 'PROSPECCION': {
        const p = await this.prospeccionesApi.obtener(id);
        return `${textoDe(p.codigoProspeccion)} · ${textoDe(p.direccion)}`;
      }
      case 'CAPTACION': {
        const c = await this.captacionesApi.obtener(id);
        return `${textoDe(c.codigoCaptacion)} · ${textoDe(c.direccionLocal)}`;
      }
      case 'CLIENTE': {
        const c = await this.clientesApi.obtener(id);
        return `${textoDe(c.nombre)} — ${textoDe(c.rubroComercial)}`;
      }
      default: {
        const o = await this.oportunidadesApi.obtener(id);
        return `${textoDe(o.codigoOportunidad)} · ${textoDe(o.clienteNombre)} — ${textoDe(o.direccionLocal)}`;
      }
    }
  }

  private publicar(candidatos: Candidato[], total: number): void {
    this.candidatos.set(candidatos);
    this.totalCandidatos.set(total);
  }
}

function idPositivo(valor: string | null): number | null {
  const numero = Number(valor);
  return Number.isSafeInteger(numero) && numero > 0 ? numero : null;
}

function mensajeError(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
