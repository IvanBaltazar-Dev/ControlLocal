package com.controllocal.app;

import com.controllocal.config.DBManager;
import com.controllocal.dao.LocalComercialDAO;
import com.controllocal.dao.impl.LocalComercialDAOImpl;
import com.controllocal.model.inmueble.EstadoLocalComercial;
import com.controllocal.model.inmueble.LocalComercial;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ControlLocalApplication {

    public static void main(String[] args) {

        System.out.println("ControlLocal iniciado correctamente.");

    }
}
