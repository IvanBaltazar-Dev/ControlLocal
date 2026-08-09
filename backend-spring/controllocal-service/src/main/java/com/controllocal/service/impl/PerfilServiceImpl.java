package com.controllocal.service.impl;

import com.controllocal.domain.persona.Persona;
import com.controllocal.persistence.repositorio.PersonaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PerfilService;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerfilServiceImpl implements PerfilService {

    private final PersonaRepository personas;

    public PerfilServiceImpl(PersonaRepository personas) {
        this.personas = personas;
    }

    @Override
    @Transactional(readOnly = true)
    public FichaPerfil obtener(Actor actor) {
        return ficha(cargar(actor));
    }

    @Override
    @Transactional
    public FichaPerfil actualizarTelefono(String telefono, Actor actor) {
        Persona persona = cargar(actor);
        if (telefono != null) {
            String valor = telefono.trim();
            long digitos = valor.chars().filter(Character::isDigit).count();
            if (digitos < 6 || digitos > 15) {
                throw new ReglaNegocioException(
                        "Ingresa un telefono valido de entre 6 y 15 digitos.");
            }
            persona.setTelefono(valor);
            personas.save(persona);
        }
        return ficha(persona);
    }

    @Override
    @Transactional
    public String actualizarFoto(String clave, Actor actor) {
        Persona persona = cargar(actor);
        persona.setFotoClave(clave);
        personas.save(persona);
        return clave;
    }

    private Persona cargar(Actor actor) {
        return personas.findByOrganizacionIdAndId(
                        actor.idOrganizacion(), actor.idPersona())
                .orElseThrow(() -> new NoEncontradoException("Usuario"));
    }

    private static FichaPerfil ficha(Persona persona) {
        return new FichaPerfil(persona.getNombresORazonSocial(),
                persona.getCorreo(), persona.getTelefono(), persona.getFotoClave());
    }
}
