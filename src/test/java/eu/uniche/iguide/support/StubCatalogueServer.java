package eu.uniche.iguide.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A real HTTP server standing in for the Catalogue.
 *
 * <p>Deliberately a socket rather than a mocked request factory. The behaviour under test includes the
 * connect and read timeouts and the headers the client attaches, and neither of those exists unless real
 * HTTP happens.
 */
public final class StubCatalogueServer implements AutoCloseable {

    /** What the stub should do with the next request. */
    public record Response(int status, String body, Duration delay) {

        public static Response of(int status, String body) {
            return new Response(status, body, Duration.ZERO);
        }

        public static Response status(int status) {
            return new Response(status, "", Duration.ZERO);
        }

        public static Response slow(Duration delay) {
            return new Response(200, "{}", delay);
        }
    }

    /** What the stub actually received, so header propagation can be asserted. */
    public record ReceivedRequest(String method, String path, String authorization, String requestId,
                                  String ifNoneMatch) {
    }

    private final HttpServer server;
    private final AtomicReference<Response> nextResponse =
            new AtomicReference<>(Response.of(200, "{}"));
    private final List<ReceivedRequest> received = new ArrayList<>();

    public StubCatalogueServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException cannotBind) {
            throw new UncheckedIOException(cannotBind);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void willRespond(Response response) {
        nextResponse.set(response);
    }

    public List<ReceivedRequest> received() {
        synchronized (received) {
            return List.copyOf(received);
        }
    }

    public ReceivedRequest lastRequest() {
        List<ReceivedRequest> all = received();
        return all.get(all.size() - 1);
    }

    private void handle(HttpExchange exchange) throws IOException {
        synchronized (received) {
            received.add(new ReceivedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("X-Request-Id"),
                    exchange.getRequestHeaders().getFirst("If-None-Match")));
        }
        Response response = nextResponse.get();
        if (!response.delay().isZero()) {
            try {
                Thread.sleep(response.delay().toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (response.status() == 304 || body.length == 0) {
            exchange.sendResponseHeaders(response.status(), -1);
        } else {
            exchange.sendResponseHeaders(response.status(), body.length);
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
