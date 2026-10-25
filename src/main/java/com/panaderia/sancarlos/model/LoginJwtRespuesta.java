package com.panaderia.sancarlos.model;

/** Respuesta compatible con los campos anteriores; agrega token y duración. */
public class LoginJwtRespuesta {
    private boolean exito;
    private String mensaje;
    private Usuario usuario;
    private String token;
    private long expiraEnSegundos;

    public LoginJwtRespuesta() { }

    public LoginJwtRespuesta(boolean exito, String mensaje, Usuario usuario) {
        this.exito = exito;
        this.mensaje = mensaje;
        this.usuario = usuario;
    }

    public boolean isExito() { return exito; }
    public void setExito(boolean exito) { this.exito = exito; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public long getExpiraEnSegundos() { return expiraEnSegundos; }
    public void setExpiraEnSegundos(long expiraEnSegundos) { this.expiraEnSegundos = expiraEnSegundos; }
}
