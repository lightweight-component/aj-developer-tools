package com.ajaxjs.devtools.netcapture.config;

import com.ajaxjs.devtools.netcapture.model.PacketInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Service
@RequiredArgsConstructor
public class PacketWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    private final ConcurrentHashMap<String, String> sessionFilters = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket connection established: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        sessionFilters.remove(session.getId());
        log.info("WebSocket connection closed: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();

        if (payload.startsWith("filter:") && payload.length() > 7) {
            String filter = payload.substring(7).trim();
            sessionFilters.put(session.getId(), filter);
            log.info("Filter set for session {}: {}", session.getId(), filter);
        } else if (payload.startsWith("filter:") && payload.length() == 7) {
            // 处理只有 "filter:" 没有内容的情况
            sessionFilters.put(session.getId(), "");
            log.info("Filter cleared for session {}", session.getId());
        }
    }

    public void broadcastPacket(PacketInfo packetInfo) {
        if (sessions.isEmpty())
            return;

        try {
            String packetJson = objectMapper.writeValueAsString(packetInfo);
            TextMessage message = new TextMessage(packetJson);

            sessions.forEach(session -> {
                try {
                    if (session.isOpen() && shouldSendToSession(session, packetInfo))
                        session.sendMessage(message);
                } catch (IOException e) {
                    log.error("Error sending WebSocket message to session: {}", session.getId(), e);
                    sessions.remove(session);
                }
            });
        } catch (Exception e) {
            log.error("Error broadcasting packet", e);
        }
    }

    private boolean shouldSendToSession(WebSocketSession session, PacketInfo packetInfo) {
        String filter = sessionFilters.get(session.getId());

        if (filter == null || filter.isEmpty())
            return true;

        String filterLower = filter.toLowerCase();

        if (packetInfo.getSourceIp() != null && packetInfo.getSourceIp().contains(filter))
            return true;

        if (packetInfo.getDestinationIp() != null && packetInfo.getDestinationIp().contains(filter))
            return true;

        if (packetInfo.getProtocol() != null && packetInfo.getProtocol().toLowerCase().contains(filterLower))
            return true;

        if (packetInfo.getHttpUrl() != null && packetInfo.getHttpUrl().toLowerCase().contains(filterLower))
            return true;

        return false;
    }
}