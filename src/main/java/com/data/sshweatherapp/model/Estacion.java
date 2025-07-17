package com.data.sshweatherapp.model;

import java.time.LocalDateTime;

public class Estacion {
    private String nombre;
    private String nombreAmigable;
    private String region;
    private Double latitud;
    private Double longitud;
    private boolean activa;
    private Double temperatura;
    private Double presion;
    private Double humedad;
    private Double viento;
    private Integer aqi;
    private String descripcion;
    private LocalDateTime fechaHora;

    public Estacion() {}

    public Estacion(String nombre, String nombreAmigable, String region, Double latitud, Double longitud) {
        this.nombre = nombre;
        this.nombreAmigable = nombreAmigable;
        this.region = region;
        this.latitud = latitud;
        this.longitud = longitud;
    }


    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getNombreAmigable() { return nombreAmigable; }
    public void setNombreAmigable(String nombreAmigable) { this.nombreAmigable = nombreAmigable; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public Double getLatitud() { return latitud; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }

    public Double getLongitud() { return longitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }

    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }

    public Double getTemperatura() { return temperatura; }
    public void setTemperatura(Double temperatura) { this.temperatura = temperatura; }

    public Double getPresion() { return presion; }
    public void setPresion(Double presion) { this.presion = presion; }

    public Double getHumedad() { return humedad; }
    public void setHumedad(Double humedad) { this.humedad = humedad; }

    public Double getViento() { return viento; }
    public void setViento(Double viento) { this.viento = viento; }

    public Integer getAqi() { return aqi; }
    public void setAqi(Integer aqi) { this.aqi = aqi; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
}
