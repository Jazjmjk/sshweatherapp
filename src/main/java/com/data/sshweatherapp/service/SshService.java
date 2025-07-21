package com.data.sshweatherapp.service;

import com.data.sshweatherapp.model.Estacion;
import com.data.sshweatherapp.model.WeatherData;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

@Service
public class SshService {

    @Value("${ssh.host}")
    private String host;

    @Value("${ssh.user}")
    private String user;

    @Value("${ssh.password}")
    private String password;

    @Value("${ssh.remoteBasePath}")
    private String remoteBasePath;

    private static final int SSH_CONNECT_TIMEOUT = 10000;
    private static final int SSH_COMMAND_TIMEOUT = 15000;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private List<WeatherData> cacheDatosEstaciones = new ArrayList<>();
    private long ultimaActualizacionCache = 0;
    private static final long CACHE_DURATION_MS = 60_000;

    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    private LocalDateTime ajustarFechaHora(String fechaOriginal, String horaOriginal) {
        try {
            DateTimeFormatter formatterFecha = DateTimeFormatter.ofPattern("yy-MM-dd");
            LocalDate fecha = LocalDate.parse(fechaOriginal, formatterFecha);
            LocalTime hora = LocalTime.parse(horaOriginal);
            return LocalDateTime.of(fecha, hora).minusHours(6);
        } catch (Exception e) {
            System.err.println("Error ajustando fecha/hora: " + fechaOriginal + " " + horaOriginal);
            return null;
        }
    }

    private boolean esCada15Minutos(LocalTime hora) {
        return hora.getMinute() % 15 == 0;
    }

    private Session createSshSession() throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(user, host, 22);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(SSH_CONNECT_TIMEOUT);
        session.connect(SSH_CONNECT_TIMEOUT);
        return session;
    }

    private String executeCommand(Session session, String command, int timeoutMs) throws Exception {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            InputStream in = channel.getInputStream();
            channel.connect(timeoutMs);

            StringBuilder result = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                long startTime = System.currentTimeMillis();

                while ((line = reader.readLine()) != null) {

                    if (System.currentTimeMillis() - startTime > timeoutMs) {
                        throw new Exception("Timeout ejecutando comando: " + command);
                    }
                    result.append(line).append("\n");
                }
            }

            return result.toString();
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }
    private <T> T executeWithRetry(Callable<T> operation, String operationName) {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return operation.call();
            } catch (Exception e) {
                System.err.println("Intento " + attempt + " fallido para " + operationName + ": " + e.getMessage());

                if (attempt == MAX_RETRY_ATTEMPTS) {
                    System.err.println("❌ Operación " + operationName + " falló después de " + MAX_RETRY_ATTEMPTS + " intentos");
                    return null;
                }

                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    public List<String> getNombreEstaciones() {
        return executeWithRetry(() -> {
            List<String> estaciones = new ArrayList<>();
            Session session = null;

            try {
                session = createSshSession();

                String cmdZonas = "ls " + remoteBasePath;
                String zonasResult = executeCommand(session, cmdZonas, SSH_COMMAND_TIMEOUT);

                if (zonasResult == null || zonasResult.trim().isEmpty()) {
                    System.err.println("No se encontraron zonas en: " + remoteBasePath);
                    return estaciones;
                }

                String[] zonas = zonasResult.trim().split("\n");

                for (String zona : zonas) {
                    if (zona.trim().isEmpty()) continue;

                    try {
                        String cmdEstaciones = "ls " + remoteBasePath + "/" + zona.trim();
                        String estacionesResult = executeCommand(session, cmdEstaciones, SSH_COMMAND_TIMEOUT);

                        if (estacionesResult != null && !estacionesResult.trim().isEmpty()) {
                            String[] estacionesArray = estacionesResult.trim().split("\n");
                            for (String est : estacionesArray) {
                                if (!est.trim().isEmpty()) {
                                    estaciones.add(zona.trim() + "/" + est.trim());
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error obteniendo estaciones para zona " + zona + ": " + e.getMessage());
                    }
                }

            } finally {
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            }

            return estaciones;
        }, "getNombreEstaciones");
    }

    public String getSubcarpetaDinamica(Session session, String estacion) throws Exception {
        try {
            String pathEstacion = remoteBasePath + "/" + estacion;
            String result = executeCommand(session, "ls " + pathEstacion, SSH_COMMAND_TIMEOUT);

            if (result == null || result.trim().isEmpty()) {
                return null;
            }

            List<String> subcarpetas = new ArrayList<>();
            String[] lines = result.trim().split("\n");

            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && (line.matches("\\d{4}") || line.matches("\\d{2}"))) {
                    subcarpetas.add(line);
                }
            }

            if (subcarpetas.isEmpty()) {
                return null;
            }

            int mesActual = LocalDate.now().getMonthValue();
            String mesStr = String.format("%02d", mesActual);

            subcarpetas.sort(Collections.reverseOrder());

            for (String subcarpeta : subcarpetas) {
                if (subcarpetaTieneDatos(session, estacion, subcarpeta, mesStr)) {
                    return subcarpeta;
                }
            }

            for (int i = 1; i <= 3; i++) {
                int mesAnterior = mesActual - i;
                if (mesAnterior <= 0) continue;

                String mesAnteriorStr = String.format("%02d", mesAnterior);
                for (String subcarpeta : subcarpetas) {
                    if (subcarpetaTieneDatos(session, estacion, subcarpeta, mesAnteriorStr)) {
                        return subcarpeta;
                    }
                }
            }

            return subcarpetas.get(0);

        } catch (Exception e) {
            System.err.println("Error en getSubcarpetaDinamica para " + estacion + ": " + e.getMessage());
            return null;
        }
    }

    private boolean subcarpetaTieneDatos(Session session, String estacion, String subcarpeta, String mes) {
        try {
            String pathMes = remoteBasePath + "/" + estacion + "/" + subcarpeta + "/" + mes;
            String comando = "if [ -d \"" + pathMes + "\" ]; then " +
                    "find \"" + pathMes + "\" -name '*.csv' -type f | head -n 1; " +
                    "else echo ''; fi";

            String resultado = executeCommand(session, comando, SSH_COMMAND_TIMEOUT);
            return resultado != null && !resultado.trim().isEmpty();
        } catch (Exception e) {
            System.err.println("Error verificando datos en subcarpeta " + subcarpeta + ": " + e.getMessage());
            return false;
        }
    }

    public String getArchivoMasReciente(String estacion, int mes) {
        return executeWithRetry(() -> {
            Session session = null;
            String archivoReciente = null;

            try {
                session = createSshSession();

                String subcarpeta = getSubcarpetaDinamica(session, estacion);
                if (subcarpeta == null || subcarpeta.isEmpty()) {
                    System.out.println("No se encontró subcarpeta para estación: " + estacion);
                    return null;
                }

                String mesStr = String.format("%02d", mes);
                String pathMes = remoteBasePath + "/" + estacion + "/" + subcarpeta + "/" + mesStr;

                String comando = "if [ -d \"" + pathMes + "\" ]; then " +
                        "find \"" + pathMes + "\" -maxdepth 1 -type f -name '*.csv' | " +
                        "while read file; do echo \"$(stat -c '%Y' \"$file\") $file\"; done | " +
                        "sort -nr | head -n 1 | cut -d' ' -f2; " +
                        "else echo 'DIRECTORIO_NO_EXISTE'; fi";

                String resultado = executeCommand(session, comando, SSH_COMMAND_TIMEOUT);

                if (resultado != null && !resultado.trim().isEmpty() &&
                        !resultado.equals("DIRECTORIO_NO_EXISTE")) {
                    archivoReciente = resultado.trim();
                } else {
                    for (int i = 1; i <= 3 && archivoReciente == null; i++) {
                        int mesAnterior = mes - i;
                        if (mesAnterior <= 0) continue;

                        String mesAnteriorStr = String.format("%02d", mesAnterior);
                        String pathMesAnterior = remoteBasePath + "/" + estacion + "/" + subcarpeta + "/" + mesAnteriorStr;

                        String comandoAnterior = "if [ -d \"" + pathMesAnterior + "\" ]; then " +
                                "find \"" + pathMesAnterior + "\" -maxdepth 1 -type f -name '*.csv' | " +
                                "while read file; do echo \"$(stat -c '%Y' \"$file\") $file\"; done | " +
                                "sort -nr | head -n 1 | cut -d' ' -f2; " +
                                "else echo ''; fi";

                        String resultadoAnterior = executeCommand(session, comandoAnterior, SSH_COMMAND_TIMEOUT);
                        if (resultadoAnterior != null && !resultadoAnterior.trim().isEmpty()) {
                            archivoReciente = resultadoAnterior.trim();
                            break;
                        }
                    }
                }

            } finally {
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            }

            return archivoReciente;
        }, "getArchivoMasReciente-" + estacion);
    }

    public WeatherData leerUltimaLineaArchivo(String rutaArchivo) {
        return executeWithRetry(() -> {
            Session session = null;
            WeatherData dato = null;

            try {
                session = createSshSession();

                String resultado = executeCommand(session, "tail -n 1 " + rutaArchivo, SSH_COMMAND_TIMEOUT);

                if (resultado != null && !resultado.trim().isEmpty() &&
                        !resultado.startsWith("DATE")) {
                    String[] partes = resultado.trim().split(",");

                    if (partes.length >= 7) {
                        LocalDateTime fechaHora = ajustarFechaHora(partes[0], partes[1]);

                        dato = new WeatherData();
                        dato.setFecha(fechaHora != null ? fechaHora.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "--");
                        dato.setHora(fechaHora != null ? fechaHora.format(DateTimeFormatter.ofPattern("HH:mm")) : "--");

                        dato.setTemperatura(parsearDoubleSeguro(partes[2]));
                        dato.setPresion(parsearDoubleSeguro(partes[3]));
                        dato.setHumedad(parsearDoubleSeguro(partes[4]));
                        dato.setAqi(parsearIntSeguro(partes[5]));
                        dato.setViento(parsearDoubleSeguro(partes[6]));

                        dato.setFechaHora(fechaHora);
                    }
                }

            } finally {
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            }

            return dato;
        }, "leerUltimaLineaArchivo");
    }

    public List<WeatherData> getUltimosDatosTodasEstaciones() {
        long ahora = System.currentTimeMillis();

        if (!cacheDatosEstaciones.isEmpty() && (ahora - ultimaActualizacionCache) < CACHE_DURATION_MS) {
            return cacheDatosEstaciones;
        }

        List<WeatherData> ultimosDatos = new ArrayList<>();
        List<String> estaciones = getNombreEstaciones();

        if (estaciones == null || estaciones.isEmpty()) {
            System.err.println("No se pudieron obtener las estaciones");
            return ultimosDatos;
        }

        int mesActual = LocalDate.now().getMonthValue();
        List<CompletableFuture<WeatherData>> futures = new ArrayList<>();

        for (String estacion : estaciones) {
            CompletableFuture<WeatherData> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String archivoReciente = getArchivoMasReciente(estacion, mesActual);
                    if (archivoReciente == null || archivoReciente.trim().isEmpty()) {
                        return crearDatoVacio(estacion);
                    }

                    WeatherData dato = leerUltimaLineaArchivo(archivoReciente);
                    if (dato == null) dato = crearDatoVacio(estacion);
                    dato.setEstacion(estacion);

                    String[] partes = estacion.split("/");
                    if (partes.length >= 1) dato.setRegion(partes[0]);

                    return dato;

                } catch (Exception e) {
                    System.err.println("❌ Error procesando estación " + estacion + ": " + e.getMessage());
                    return crearDatoVacio(estacion);
                }
            }, executorService);

            futures.add(future);
        }

        for (CompletableFuture<WeatherData> future : futures) {
            try {
                WeatherData resultado = future.get(30, TimeUnit.SECONDS);
                if (resultado != null) ultimosDatos.add(resultado);
            } catch (TimeoutException e) {
                System.err.println("❌ Timeout procesando estación");
                future.cancel(true);
            } catch (Exception e) {
                System.err.println("❌ Error obteniendo resultado: " + e.getMessage());
            }
        }

        cacheDatosEstaciones = ultimosDatos;
        ultimaActualizacionCache = ahora;

        return ultimosDatos;
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

    public List<WeatherData> getDatosPorEstacionYRango(String estacion, LocalDate fechaInicio, LocalDate fechaFin) {
        return executeWithRetry(() -> {
            List<WeatherData> datosRango = new ArrayList<>();
            Session session = null;

            try {
                session = createSshSession();

                int mesInicio = fechaInicio.getMonthValue();
                int mesFin = fechaFin.getMonthValue();
                int anio = fechaInicio.getYear();

                String subcarpeta = getSubcarpetaDinamica(session, estacion);
                if (subcarpeta == null || subcarpeta.isEmpty()) {
                    return datosRango;
                }

                for (int mes = mesInicio; mes <= mesFin; mes++) {
                    String mesStr = String.format("%02d", mes);
                    String pathMes = remoteBasePath + "/" + estacion + "/" + subcarpeta + "/" + mesStr;

                    String cmdList = "[ -d \"" + pathMes + "\" ] && ls " + pathMes + "/*.csv || echo ''";
                    String archivosResult = executeCommand(session, cmdList, SSH_COMMAND_TIMEOUT);

                    if (archivosResult == null || archivosResult.trim().isEmpty()) {
                        continue;
                    }

                    String[] archivos = archivosResult.trim().split("\n");
                    for (String archivoPath : archivos) {
                        if (archivoPath == null || !archivoPath.contains("/")) continue;

                        String nombreArchivo = archivoPath.substring(archivoPath.lastIndexOf("/") + 1);
                        if (!nombreArchivo.endsWith(".csv")) continue;

                        String diaStr = nombreArchivo.replace(".csv", "");
                        if (!diaStr.matches("\\d+")) continue;

                        int dia = Integer.parseInt(diaStr);
                        LocalDate fechaArchivo = LocalDate.of(anio, mes, dia);

                        if ((fechaArchivo.isEqual(fechaInicio) || fechaArchivo.isAfter(fechaInicio)) &&
                                (fechaArchivo.isEqual(fechaFin) || fechaArchivo.isBefore(fechaFin))) {

                            String cmdCat = "tail -n +2 " + archivoPath;
                            String contenido = executeCommand(session, cmdCat, SSH_COMMAND_TIMEOUT);

                            if (contenido != null && !contenido.trim().isEmpty()) {
                                String[] lineas = contenido.trim().split("\n");
                                for (String linea : lineas) {
                                    if (!linea.trim().isEmpty()) {
                                        String[] partes = linea.trim().split(",");
                                        if (partes.length >= 7) {
                                            LocalDateTime fechaHora = ajustarFechaHora(partes[0], partes[1]);
                                            if (fechaHora != null && esCada15Minutos(fechaHora.toLocalTime())) {
                                                WeatherData dato = new WeatherData();
                                                dato.setEstacion(estacion);
                                                dato.setFecha(fechaHora.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                                                dato.setHora(fechaHora.format(DateTimeFormatter.ofPattern("HH:mm")));
                                                dato.setFechaHora(fechaHora);

                                                dato.setTemperatura(parsearDoubleSeguro(partes[2]));
                                                dato.setPresion(parsearDoubleSeguro(partes[3]));
                                                dato.setHumedad(parsearDoubleSeguro(partes[4]));
                                                dato.setAqi(parsearIntSeguro(partes[5]));
                                                dato.setViento(parsearDoubleSeguro(partes[6]));

                                                datosRango.add(dato);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            } finally {
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            }

            return datosRango;
        }, "getDatosPorEstacionYRango");
    }

    public void keepAlive() {
        executeWithRetry(() -> {
            Session session = null;
            try {
                session = createSshSession();
                executeCommand(session, "echo ping", 5000);
                return true;
            } finally {
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            }
        }, "keepAlive");
    }

    public List<Estacion> getEstacionesConCoordenadas() {
        List<Estacion> estaciones = new ArrayList<>();
        List<String> nombres = getNombreEstaciones();

        if (nombres == null || nombres.isEmpty()) {
            System.err.println("No se pudieron obtener nombres de estaciones");
            return estaciones;
        }

        for (String nombre : nombres) {
            try {
                String[] partes = nombre.split("/");
                String region = partes.length > 0 ? partes[0] : "";
                String estacionNombre = partes.length > 1 ? partes[1] : "";

                Double[] coords = generarCoordenadasPorRegion(region, estacionNombre);
                Double lat = coords[0];
                Double lon = coords[1];

                Estacion estacion = new Estacion();
                estacion.setNombre(nombre);
                estacion.setNombreAmigable(obtenerNombreAmigable(nombre));
                estacion.setLatitud(lat);
                estacion.setLongitud(lon);
                estacion.setDescripcion(obtenerNombreAmigable(nombre));
                estacion.setRegion(region);

                WeatherData dato = obtenerDatoMasReciente(nombre);

                if (dato != null && dato.getFechaHora() != null) {
                    LocalDateTime ahora = LocalDateTime.now();
                    LocalDateTime limite = ahora.minusMinutes(30);

                    estacion.setActiva(dato.getFechaHora().isAfter(limite));
                    estacion.setTemperatura(dato.getTemperatura());
                    estacion.setPresion(dato.getPresion());
                    estacion.setHumedad(dato.getHumedad());
                    estacion.setViento(dato.getViento());
                    estacion.setAqi(dato.getAqi());
                    estacion.setFechaHora(dato.getFechaHora());
                } else {
                    estacion.setActiva(false);
                    estacion.setTemperatura(0.0);
                    estacion.setPresion(0.0);
                    estacion.setHumedad(0.0);
                    estacion.setViento(0.0);
                    estacion.setAqi(0);
                }

                estaciones.add(estacion);
            } catch (Exception e) {
                System.err.println("Error procesando estación " + nombre + ": " + e.getMessage());
            }
        }

        return estaciones;
    }

    private Double[] generarCoordenadasPorRegion(String region, String estacionNombre) {
        Map<String, Double[]> coordenadasBase = Map.of(
                "TGZ", new Double[]{16.6946, -93.1861},
                "KRK", new Double[]{50.0647, 19.9450}
        );

        Double[] coordsBase = coordenadasBase.getOrDefault(region, new Double[]{16.7525, -93.1103});

        if ("TGZ".equals(region)) {
            return new Double[]{coordsBase[0], coordsBase[1]};
        }

        int hash = estacionNombre.hashCode();
        double offsetLat = ((hash % 1000) / 10000.0) * (hash % 2 == 0 ? 1 : -1);
        double offsetLon = (((hash / 1000) % 1000) / 10000.0) * (hash % 3 == 0 ? 1 : -1);

        if ("KRK".equals(region)) {
            offsetLat = Math.max(-0.03, Math.min(0.03, offsetLat));
            offsetLon = Math.max(-0.03, Math.min(0.03, offsetLon));
        }

        return new Double[]{
                coordsBase[0] + offsetLat,
                coordsBase[1] + offsetLon
        };
    }

    private WeatherData obtenerDatoMasReciente(String nombreEstacion) {
        try {
            int mesActual = LocalDate.now().getMonthValue();

            for (int i = 0; i < 3; i++) {
                int mes = mesActual - i;
                if (mes <= 0) continue;

                String archivoReciente = getArchivoMasReciente(nombreEstacion, mes);
                if (archivoReciente != null && !archivoReciente.isEmpty()) {
                    WeatherData dato = leerUltimaLineaArchivo(archivoReciente);
                    if (dato != null) {
                        return dato;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error obteniendo datos para " + nombreEstacion + ": " + e.getMessage());
        }
        return null;
    }

    public static String obtenerNombreAmigable(String codigo) {
        if (codigo.contains("/")) {
            String[] partes = codigo.split("/");
            String region = partes[0];
            String estacion = partes[1];

            String nombreRegion = Map.of(
                    "TGZ", "Tuxtla Gutiérrez",
                    "KRK", "Cracovia"
            ).getOrDefault(region, region);

            return nombreRegion + " - " + estacion;
        }

        return Map.of(
                "TGZ", "Tuxtla Gutiérrez",
                "KRK", "Cracovia"
        ).getOrDefault(codigo, codigo);
    }

    private Double parsearDoubleSeguro(String valor) {
        try {
            if (valor == null || valor.trim().isEmpty() ||
                    valor.equalsIgnoreCase("None") || valor.equalsIgnoreCase("null")) {
                return 0.0;
            }
            return Double.parseDouble(valor.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private Integer parsearIntSeguro(String valor) {
        try {
            if (valor == null || valor.trim().isEmpty() ||
                    valor.equalsIgnoreCase("None") || valor.equalsIgnoreCase("null")) {
                return 0;
            }

            double doubleValue = Double.parseDouble(valor.trim());
            return (int) Math.round(doubleValue);

        } catch (NumberFormatException e) {
            System.err.println("Error parseando entero: " + valor + " - " + e.getMessage());
            return 0;
        }
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}