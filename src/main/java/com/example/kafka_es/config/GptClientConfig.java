package com.example.kafka_es.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class GptClientConfig {

    // 예: http://gpt-server:8000  (뒤에 슬래시 없이 두는 것을 권장)
    @Value("${GPT_SERVER:http://localhost:8000}")
    private String gptServer;

    // 필요 시 환경변수/프로퍼티로 조정 가능하도록 노출
    @Value("${GPT_CONNECT_TIMEOUT_MS:5000}")
    private int connectTimeoutMs;

    @Value("${GPT_RESPONSE_TIMEOUT_SEC:60}")
    private int responseTimeoutSec;

    @Value("${GPT_READWRITE_TIMEOUT_SEC:60}")
    private int readWriteTimeoutSec;

    @Value("${GPT_MAX_INMEMORY_MB:10}")
    private int maxInMemoryMb;

    @Bean
    public WebClient gptClient() {
        HttpClient httpClient = HttpClient.create()
                // TCP 연결 타임아웃
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                // 첫 바이트(헤더 포함) 수신까지의 타임아웃
                .responseTimeout(Duration.ofSeconds(responseTimeoutSec))
                // 연결 성사 후 채널 read/write 타임아웃(소켓 레벨)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readWriteTimeoutSec))
                        .addHandlerLast(new WriteTimeoutHandler(readWriteTimeoutSec)));


        // 큰 JSON 응답 대응(기본 256KB → 10MB 기본)
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(maxInMemoryMb * 1024 * 1024))
                .build();

        return WebClient.builder()
                .baseUrl(gptServer) // 컨트롤러에서는 uri 앞에 슬래시 없이: "comments/summary"
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}
