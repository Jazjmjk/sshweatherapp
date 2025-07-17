package com.data.sshweatherapp.controller;

import com.data.sshweatherapp.model.Estacion;
import com.data.sshweatherapp.model.WeatherData;
import com.data.sshweatherapp.service.SshService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.DoubleSummaryStatistics;
import java.util.Optional;

@Controller
public class WeatherController {

    @Autowired
    private SshService sshService;

    @Autowired
    private ObjectMapper objectMapper;

    @Scheduled(fixedRate = 60000)
    public void mantenerConexionSSH() {
        sshService.keepAlive();
    }

    @GetMapping("/")
    public String showPrincipal(Model model) {
        try {
            List<String> todasLasEstaciones = sshService.getNombreEstaciones();
            List<WeatherData> todosLosDatos = sshService.getUltimosDatosTodasEstaciones();

            System.out.println("Total de estaciones: " + todasLasEstaciones.size());
            System.out.println("Total de datos obtenidos: " + todosLosDatos.size());

            Map<String, List<WeatherData>> datosPorRegion = new LinkedHashMap<>();
            Map<String, String> aqiTextoMap = new HashMap<>();

            Map<String, WeatherData> datosMap = new HashMap<>();
            for (WeatherData data : todosLosDatos) {
                if (data != null && data.getEstacion() != null) {
                    datosMap.put(data.getEstacion(), data);
                }
            }

            List<String> estacionesTGZ = new ArrayList<>();
            List<String> estacionesKRK = new ArrayList<>();
            List<String> estacionesOtras = new ArrayList<>();

            for (String estacion : todasLasEstaciones) {
                if (estacion.startsWith("TGZ/")) {
                    estacionesTGZ.add(estacion);
                } else if (estacion.startsWith("KRK/")) {
                    estacionesKRK.add(estacion);
                } else {
                    estacionesOtras.add(estacion);
                }
            }

            Collections.sort(estacionesTGZ);
            Collections.sort(estacionesKRK);
            Collections.sort(estacionesOtras);

            Map<String, WeatherData> estacionesPrincipales = new LinkedHashMap<>();

            if (!estacionesTGZ.isEmpty()) {
                String estacionPrincipalTGZ = estacionesTGZ.get(0);
                WeatherData dato = datosMap.get(estacionPrincipalTGZ);
                if (dato == null) {
                    dato = crearDatoVacio(estacionPrincipalTGZ);
                }
                estacionesPrincipales.put(estacionPrincipalTGZ, dato);
            }

            if (!estacionesKRK.isEmpty()) {
                String estacionPrincipalKRK = estacionesKRK.get(0);
                WeatherData dato = datosMap.get(estacionPrincipalKRK);
                if (dato == null) {
                    dato = crearDatoVacio(estacionPrincipalKRK);
                }
                estacionesPrincipales.put(estacionPrincipalKRK, dato);
            }

            if (!estacionesOtras.isEmpty()) {
                String estacionPrincipalOtra = estacionesOtras.get(0);
                WeatherData dato = datosMap.get(estacionPrincipalOtra);
                if (dato == null) {
                    dato = crearDatoVacio(estacionPrincipalOtra);
                }
                estacionesPrincipales.put(estacionPrincipalOtra, dato);
            }

            List<String> estacionesOrdenadas = new ArrayList<>();
            estacionesOrdenadas.addAll(estacionesTGZ);
            estacionesOrdenadas.addAll(estacionesKRK);
            estacionesOrdenadas.addAll(estacionesOtras);

            Map<String, String> nombresAmigables = Map.of(
                    "TGZ", "Tuxtla Gutiérrez",
                    "KRK", "Cracovia"
            );

            Map<String, String> nombresAmigablesEstaciones = new HashMap<>();

            for (String estacion : estacionesOrdenadas) {
                WeatherData dato = datosMap.get(estacion);
                if (dato == null) {
                    dato = crearDatoVacio(estacion);
                }

                nombresAmigablesEstaciones.put(estacion, obtenerNombreEstacionFormateado(estacion));

                if (dato.getAqi() != null) {
                    aqiTextoMap.put(estacion, getTextoAQI(dato.getAqi()));
                } else {
                    aqiTextoMap.put(estacion, "--");
                }

                String region = dato.getRegion();
                if (region != null && !region.isEmpty()) {
                    datosPorRegion.computeIfAbsent(region, k -> new ArrayList<>()).add(dato);
                }
            }

            for (String estacion : estacionesPrincipales.keySet()) {
                WeatherData dato = estacionesPrincipales.get(estacion);
                nombresAmigablesEstaciones.put(estacion, SshService.obtenerNombreAmigable(estacion));

                if (dato.getAqi() != null) {
                    aqiTextoMap.put(estacion, getTextoAQI(dato.getAqi()));
                } else {
                    aqiTextoMap.put(estacion, "--");
                }
            }

            Map<String, List<WeatherData>> datosPorRegionOrdenado = new LinkedHashMap<>();
            if (datosPorRegion.containsKey("TGZ")) {
                datosPorRegionOrdenado.put("TGZ", datosPorRegion.get("TGZ"));
            }
            if (datosPorRegion.containsKey("KRK")) {
                datosPorRegionOrdenado.put("KRK", datosPorRegion.get("KRK"));
            }
            for (Map.Entry<String, List<WeatherData>> entry : datosPorRegion.entrySet()) {
                if (!entry.getKey().equals("TGZ") && !entry.getKey().equals("KRK")) {
                    datosPorRegionOrdenado.put(entry.getKey(), entry.getValue());
                }
            }

            model.addAttribute("datosPorEstacion", estacionesPrincipales);
            model.addAttribute("ultimosDatosEstaciones", todosLosDatos);
            model.addAttribute("fechaMin", LocalDate.now().withDayOfMonth(1));
            model.addAttribute("fechaMax", LocalDate.now());
            model.addAttribute("nombresAmigablesEstaciones", nombresAmigablesEstaciones);
            model.addAttribute("aqiTextoMap", aqiTextoMap);
            model.addAttribute("datosPorRegion", datosPorRegionOrdenado);
            model.addAttribute("nombresAmigables", nombresAmigables);

            System.out.println("Estaciones principales para Inicio: " + estacionesPrincipales.keySet());
            System.out.println("Datos por región para Reporte: " + datosPorRegionOrdenado.keySet());

        } catch (Exception e) {
            e.printStackTrace();
            manejarError(model);
        }

        return "principal";
    }

    @GetMapping("/descargarCSV")
    @ResponseBody
    public ResponseEntity<byte[]> descargarCSV(
            @RequestParam("estacion") String estacion,
            @RequestParam("fechaInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam("fechaFin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        List<WeatherData> datos = sshService.getDatosPorEstacionYRango(estacion, fechaInicio, fechaFin);
        if (datos == null || datos.isEmpty()) {
            String mensaje = "No hay datos disponibles para la estación " + estacion +
                    " entre " + fechaInicio + " y " + fechaFin;
            return ResponseEntity.badRequest()
                    .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
                    .body(mensaje.getBytes());
        }

        StringBuilder csv = new StringBuilder("Fecha,Hora,Temperatura,Presion,Humedad,AQI,Viento\n");
        for (WeatherData dato : datos) {
            csv.append(String.format("%s,%s,%.2f,%.4f,%.2f,%d,%.3f\n",
                    dato.getFecha(),
                    dato.getHora(),
                    dato.getTemperatura() != null ? dato.getTemperatura() : 0,
                    dato.getPresion() != null ? dato.getPresion() : 0,
                    dato.getHumedad() != null ? dato.getHumedad() : 0,
                    dato.getAqi() != null ? dato.getAqi() : 0,
                    dato.getViento() != null ? dato.getViento() : 0));
        }

        byte[] contenido = csv.toString().getBytes();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=datos_" + estacion + ".csv");
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok().headers(headers).body(contenido);
    }

    @GetMapping("/api/datosMensuales")
    @ResponseBody
    public List<WeatherData> getDatosMensuales(@RequestParam("estacion") String estacion) {
        LocalDate hoy = LocalDate.now();
        LocalDate inicioMes = hoy.withDayOfMonth(1);
        return sshService.getDatosPorEstacionYRango(estacion, inicioMes, hoy);
    }

    @GetMapping("/api/estacion")
    @ResponseBody
    public List<Estacion> getEstaciones() {
        return sshService.getEstacionesConCoordenadas();
    }

    // ========== MÉTODOS ADICIONALES PARA COMPLETAR LA API ==========

    /**
     * Obtiene la lista de nombres de todas las estaciones
     */
    @GetMapping("/api/estaciones/nombres")
    @ResponseBody
    public ResponseEntity<List<String>> getNombresEstaciones() {
        try {
            List<String> estaciones = sshService.getNombreEstaciones();
            if (estaciones == null || estaciones.isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(estaciones);
        } catch (Exception e) {
            System.err.println("Error obteniendo nombres de estaciones: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Obtiene el archivo más reciente de una estación específica
     */
    @GetMapping("/api/estacion/{estacion}/archivo-reciente")
    @ResponseBody
    public ResponseEntity<Map<String, String>> getArchivoReciente(
            @PathVariable String estacion,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getMonthValue()}") int mes) {

        try {
            String estacionFormateada = estacion.replace("-", "/");
            String archivoReciente = sshService.getArchivoMasReciente(estacionFormateada, mes);

            Map<String, String> respuesta = new HashMap<>();
            respuesta.put("estacion", estacionFormateada);
            respuesta.put("mes", String.valueOf(mes));
            respuesta.put("archivo", archivoReciente != null ? archivoReciente : "No encontrado");

            if (archivoReciente != null) {
                return ResponseEntity.ok(respuesta);
            } else {
                return ResponseEntity.noContent().build();
            }

        } catch (Exception e) {
            System.err.println("Error obteniendo archivo reciente: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Lee la última línea de un archivo específico
     */
    @GetMapping("/api/archivo/ultima-linea")
    @ResponseBody
    public ResponseEntity<WeatherData> getUltimaLineaArchivo(@RequestParam String rutaArchivo) {
        try {
            WeatherData dato = sshService.leerUltimaLineaArchivo(rutaArchivo);

            if (dato != null) {
                return ResponseEntity.ok(dato);
            } else {
                return ResponseEntity.noContent().build();
            }

        } catch (Exception e) {
            System.err.println("Error leyendo última línea: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Obtiene los últimos datos de todas las estaciones con validación
     */
    @GetMapping("/api/estaciones/datos-recientes")
    @ResponseBody
    public ResponseEntity<List<WeatherData>> getUltimosDatosTodasEstaciones() {
        try {
            List<WeatherData> datos = sshService.getUltimosDatosTodasEstaciones();

            if (datos == null || datos.isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            return ResponseEntity.ok(datos);

        } catch (Exception e) {
            System.err.println("Error obteniendo últimos datos: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Endpoint para verificar conectividad SSH
     */
    @GetMapping("/api/ssh/ping")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verificarConectividad() {
        try {
            long startTime = System.currentTimeMillis();
            sshService.keepAlive();
            long endTime = System.currentTimeMillis();

            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("estado", "OK");
            respuesta.put("tiempo_respuesta_ms", endTime - startTime);
            respuesta.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(respuesta);

        } catch (Exception e) {
            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("estado", "ERROR");
            respuesta.put("mensaje", "Error de conectividad SSH");
            respuesta.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(500).body(respuesta);
        }
    }

    /**
     * Obtiene datos de una estación específica en un rango de fechas con validación mejorada
     */
    @GetMapping("/api/estacion/{estacion}/datos-rango")
    @ResponseBody
    public ResponseEntity<List<WeatherData>> getDatosRangoValidado(
            @PathVariable String estacion,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        try {
            // Validar que la fecha de inicio no sea posterior a la fecha fin
            if (fechaInicio.isAfter(fechaFin)) {
                return ResponseEntity.badRequest().build();
            }

            // Validar que no se soliciten más de 31 días
            if (fechaInicio.until(fechaFin).getDays() > 31) {
                return ResponseEntity.badRequest().build();
            }

            String estacionFormateada = estacion.replace("-", "/");
            List<WeatherData> datos = sshService.getDatosPorEstacionYRango(estacionFormateada, fechaInicio, fechaFin);

            if (datos == null || datos.isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            return ResponseEntity.ok(datos);

        } catch (Exception e) {
            System.err.println("Error obteniendo datos por rango: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/api/estacion/{nombreEstacion}/info")
    @ResponseBody
    public ResponseEntity<Estacion> getInfoEstacion(@PathVariable String nombreEstacion) {
        try {
            String estacionFormateada = nombreEstacion.replace("-", "/");
            List<Estacion> todasEstaciones = sshService.getEstacionesConCoordenadas();

            Optional<Estacion> estacionEncontrada = todasEstaciones.stream()
                    .filter(e -> e.getNombre().equals(estacionFormateada))
                    .findFirst();

            if (estacionEncontrada.isPresent()) {
                return ResponseEntity.ok(estacionEncontrada.get());
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            System.err.println("Error obteniendo info de estación: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/api/estacion/{estacion}/estadisticas")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEstadisticasEstacion(
            @PathVariable String estacion,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        try {
            String estacionFormateada = estacion.replace("-", "/");
            List<WeatherData> datos = sshService.getDatosPorEstacionYRango(estacionFormateada, fechaInicio, fechaFin);

            if (datos == null || datos.isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            DoubleSummaryStatistics tempStats = datos.stream()
                    .filter(d -> d.getTemperatura() != null)
                    .mapToDouble(WeatherData::getTemperatura)
                    .summaryStatistics();

            DoubleSummaryStatistics humedadStats = datos.stream()
                    .filter(d -> d.getHumedad() != null)
                    .mapToDouble(WeatherData::getHumedad)
                    .summaryStatistics();

            DoubleSummaryStatistics presionStats = datos.stream()
                    .filter(d -> d.getPresion() != null)
                    .mapToDouble(WeatherData::getPresion)
                    .summaryStatistics();

            Map<String, Object> estadisticas = new HashMap<>();
            estadisticas.put("estacion", estacionFormateada);
            estadisticas.put("periodo", fechaInicio + " a " + fechaFin);
            estadisticas.put("total_registros", datos.size());

            Map<String, Double> temperatura = new HashMap<>();
            temperatura.put("minima", tempStats.getMin());
            temperatura.put("maxima", tempStats.getMax());
            temperatura.put("promedio", tempStats.getAverage());
            estadisticas.put("temperatura", temperatura);

            Map<String, Double> humedad = new HashMap<>();
            humedad.put("minima", humedadStats.getMin());
            humedad.put("maxima", humedadStats.getMax());
            humedad.put("promedio", humedadStats.getAverage());
            estadisticas.put("humedad", humedad);

            Map<String, Double> presion = new HashMap<>();
            presion.put("minima", presionStats.getMin());
            presion.put("maxima", presionStats.getMax());
            presion.put("promedio", presionStats.getAverage());
            estadisticas.put("presion", presion);

            return ResponseEntity.ok(estadisticas);

        } catch (Exception e) {
            System.err.println("Error calculando estadísticas: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/api/health")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", LocalDateTime.now().toString());

            try {
                sshService.keepAlive();
                health.put("ssh_connection", "OK");
            } catch (Exception e) {
                health.put("ssh_connection", "ERROR");
                health.put("ssh_error", e.getMessage());
            }

            try {
                List<String> estaciones = sshService.getNombreEstaciones();
                health.put("estaciones_disponibles", estaciones != null ? estaciones.size() : 0);
            } catch (Exception e) {
                health.put("estaciones_disponibles", 0);
            }

            return ResponseEntity.ok(health);

        } catch (Exception e) {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            health.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(500).body(health);
        }
    }


    private WeatherData crearDatoVacio(String estacion) {
        WeatherData dato = new WeatherData();
        dato.setEstacion(estacion);
        dato.setFecha("--");
        dato.setHora("--");
        dato.setTemperatura(0.0);
        dato.setPresion(0.0);
        dato.setHumedad(0.0);
        dato.setAqi(0);
        dato.setViento(0.0);

        String[] partes = estacion.split("/");
        if (partes.length >= 1) {
            dato.setRegion(partes[0]);
        }

        return dato;
    }

    private void manejarError(Model model) {
        try {
            List<String> estaciones = sshService.getNombreEstaciones();
            Map<String, WeatherData> datosVacios = new LinkedHashMap<>();
            Map<String, String> nombresVacios = new HashMap<>();

            List<String> estacionesTGZ = new ArrayList<>();
            List<String> estacionesKRK = new ArrayList<>();

            for (String estacion : estaciones) {
                if (estacion.startsWith("TGZ/")) {
                    estacionesTGZ.add(estacion);
                } else if (estacion.startsWith("KRK/")) {
                    estacionesKRK.add(estacion);
                }
            }

            if (!estacionesTGZ.isEmpty()) {
                String estacion = estacionesTGZ.get(0);
                datosVacios.put(estacion, crearDatoVacio(estacion));
                nombresVacios.put(estacion, SshService.obtenerNombreAmigable(estacion));
            }

            if (!estacionesKRK.isEmpty()) {
                String estacion = estacionesKRK.get(0);
                datosVacios.put(estacion, crearDatoVacio(estacion));
                nombresVacios.put(estacion, SshService.obtenerNombreAmigable(estacion));
            }

            model.addAttribute("datosPorEstacion", datosVacios);
            model.addAttribute("nombresAmigablesEstaciones", nombresVacios);
        } catch (Exception ex) {
            model.addAttribute("datosPorEstacion", new LinkedHashMap<>());
            model.addAttribute("nombresAmigablesEstaciones", new HashMap<>());
        }

        model.addAttribute("ultimosDatosEstaciones", new ArrayList<>());
        model.addAttribute("fechaMin", LocalDate.now().withDayOfMonth(1));
        model.addAttribute("fechaMax", LocalDate.now());
        model.addAttribute("aqiTextoMap", new HashMap<>());
        model.addAttribute("datosPorRegion", new LinkedHashMap<>());
        model.addAttribute("nombresAmigables", Map.of(
                "TGZ", "Tuxtla Gutiérrez",
                "KRK", "Cracovia"
        ));
    }

    private String getTextoAQI(Integer aqi) {
        if (aqi == null) {
            return "--";
        }

        if (aqi <= 50) {
            return "Bueno";
        } else if (aqi <= 100) {
            return "Regular";
        } else {
            return "Malo";
        }
    }

    private String obtenerNombreEstacionFormateado(String estacion) {
        if (estacion == null || !estacion.contains("/")) {
            return estacion;
        }
        String[] partes = estacion.split("/");
        if (partes.length >= 2) {
            return partes[0] + " - " + partes[1];
        }
        return estacion;
    }
}