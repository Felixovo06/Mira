package com.felix.miraagent.weixin.login;

import com.felix.miraagent.weixin.client.ILinkClient;
import com.felix.miraagent.weixin.client.RuntimeConfig;
import com.felix.miraagent.weixin.client.dto.QrCodeResponse;
import com.felix.miraagent.weixin.client.dto.QrStatusResponse;
import com.felix.miraagent.weixin.poll.WeixinPoller;
import lombok.extern.slf4j.Slf4j;

/**
 * Web 驱动的微信 bot 扫码登录：按需拉取真实 ilink 二维码、轮询扫码状态，
 * 确认后写入 token 并启动 poller。供 {@code /api/weixin/*} 暴露给前端。
 * <p>与启动期的控制台 {@link QrLoginService} 共享 RuntimeConfig/WeixinPoller——
 * 任一路径确认即生效，poller.start() 已幂等。
 */
@Slf4j
public class WeixinLoginService {

    private final ILinkClient client;
    private final RuntimeConfig runtimeConfig;
    private final WeixinPoller poller;

    private volatile String currentQrcode;

    public WeixinLoginService(ILinkClient client, RuntimeConfig runtimeConfig, WeixinPoller poller) {
        this.client = client;
        this.runtimeConfig = runtimeConfig;
        this.poller = poller;
    }

    public synchronized WeixinLoginState status() {
        if (runtimeConfig.hasToken()) {
            return WeixinLoginState.logged();
        }
        return WeixinLoginState.of("idle", "尚未获取二维码");
    }

    /** 拉取一张新的真实二维码。 */
    public synchronized WeixinLoginState requestQr() {
        if (runtimeConfig.hasToken()) {
            return WeixinLoginState.logged();
        }
        try {
            QrCodeResponse resp = client.getQrCode(runtimeConfig.getBaseUrl());
            if (resp.isError()) {
                return WeixinLoginState.of("error", "获取二维码失败: " + resp.getErrmsg());
            }
            currentQrcode = resp.getQrcode();
            return WeixinLoginState.qr(resp.getQrcodeImgContent());
        } catch (Exception e) {
            log.warn("[Weixin] requestQr failed: {}", e.getMessage());
            return WeixinLoginState.of("error", "ilink 不可达: " + e.getMessage());
        }
    }

    /** 轮询当前二维码的扫码状态；确认则落 token + 启 poller。 */
    public synchronized WeixinLoginState poll() {
        if (runtimeConfig.hasToken()) {
            return WeixinLoginState.logged();
        }
        if (currentQrcode == null) {
            return WeixinLoginState.of("idle", "尚未获取二维码");
        }
        try {
            QrStatusResponse resp = client.getQrStatus(runtimeConfig.getBaseUrl(), currentQrcode);
            if (resp.isError()) {
                return WeixinLoginState.of("error", "状态查询失败: " + resp.getErrmsg());
            }
            String status = resp.getStatus();
            switch (status) {
                case "confirmed" -> {
                    runtimeConfig.setBotToken(resp.getBotToken());
                    if (resp.getBaseUrl() != null && !resp.getBaseUrl().isBlank()) {
                        runtimeConfig.setBaseUrl(resp.getBaseUrl());
                    }
                    poller.start();
                    currentQrcode = null;
                    log.info("[Weixin] QR confirmed via web, poller started");
                    return WeixinLoginState.logged();
                }
                case "scaned_but_redirect" -> {
                    if (resp.getBaseUrl() != null && !resp.getBaseUrl().isBlank()) {
                        runtimeConfig.setBaseUrl(resp.getBaseUrl());
                    }
                    return WeixinLoginState.of("scaned", "已扫码，等待确认");
                }
                case "scaned" -> {
                    return WeixinLoginState.of("scaned", "已扫码，请在手机上确认");
                }
                case "expired" -> {
                    currentQrcode = null;
                    return WeixinLoginState.of("expired", "二维码已过期，请刷新");
                }
                default -> {
                    return WeixinLoginState.of("qr", "等待扫码");
                }
            }
        } catch (Exception e) {
            log.warn("[Weixin] poll failed: {}", e.getMessage());
            return WeixinLoginState.of("error", "ilink 不可达: " + e.getMessage());
        }
    }
}
