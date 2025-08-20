package com.omp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .requestFactory(clientHttpRequestFactory())
                .build();
    }

    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
//        var requestFactory = new SimpleClientHttpRequestFactory();
//        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 3128));
//        requestFactory.setProxy(proxy);
//        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
//        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return requestFactory;
    }
}
