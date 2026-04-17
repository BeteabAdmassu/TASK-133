package com.eaglepoint.console.ui.shared;

import com.eaglepoint.console.config.AppConfig;
import com.eaglepoint.console.ui.AuthSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);
    private static ApiClient instance;

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;

    private ApiClient() {
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.mapper = new ObjectMapper();
        int port = AppConfig.getInstance().getApiPort();
        this.baseUrl = "http://127.0.0.1:" + port;
    }

    public static synchronized ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    private HttpRequest.Builder requestBuilder(String path) {
        var builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json");
        AuthSession.getInstance().getRawToken().ifPresent(token ->
            builder.header("Authorization", "Bearer " + token));
        return builder;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String path) throws IOException, InterruptedException {
        var req = requestBuilder(path).GET().build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        checkStatus(resp);
        return mapper.readValue(resp.body(), Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> post(String path, Object body) throws IOException, InterruptedException {
        String json = mapper.writeValueAsString(body);
        var req = requestBuilder(path)
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        checkStatus(resp);
        return mapper.readValue(resp.body(), Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> put(String path, Object body) throws IOException, InterruptedException {
        String json = mapper.writeValueAsString(body);
        var req = requestBuilder(path)
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        checkStatus(resp);
        return mapper.readValue(resp.body(), Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> delete(String path) throws IOException, InterruptedException {
        var req = requestBuilder(path).DELETE().build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        checkStatus(resp);
        if (resp.statusCode() == 204) return Map.of();
        return mapper.readValue(resp.body(), Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> postMultipart(String path, Path file) throws IOException, InterruptedException {
        String boundary = UUID.randomUUID().toString();
        byte[] fileBytes = Files.readAllBytes(file);
        String filename = file.getFileName().toString();

        String bodyPart = "--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n";
        String endBoundary = "\r\n--" + boundary + "--\r\n";

        byte[] start = bodyPart.getBytes();
        byte[] end = endBoundary.getBytes();
        byte[] combined = new byte[start.length + fileBytes.length + end.length];
        System.arraycopy(start, 0, combined, 0, start.length);
        System.arraycopy(fileBytes, 0, combined, start.length, fileBytes.length);
        System.arraycopy(end, 0, combined, start.length + fileBytes.length, end.length);

        var builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(combined));

        // Propagate the logged-in user's bearer token — without this, multipart
        // uploads (route imports) hit a 401 even when the session is valid.
        AuthSession.getInstance().getRawToken().ifPresent(token ->
            builder.header("Authorization", "Bearer " + token));

        var resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        checkStatus(resp);
        return mapper.readValue(resp.body(), Map.class);
    }

    private void checkStatus(HttpResponse<String> resp) throws IOException {
        if (resp.statusCode() >= 400) {
            String msg = "HTTP " + resp.statusCode() + ": " + resp.body();
            log.warn("API error: {}", msg);
            throw new IOException(msg);
        }
    }
}
