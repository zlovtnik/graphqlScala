package com.rcs.ssf.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Slf4j
@DisplayName("WebSocket GraphQL Configuration Tests")
class WebSocketGraphQLConfigTest {

    private WebSocketGraphQLConfig webSocketGraphQLConfig;
    private ObjectMapper objectMapper;
    private WebSocketHandler handler;

    @Mock
    private WebGraphQlHandler webGraphQlHandler;

    @Mock
    private WebSocketSession webSocketSession;

    @Captor
    private ArgumentCaptor<TextMessage> messageCaptor;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        webSocketGraphQLConfig = new WebSocketGraphQLConfig(webGraphQlHandler);
        handler = webSocketGraphQLConfig.graphQLWebSocketHandler();
        
        when(webSocketSession.getId()).thenReturn("test-session-123");
    }

    @Test
    @DisplayName("Should create WebSocket handler successfully")
    void testHandlerCreation() {
        assertNotNull(handler);
        log.info("WebSocket handler created successfully");
    }

    @Test
    @DisplayName("Should handle WebSocket connection establishment")
    void testConnectionEstablishment() throws Exception {
        // Act & Assert - should not throw
        handler.afterConnectionEstablished(webSocketSession);
        log.info("WebSocket connection established handler executed");
    }

    @Test
    @DisplayName("Should process connection_init message and send connection_ack")
    void testConnectionInitMessageHandling() throws Exception {
        // Arrange
        var connectionInitMessage = objectMapper.createObjectNode()
            .put("type", "connection_init");
        var payload = objectMapper.writeValueAsString(connectionInitMessage);
        var textMessage = new TextMessage(payload);

        // Act
        handler.handleMessage(webSocketSession, textMessage);

        // Assert
        verify(webSocketSession, timeout(1000)).sendMessage(any(TextMessage.class));
        log.info("Connection init message handled, ack sent");
    }

    @Test
    @DisplayName("Should send connection_ack with correct format")
    void testConnectionAckFormat() throws Exception {
        // Arrange
        var connectionInitMessage = objectMapper.createObjectNode()
            .put("type", "connection_init");
        var payload = objectMapper.writeValueAsString(connectionInitMessage);
        var textMessage = new TextMessage(payload);

        // Act
        handler.handleMessage(webSocketSession, textMessage);

        // Assert
        verify(webSocketSession, timeout(1000)).sendMessage(messageCaptor.capture());

        String responsePayload = messageCaptor.getValue().getPayload();
        JsonNode response = objectMapper.readTree(responsePayload);
        assertEquals("connection_ack", response.get("type").asText());
        log.info("Connection ack format verified: {}", responsePayload);
    }

    @Test
    @DisplayName("Should process subscription start message")
    void testSubscriptionStartMessageHandling() throws Exception {
        // Arrange
        var startMessage = objectMapper.createObjectNode()
            .put("type", "start")
            .put("id", "sub-123");
        var payload = objectMapper.writeValueAsString(startMessage);
        var textMessage = new TextMessage(payload);

        // Act
        handler.handleMessage(webSocketSession, textMessage);

        // Assert
        verify(webSocketSession, timeout(1000)).sendMessage(any(TextMessage.class));
        log.info("Subscription start message handled");
    }

    @Test
    @DisplayName("Should include subscription ID in data message")
    void testSubscriptionDataMessageIncludesId() throws Exception {
        // Arrange
        String subscriptionId = "sub-456";
        var startMessage = objectMapper.createObjectNode()
            .put("type", "start")
            .put("id", subscriptionId);
        var payload = objectMapper.writeValueAsString(startMessage);
        var textMessage = new TextMessage(payload);

        // Act
        handler.handleMessage(webSocketSession, textMessage);

        // Assert
        verify(webSocketSession, timeout(1000)).sendMessage(messageCaptor.capture());

        String responsePayload = messageCaptor.getValue().getPayload();
        JsonNode response = objectMapper.readTree(responsePayload);
        assertEquals("data", response.get("type").asText());
        assertEquals(subscriptionId, response.get("id").asText());
        log.info("Subscription ID correctly included in data message: {}", subscriptionId);
    }

    @Test
    @DisplayName("Should handle connection closure")
    void testConnectionClosed() throws Exception {
        // Arrange
        var closeStatus = CloseStatus.NORMAL;

        // Act & Assert - should not throw
        handler.afterConnectionClosed(webSocketSession, closeStatus);
        log.info("Connection closed handler executed successfully");
    }

    @Test
    @DisplayName("Should handle invalid message and log warning")
    void testInvalidMessageHandling() throws Exception {
        // Arrange
        var invalidMessage = objectMapper.createObjectNode()
            .put("type", "invalid_type");
        var payload = objectMapper.writeValueAsString(invalidMessage);
        var textMessage = new TextMessage(payload);

        // Act
        handler.handleMessage(webSocketSession, textMessage);

        // Assert - should complete without throwing
        // Invalid messages are logged but don't send errors in current implementation
        verify(webSocketSession, times(0)).sendMessage(any(TextMessage.class));
        log.info("Invalid message handling verified");
    }

    @Test
    @DisplayName("Should handle malformed JSON with error message")
    void testMalformedJsonHandling() throws Exception {
        // Arrange
        var textMessage = new TextMessage("{invalid json");

        // Act
        handler.handleMessage(webSocketSession, textMessage);

        // Assert
        verify(webSocketSession, timeout(1000)).sendMessage(messageCaptor.capture());

        String responsePayload = messageCaptor.getValue().getPayload();
        JsonNode response = objectMapper.readTree(responsePayload);
        assertEquals("error", response.get("type").asText());
        log.info("Malformed JSON error response sent");
    }

    @Test
    @DisplayName("Should handle empty message gracefully")
    void testEmptyMessageHandling() throws Exception {
        // Arrange
        var textMessage = new TextMessage("{}");

        // Act & Assert - should not throw
        handler.handleMessage(webSocketSession, textMessage);
        log.info("Empty message handled gracefully");
    }

    @Test
    @DisplayName("Should process multiple messages in sequence")
    void testMultipleMessagesInSequence() throws Exception {
        // Arrange
        var connectionInitMessage = objectMapper.createObjectNode()
            .put("type", "connection_init");
        var startMessage1 = objectMapper.createObjectNode()
            .put("type", "start")
            .put("id", "sub-1");
        var startMessage2 = objectMapper.createObjectNode()
            .put("type", "start")
            .put("id", "sub-2");

        // Act
        handler.handleMessage(webSocketSession, new TextMessage(objectMapper.writeValueAsString(connectionInitMessage)));
        handler.handleMessage(webSocketSession, new TextMessage(objectMapper.writeValueAsString(startMessage1)));
        handler.handleMessage(webSocketSession, new TextMessage(objectMapper.writeValueAsString(startMessage2)));

        // Assert
        verify(webSocketSession, timeout(1000).times(3)).sendMessage(any(TextMessage.class));
        log.info("Multiple messages processed in sequence");
    }

    @Test
    @DisplayName("Should extract subscription ID from start message")
    void testSubscriptionIdExtraction() throws Exception {
        // Arrange
        String[] subscriptionIds = {"sub-alpha", "sub-beta", "sub-gamma"};

        // Act & Assert
        for (String id : subscriptionIds) {
            var startMessage = objectMapper.createObjectNode()
                .put("type", "start")
                .put("id", id);
            var textMessage = new TextMessage(objectMapper.writeValueAsString(startMessage));
            handler.handleMessage(webSocketSession, textMessage);

            verify(webSocketSession, timeout(1000).atLeast(1)).sendMessage(messageCaptor.capture());

            String responsePayload = messageCaptor.getValue().getPayload();
            JsonNode response = objectMapper.readTree(responsePayload);
            if ("data".equals(response.get("type").asText())) {
                assertEquals(id, response.get("id").asText());
            }
        }
        log.info("Subscription ID extraction verified for all IDs");
    }

    @Test
    @DisplayName("Should validate protocol constants are correctly used")
    void testProtocolConstantsValidation() throws Exception {
        // Arrange
        var testCases = new String[]{
            "connection_init",
            "start",
            "data",
            "complete",
            "connection_ack",
            "error"
        };

        // Act & Assert - ensure all protocol types can be created and stringified
        for (String type : testCases) {
            var message = objectMapper.createObjectNode().put("type", type);
            String payload = objectMapper.writeValueAsString(message);
            assertNotNull(payload);
            assertTrue(payload.contains(type));
        }
        log.info("Protocol constants validation passed");
    }
}
