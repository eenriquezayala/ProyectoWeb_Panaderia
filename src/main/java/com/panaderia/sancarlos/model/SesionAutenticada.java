package com.panaderia.sancarlos.model;

import java.security.Principal;

/** Datos del usuario verificados por FiltroJwt contra Oracle. */
public final class SesionAutenticada implements Principal {
    private final int idUsuario;
    private final String usuario;
    private final String rol;
    private final Integer idSucursal;

    public SesionAutenticada(int idUsuario, String usuario,
            String rol, Integer idSucursal) {
        this.idUsuario = idUsuario;
        this.usuario = usuario;
        this.rol = rol;
        this.idSucursal = idSucursal;
    }

    @Override
    public String getName() {
        return usuario;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public String getRol() {
        return rol;
    }

    public Integer getIdSucursal() {
        return idSucursal;
    }

    public boolean esSucursal() {
        return "SUCURSAL".equals(rol);
    }
}
