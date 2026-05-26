package com.controllocal.bl.impl;

import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.PersonaBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.impl.PersonaDAOImpl;
import com.controllocal.dao.PersonaDAO;
import com.controllocal.model.persona.Persona;

public class PersonaBusinessLogicImpl implements PersonaBusinessLogic {

    private final PersonaDAO personaDAO;

    public PersonaBusinessLogicImpl() {
        this(new PersonaDAOImpl());
    }

    public PersonaBusinessLogicImpl(PersonaDAO personaDAO) {
        this.personaDAO = personaDAO;
    }

    public Long registrar(Persona persona) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.persona(persona);
            return personaDAO.crear(persona);
        });
    }

    public Optional<Persona> buscarPorId(Long idPersona) {
        BusinessValidations.id(idPersona, "El id de persona");
        return personaDAO.buscarPorId(idPersona);
    }

    public List<Persona> listarTodos() {
        return personaDAO.listarTodos();
    }

    public boolean actualizar(Persona persona) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(persona != null ? persona.getIdPersona() : null, "El id de persona");
            BusinessValidations.persona(persona);
            return personaDAO.actualizar(persona);
        });
    }

    public boolean desactivar(Long idPersona) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idPersona, "El id de persona");
            return personaDAO.eliminar(idPersona);
        });
    }
}

