package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Data
@AllArgsConstructor
class Document {
    private String format;
    private String content;
    private String group;
    private String type;
}

@Getter
class CreateDocumentRequestDto {
    private String document_format;
    private String product_document;
    private String product_group;
    private String type;
    private String signature;

    private CreateDocumentRequestDto() {}

    public static CreateDocumentRequestDto of(Document document, String signature) {
        var dto = new CreateDocumentRequestDto();
        dto.document_format = document.getFormat();
        dto.product_document = document.getContent();
        dto.product_group = document.getGroup();
        dto.type = document.getType();
        dto.signature = signature;
        return dto;
    }
}

class CreateDocumentException extends RuntimeException {
    @Getter
    private final Map<String, String> apiResponse;

    public CreateDocumentException(Map<String, String> apiResponse) {
        this.apiResponse = apiResponse;
    }
}

public class CrptApi {
    private TimeUnit timeUnit;
    private int requestLimit;

    private volatile Instant firstRequestWithinInterval = null;
    private int requestsCount = 0;

    private final String authToken = System.getenv("CRPT_API_AUTH");

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
    }

    public UUID createDocument(Document document, String signature) throws IOException, InterruptedException {
        synchronized (this) {
            if (firstRequestWithinInterval == null) firstRequestWithinInterval = Instant.now();
            if (Duration.between(firstRequestWithinInterval, Instant.now()).toNanos() < timeUnit.toNanos(1)) {
                if (++requestsCount >= requestLimit) {
                    Duration timeUntilNewInterval = Duration.between(Instant.now(), firstRequestWithinInterval.plusNanos(timeUnit.toNanos(1)));
                    Thread.sleep(timeUntilNewInterval);
                }
            } else {
                firstRequestWithinInterval = Instant.now();
                requestsCount = 0;
            }
        }

        try (HttpClient client = HttpClient.newHttpClient()) {
            CreateDocumentRequestDto dto = CreateDocumentRequestDto.of(document, signature);
            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(dto);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://markirovka.demo.crpt.tech/api/v3/lk/documents/commissioning/contract/create"))
                    .header("Authorization", "Bearer %s".formatted(authToken))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            Map<String, String> resonseAsMap = objectMapper.readValue(response.body(), HashMap.class);
            if (response.statusCode() == 200) {
                return UUID.fromString(resonseAsMap.get("value"));
            }

            throw new CreateDocumentException(resonseAsMap);
        }
    }
}
