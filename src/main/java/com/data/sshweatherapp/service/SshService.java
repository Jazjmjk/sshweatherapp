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

    private LocalDateTime ajustarFechaHora(String fechaOriginal, String horaOriginal) {
        try {
            DateTimeFormatter formatterFecha = DateTimeFormatter.ofPattern("yy-MM-dd");
            LocalDate fecha = LocalDate.parse(fechaOriginal, formatterFecha);
            LocalTime hora = LocalTime.parse(horaOriginal);
            return LocalDateTime.of(fecha, hora).minusHours(6);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean esCada15Minutos(LocalTime hora) {
        return hora.getMinute() % 15 == 0;
    }

    public List<String> getNombreEstaciones() {
        List<String> estaciones = new ArrayList<>();
        Session session = null;
        ChannelExec channel = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            String cmdZonas = "ls " + remoteBasePath;
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(cmdZonas);
            InputStream in = channel.getInputStream();
            channel.connect();

            List<String> zonas = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) zonas.add(line.trim());
                }
            }
            channel.disconnect();

            for (String zona : zonas) {
                channel = (ChannelExec) session.openChannel("exec");
                String cmdEstaciones = "ls " + remoteBasePath + "/" + zona;
                channel.setCommand(cmdEstaciones);
                InputStream inEstaciones = channel.getInputStream();
                channel.connect();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inEstaciones, StandardCharsets.UTF_8))) {
                    String est;
                    while ((est = reader.readLine()) != null) {
                        if (!est.trim().isEmpty()) {
                            estaciones.add(zona + "/" + est.trim());
                        }
                    }
                }
                channel.disconnect();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }

        return estaciones;
    }

    public String getSubcarpetaDinamica(Session session, String estacion) throws Exception {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            String pathEstacion = remoteBasePath + "/" + estacion;
            channel.setCommand("ls " + pathEstacion);
            InputStream in = channel.getInputStream();
            channel.connect();

            List<String> subcarpetas = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        if (line.matches("\\d{4}") || line.matches("\\d{2}")) {
                            subcarpetas.add(line);
                        }
                    }
                }
            }

            if (!subcarpetas.isEmpty()) {
                int mesActual = LocalDate.now().getMonthValue();
                String mesStr = String.format("%02d", mesActual);

                subcarpetas.sort(Collections.reverseOrder());

                for (String subcarpeta : subcarpetas) {
                    if (subcarpetaTieneDatos(session, estacion, subcarpeta, mesStr)) {
                        System.out.println("Subcarpeta seleccionada para " + estacion + ": " + subcarpeta + " (con datos)");
                        return subcarpeta;
                    }
                }

                for (int i = 1; i <= 3; i++) {
                    int mesAnterior = mesActual - i;
                    if (mesAnterior <= 0) continue;

                    String mesAnteriorStr = String.format("%02d", mesAnterior);
                    for (String subcarpeta : subcarpetas) {
                        if (subcarpetaTieneDatos(session, estacion, subcarpeta, mesAnteriorStr)) {
                            System.out.println("Subcarpeta seleccionada para " + estacion + ": " + subcarpeta + " (con datos del mes " + mesAnteriorStr + ")");
                            return subcarpeta;
                        }
                    }
                }

                String resultado = subcarpetas.get(0);
                System.out.println("Subcarpeta seleccionada para " + estacion + ": " + resultado + " (sin verificar datos)");
                return resultado;
            }

            System.out.println("No se encontraron subcarpetas para: " + estacion);
            return null;
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
        }
    }

    private boolean subcarpetaTieneDatos(Session session, String estacion, String subcarpeta, String mes) {
        ChannelExec channel = null;
        try {
            String pathMes = remoteBasePath + "/" + estacion + "/" + subcarpeta + "/" + mes;

            channel = (ChannelExec) session.openChannel("exec");
            String comando = "if [ -d \"" + pathMes + "\" ]; then " +
                    "find \"" + pathMes + "\" -name '*.csv' -type f | head -n 1; " +
                    "else echo ''; fi";

            channel.setCommand(comando);
            InputStream in = channel.getInputStream();
            channel.connect();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String resultado = reader.readLine();
                return resultado != null && !resultado.trim().isEmpty();
            }
        } catch (Exception e) {
            System.err.println("Error verificando datos en subcarpeta " + subcarpeta + ": " + e.getMessage());
            return false;
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
        }
    }

    public String getArchivoMasReciente(String estacion, int mes) {
        Session session = null;
        ChannelExec channel = null;
        String archivoReciente = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            String subcarpeta = getSubcarpetaDinamica(session, estacion);
            if (subcarpeta == null || subcarpeta.isEmpty()) {
                System.out.println("No se encontró subcarpeta para estación: " + estacion);
                return null;
            }

            String mesStr = String.format("%02d", mes);
            String pathMes = remoteBasePath + "/" + estacion + "/" + subcarpeta + "/" + mesStr;

            System.out.println("Buscando archivos en: " + pathMes);

            String comando = "if [ -d \"" + pathMes + "\" ]; then " +
                    "find \"" + pathMes + "\" -maxdepth 1 -type f -name '*.csv' | " +
                    "while read file; do echo \"$(stat -c '%Y' \"$file\") $file\"; done | " +
                    "sort -nr | head -n 1 | cut -d' ' -f2; " +
                    "else echo 'DIRECTORIO_NO_EXISTE'; fi";

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(comando);
            InputStream in = channel.getInputStream();
            channel.connect();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String resultado = reader.readLine();
                if (resultado != null && !resultado.trim().isEmpty() && !resultado.equals("DIRECTORIO_NO_EXISTE")) {
                    archivoReciente = resultado.trim();
                    System.out.println("Archivo encontrado: " + archivoReciente);
                } else {
                    System.out.println("No se encontraron archivos CSV en: " + pathMes);

                    for (int i = 1; i <= 3; i++) {
                        int mesAnterior = mes - i;
                        if (mesAnterior <= 0) continue;

                        String mesAnteriorStr = String.format("%02d", mesAnterior);
                        String pathMesAnterior = remoteBasePath + "/" + estacion + "/" + subcarpeta + "/" + mesAnteriorStr;

                        System.out.println("Intentando con mes anterior: " + pathMesAnterior);

                        String comandoAnterior = "if [ -d \"" + pathMesAnterior + "\" ]; then " +
                                "find \"" + pathMesAnterior + "\" -maxdepth 1 -type f -name '*.csv' | " +
                                "while read file; do echo \"$(stat -c '%Y' \"$file\") $file\"; done | " +
                                "sort -nr | head -n 1 | cut -d' ' -f2; " +
                                "else echo ''; fi";

                        channel = (ChannelExec) session.openChannel("exec");
                        channel.setCommand(comandoAnterior);
                        InputStream inAnterior = channel.getInputStream();
                        channel.connect();

                        try (BufferedReader readerAnterior = new BufferedReader(new InputStreamReader(inAnterior, StandardCharsets.UTF_8))) {
                            String resultadoAnterior = readerAnterior.readLine();
                            if (resultadoAnterior != null && !resultadoAnterior.trim().isEmpty()) {
                                archivoReciente = resultadoAnterior.trim();
                                System.out.println("Archivo encontrado en mes anterior: " + archivoReciente);
                                break;
                            }
                        }
                        channel.disconnect();
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Error en getArchivoMasReciente para " + estacion + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }

        return archivoReciente;
    }

    public WeatherData leerUltimaLineaArchivo(String rutaArchivo) {
        Session session = null;
        ChannelExec channel = null;
        WeatherData dato = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("tail -n 1 " + rutaArchivo);
            InputStream in = channel.getInputStream();
            channel.connect();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String linea = reader.readLine();
                if (linea != null && !linea.trim().isEmpty() && !linea.startsWith("DATE")) {
                    String[] partes = linea.trim().split(",");

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
            }

        } catch (Exception e) {
            System.err.println("Error leyendo línea final de archivo " + rutaArchivo + ": " + e.getMessage());
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }

        return dato;
    }

    public List<WeatherData> getUltimosDatosTodasEstaciones() {
        List<WeatherData> ultimosDatos = new ArrayList<>();
        List<String> estaciones = getNombreEstaciones();
        int mesActual = LocalDate.now().getMonthValue();

        for (String estacion : estaciones) {
            try {
                String archivoReciente = getArchivoMasReciente(estacion, mesActual);

                if (archivoReciente == null || archivoReciente.trim().isEmpty()) {
                    System.out.println("⚠️ No se encontró archivo CSV para estación: " + estacion);
                    continue;
                }

                System.out.println("✅ Estación: " + estacion + " | Archivo: " + archivoReciente);

                WeatherData dato = leerUltimaLineaArchivo(archivoReciente);

                if (dato == null) {
                    dato = new WeatherData();
                    dato.setFecha("--");
                    dato.setHora("--");
                }

                dato.setEstacion(estacion);
                String[] partes = estacion.split("/");
                if (partes.length >= 1) {
                    dato.setRegion(partes[0]);
                }

                ultimosDatos.add(dato);

            } catch (Exception e) {
                System.err.println("❌ Error procesando estación " + estacion + ": " + e.getMessage());
            }
        }
        return ultimosDatos;
    }

    public List<WeatherData> getDatosPorEstacionYRango(String estacion, LocalDate fechaInicio, LocalDate fechaFin) {
        List<WeatherData> datosRango = new ArrayList<>();
        Session session = null;
        ChannelExec channel = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

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
                if (channel != null && channel.isConnected()) channel.disconnect();
                channel = (ChannelExec) session.openChannel("exec");
                channel.setCommand(cmdList);
                InputStream inList = channel.getInputStream();
                channel.connect();

                List<String> archivos = new ArrayList<>();
                try (BufferedReader readerList = new BufferedReader(new InputStreamReader(inList))) {
                    String archivo;
                    while ((archivo = readerList.readLine()) != null) {
                        archivo = archivo.trim();
                        if (!archivo.isEmpty() && archivo.endsWith(".csv")) {
                            archivos.add(archivo);
                        }
                    }
                }
                channel.disconnect();

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

                        if (channel != null && channel.isConnected()) channel.disconnect();
                        channel = (ChannelExec) session.openChannel("exec");
                        String cmdCat = "tail -n +2 " + archivoPath;
                        channel.setCommand(cmdCat);
                        InputStream in2 = channel.getInputStream();
                        channel.connect();

                        try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(in2))) {
                            String linea;
                            while ((linea = reader2.readLine()) != null) {
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

                                            if (!"None".equalsIgnoreCase(partes[2]) && !partes[2].isEmpty())
                                                dato.setTemperatura(Double.parseDouble(partes[2]));

                                            if (!"None".equalsIgnoreCase(partes[3]) && !partes[3].isEmpty())
                                                dato.setPresion(Double.parseDouble(partes[3]));

                                            if (!"None".equalsIgnoreCase(partes[4]) && !partes[4].isEmpty())
                                                dato.setHumedad(Double.parseDouble(partes[4]));

                                            if (!"None".equalsIgnoreCase(partes[5]) && !partes[5].isEmpty())
                                                dato.setAqi(Integer.parseInt(partes[5]));

                                            if (!"None".equalsIgnoreCase(partes[6]) && !partes[6].isEmpty())
                                                dato.setViento(Double.parseDouble(partes[6]));

                                            datosRango.add(dato);
                                        }
                                    }
                                }
                            }
                        }
                        channel.disconnect();
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }

        return datosRango;
    }

    public void keepAlive() {
        Session session = null;
        ChannelExec channel = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(5000);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("echo ping");
            channel.connect(3000);

            System.out.println("Ping SSH exitoso");

        } catch (Exception e) {
            System.err.println("Ping SSH falló: " + e.getMessage());
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    public List<Estacion> getEstacionesConCoordenadas() {
        List<Estacion> estaciones = new ArrayList<>();
        List<String> nombres = getNombreEstaciones();

        System.out.println("🗺️ Generando estaciones con coordenadas para " + nombres.size() + " estaciones");

        for (String nombre : nombres) {
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
            System.out.println("✅ Estación creada: " + nombre + " -> [" + lat + ", " + lon + "] - Activa: " + estacion.isActiva());
        }

        System.out.println("🎯 Total de estaciones generadas: " + estaciones.size());
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
            System.err.println("Error parseando double: " + valor);
            return 0.0;
        }
    }

    private Integer parsearIntSeguro(String valor) {
        try {
            if (valor == null || valor.trim().isEmpty() ||
                    valor.equalsIgnoreCase("None") || valor.equalsIgnoreCase("null")) {
                return 0;
            }
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            System.err.println("Error parseando int: " + valor);
            return 0;
        }
    }
}