package com.controllocal.service.demanda;

import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.service.Actor;
import com.controllocal.service.soporte.Procedencia;

/** RF003: Persona de una relación legítima; CONTACTO no es un rol CLIENTE. */
public interface InteresadosDeDemanda {
    enum TipoReferencia { CLIENTE, CONTACTO_NUEVO, DEMANDA_ORIGEN }
    record DatosContacto(String tipoPersona,String tipoDocumento,String numeroDocumento,
                         String nombre,String telefono,String correo) { }
    record Referencia(TipoReferencia tipo,Long idCliente,Long idDemandaOrigen,
                      Long versionOrigen,DatosContacto contacto) { }
    record Interesado(Persona persona,DetalleCliente cliente,String contextoJson) { }
    record Lectura(boolean autorizada,Long persona) { }

    /** RF003 contextual: misma autorización y forma, sin crear Persona antes de confirmar. */
    Referencia validar(Referencia referencia,Actor actor);
    /** Lectura del origen actual sin CAS ni creación; preserva la identidad original de un snapshot. */
    void exigirLectura(Referencia referencia,Long personaEsperada,Actor actor);
    /** Denegación esperada como dato para podar poblaciones antes de filtros/conteos, sin rollback por fila. */
    Lectura consultarLectura(Referencia referencia,Long personaEsperada,Actor actor);

    /** Sólo se ejecuta dentro de la misma TX de alta, después de validar el SID actual. */
    Interesado resolver(Referencia referencia,Actor actor,Procedencia procedencia);
}
