import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { Sesion } from '../auth/sesion.model';
import { ApiClient } from './api.client';
import { ApiError } from './api.types';
import { CapturaService, RevisionCaptura, ResultadoActuacionCaptura } from './captura.service';

const REVISION: RevisionCaptura = {
  propuesta: { referencia: 'referencia-original', capacidad: 'confirmar_captura', version: 3,
    huella: 'huella-exacta', caducaEnEpochSeconds: 9999999999,
    contenido: { idBorrador: 7, versionCaptura: 5, codigoCaptura: 'CAP-7',
      intencion: 'REGISTRAR_PROPIEDAD', efecto: 'REGISTRAR_PROPIEDAD',
      datos: { direccion: 'Av. Central 10' }, definicionCore: {
        intencion: 'REGISTRAR_PROPIEDAD', tipoPropiedad: 'CASA', operaciones: [],
        comunes: [], delTipo: [], deLaOperacion: [] } } }, pruebaRevision: 'nonce-solo-superficie-humana',
};
const EXITO: ResultadoActuacionCaptura = { referencia: 'referencia-original', estado: 'EXITO',
  propuesta: REVISION.propuesta, resultado: { idBorrador: 7, idPropiedad: 42,
    codigoPropiedad: 'PROP-42', idsEncargos: [], reintento: false }, motivo: null };

describe('CapturaService: actuación revisada y recuperación', () => {
  let api: jasmine.SpyObj<ApiClient>;
  let captura: CapturaService;
  const identidad = signal<Sesion | null>(null);

  beforeEach(async () => {
    identidad.set({
      idCuenta: 901, idOrganizacion: 9, revisionContexto: 1, contextoRequerido: false, contextos: [],
      modalidad: 'EMPRESARIAL', referenciaSesion: 'sesion-fixture', contextoRef: 'contexto-fixture', idUsuario: 11, idDominio: 21, rol: 'AGENTE', usuario: 'profesional',
      nombre: 'Profesional', token: 'token-fixture-no-persistido', expiraEnSegundos: 1800,
      expiraEn: '2099-01-01T00:00:00Z' });
    api = jasmine.createSpyObj<ApiClient>('ApiClient', ['post', 'get', 'delete']);
    TestBed.configureTestingModule({ providers: [CapturaService, { provide: ApiClient, useValue: api },
      { provide: AuthService, useValue: { sesion: identidad } }] });
    captura = TestBed.inject(CapturaService);
    sessionStorage.removeItem('brox:actuacion:v1:9:11:AGENTE:21:7');
    api.get.and.resolveTo({ capacidades: [{ idCore: 'consultar_actuacion', contexto: {
      organizacionId: 9, personaId: 11, rol: 'AGENTE' } }] });
    await captura.cargarContextoReferencias();
    api.get.calls.reset();
  });

  it('prepara y obtiene revisión humana de la misma propuesta antes de confirmar', async () => {
    api.post.and.resolveTo(REVISION.propuesta);
    api.get.and.resolveTo(REVISION);
    expect(await captura.prepararRevision(7)).toEqual(REVISION);
    expect(api.post).toHaveBeenCalledOnceWith('actuaciones/capturas/7/propuestas');
    expect(api.get).toHaveBeenCalledOnceWith('actuaciones/referencia-original/revision');
  });

  it('rechaza una revisión que corresponde a otra versión o contenido', async () => {
    api.post.and.resolveTo(REVISION.propuesta);
    api.get.and.resolveTo({ ...REVISION, propuesta: { ...REVISION.propuesta, version: 4 } });
    await expectAsync(captura.prepararRevision(7)).toBeRejectedWithError(ApiError);
    expect(api.post).toHaveBeenCalledTimes(1);
  });

  it('confirma exactamente huella, versión, nonce y evento recibidos sin volver a preparar', async () => {
    api.post.and.resolveTo(EXITO);
    expect(await captura.confirmarRevision(REVISION, 'evento-estable')).toEqual(EXITO);
    expect(api.post).toHaveBeenCalledOnceWith('actuaciones/referencia-original/confirmacion', {
      version: 3, huella: 'huella-exacta', pruebaRevision: REVISION.pruebaRevision,
      eventoHumano: 'evento-estable',
    });
    expect(api.get).not.toHaveBeenCalled();
  });

  it('una respuesta perdida consulta la referencia original y nunca reenvía el POST', async () => {
    api.post.and.rejectWith(new ApiError(0, 'sin respuesta'));
    api.get.and.resolveTo(EXITO);
    expect(await captura.confirmarRevision(REVISION, 'evento-estable')).toEqual(EXITO);
    expect(api.post).toHaveBeenCalledTimes(1);
    expect(api.get).toHaveBeenCalledOnceWith('actuaciones/referencia-original');
  });

  it('sin resultado verificable conserva incertidumbre y sólo la referencia técnica', async () => {
    api.post.and.rejectWith(new ApiError(503, 'servicio no disponible'));
    api.get.and.rejectWith(new ApiError(503, 'sin consulta'));
    const resultado = await captura.confirmarRevision(REVISION, 'evento-estable');
    expect(resultado.estado).toBe('EN_VERIFICACION');
    expect(resultado.resultado).toBeNull();
    expect(captura.referenciaPendiente(7)).toBe('referencia-original');
    expect(sessionStorage.getItem('brox:actuacion:v1:9:11:AGENTE:21:7')).not.toContain('nonce');
    expect(api.post).toHaveBeenCalledTimes(1);
  });

  it('una denegación concreta no se interpreta como timeout ni resultado vacío', async () => {
    api.post.and.rejectWith(new ApiError(403, 'permiso insuficiente'));
    await expectAsync(captura.confirmarRevision(REVISION, 'evento-estable')).toBeRejectedWithError(ApiError);
    expect(api.get).not.toHaveBeenCalled();
    expect(captura.referenciaPendiente(7)).toBeNull();
  });

  it('un cuerpo de éxito incompatible tampoco prueba el resultado de negocio', async () => {
    api.post.and.resolveTo({ estado: 'EXITO' });
    api.get.and.rejectWith(new ApiError(502, 'sin contrato válido'));
    expect((await captura.confirmarRevision(REVISION, 'evento-estable')).estado).toBe('EN_VERIFICACION');
    expect(api.post).toHaveBeenCalledTimes(1);
    expect(api.get).toHaveBeenCalledTimes(1);
  });

  it('no ofrece referencias locales de otro operador o después de cerrar sesión', async () => {
    api.post.and.rejectWith(new ApiError(0, 'sin respuesta'));
    api.get.and.rejectWith(new ApiError(503, 'sin consulta'));
    await captura.confirmarRevision(REVISION, 'evento-estable');
    expect(captura.referenciasPendientes().length).toBe(1);
    identidad.set(null);
    expect(captura.referenciasPendientes()).toEqual([]);
    expect(captura.referenciaPendiente(7)).toBeNull();
  });

  it('cambiar de organización invalida la memoria local sin borrar el registro guardado', () => {
    sessionStorage.setItem('brox:actuacion:v1:9:11:AGENTE:21:7', 'referencia-original');
    identidad.update(s => s ? { ...s, idOrganizacion: 10 } : s);
    expect(captura.referenciaPendiente(7)).toBeNull();
    expect(captura.referenciasPendientes()).toEqual([]);
    expect(sessionStorage.getItem('brox:actuacion:v1:9:11:AGENTE:21:7')).toBe('referencia-original');
  });

  it('no mezcla la organización de la capacidad con otra sesión vigente', async () => {
    identidad.update(s => s ? { ...s, idOrganizacion: 10 } : s);
    api.get.and.resolveTo({ capacidades: [{ idCore: 'consultar_actuacion', contexto: {
      organizacionId: 9, personaId: 11, rol: 'AGENTE' } }] });
    await expectAsync(captura.cargarContextoReferencias()).toBeRejectedWithError(ApiError);
  });
});
