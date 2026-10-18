package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;

/**
 * Add Hipchat integration. So far it is only for alert notification purpose.
 * We can add command notification in the future.
 *
 * @author xrao
 */
@Slf4j
public class HipchatIntegration {

    /**
     * 保存hipchat url信息。
     */
    @Getter
    @Setter
    private String hipchatURL; //must be in the format like https://xxx.hipchat.com/v2/room/{roomnumber}/notification?
    /**
     * 保存auth token信息。
     */
    @Getter
    @Setter
    private String authToken; //we will append authToken to construct the full url
    /**
     * 保存hostname信息。
     */
    private String hostname;
    /**
     * 标记enabled状态。
     */
    @Getter
    private boolean enabled = false;

    /**
     * 创建HipchatIntegration对象。
     */
    public HipchatIntegration() {

    }

    /**
     * 执行init操作。
     *
     * @param ctx ctx参数。
     */
    public void init(MyPerfContext ctx) {
        this.enabled = false;
        try {
            this.hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warn("Failed to get local host name", e);
        }
        this.hipchatURL = ctx.getMyperfConfig().getHipchatUrl();
        this.authToken = ctx.getMyperfConfig().getHipchatAuthToken();

        if (authToken != null && !authToken.isEmpty()
                && hipchatURL != null && !hipchatURL.isEmpty()) this.enabled = true;

        if (this.enabled) {
            //do a test
            this.enabled = this.sendMessage("MySQL Perf Analzyer Hipchat Integration Initiated on " + this.hostname);

            if (this.enabled)
                log.info("Hipchat integration is enabed.");

        }
    }

    /**
     * This should be invoked if isEnabled = true
     *
     * @param mgs
     */
    public boolean sendMessage(String msg) {

        if (!this.enabled) {
            log.info("Hichat is not enabled, ignore.");
            return false;
        }

        String url = this.hipchatURL + "auth_token=" + this.authToken;
        try {

            URL hipchatUrl = new URL(url);
            HttpURLConnection conn = HttpURLConnection.class.cast(hipchatUrl.openConnection());
            conn.setDoOutput(true);
            conn.addRequestProperty("Content-Type", "application/json");
            String jsonMsg = this.constructJsonMessage("Source: " + this.hostname + "\n" + msg);
            //log.info("Sending message to hipchat (" + url + "): " + jsonMsg);
            byte[] jsonByte = jsonMsg.getBytes();
            conn.addRequestProperty("Content-Length", String.valueOf(jsonByte.length));
            try (OutputStream out = conn.getOutputStream()) {
                out.write(jsonByte);
                out.flush();
                int code = conn.getResponseCode();
                try (InputStream in = conn.getInputStream()) {
                    //log.info("Recieve response code " + code);
                    if (code >= 200 && code < 400)
                        return true;
                }
                log.warn("Failed hipchat integration with URL: " + this.hipchatURL + ", code: " + code + ", data: " + jsonMsg);
            }
        } catch (Throwable th) {
            log.warn("Failed to send message: " + msg, th);
            return false;
        }
        return false;
    }

    /**
     * 执行construct json message操作。
     *
     * @param str str参数。
     * @return 处理结果。
     */
    private String constructJsonMessage(String str) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"color\":\"green\",\"message\":\"");
        sb.append(CommonUtils.escapeJson(str));
        sb.append("\",\"notify\":false,\"message_format\":\"text\"}");
        return sb.toString();
    }
}
