package com.panaderia.sancarlos.model;

public class Pedido {

    private int idPedido;
    private String sucursal;
    private String fechaPedido;
    private String fechaRequerida;
    private String estado;
    private String observaciones;

    public Pedido() {
    }

    public int getIdPedido() {
        return idPedido;
    }

    public void setIdPedido(int idPedido) {
        this.idPedido = idPedido;
    }

    public String getSucursal() {
        return sucursal;
    }

    public void setSucursal(String sucursal) {
        this.sucursal = sucursal;
    }

    public String getFechaPedido() {
        return fechaPedido;
    }

    public void setFechaPedido(String fechaPedido) {
        this.fechaPedido = fechaPedido;
    }

    public String getFechaRequerida() {
        return fechaRequerida;
    }

    public void setFechaRequerida(String fechaRequerida) {
        this.fechaRequerida = fechaRequerida;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}