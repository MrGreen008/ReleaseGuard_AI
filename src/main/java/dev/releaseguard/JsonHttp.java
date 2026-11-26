package dev.releaseguard;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
class JsonHttp {
    private static final int MAX_RESPONSE = 1_000_000;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json;

    JsonHttp(ObjectMapper json) {
        this.json = json;
    }

    Result send(String provider, String url, String method, Map<String, String> headers, Object body, Duration timeout) {
        try {
            var builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Accept", "application/json");
            headers.forEach(builder::header);
            var publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body));
            if (body != null) {
                builder.header("Content-Type", "application/json");
            }
            var pending = client.sendAsync(builder.method(method, publisher).build(),
                info -> new BoundedBody(info.headers().firstValueAsLong("Content-Length").orElse(-1), MAX_RESPONSE));
            try {
                var response = pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
                byte[] bytes = response.body();
                return new Result(response.statusCode(), bytes.length == 0 ? json.nullNode() : json.readTree(bytes));
            } catch (TimeoutException | InterruptedException e) {
                pending.cancel(true);
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ServiceFault.unavailable(provider + " request was interrupted.");
        } catch (IOException | ExecutionException | TimeoutException | IllegalArgumentException e) {
            throw ServiceFault.unavailable(provider + " request or response failed.");
        }
    }

    record Result(int status, JsonNode body) {
        JsonNode requireOk(String provider) {
            if (status < 200 || status >= 300 || body == null || body.isNull()) {
                throw ServiceFault.unavailable(provider + " returned HTTP " + status + ".");
            }
            return body;
        }
    }
}
