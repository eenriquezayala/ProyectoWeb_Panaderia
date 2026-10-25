package com.panaderia.sancarlos.model;

public class RespuestaOperacion {

    private boolean exito;
    private int id;
    private String mensaje;

    public RespuestaOperacion() {
    }

    public RespuestaOperacion(boolean exito, int id, String mensaje) {
        this.exito = exito;
        this.id = id;
        this.mensaje = mensaje;
    }

    public boolean isExito() {
        return exito;
    }

    public void setExito(boolean exito) {
        this.exito = exito;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}