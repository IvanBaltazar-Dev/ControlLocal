import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MembresiasService } from '../../core/api/membresias.service';
import { ApiError } from '../../core/api/api.types';
import { AvisoDeGobierno, CuentaDeGobierno, SeguridadService } from '../../core/api/seguridad.service';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { BroxPageHeader } from '../../shared/page-header/page-header';

/** Etiquetas de hechos conservados; leer un antecedente no habilita repetir la acción. */
const ETIQUETA_EVENTO: Readonly<Record<string, string>> = {
  MEMBRESIA_BAJA_FORMAL: 'Baja formal de pertenencia', INVITACION_RETIRADA: 'Invitación retirada',
  MFA_ACTIVADO: 'Segundo factor activado', MFA_REVOCADO: 'Segundo factor revocado',
  MFA_CODIGOS_REGENERADOS: 'Códigos de respaldo regenerados', MFA_CODIGO_RESPALDO_USADO: 'Acceso con código de respaldo',
  CUENTA_BLOQUEADA: 'Cuenta bloqueada por intentos', CUENTA_DESBLOQUEADA: 'Cuenta desbloqueada',
  CUENTA_ACTIVADA: 'Acceso activado', CUENTA_DESACTIVADA: 'Acceso desactivado',
  ROL_OTORGADO: 'Rol otorgado', ROL_REVOCADO: 'Rol revocado', PASSWORD_RESTABLECIDA: 'Contraseña restablecida',
  INVITACION_EMITIDA: 'Invitación registrada', ACCESO_TENANT_CONCEDIDO: 'Acceso a la organización concedido',
  ACCESO_TENANT_USADO: 'Acceso a la organización usado', BREAK_GLASS_ACTIVADO: 'Recuperación de emergencia activada',
};

/** Consulta de gobierno. Las credenciales y MFA pertenecen exclusivamente a su titular. */
@Component({
  selector: 'cl-seguridad', imports: [EstadoListado, BroxPageHeader, FormsModule, RouterLink],
  templateUrl: './seguridad.html', styleUrl: './seguridad.scss', changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Seguridad implements OnInit {
  private readonly api = inject(SeguridadService);
  private readonly membresias=inject(MembresiasService);
  protected readonly baja=signal<CuentaDeGobierno|null>(null);
  protected motivoBaja="";
  protected readonly guardando=signal(false);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly cuentas = signal<CuentaDeGobierno[]>([]);
  protected readonly avisos = signal<AvisoDeGobierno[]>([]);
  ngOnInit(): void { void this.cargar(); }
  protected async cargar(): Promise<void> {
    this.cargando.set(true); this.error.set(null);
    try {
      const [cuentas, avisos] = await Promise.all([this.api.cuentas(), this.api.avisos(1,30)]);
      this.cuentas.set(cuentas); this.avisos.set(avisos.items);
    } catch (error) {
      this.error.set(error instanceof ApiError ? error.message : 'No se pudo cargar el gobierno de accesos.');
    } finally { this.cargando.set(false); }
  }
  protected pedirBaja(cuenta: CuentaDeGobierno): void {this.motivoBaja='';this.baja.set(cuenta);}
  protected async confirmarBaja(): Promise<void> {
    const c=this.baja();if(!c||this.guardando()||!this.motivoBaja.trim())return;
    this.guardando.set(true);this.error.set(null);
    try{await this.membresias.darBaja(c.idMembresia,c.version,this.motivoBaja.trim());this.baja.set(null);await this.cargar();}
    catch(e){this.error.set(e instanceof ApiError?e.message:'No se pudo formalizar la baja.');}
    finally{this.guardando.set(false);}
  }
  protected etiqueta(tipo: string): string { return ETIQUETA_EVENTO[tipo] ?? tipo; }
  protected estadoDe(cuenta: CuentaDeGobierno): string { return cuenta.pertenenciaActiva ? 'Activa' : 'Inactiva'; }
}
