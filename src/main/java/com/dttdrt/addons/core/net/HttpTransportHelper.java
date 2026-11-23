package com.dttdrt.addons.core.net;

import com.dttdrt.addons.core.log.ModLogManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;

/**
 * HTTP client helper that tries direct connectivity first and automatically falls back
 * to the Windows system proxy (HTTP/HTTPS/SOCKS via WinINET registry or JVM properties)
 * so users running system-proxy-only tools without TUN mode can connect reliably.
 */
public final class HttpTransportHelper {

    public record HttpResult(int statusCode, String body) {}

    private record ProxyEndpoint(Proxy.Type type, String host, int port) {}

    private static final Pattern REG_DWORD_PATTERN = Pattern.compile("0x([0-9a-fA-F]+)|(\\d+)\\s*$");

    private static final HttpClient DIRECT_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpTransportHelper() {
    }

    public static HttpResult send(
            String url,
            String method,
            String body,
            Map<String, String> headers,
            int timeoutSeconds
    ) throws IOException {
        IOException directError = null;
        try {
            HttpResult res = sendViaHttpClient(DIRECT_CLIENT, url, method, body, headers, timeoutSeconds);
            if (res.statusCode() < 520) {
                return res;
            }
            directError = new IOException("HTTP " + res.statusCode());
        } catch (IOException e) {
            directError = e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }

        ProxyEndpoint systemProxy = detectSystemProxy(url);
        if (systemProxy != null) {
            try {
                if (systemProxy.type() == Proxy.Type.HTTP) {
                    HttpClient proxied = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(Math.max(6, timeoutSeconds)))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .proxy(ProxySelector.of(new InetSocketAddress(systemProxy.host(), systemProxy.port())))
                            .build();
                    return sendViaHttpClient(proxied, url, method, body, headers, timeoutSeconds);
                } else {
                    return sendViaSocksUrlConnection(systemProxy, url, method, body, headers, timeoutSeconds);
                }
            } catch (IOException proxyErr) {
                ModLogManager.info("Network", "System proxy fallback failed (" + systemProxy.host()
                        + ":" + systemProxy.port() + "): " + proxyErr.getMessage());
                if (directError != null) {
                    throw directError;
                }
                throw proxyErr;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Proxy request interrupted", e);
            }
        }

        throw directError != null ? directError : new IOException("Request failed");
    }

    /**
     * Downloads a binary file to {@code targetFile} using direct connection first,
     * falling back to detected HTTP/SOCKS system proxies on failure.
     */
    public static int downloadFile(
            String url,
            Path targetFile,
            Map<String, String> headers,
            int timeoutSeconds
    ) throws IOException {
        Path parent = targetFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        IOException directError = null;
        try {
            int status = downloadViaHttpClient(DIRECT_CLIENT, url, targetFile, headers, timeoutSeconds);
            if (status >= 200 && status < 300) {
                return status;
            }
            if (status < 520) {
                throw new IOException("HTTP " + status + " downloading " + url);
            }
            directError = new IOException("HTTP " + status);
        } catch (IOException e) {
            directError = e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }

        ProxyEndpoint systemProxy = detectSystemProxy(url);
        if (systemProxy != null) {
            try {
                if (systemProxy.type() == Proxy.Type.HTTP) {
                    HttpClient proxied = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(Math.max(8, timeoutSeconds)))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .proxy(ProxySelector.of(new InetSocketAddress(systemProxy.host(), systemProxy.port())))
                            .build();
                    int status = downloadViaHttpClient(proxied, url, targetFile, headers, timeoutSeconds);
                    if (status >= 200 && status < 300) {
                        return status;
                    }
                    throw new IOException("Proxy HTTP " + status);
                } else {
                    int status = downloadViaSocksUrlConnection(systemProxy, url, targetFile, headers, timeoutSeconds);
                    if (status >= 200 && status < 300) {
                        return status;
                    }
                    throw new IOException("SOCKS proxy HTTP " + status);
                }
            } catch (IOException proxyErr) {
                ModLogManager.info("Network", "System proxy download fallback failed (" + systemProxy.host()
                        + ":" + systemProxy.port() + "): " + proxyErr.getMessage());
                if (directError != null) {
                    throw directError;
                }
                throw proxyErr;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Proxy download interrupted", e);
            }
        }

        throw directError != null ? directError : new IOException("Download failed");
    }

    private static int downloadViaHttpClient(
            HttpClient client,
            String url,
            Path targetFile,
            Map<String, String> headers,
            int timeoutSeconds
    ) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET();
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                builder.header(e.getKey(), e.getValue());
            }
        }
        HttpResponse<Path> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofFile(targetFile));
        return resp.statusCode();
    }

    private static int downloadViaSocksUrlConnection(
            ProxyEndpoint endpoint,
            String urlStr,
            Path targetFile,
            Map<String, String> headers,
            int timeoutSeconds
    ) throws IOException {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(endpoint.host(), endpoint.port()));
        String currentUrl = urlStr;
        for (int hops = 0; hops < 6; hops++) {
            URL url = URI.create(currentUrl).toURL();
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection(proxy);
            conn.setConnectTimeout(timeoutSeconds * 1000);
            conn.setReadTimeout(timeoutSeconds * 1000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            int status = conn.getResponseCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isBlank()) {
                    return status;
                }
                currentUrl = URI.create(currentUrl).resolve(location).toString();
                continue;
            }
            if (status >= 200 && status < 300) {
                try (InputStream is = conn.getInputStream();
                     OutputStream os = Files.newOutputStream(targetFile)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        os.write(buf, 0, n);
                    }
                } finally {
                    conn.disconnect();
                }
                return status;
            }
            conn.disconnect();
            return status;
        }
        throw new IOException("Too many redirects downloading " + urlStr);
    }

    private static HttpResult sendViaHttpClient(
            HttpClient client,
            String url,
            String method,
            String body,
            Map<String, String> headers,
            int timeoutSeconds
    ) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds));
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                builder.header(e.getKey(), e.getValue());
            }
        }
        if ("POST".equalsIgnoreCase(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "", StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }
        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new HttpResult(resp.statusCode(), resp.body() != null ? resp.body() : "");
    }

    private static HttpResult sendViaSocksUrlConnection(
            ProxyEndpoint endpoint,
            String urlStr,
            String method,
            String body,
            Map<String, String> headers,
            int timeoutSeconds
    ) throws IOException {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(endpoint.host(), endpoint.port()));
        URL url = URI.create(urlStr).toURL();
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection(proxy);
        conn.setConnectTimeout(timeoutSeconds * 1000);
        conn.setReadTimeout(timeoutSeconds * 1000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod(method != null ? method.toUpperCase(Locale.ROOT) : "GET");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        if ("POST".equalsIgnoreCase(method)) {
            conn.setDoOutput(true);
            byte[] payload = (body != null ? body : "").getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }
        }
        int status = conn.getResponseCode();
        InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String respBody = "";
        if (stream != null) {
            try (InputStream is = stream) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                respBody = baos.toString(StandardCharsets.UTF_8);
            }
        }
        conn.disconnect();
        return new HttpResult(status, respBody);
    }

    private static ProxyEndpoint detectSystemProxy(String url) {
        ProxyEndpoint propProxy = readJvmPropertyProxy();
        if (propProxy != null) {
            return propProxy;
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            ProxyEndpoint winProxy = readWindowsRegistryProxy();
            if (winProxy != null) {
                return winProxy;
            }
        }

        try {
            ProxySelector def = ProxySelector.getDefault();
            if (def != null) {
                for (Proxy p : def.select(URI.create(url))) {
                    if (p != null && p.type() != Proxy.Type.DIRECT && p.address() instanceof InetSocketAddress isa) {
                        return new ProxyEndpoint(p.type(), isa.getHostString(), isa.getPort());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static ProxyEndpoint readJvmPropertyProxy() {
        try {
            String host = System.getProperty("https.proxyHost");
            String portStr = System.getProperty("https.proxyPort");
            if (host == null || host.isBlank()) {
                host = System.getProperty("http.proxyHost");
                portStr = System.getProperty("http.proxyPort");
            }
            if (host != null && !host.isBlank() && portStr != null && !portStr.isBlank()) {
                int port = Integer.parseInt(portStr.trim());
                if (port > 0 && port <= 65535) {
                    return new ProxyEndpoint(Proxy.Type.HTTP, host.trim(), port);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static ProxyEndpoint readWindowsRegistryProxy() {
        try {
            String enableOut = queryRegistryValue("ProxyEnable");
            if (enableOut == null || !isProxyEnabledValue(enableOut)) {
                return null;
            }
            String serverOut = queryRegistryValue("ProxyServer");
            if (serverOut == null || serverOut.isBlank()) {
                return null;
            }
            return parseProxyServerString(serverOut);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isProxyEnabledValue(String regLine) {
        Matcher m = REG_DWORD_PATTERN.matcher(regLine.trim());
        if (!m.find()) {
            return false;
        }
        if (m.group(1) != null) {
            return Integer.parseInt(m.group(1), 16) != 0;
        }
        if (m.group(2) != null) {
            return Integer.parseInt(m.group(2)) != 0;
        }
        return false;
    }

    private static String queryRegistryValue(String valueName) {
        try {
            Process proc = new ProcessBuilder(
                    "reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                    "/v", valueName
            ).redirectErrorStream(true).start();
            boolean finished = proc.waitFor(1500, TimeUnit.MILLISECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return null;
            }
            String out;
            try (InputStream is = proc.getInputStream()) {
                out = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String line : out.split("\\R")) {
                if (line.contains(valueName)) {
                    int idx = line.indexOf("REG_");
                    if (idx >= 0) {
                        String tail = line.substring(idx).trim();
                        String[] parts = tail.split("\\s+", 3);
                        if (parts.length >= 2) {
                            return parts[parts.length - 1].trim();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static ProxyEndpoint parseProxyServerString(String raw) {
        String s = raw.trim();
        if (!s.contains("=")) {
            return parseHostPort(Proxy.Type.HTTP, s);
        }
        ProxyEndpoint httpFallback = null;
        ProxyEndpoint socksFallback = null;
        for (String entry : s.split(";")) {
            String trimmed = entry.trim();
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String proto = trimmed.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String hp = trimmed.substring(eq + 1).trim();
            if ("https".equals(proto)) {
                ProxyEndpoint ep = parseHostPort(Proxy.Type.HTTP, hp);
                if (ep != null) return ep;
            } else if ("http".equals(proto) && httpFallback == null) {
                httpFallback = parseHostPort(Proxy.Type.HTTP, hp);
            } else if ("socks".equals(proto) && socksFallback == null) {
                socksFallback = parseHostPort(Proxy.Type.SOCKS, hp);
            }
        }
        return httpFallback != null ? httpFallback : socksFallback;
    }

    private static ProxyEndpoint parseHostPort(Proxy.Type type, String raw) {
        String clean = raw.trim();
        if (clean.startsWith("http://")) clean = clean.substring(7);
        else if (clean.startsWith("https://")) clean = clean.substring(8);
        else if (clean.startsWith("socks://")) clean = clean.substring(8);
        else if (clean.startsWith("socks5://")) clean = clean.substring(9);

        int colon = clean.lastIndexOf(':');
        if (colon <= 0 || colon >= clean.length() - 1) {
            return null;
        }
        String host = clean.substring(0, colon).trim();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        try {
            int port = Integer.parseInt(clean.substring(colon + 1).trim());
            if (!host.isEmpty() && port > 0 && port <= 65535) {
                return new ProxyEndpoint(type, host, port);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }
}
