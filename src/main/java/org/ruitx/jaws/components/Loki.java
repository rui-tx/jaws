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
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.server.Queue;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.types.QueuedRequest;
import org.ruitx.jaws.types.QueuedResponse;
import org.tinylog.Logger;

import javax.jms.*;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
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
 * - Thread-safe operations with per-thread JMS resources
 */
public class Loki {
    
    private static Loki instance;
    
    // ActiveMQ Artemis components
    private ActiveMQServer artemisServer;
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session headSession; // Only used for head mode operations
    private javax.jms.Queue requestQueue;
    private javax.jms.Queue responseQueue;
    private MessageProducer requestProducer;
    private MessageProducer responseProducer;
    private MessageConsumer responseConsumer; // Only used in head mode
    
    // Thread-local JMS resources for worker threads
    private final ThreadLocal<WorkerJMSResources> workerResources = new ThreadLocal<>();
    
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
     * Thread-local JMS resources for worker threads to avoid concurrent session usage.
     */
    private static class WorkerJMSResources {
        private final Session session;
        private final MessageConsumer requestConsumer;
        private final MessageProducer responseProducer;
        private final javax.jms.Queue requestQueue;
        private final javax.jms.Queue responseQueue;
        
        public WorkerJMSResources(Session session, MessageConsumer requestConsumer, 
                                 MessageProducer responseProducer, javax.jms.Queue requestQueue, javax.jms.Queue responseQueue) {
            this.session = session;
            this.requestConsumer = requestConsumer;
            this.responseProducer = responseProducer;
            this.requestQueue = requestQueue;
            this.responseQueue = responseQueue;
        }
        
        public void close() {
            try {
                if (requestConsumer != null) requestConsumer.close();
                if (responseProducer != null) responseProducer.close();
                if (session != null) session.close();
            } catch (Exception e) {
                Logger.debug("Error closing worker JMS resources: {}", e.getMessage());
            }
        }
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
        
        // Create connection
        connection = connectionFactory.createConnection();
        
        // Create queues (these are shared references)
        if (isHeadMode()) {
            // Head mode needs a session for sending requests and receiving responses
            headSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            requestQueue = headSession.createQueue(requestQueueName);
            responseQueue = headSession.createQueue(responseQueueName);
            
            // Create producers for head mode
            requestProducer = headSession.createProducer(requestQueue);
        } else {
            // Worker mode - we'll create per-thread sessions as needed
            // Just create temporary session to define the queue objects
            Session tempSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            requestQueue = tempSession.createQueue(requestQueueName);
            responseQueue = tempSession.createQueue(responseQueueName);
            tempSession.close();
        }
        
        // Start the connection
        connection.start();
        
        Logger.info("JMS connections established for {} mode", mode);
    }
    
    /**
     * Sets up message consumers for worker mode - now using thread-local resources.
     */
    private void setupWorkerConsumers() throws JMSException {
        Logger.info("Worker consumers will be created per-thread as needed");
        // No shared consumers in worker mode - each thread gets its own
    }
    
    /**
     * Sets up message consumers for head mode.
     */
    private void setupHeadConsumers() throws JMSException {
        Logger.info("Setting up head consumers...");
        responseConsumer = headSession.createConsumer(responseQueue);
        
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
            
            // Send to Artemis queue using head session
            String requestJson = objectMapper.writeValueAsString(queuedRequest);
            TextMessage message = headSession.createTextMessage(requestJson);
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
     * Now uses thread-local JMS resources to avoid concurrent session usage.
     */
    public QueuedRequest pollRequest(long timeoutMs) {
        if (!isInitialized) {
            return null;
        }
        
        try {
            // Get or create thread-local JMS resources
            WorkerJMSResources resources = getOrCreateWorkerResources();
            
            Message message = resources.requestConsumer.receive(timeoutMs);
            if (message instanceof TextMessage textMessage) {
                String requestJson = textMessage.getText();
                QueuedRequest request = objectMapper.readValue(requestJson, QueuedRequest.class);
                
                Logger.debug("Loki delivering request to worker: {}", request.getId());
                return request;
            }
            return null;
            
        } catch (Exception e) {
            Logger.error("Error polling for requests: {}", e.getMessage());
            
            // Clean up thread-local resources on error
            cleanupWorkerResources();
            
            return null;
        }
    }
    
    /**
     * Gets or creates thread-local JMS resources for worker operations.
     */
    private WorkerJMSResources getOrCreateWorkerResources() throws JMSException {
        WorkerJMSResources resources = workerResources.get();
        if (resources == null) {
            Logger.debug("Creating new JMS resources for worker thread: {}", Thread.currentThread().getName());
            
            // Create a new session for this worker thread
            Session workerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            
            // Create queues
            javax.jms.Queue workerRequestQueue = workerSession.createQueue(requestQueueName);
            javax.jms.Queue workerResponseQueue = workerSession.createQueue(responseQueueName);
            
            // Create consumer and producer for this thread
            MessageConsumer workerRequestConsumer = workerSession.createConsumer(workerRequestQueue);
            MessageProducer workerResponseProducer = workerSession.createProducer(workerResponseQueue);
            
            resources = new WorkerJMSResources(workerSession, workerRequestConsumer, 
                                             workerResponseProducer, workerRequestQueue, workerResponseQueue);
            workerResources.set(resources);
        }
        return resources;
    }
    
    /**
     * Cleans up thread-local JMS resources when a worker thread is done.
     */
    public void cleanupWorkerResources() {
        WorkerJMSResources resources = workerResources.get();
        if (resources != null) {
            Logger.debug("Cleaning up JMS resources for worker thread: {}", Thread.currentThread().getName());
            resources.close();
            workerResources.remove();
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
     * Gets the actual queue depth using ActiveMQ management API.
     */
    private long getQueueDepth(String queueName) {
        try {
            if (artemisServer != null && isBrokerStarted) {
                // Try to get the actual queue from the server
                org.apache.activemq.artemis.core.server.Queue serverQueue = artemisServer.locateQueue(queueName);
                if (serverQueue != null) {
                    // Return the actual message count
                    return serverQueue.getMessageCount();
                }
                
                Logger.debug("Queue {} not found on server", queueName);
            }
            return 0;
        } catch (Exception e) {
            Logger.debug("Could not get queue depth for {}: {}", queueName, e.getMessage());
            return 0;
        }
    }
    
    /**
     * Gets queue statistics for monitoring with real-time data.
     */
    public QueueStats getStats() {
        try {
            int pendingRequests = 0;
            int pendingResponses = responseMap.size();
            
            // Get actual queue depth from Artemis
            if (isInitialized && connection != null) {
                try {
                    if (isHeadMode()) {
                        // For head mode, get request queue depth (messages waiting to be processed)
                        pendingRequests = (int) getQueueDepth(requestQueueName);
                        // For head mode, pending responses are tracked in memory
                        pendingResponses = responseMap.size();
                    } else if (isWorkerMode()) {
                        // For worker mode, also get request queue depth 
                        // This shows how much work is still pending across all workers
                        pendingRequests = (int) getQueueDepth(requestQueueName);
                        // Workers don't track pending responses
                        pendingResponses = 0;
                    } else {
                        // Standalone mode - no queues
                        pendingRequests = 0;
                        pendingResponses = 0;
                    }
                } catch (Exception e) {
                    Logger.debug("Could not get queue statistics: {}", e.getMessage());
                }
            }
            
            Logger.debug("Queue stats - Mode: {}, Pending Requests: {}, Pending Responses: {}", 
                        mode, pendingRequests, pendingResponses);
            
            return new QueueStats(pendingRequests, pendingResponses, mode);
            
        } catch (Exception e) {
            Logger.error("Error getting queue stats: {}", e.getMessage());
            return new QueueStats(0, 0, mode);
        }
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
     * Sends a response back to the head instance.
     * Used by worker instances after processing a request.
     * Now uses thread-local JMS resources.
     */
    public void sendResponse(QueuedResponse response) {
        if (!isInitialized) {
            Logger.error("Cannot send response - Loki not initialized");
            return;
        }
        
        try {
            // Get or create thread-local JMS resources
            WorkerJMSResources resources = getOrCreateWorkerResources();
            
            Logger.debug("Loki sending response via Artemis: {}", response.getRequestId());
            
            String responseJson = objectMapper.writeValueAsString(response);
            TextMessage message = resources.session.createTextMessage(responseJson);
            message.setJMSCorrelationID(response.getRequestId());
            
            resources.responseProducer.send(message);
            
        } catch (Exception e) {
            Logger.error("Error sending response: {}", e.getMessage());
            
            // Clean up thread-local resources on error
            cleanupWorkerResources();
        }
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
            
            // Clean up any remaining thread-local resources
            try {
                cleanupWorkerResources();
                Logger.debug("Cleaned up thread-local JMS resources during shutdown");
            } catch (Exception e) {
                Logger.warn("Error cleaning up thread-local resources: {}", e.getMessage());
            }
            
            // Close session and connection
            if (headSession != null) {
                try {
                    headSession.close();
                } catch (Exception e) {
                    Logger.debug("Error closing head session: {}", e.getMessage());
                }
                headSession = null;
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