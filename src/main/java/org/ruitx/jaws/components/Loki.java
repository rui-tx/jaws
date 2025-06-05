package org.ruitx.jaws.components;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.types.QueuedRequest;
import org.ruitx.jaws.types.QueuedResponse;
import org.tinylog.Logger;

import javax.jms.*;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;

/**
 * Loki - The Trickster God of Queues (ActiveMQ Artemis Edition)
 * 
 * Loki manages distributed message queues for JAWS using ActiveMQ Artemis,
 * enabling true horizontal scaling through request queuing and worker processing.
 * Named after the Norse god who could shapeshift and connect different realms - 
 * fitting for a component that transforms and routes requests between different JAWS instances.
 * 
 * Features:
 * - ActiveMQ Artemis embedded broker
 * - Distributed queues for production use
 * - Request/response correlation
 * - Timeout handling
 * - Automatic broker setup and teardown
 * - Thread-safe operations
 */
public class Loki {
    
    private static Loki instance;
    
    // ActiveMQ Artemis components
    private ActiveMQServer artemisServer;
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;
    private Queue requestQueue;
    private Queue responseQueue;
    private MessageProducer requestProducer;
    private MessageProducer responseProducer;
    private MessageConsumer requestConsumer;
    private MessageConsumer responseConsumer;
    
    // Response correlation
    private final Map<String, QueuedResponse> responseMap;
    private final ObjectMapper objectMapper;
    
    // Configuration
    private final long requestTimeout;
    private final String mode;
    private final String requestQueueName;
    private final String responseQueueName;
    
    private volatile boolean isInitialized = false;
    private volatile boolean isBrokerStarted = false;
    
    private Loki() {
        this.responseMap = new ConcurrentHashMap<>();
        this.objectMapper = Odin.getMapper();
        this.requestTimeout = ApplicationConfig.QUEUE_REQUEST_TIMEOUT;
        this.mode = ApplicationConfig.JAWS_MODE;
        this.requestQueueName = ApplicationConfig.QUEUE_REQUEST_NAME;
        this.responseQueueName = ApplicationConfig.QUEUE_RESPONSE_NAME;
        
        Logger.info("Loki awakens! Artemis mode: {}, timeout: {}ms", mode, requestTimeout);
        
        // Initialize Artemis broker and connections
        initializeArtemis();
    }
    
    public static synchronized Loki getInstance() {
        if (instance == null) {
            instance = new Loki();
        }
        return instance;
    }
    
    /**
     * Initializes ActiveMQ Artemis broker and JMS connections.
     */
    private void initializeArtemis() {
        try {
            Logger.info("Loki initializing Artemis broker...");
            
            // Only start embedded broker in head mode (not worker mode)
            if (isHeadMode()) {
                startEmbeddedBroker();
            } else {
                Logger.info("Worker mode - connecting to existing broker...");
            }
            
            // Set up JMS connections for all modes
            setupJMSConnections();
            
            // Set up consumers based on mode
            if (isWorkerMode()) {
                setupWorkerConsumers();
            } else if (isHeadMode()) {
                setupHeadConsumers();
            }
            
            isInitialized = true;
            Logger.info("Loki Artemis initialization complete for mode: {}", mode);
            
        } catch (Exception e) {
            Logger.error("Loki failed to initialize Artemis: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Loki Artemis broker", e);
        }
    }
    
    /**
     * Starts the embedded ActiveMQ Artemis broker.
     */
    private void startEmbeddedBroker() throws Exception {
        Logger.info("Starting embedded Artemis broker with TCP transport...");
        
        Configuration config = new ConfigurationImpl();
        config.setPersistenceEnabled(false);
        config.setJournalDirectory("target/data/journal");
        config.setSecurityEnabled(false);
        
        // Add TCP acceptor for distributed communication
        Map<String, Object> acceptorParams = new HashMap<>();
        acceptorParams.put("host", "localhost");
        acceptorParams.put("port", "61616");
        TransportConfiguration tcpAcceptor = new TransportConfiguration(NettyAcceptorFactory.class.getName(), acceptorParams);
        config.getAcceptorConfigurations().add(tcpAcceptor);
        
        // Also add InVM acceptor for local connections (if needed)
        TransportConfiguration inVMAcceptor = new TransportConfiguration(InVMAcceptorFactory.class.getName());
        config.getAcceptorConfigurations().add(inVMAcceptor);
        
        artemisServer = ActiveMQServers.newActiveMQServer(config);
        artemisServer.start();
        isBrokerStarted = true;
        
        Logger.info("Embedded Artemis broker started with TCP on port 61616");
    }
    
    /**
     * Sets up JMS connections and queues.
     */
    private void setupJMSConnections() throws JMSException {
        Logger.info("Setting up JMS connections...");
        
        // Create connection factory based on mode
        TransportConfiguration transportConfiguration;
        
        if (isWorkerMode()) {
            // Workers connect via TCP to the head's broker
            Map<String, Object> connectorParams = new HashMap<>();
            connectorParams.put("host", "localhost");
            connectorParams.put("port", "61616");
            transportConfiguration = new TransportConfiguration(NettyConnectorFactory.class.getName(), connectorParams);
            Logger.info("Worker connecting via TCP to broker at localhost:61616");
        } else {
            // Head uses InVM connection to its own embedded broker
            transportConfiguration = new TransportConfiguration(InVMConnectorFactory.class.getName());
            Logger.info("Head using InVM connection to embedded broker");
        }
        
        connectionFactory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, transportConfiguration);
        
        // Create connection and session
        connection = connectionFactory.createConnection();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        
        // Create queues
        requestQueue = session.createQueue(requestQueueName);
        responseQueue = session.createQueue(responseQueueName);
        
        // Create producers
        requestProducer = session.createProducer(requestQueue);
        responseProducer = session.createProducer(responseQueue);
        
        // Start the connection
        connection.start();
        
        Logger.info("JMS connections established for {} mode", mode);
    }
    
    /**
     * Sets up message consumers for worker mode.
     */
    private void setupWorkerConsumers() throws JMSException {
        Logger.info("Setting up worker consumers...");
        requestConsumer = session.createConsumer(requestQueue);
        // Workers don't need response consumers - they only send responses
    }
    
    /**
     * Sets up message consumers for head mode.
     */
    private void setupHeadConsumers() throws JMSException {
        Logger.info("Setting up head consumers...");
        responseConsumer = session.createConsumer(responseQueue);
        
        // Set up response message listener
        responseConsumer.setMessageListener(message -> {
            try {
                if (message instanceof TextMessage textMessage) {
                    String responseJson = textMessage.getText();
                    QueuedResponse response = objectMapper.readValue(responseJson, QueuedResponse.class);
                    
                    Logger.debug("Loki received response for request: {}", response.getRequestId());
                    responseMap.put(response.getRequestId(), response);
                }
            } catch (Exception e) {
                Logger.error("Error processing response message: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * Queues a request for processing by workers.
     * Used by the head instance to send requests to workers.
     */
    public QueuedResponse queueRequest(Yggdrasill.RequestContext context) {
        if (!isInitialized) {
            return QueuedResponse.error("unknown", 503, "Loki not initialized");
        }
        
        try {
            String requestId = UUID.randomUUID().toString();
            
            // Create queued request from context
            QueuedRequest queuedRequest = new QueuedRequest(
                requestId,
                context.getRequest().getMethod(),
                context.getRequest().getRequestURI(),
                context.getRequestBody(),
                context.getHeaders(),
                context.getQueryParams(),
                System.currentTimeMillis()
            );
            
            Logger.debug("Loki queuing request via Artemis: {}", requestId);
            
            // Send to Artemis queue
            String requestJson = objectMapper.writeValueAsString(queuedRequest);
            TextMessage message = session.createTextMessage(requestJson);
            message.setJMSCorrelationID(requestId);
            
            requestProducer.send(message);
            
            // Wait for response
            return waitForResponse(requestId);
            
        } catch (Exception e) {
            Logger.error("Loki failed to queue request: {}", e.getMessage(), e);
            return QueuedResponse.error("unknown", 500, "Internal server error: " + e.getMessage());
        }
    }
    
    /**
     * Polls for the next request to process.
     * Used by worker instances to get requests from the queue.
     */
    public QueuedRequest pollRequest(long timeoutMs) {
        if (!isInitialized) {
            return null;
        }
        
        // Check if consumer is valid, reconnect if needed
        if (!isConnectionValid()) {
            Logger.warn("Connection invalid, attempting to reconnect...");
            if (!reconnect()) {
                Logger.error("Failed to reconnect, returning null");
                return null;
            }
        }
        
        try {
            if (requestConsumer == null) {
                Logger.error("Request consumer is null");
                return null;
            }
            
            Message message = requestConsumer.receive(timeoutMs);
            if (message instanceof TextMessage textMessage) {
                String requestJson = textMessage.getText();
                QueuedRequest request = objectMapper.readValue(requestJson, QueuedRequest.class);
                
                Logger.debug("Loki delivering request to worker: {}", request.getId());
                return request;
            }
            return null;
            
        } catch (Exception e) {
            Logger.error("Error polling for requests: {}", e.getMessage());
            
            // If we get a connection-related error, mark the connection as invalid
            if (isConnectionError(e)) {
                Logger.warn("Connection error detected, marking connection as invalid");
                markConnectionInvalid();
            }
            
            return null;
        }
    }
    
    /**
     * Checks if the current connection is valid.
     */
    private boolean isConnectionValid() {
        try {
            // Check if objects exist and test connection with a simple operation
            if (connection == null || session == null || requestConsumer == null) {
                return false;
            }
            
            // Try to perform a simple operation to test if connection is alive
            // This will throw an exception if the connection is closed
            session.getAcknowledgeMode();
            return true;
            
        } catch (Exception e) {
            Logger.debug("Connection validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if an exception indicates a connection error.
     */
    private boolean isConnectionError(Exception e) {
        String message = e.getMessage();
        return message != null && (
            message.contains("Consumer is closed") ||
            message.contains("Connection is destroyed") ||
            message.contains("Session is closed") ||
            message.contains("AMQ219017")
        );
    }
    
    /**
     * Marks the connection as invalid by setting references to null.
     */
    private void markConnectionInvalid() {
        try {
            if (requestConsumer != null) {
                requestConsumer.close();
                requestConsumer = null;
            }
            if (session != null) {
                session.close();
                session = null;
            }
            if (connection != null) {
                connection.close();
                connection = null;
            }
        } catch (Exception e) {
            Logger.debug("Error while cleaning up invalid connection: {}", e.getMessage());
        }
    }
    
    /**
     * Attempts to reconnect to the broker.
     */
    private boolean reconnect() {
        try {
            Logger.info("Attempting to reconnect to Artemis broker...");
            
            // Clean up old connections
            markConnectionInvalid();
            
            // Wait a bit before reconnecting
            Thread.sleep(1000);
            
            // Re-establish connections
            setupJMSConnections();
            
            // Set up consumers based on mode
            if (isWorkerMode()) {
                setupWorkerConsumers();
            } else if (isHeadMode()) {
                setupHeadConsumers();
            }
            
            Logger.info("Successfully reconnected to Artemis broker");
            return true;
            
        } catch (Exception e) {
            Logger.error("Failed to reconnect to Artemis broker: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Sends a response back to the head instance.
     * Used by worker instances after processing a request.
     */
    public void sendResponse(QueuedResponse response) {
        if (!isInitialized) {
            Logger.error("Cannot send response - Loki not initialized");
            return;
        }
        
        // Check if connection is valid, reconnect if needed
        if (!isConnectionValid()) {
            Logger.warn("Connection invalid for sending response, attempting to reconnect...");
            if (!reconnect()) {
                Logger.error("Failed to reconnect, cannot send response");
                return;
            }
        }
        
        try {
            if (responseProducer == null) {
                Logger.error("Response producer is null");
                return;
            }
            
            Logger.debug("Loki sending response via Artemis: {}", response.getRequestId());
            
            String responseJson = objectMapper.writeValueAsString(response);
            TextMessage message = session.createTextMessage(responseJson);
            message.setJMSCorrelationID(response.getRequestId());
            
            responseProducer.send(message);
            
        } catch (Exception e) {
            Logger.error("Error sending response: {}", e.getMessage());
            
            // If we get a connection-related error, mark the connection as invalid
            if (isConnectionError(e)) {
                Logger.warn("Connection error detected while sending response, marking connection as invalid");
                markConnectionInvalid();
            }
        }
    }
    
    /**
     * Waits for a response with the given request ID.
     * Uses polling with timeout to avoid blocking forever.
     */
    private QueuedResponse waitForResponse(String requestId) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = requestTimeout;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            QueuedResponse response = responseMap.remove(requestId);
            if (response != null) {
                Logger.debug("Loki found response for request {}", requestId);
                return response;
            }
            
            // Small sleep to avoid busy waiting
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        Logger.warn("Loki timeout waiting for response to request {}", requestId);
        return QueuedResponse.error(requestId, 408, "Request timeout");
    }
    
    /**
     * Gets queue statistics for monitoring.
     */
    public QueueStats getStats() {
        // For now, return basic stats. In production, we could query Artemis for detailed metrics
        return new QueueStats(
            0, // Artemis queue size would require management API
            responseMap.size(),
            mode
        );
    }
    
    /**
     * Checks if Loki is operating in head mode (receives requests).
     */
    public boolean isHeadMode() {
        return "head".equalsIgnoreCase(mode);
    }
    
    /**
     * Checks if Loki is operating in worker mode (processes requests).
     */
    public boolean isWorkerMode() {
        return "worker".equalsIgnoreCase(mode);
    }
    
    /**
     * Checks if Loki is operating in standalone mode (traditional JAWS).
     */
    public boolean isStandaloneMode() {
        return "standalone".equalsIgnoreCase(mode);
    }
    
    /**
     * Shuts down Artemis connections and broker.
     */
    public void shutdown() {
        Logger.info("Loki shutting down Artemis...");
        
        // Mark as not initialized to prevent new operations
        isInitialized = false;
        
        try {
            // Close consumers first
            if (requestConsumer != null) {
                try {
                    requestConsumer.close();
                } catch (Exception e) {
                    Logger.debug("Error closing request consumer: {}", e.getMessage());
                }
                requestConsumer = null;
            }
            
            if (responseConsumer != null) {
                try {
                    responseConsumer.close();
                } catch (Exception e) {
                    Logger.debug("Error closing response consumer: {}", e.getMessage());
                }
                responseConsumer = null;
            }
            
            // Close producers
            if (requestProducer != null) {
                try {
                    requestProducer.close();
                } catch (Exception e) {
                    Logger.debug("Error closing request producer: {}", e.getMessage());
                }
                requestProducer = null;
            }
            
            if (responseProducer != null) {
                try {
                    responseProducer.close();
                } catch (Exception e) {
                    Logger.debug("Error closing response producer: {}", e.getMessage());
                }
                responseProducer = null;
            }
            
            // Close session and connection
            if (session != null) {
                try {
                    session.close();
                } catch (Exception e) {
                    Logger.debug("Error closing session: {}", e.getMessage());
                }
                session = null;
            }
            
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    Logger.debug("Error closing connection: {}", e.getMessage());
                }
                connection = null;
            }
            
            // Stop broker if we started it
            if (isBrokerStarted && artemisServer != null) {
                try {
                    artemisServer.stop();
                    Logger.info("Embedded Artemis broker stopped");
                } catch (Exception e) {
                    Logger.error("Error stopping Artemis broker: {}", e.getMessage());
                }
                artemisServer = null;
                isBrokerStarted = false;
            }
            
            Logger.info("Loki Artemis shutdown complete");
            
        } catch (Exception e) {
            Logger.error("Error during Loki shutdown: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Simple statistics class for monitoring queue health.
     */
    public static class QueueStats {
        private final int pendingRequests;
        private final int pendingResponses;
        private final String mode;
        
        public QueueStats(int pendingRequests, int pendingResponses, String mode) {
            this.pendingRequests = pendingRequests;
            this.pendingResponses = pendingResponses;
            this.mode = mode;
        }
        
        public int getPendingRequests() { return pendingRequests; }
        public int getPendingResponses() { return pendingResponses; }
        public String getMode() { return mode; }
        
        @Override
        public String toString() {
            return String.format("QueueStats{mode='%s', pendingRequests=%d, pendingResponses=%d}", 
                mode, pendingRequests, pendingResponses);
        }
    }
} 