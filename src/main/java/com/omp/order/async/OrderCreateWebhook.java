package com.omp.order.async;

import com.omp.delivery.dto.CreateAsyncOrderEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
@Component
@Slf4j
public class OrderCreateWebhook {
    private final RestClient restClient;
    private final Executor webhookTaskExecutor;

    @Value("${client-url}")
    private String clientUrl;

    public void sendOrderWebhook(final OrderWebhookRequest orderWebhookRequest) {
        try {
            CompletableFuture
                    .supplyAsync(() ->
                                    restClient.post()
                                            .uri(clientUrl)
                                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                            .header(HttpHeaders.LOCATION, "/api/v1/order/" + orderWebhookRequest.getOrderId())
                                            .body(orderWebhookRequest)
                                            .retrieve()
                                            .toBodilessEntity(),
                            webhookTaskExecutor)
                    .whenComplete((responseEntity, exception) -> {
                        if (exception != null) {
                            log.warn("order webhook exception : {}", exception.getMessage());
                        } else if (responseEntity == null) {
                            log.warn("order webhook response fail");
                        } else if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                            log.warn("order webhook fail :{}", responseEntity.getStatusCode());
                        } else {
                            log.info("order webhook success :{}", responseEntity.getStatusCode());
                        }
                    });
        } catch (Exception e) {
            log.error("webhook client connect fail", e);
        }
    }

    public void sendFailedOrderWebhook(final CreateAsyncOrderEvent event,
                                       final Throwable throwable) {
        try {
            CompletableFuture
                    .supplyAsync(() ->
                                    restClient.post()
                                            .uri(clientUrl)
                                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                            .body(
                                                    new OrderFailedWebhookRequest(
                                                            event.getOrdererId(), event.getCartId(), event.getShopId(),
                                                            event.getUuid(),
                                                            throwable.getMessage()
                                                    )
                                            )
                                            .retrieve()
                                            .toBodilessEntity(),
                            webhookTaskExecutor)
                    .whenComplete((responseEntity, exception) -> {
                        if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                            log.warn("fail order webhook fail : {}, {}", responseEntity.getStatusCode(),
                                    exception.getMessage());
                        } else {
                            log.info("fail order webhook success :{}", responseEntity.getStatusCode());
                        }
                    });
        } catch (Exception e) {
            log.error("fail webhook client connect fail", e);
        }
    }
}