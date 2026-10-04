package com.example.cloudpicture.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BailianVisionClientTest {

    private static final String SUCCESS_BODY =
            "{\"choices\":[{\"message\":{\"content\":\"{\\\"introduction\\\":\\\"x\\\",\\\"tags\\\":[]}\"}}]}";

    @Test
    void returnsContentOnSuccess() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, 200, SUCCESS_BODY));
        try {
            assertEquals("{\"introduction\":\"x\",\"tags\":[]}", newClient(server).complete("data:image/png;base64,AAAA", "p"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsBearerTokenAndInlineImage() throws IOException {
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, SUCCESS_BODY);
        });
        try {
            new BailianVisionClient(baseUrl(server), "secret-key", "qwen3-vl-flash", 5)
                    .complete("data:image/png;base64,AAAA", "prompt-here");
            assertEquals("Bearer secret-key", auth.get());
            assertTrue(body.get().contains("qwen3-vl-flash"));
            assertTrue(body.get().contains("data:image/png;base64,AAAA"));
            assertTrue(body.get().contains("prompt-here"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsNonSuccessStatusToUnavailable() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, 400, "{\"error\":{\"code\":\"Arrearage\"}}"));
        try {
            assertUnavailable(newClient(server));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsUnparsableBodyToUnavailable() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, 200, "not-json"));
        try {
            assertUnavailable(newClient(server));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsTimeoutToUnavailable() throws IOException {
        HttpServer server = server(exchange -> {
            sleep(2000);
            respond(exchange, 200, SUCCESS_BODY);
        });
        try {
            assertUnavailable(new BailianVisionClient(baseUrl(server), "key", "qwen3-vl-flash", 1));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void blankApiKeyIsUnavailableWithoutCalling() {
        BailianVisionClient client = new BailianVisionClient("http://127.0.0.1:1", " ", "qwen3-vl-flash", 5);
        assertUnavailable(client);
    }

    private static void assertUnavailable(BailianVisionClient client) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.complete("data:image/png;base64,AAAA", "p"));
        assertEquals(ErrorCode.AI_UNAVAILABLE, ex.getErrorCode());
    }

    private static BailianVisionClient newClient(HttpServer server) {
        return new BailianVisionClient(baseUrl(server), "key", "qwen3-vl-flash", 5);
    }

    private static HttpServer server(Handler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception e) {
                respond(exchange, 500, "{}");
            }
        });
        server.start();
        return server;
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws Exception;
    }
}