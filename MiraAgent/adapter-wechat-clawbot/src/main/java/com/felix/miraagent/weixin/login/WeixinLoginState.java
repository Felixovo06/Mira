package com.felix.miraagent.weixin.login;

/**
 * 微信 bot 登录状态（供 Web UI 展示二维码与轮询）。
 *
 * @param loggedIn 是否已登录（bot token 就绪）
 * @param status   状态：logged_in / qr / scaned / expired / idle / error / disabled
 * @param qrImage  二维码图片内容（base64 或 URL，仅 status=qr 时有值）
 * @param message  附加说明（错误信息等）
 */
public record WeixinLoginState(boolean loggedIn, String status, String qrImage, String message) {

    public static WeixinLoginState logged() {
        return new WeixinLoginState(true, "logged_in", null, "微信已登录");
    }

    public static WeixinLoginState qr(String qrImage) {
        return new WeixinLoginState(false, "qr", qrImage, "请用微信扫码");
    }

    public static WeixinLoginState of(String status, String message) {
        return new WeixinLoginState(false, status, null, message);
    }
}
