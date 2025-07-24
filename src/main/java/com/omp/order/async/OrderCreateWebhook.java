package com.omp.order.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
@Component
@Slf4j
public class OrderCreateWebhook {
    private final RestClient restClient;

    @Value("${client-url}")
    private String clientUrl;

    @Retryable(
            recover = "recoverWebhook",
            retryFor = IllegalStateException.class,
            backoff = @Backoff(
                    multiplier = 2.0, random = true, maxDelay = 6000L
            )
    )
    @Async("webhookTaskExecutor")
    @TransactionalEventListener
    public void sendOrderWebhook(final OrderWebhookRequest orderWebhookRequest) {
        var responseEntity = restClient.post()
                .uri(clientUrl)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.LOCATION, "/api/v1/order/" + orderWebhookRequest.getOrderId())
                .body(orderWebhookRequest)
                .retrieve()
                .toBodilessEntity();

        if (!responseEntity.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Could not send order webhook: " + responseEntity.getBody());
        }
    }


    @Recover
    public void recoverWebhook(final Exception ex, final OrderWebhookRequest orderWebhookRequest) {
        log.error("order webhook send fail: uuid={}, orderId={}", orderWebhookRequest.getJobUuid(),
                orderWebhookRequest.getOrderId());
        log.error(ex.getMessage(), ex);
    }
}
