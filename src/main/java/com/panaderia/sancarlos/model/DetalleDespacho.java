
package com.panaderia.sancarlos.model;

public class DetalleDespacho {

    private String producto;
    private int cantidad;

    public DetalleDespacho() {
    }

    public String getProducto() {
        return producto;
    }

    public void setProducto(String producto) {
        this.producto = producto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }
}
