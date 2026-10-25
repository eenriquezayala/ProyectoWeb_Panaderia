package com.panaderia.sancarlos.model;

public class LoginRespuesta {

    private boolean exito;
    private String mensaje;
    private Usuario usuario;

    public LoginRespuesta() {
    }

    public LoginRespuesta(
            boolean exito,
            String mensaje,
            Usuario usuario) {

        this.exito = exito;
        this.mensaje = mensaje;
        this.usuario = usuario;
    }

    public boolean isExito() {
        return exito;
    }

    public void setExito(boolean exito) {
        this.exito = exito;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }
}