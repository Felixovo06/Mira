package com.felix.miraagent.api.controller;

import com.felix.miraagent.weixin.login.WeixinLoginService;
import com.felix.miraagent.weixin.login.WeixinLoginState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 微信 bot 扫码登录 API：暴露真实 ilink 二维码 + 扫码状态轮询，供 Web UI 展示。
 * 微信未启用（mira.weixin.enabled≠true）时 WeixinLoginService 不存在，统一返回 disabled 状态。
 */
@RestController
@RequestMapping("/api/weixin")
public class WeixinController {

    private final Optional<WeixinLoginService> loginService;

    public WeixinController(Optional<WeixinLoginService> loginService) {
        this.loginService = loginService;
    }

    @GetMapping("/status")
    public ResponseEntity<WeixinLoginState> status() {
        return ResponseEntity.ok(loginService.map(WeixinLoginService::status).orElseGet(WeixinController::disabled));
    }

    @PostMapping("/qr")
    public ResponseEntity<WeixinLoginState> requestQr() {
        return ResponseEntity.ok(loginService.map(WeixinLoginService::requestQr).orElseGet(WeixinController::disabled));
    }

    @GetMapping("/poll")
    public ResponseEntity<WeixinLoginState> poll() {
        return ResponseEntity.ok(loginService.map(WeixinLoginService::poll).orElseGet(WeixinController::disabled));
    }

    private static WeixinLoginState disabled() {
        return new WeixinLoginState(false, "disabled", null, "微信未启用（设置 mira.weixin.enabled=true）");
    }
}
