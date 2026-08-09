import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnDestroy,
  OnInit,
  signal,
  WritableSignal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { EstadoMfa, MfaService } from '../../core/api/mfa.service';
import { Perfil as DatosPerfil, PerfilService } from '../../core/api/perfil.service';
import { ArchivosService } from '../../core/archivos/archivos.service';
import { AVISO_MFA_REEMPLAZO } from '../../core/auth/avisos-acceso';
import { AuthService } from '../../core/auth/auth.service';
import { MFA_CODIGO_REUTILIZADO } from '../../core/auth/codigos-mfa';
import { iniciales as inicialesDe, texto as textoDe } from '../../core/formato';
import { CodigosRespaldo } from '../../shared/codigos-respaldo/codigos-respaldo';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { ImagenSegura } from '../../shared/imagen-segura/imagen-segura';

/** Qué está haciendo el usuario en el bloque de seguridad. */
type AccionMfa = 'regenerar' | 'reemplazar';

/** El backend cuenta DÍGITOS, no caracteres: "+51 999 888 777" son 11. */
const MIN_DIGITOS = 6;
const MAX_DIGITOS = 15;
const MAX_FOTO = 5 * 1024 * 1024;
/** Lo que dura un "listo" en pantalla antes de borrarse solo. */
const DURACION_AVISO_MS = 4000;

/**
 * Perfil propio de la sesión.
 *
 * **El alcance es deliberadamente pequeño y no hay que ampliarlo**: el contrato
 * congelado solo cubre teléfono y foto. En particular **no hay cambio de
 * contraseña** —la pantalla Blazor que lo ofrecía era un mock sin llamada HTTP—,
 * así que aquí no se pinta ese formulario: enseñarlo prometería algo que ningún
 * endpoint hace. Cuando exista el endpoint, se añade.
 *
 * Nombre y correo son de solo lectura: el cable no los edita desde aquí.
 *
 * La foto viaja en **base64** (único camino de `POST /perfil/foto`) y la v1
 * valida **extensión, no firma binaria**. Aquí se valida además la firma con
 * `ArchivosService` porque es gratis y no cambia el cable: un archivo que la v1
 * aceptaría y este rechaza es un archivo que no era una imagen.
 */
@Component({
  selector: 'cl-perfil',
  imports: [
    EstadoListado,
    ImagenSegura,
    ReactiveFormsModule,
    RouterLink,
    DialogoConfirmacion,
    CodigosRespaldo,
  ],
  templateUrl: './perfil.html',
  styleUrl: './perfil.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Perfil implements OnInit, OnDestroy {
  private readonly api = inject(PerfilService);
  private readonly mfa = inject(MfaService);
  private readonly archivos = inject(ArchivosService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly perfil = signal<DatosPerfil | null>(null);
  protected readonly estadoMfa = signal<EstadoMfa | null>(null);

  protected readonly guardando = signal(false);
  protected readonly errorTelefono = signal<string | null>(null);
  protected readonly exitoTelefono = signal<string | null>(null);

  protected readonly subiendo = signal(false);
  protected readonly errorFoto = signal<string | null>(null);
  protected readonly exitoFoto = signal<string | null>(null);

  protected readonly formulario = this.fb.nonNullable.group({
    telefono: this.fb.nonNullable.control('', [Validators.required, digitosValidos]),
  });

  protected readonly rol = computed(() => this.auth.sesion()?.rol ?? '');
  /** Respaldo del avatar mientras no haya foto; el mismo que pinta la topbar. */
  protected readonly iniciales = computed(() => inicialesDe(this.perfil()?.nombre));

  // ------------------------------------------------------- seguridad / MFA

  protected readonly accionMfa = signal<AccionMfa | null>(null);
  protected readonly ocupadoMfa = signal(false);
  protected readonly errorMfa = signal<string | null>(null);
  /** Los ocho nuevos, en memoria y solo mientras se muestran. */
  protected readonly codigosNuevos = signal<string[] | null>(null);

  /**
   * Reautenticación reforzada (D-S0-34): contraseña **y** código vigente. Los
   * dos, siempre. Una sesión abierta no basta para tocar el factor — si
   * bastara, robarla equivaldría a quedarse con la cuenta.
   */
  protected readonly reautenticacion = this.fb.nonNullable.group({
    contrasena: ['', Validators.required],
    codigo: ['', Validators.required],
  });

  protected abrirAccionMfa(accion: AccionMfa): void {
    this.accionMfa.set(accion);
    this.errorMfa.set(null);
    this.reautenticacion.reset({ contrasena: '', codigo: '' });
  }

  protected cerrarAccionMfa(): void {
    this.accionMfa.set(null);
    this.errorMfa.set(null);
    this.reautenticacion.reset({ contrasena: '', codigo: '' });
  }

  protected get reautenticacionIncompleta(): boolean {
    return this.reautenticacion.invalid;
  }

  protected async confirmarAccionMfa(): Promise<void> {
    const accion = this.accionMfa();
    if (!accion || this.ocupadoMfa() || this.reautenticacion.invalid) {
      return;
    }
    const datos = this.reautenticacion.getRawValue();
    const credenciales = { contrasena: datos.contrasena, codigo: datos.codigo.trim() };
    this.ocupadoMfa.set(true);
    this.errorMfa.set(null);
    try {
      if (accion === 'regenerar') {
        const respuesta = await this.mfa.regenerarCodigos(credenciales);
        // La sesión sigue viva a propósito: regenerar códigos no cambia quién
        // eres ni cómo entras, así que echar al usuario aquí sería castigo sin
        // motivo. Lo que sí cambia es el contador, y hay que refrescarlo.
        this.accionMfa.set(null);
        this.codigosNuevos.set(respuesta.codigos);
        await this.cargarEstadoMfa();
      } else {
        await this.mfa.reemplazar(credenciales);
        // Aquí NO se puede seguir: el servidor revocó el factor y mató todas
        // las sesiones. El usuario vuelve por el login y entra con la sesión
        // capada, que es la que lo lleva al enrolamiento — no hay forma de
        // acabar operando sin haber vuelto a enrolar.
        this.auth.olvidarSesionLocal();
        await this.router.navigate(['/login'], {
          queryParams: { aviso: AVISO_MFA_REEMPLAZO },
          replaceUrl: true,
        });
      }
    } catch (error) {
      this.errorMfa.set(this.mensajeMfa(error));
    } finally {
      this.ocupadoMfa.set(false);
    }
  }

  /**
   * Por `codigo`, no por texto. Lo que importa separar es "ese código ya se
   * usó" de "está mal": el primero **no se corrige reescribiéndolo**, y sin
   * decirlo el usuario insiste con el mismo hasta gastar sus intentos.
   */
  private mensajeMfa(error: unknown): string {
    if (!(error instanceof ApiError)) {
      return 'No se pudo completar la operación.';
    }
    if (error.codigo === MFA_CODIGO_REUTILIZADO) {
      return 'Ese código ya se usó. Espera a que tu aplicación muestre el siguiente.';
    }
    return error.message;
  }

  protected cerrarCodigosNuevos(): void {
    this.codigosNuevos.set(null);
  }

  ngOnInit(): void {
    void this.cargar();
  }

  ngOnDestroy(): void {
    for (const temporizador of this.temporizadores.values()) {
      clearTimeout(temporizador);
    }
  }

  private readonly temporizadores = new Map<WritableSignal<string | null>, number>();

  /**
   * Un "listo" que se borra solo. Estos mensajes se quedaban pegados hasta
   * cambiar de pantalla: "Teléfono actualizado." seguía ahí diez minutos
   * después de guardarlo, y un aviso permanente deja de leerse como aviso —el
   * usuario no sabe si confirma lo que acaba de hacer o lo de hace un rato—.
   *
   * Los errores NO pasan por aquí a propósito: un fallo se queda hasta que se
   * corrige.
   */
  private anunciar(destino: WritableSignal<string | null>, mensaje: string): void {
    destino.set(mensaje);
    clearTimeout(this.temporizadores.get(destino));
    this.temporizadores.set(
      destino,
      window.setTimeout(() => destino.set(null), DURACION_AVISO_MS),
    );
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const perfil = await this.api.obtener();
      this.perfil.set(perfil);
      this.formulario.patchValue({ telefono: perfil.telefono ?? '' });
      // Aparte y sin `await` en el camino crítico: que el estado del segundo
      // factor no se pueda leer no es motivo para no enseñar el perfil.
      void this.cargarEstadoMfa();
    } catch (error) {
      this.error.set(
        error instanceof ApiError ? error.message : 'No se pudo cargar tu perfil.',
      );
    } finally {
      this.cargando.set(false);
    }
  }

  private async cargarEstadoMfa(): Promise<void> {
    try {
      this.estadoMfa.set(await this.mfa.estado());
    } catch {
      // Sin dato no se pinta la tarjeta. Es mejor que inventar un "sin
      // configurar" que llevaría a un enrolamiento que el backend rechazaría.
      this.estadoMfa.set(null);
    }
  }

  protected get telefonoInvalido(): boolean {
    const control = this.formulario.controls.telefono;
    return control.invalid && (control.dirty || control.touched);
  }

  protected async guardarTelefono(): Promise<void> {
    if (this.guardando()) return;
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    this.errorTelefono.set(null);
    this.exitoTelefono.set(null);
    try {
      const perfil = await this.api.actualizarTelefono(
        this.formulario.controls.telefono.value.trim(),
      );
      this.perfil.set(perfil);
      this.anunciar(this.exitoTelefono, 'Teléfono actualizado.');
    } catch (error) {
      this.errorTelefono.set(
        error instanceof ApiError ? error.message : 'No se pudo actualizar el teléfono.',
      );
    } finally {
      this.guardando.set(false);
    }
  }

  protected async elegirFoto(evento: Event): Promise<void> {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0];
    entrada.value = '';
    if (!archivo || this.subiendo()) return;

    this.errorFoto.set(null);
    this.exitoFoto.set(null);

    const resultado = await this.archivos.validar(archivo, {
      extensiones: ['.png', '.jpg', '.jpeg'],
      tamanoMaximo: MAX_FOTO,
    });
    if (!resultado.valido) {
      this.errorFoto.set(resultado.error);
      return;
    }

    this.subiendo.set(true);
    try {
      const base64 = await this.archivos.base64(resultado.archivo.archivo);
      const subida = await this.api.subirFoto(resultado.archivo.nombreSeguro, base64);
      this.perfil.update((actual) => (actual ? { ...actual, fotoClave: subida.clave } : actual));
      this.anunciar(this.exitoFoto, 'Foto actualizada.');
    } catch (error) {
      this.errorFoto.set(
        error instanceof ApiError ? error.message : 'No se pudo subir la foto.',
      );
    } finally {
      this.subiendo.set(false);
    }
  }

  protected texto(valor: string | undefined | null): string {
    return textoDe(valor);
  }
}

/**
 * Espejo de la validación del cable: entre 6 y 15 **dígitos**, contando solo
 * caracteres numéricos. Se valida aquí para no descubrirlo con un 400.
 */
function digitosValidos(control: { value: string }): Record<string, boolean> | null {
  const digitos = (control.value ?? '').replace(/\D/g, '');
  return digitos.length >= MIN_DIGITOS && digitos.length <= MAX_DIGITOS
    ? null
    : { telefono: true };
}
