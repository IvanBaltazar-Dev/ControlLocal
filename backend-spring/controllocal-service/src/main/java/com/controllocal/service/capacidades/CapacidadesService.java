package com.controllocal.service.capacidades;

import com.controllocal.service.Actor;
import java.util.List;
import java.util.Map;

/** CAT-E40 y plan §4. Describe adaptadores reconocidos; nunca concede una autorización de escritura. */
public interface CapacidadesService {
    int VERSION = 2;

    enum Estado { HABILITADA, CONTEXTO_REQUERIDO, PERMISO_INSUFICIENTE, DESHABILITADA }
    enum Efecto { LECTURA, BORRADOR, ESCRITURA, EXTERNO }
    enum Interaccion { AUTO, AVISA, CONFIRMA, HUMANO }

    /** Actor y tenant se resuelven en servidor. No forman parte de la consulta enviada por el cliente. */
    record Consulta(String idCore, String recurso, String referencia, String operacion,
                    String atributo, String etapa, String referenciaRelacionada) {
        public static Consulta general() { return new Consulta(null, null, null, null, null, null, null); }
    }

    record Contexto(long organizacionId, long personaId, String rol, String recurso,
                    String referencia, String operacion, String atributo, String etapa,
                    String referenciaRelacionada) { }

    record DescriptorCapacidad(String idCore, String familia, Efecto efecto, Interaccion interaccion,
                               Map<String, Object> esquema, int version, Estado estado,
                               String motivo, Contexto contexto) { }

    record CatalogoCapacidades(int version, List<DescriptorCapacidad> capacidades) {
        public CatalogoCapacidades { capacidades = List.copyOf(capacidades); }
    }

    CatalogoCapacidades consultar(Consulta consulta, Actor actor);
}
