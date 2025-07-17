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
    import java.util.*;

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


        private WeatherData crearDatoVacio(String estacion) {
            WeatherData dato = new WeatherData();
            dato.setEstacion(estacion);
            dato.setFecha("--");
            dato.setHora("--");

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