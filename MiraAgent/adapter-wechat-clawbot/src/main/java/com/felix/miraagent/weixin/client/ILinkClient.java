package com.felix.miraagent.weixin.client;

import com.felix.miraagent.weixin.client.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
public class ILinkClient {

    private static final String APP_ID = "bot";
    private static final String CLIENT_VERSION = "131072";

    private final RestClient normalClient;
    private final RestClient longPollClient;

    public ILinkClient() {
        SimpleClientHttpRequestFactory normalFactory = new SimpleClientHttpRequestFactory();
        normalFactory.setConnectTimeout(Duration.ofSeconds(10));
        normalFactory.setReadTimeout(Duration.ofSeconds(15));

        SimpleClientHttpRequestFactory longPollFactory = new SimpleClientHttpRequestFactory();
        longPollFactory.setConnectTimeout(Duration.ofSeconds(10));
        // Server holds connection up to 35s; give 45s to avoid spurious timeouts
        longPollFactory.setReadTimeout(Duration.ofSeconds(45));

        this.normalClient = RestClient.builder()
                .requestFactory(normalFactory)
                .defaultHeader("iLink-App-Id", APP_ID)
                .defaultHeader("iLink-App-ClientVersion", CLIENT_VERSION)
                .build();

        this.longPollClient = RestClient.builder()
                .requestFactory(longPollFactory)
                .defaultHeader("iLink-App-Id", APP_ID)
                .defaultHeader("iLink-App-ClientVersion", CLIENT_VERSION)
                .build();
    }

    public QrCodeResponse getQrCode(String baseUrl) {
        return normalClient.get()
                .uri(baseUrl + "/ilink/bot/get_bot_qrcode?bot_type=3")
                .retrieve()
                .body(QrCodeResponse.class);
    }

    public QrStatusResponse getQrStatus(String baseUrl, String qrcode) {
        return normalClient.get()
                .uri(baseUrl + "/ilink/bot/get_qrcode_status?qrcode=" + qrcode)
                .retrieve()
                .body(QrStatusResponse.class);
    }

    public GetUpdatesResponse getUpdates(String baseUrl, String botToken, String cursor) {
        GetUpdatesRequest request = new GetUpdatesRequest(
                cursor != null ? cursor : "",
                BaseInfo.defaults()
        );
        return longPollClient.post()
                .uri(baseUrl + "/ilink/bot/getupdates")
                .header("Authorization", "Bearer " + botToken)
                .header("AuthorizationType", "ilink_bot_token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GetUpdatesResponse.class);
    }

    public void sendMessage(String baseUrl, String botToken, SendMessageRequest request) {
        normalClient.post()
                .uri(baseUrl + "/ilink/bot/sendmessage")
                .header("Authorization", "Bearer " + botToken)
                .header("AuthorizationType", "ilink_bot_token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
