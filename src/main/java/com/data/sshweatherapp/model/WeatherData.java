package com.data.sshweatherapp.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

public class WeatherData {

    private String estacion;
    private String fecha;
    private String hora;
    private Double temperatura;
    private Double presion;
    private Double humedad;
    private Integer aqi;
    private Double viento;
    private String region;

    private LocalDateTime fechaHora;

    public String getEstacion() {
        return estacion;
    }

    public void setEstacion(String estacion) {
        this.estacion = estacion;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public String getHora() {
        return hora;
    }

    public void setHora(String hora) {
        this.hora = hora;
    }

    public Double getTemperatura() {
        return temperatura;
    }

    public void setTemperatura(Double temperatura) {
        this.temperatura = temperatura;
    }

    public Double getPresion() {
        return presion;
    }

    public void setPresion(Double presion) {
        this.presion = presion;
    }

    public Double getHumedad() {
        return humedad;
    }

    public void setHumedad(Double humedad) {
        this.humedad = humedad;
    }

    public Integer getAqi() {
        return aqi;
    }

    public void setAqi(Integer aqi) {
        this.aqi = aqi;
    }

    public Double getViento() {
        return viento;
    }

    public void setViento(Double viento) {
        this.viento = viento;
    }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public LocalDateTime getFechaHora() {
        if (fechaHora != null) {
            return fechaHora;
        }
        if (fecha == null || hora == null) return null;

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm");
            return LocalDateTime.parse(fecha + " " + hora, formatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public boolean esDeDia() {
        LocalDateTime fh = getFechaHora();
        if (fh == null) return true;
        int h = fh.getHour();
        return h >= 6 && h <= 18;
    }

    public boolean esClimaSoleado() {
        return temperatura != null && humedad != null && temperatura >= 25 && humedad <= 60;
    }

    public String getEstadoClima() {
        if (!esDeDia()) return "🌙";
        return esClimaSoleado() ? "☀️" : "⛅";
    }

    public String getCalidadAireTexto() {
        if (aqi == null) return "--";
        if (aqi <= 50) return "Bueno";
        if (aqi <= 100) return "Regular";
        return "Malo";
    }

    public String getFechaHoraFormateada() {
        LocalDateTime fh = getFechaHora();
        if (fh == null) return "--";
        return fh.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    @Override
    public String toString() {
        return "WeatherData{" +
                "estacion='" + estacion + '\'' +
                ", fecha='" + fecha + '\'' +
                ", hora='" + hora + '\'' +
                ", temperatura=" + temperatura +
                ", presion=" + presion +
                ", humedad=" + humedad +
                ", aqi=" + aqi +
                ", viento=" + viento +
                ", fechaHora=" + getFechaHoraFormateada() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WeatherData)) return false;
        WeatherData that = (WeatherData) o;
        return Objects.equals(estacion, that.estacion) &&
                Objects.equals(fecha, that.fecha) &&
                Objects.equals(hora, that.hora);
    }

    @Override
    public int hashCode() {
        return Objects.hash(estacion, fecha, hora);
    }

}
