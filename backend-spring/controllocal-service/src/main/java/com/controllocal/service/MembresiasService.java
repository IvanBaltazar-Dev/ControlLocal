package com.controllocal.service;

import java.time.Instant;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;

/** CAT-E01/RF-002/AC-025: baja formal local con versión, motivo y gobierno competente. */
public interface MembresiasService {
    record BajaFormal(long idMembresia,long idPersona,Instant altaRealEn,Instant bajaRealEn,long version) { }
    BajaFormal darBaja(long idMembresia,long versionEsperada,String motivo,Actor actor,ContextoSesion sesion);
}
