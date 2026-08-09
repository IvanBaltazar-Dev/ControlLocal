package com.controllocal.service.soporte;

import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.service.excepcion.ReglaNegocioException;

import java.time.LocalDate;
import java.util.Set;

/**
 * Reglas COMPARTIDAS de la persona en Party-Role: los vocabularios de codigo, la
 * validacion de documento y el alta de {@code persona} + {@code persona_rol}.
 *
 * <p>Existe porque cliente y propietario son <b>el mismo alta</b> con distinto
 * {@link TipoRol} —y agente y broker le anaden su detalle encima—: sin esto,
 * cada vertical de personas volveria a copiar los mismos mensajes del cable, que
 * es justo como se desincronizan.
 *
 * <p>Los mensajes son los de {@code BusinessValidations.persona} de la v1 y
 * estan CONGELADOS.
 */
public final class Personas {

    public static final Set<String> TIPOS_PERSONA = Set.of("N", "J");
    public static final Set<String> TIPOS_DOCUMENTO = Set.of("D", "R", "C", "P");
    public static final Set<String> ESTADOS = Set.of("A", "I");

    public static final String ACTIVO = "A";
    public static final String INACTIVO = "I";

    private Personas() {
    }

    /**
     * Documento y nombre. El largo solo se exige a DNI (8) y RUC (11); carne de
     * extranjeria y pasaporte pasan tal cual, igual que en la v1.
     */
    public static void validar(String tipoDocumento, String numeroDocumento, String nombre) {
        if (numeroDocumento == null || numeroDocumento.isBlank()) {
            throw new ReglaNegocioException("El numero de documento es obligatorio.");
        }
        String numero = numeroDocumento.trim();
        if ("R".equals(tipoDocumento)) {
            exigirDigitos(numero, "El RUC");
            if (numero.length() != 11) {
                throw new ReglaNegocioException("El RUC debe tener 11 digitos.");
            }
        } else if ("D".equals(tipoDocumento)) {
            exigirDigitos(numero, "El DNI");
            if (numero.length() != 8) {
                throw new ReglaNegocioException("El DNI debe tener 8 digitos.");
            }
        }
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("El nombre o razon social es obligatorio.");
        }
    }

    /**
     * Mensaje del {@code enumDesde} de la v1: un codigo ausente tambien es
     * "invalido", no "obligatorio".
     */
    public static String exigirCodigo(String valor, Set<String> validos, String campo) {
        if (valor == null || valor.isBlank() || !validos.contains(valor.trim())) {
            throw new ReglaNegocioException("Valor invalido para " + campo + ": " + valor);
        }
        return valor.trim();
    }

    /** Estado ausente = ACTIVO (default de {@code personaDesde} en la v1). */
    public static String estadoOActivo(String estado) {
        return estado == null || estado.isBlank()
                ? ACTIVO
                : exigirCodigo(estado, ESTADOS, "estado de la persona");
    }

    /**
     * Arma la persona con los codigos ya validados. No persiste: el llamador
     * decide en que orden guarda persona, rol y detalle.
     */
    public static Persona nueva(long idOrganizacion, String tipoPersona, String tipoDocumento,
                               String numeroDocumento, String nombre, String telefono, String correo,
                               String estado, Boolean consentimientoUsoDato) {
        Persona persona = new Persona();
        persona.setOrganizacionId(idOrganizacion);
        persona.setTipoPersona(tipoPersona);
        persona.setTipoDocumento(tipoDocumento);
        persona.setNumeroDocumento(numeroDocumento);
        persona.setNombresORazonSocial(nombre);
        persona.setTelefono(telefono);
        persona.setCorreo(correo);
        persona.setEstado(estado);
        persona.setConsentimientoUsoDato(consentimientoUsoDato);
        return persona;
    }

    /** Rol VIGENTE (sin fecha de cierre) de la persona dada. */
    public static PersonaRol nuevoRol(long idOrganizacion, Persona persona, TipoRol tipoRol) {
        PersonaRol rol = new PersonaRol();
        rol.setOrganizacionId(idOrganizacion);
        rol.setPersona(persona);
        rol.setTipoRol(tipoRol);
        rol.setVigenciaDesde(LocalDate.now());
        return rol;
    }

    private static void exigirDigitos(String valor, String campo) {
        if (!valor.chars().allMatch(Character::isDigit)) {
            throw new ReglaNegocioException(campo + " solo debe contener numeros.");
        }
    }
}
