package com.rcs.ssf.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.lang.NonNull;

/**
 * WebSocket Configuration for GraphQL Subscriptions.
 *
 * STATUS: WebSocket support is currently DISABLED in favor of HTTP polling for dashboard stats.
 *
 * REASON: Full implementation of graphql-transport-ws protocol with Spring GraphQL subscriptions
 * requires custom routing of subscription messages to the GraphQL engine with Flux-based streaming.
 * The current stubbed implementation was only sending empty acknowledgments without actual data,
 * causing "Invalid subscription response" errors on the client.
 *
 * CURRENT ARCHITECTURE:
 * - Frontend attempts WebSocket connection to /graphql-ws (see frontend/src/app/graphql.config.ts)
 * - On WebSocket connection failure, client falls back to HTTP polling
 * - HTTP polling endpoint: GET /api/dashboard/stats (5-second interval)
 * - This polling fallback ensures stats are always delivered, eliminating need for WebSocket
 *
 * PERFORMANCE IMPACT:
 * - HTTP polling adds ~50-100ms latency compared to WebSocket push
 * - But provides stable, reliable stats delivery without WebSocket complexity
 * - Suitable for dashboard stats use case (updates every 5 seconds anyway)
 *
 * TO RE-ENABLE WebSocket:
 * 1. Implement full GraphQL subscription routing in handleTextMessage()
 * 2. Parse "payload" field with GraphQL query and variables
 * 3. Create GraphQLRequest and execute via webGraphQlHandler
 * 4. Stream Flux results back to client with PROTOCOL_DATA messages
 * 5. Add auth token extraction from connection params
 * 6. Handle subscription complete/error messages
 * 7. Add integration tests with TestContainers
 *
 * See: WEBSOCKET_TEST_REPORT.md for comprehensive test suite
 */
@Slf4j
public class WebSocketGraphQLConfig implements WebSocketConfigurer {

    private static final String PROTOCOL_CONNECTION_INIT = "connection_init";
    private static final String PROTOCOL_CONNECTION_ACK = "connection_ack";
    private static final String PROTOCOL_START = "start";
    private static final String PROTOCOL_DATA = "data";
    private static final String PROTOCOL_ERROR = "error";
    private static final String PROTOCOL_COMPLETE = "complete";

    private final WebGraphQlHandler webGraphQlHandler;
    private final ObjectMapper objectMapper;

    public WebSocketGraphQLConfig(WebGraphQlHandler webGraphQlHandler) {
        this.webGraphQlHandler = webGraphQlHandler;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        log.warn("WebSocket GraphQL subscriptions are not fully implemented. Using HTTP polling fallback instead.");
        log.info("GraphQL subscriptions: Client will attempt WebSocket connection to /graphql-ws, fall back to polling on /api/dashboard/stats");
        // Handler registration disabled until full implementation of GraphQL subscription routing is complete.
        // See WEBSOCKET_TEST_REPORT.md for comprehensive details on WebSocket implementation.
    }

    /**
     * Returns a WebSocket handler for testing purposes.
     * This creates a stub implementation that acknowledges messages but doesn't route to GraphQL.
     */
    public WebSocketHandler graphQLWebSocketHandler() {
        return new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                log.debug("WebSocket connection established: {}", session.getId());
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                if (message instanceof TextMessage textMessage) {
                    handleTextMessage(session, textMessage);
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                log.debug("WebSocket connection closed: {} with status {}", session.getId(), closeStatus);
            }

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        };
    }

    private void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received WebSocket message: {}", payload);

        try {
            JsonNode json = objectMapper.readTree(payload);
            JsonNode typeNode = json.get("type");
            if (typeNode == null) {
                log.warn("WebSocket message missing 'type' field: {}", payload);
                return;
            }
            String type = typeNode.asText();

            switch (type) {
                case PROTOCOL_CONNECTION_INIT -> handleConnectionInit(session, json);
                case PROTOCOL_START -> handleStart(session, json);
                default -> handleUnknownMessage(session, json);
            }
        } catch (Exception e) {
            log.error("Error processing WebSocket message: {}", e.getMessage());
            sendErrorMessage(session, null, "Failed to process message: " + e.getMessage());
        }
    }

    private void handleConnectionInit(WebSocketSession session, JsonNode json) throws Exception {
        log.debug("Processing connection_init message");
        JsonNode ackMessage = objectMapper.createObjectNode()
            .put("type", PROTOCOL_CONNECTION_ACK);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ackMessage)));
    }

    private void handleStart(WebSocketSession session, JsonNode json) throws Exception {
        String id = json.has("id") ? json.get("id").asText() : null;
        log.debug("Processing subscription start message with id: {}", id);

        // For now, just acknowledge the subscription without routing to GraphQL
        // In a full implementation, this would execute the GraphQL subscription
        JsonNode dataMessage = objectMapper.createObjectNode()
            .put("type", PROTOCOL_DATA)
            .put("id", id);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(dataMessage)));
    }

    private void handleUnknownMessage(WebSocketSession session, JsonNode json) throws Exception {
        String type = json.get("type").asText();
        log.warn("Received unknown message type: {}", type);
        // Invalid messages are logged but don't send errors in current implementation
    }

    private void sendErrorMessage(WebSocketSession session, String id, String errorMessage) throws Exception {
        var errorResponse = objectMapper.createObjectNode()
            .put("type", PROTOCOL_ERROR);
        if (id != null) {
            errorResponse.put("id", id);
        }
        var payload = objectMapper.createObjectNode()
            .put("message", errorMessage);
        errorResponse.set("payload", payload);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
    }
}
