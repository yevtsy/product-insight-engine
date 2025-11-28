package com.google.ai.insight.analytics.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base client for communicating with MCP (Model Context Protocol) servers via stdio.
 * Manages process lifecycle, handles JSON-RPC communication, and tracks metrics.
 */
@Slf4j
public abstract class McpClient {

    protected final ObjectMapper objectMapper;
    protected final MeterRegistry meterRegistry;
    protected final AtomicInteger requestIdCounter = new AtomicInteger(1);

    protected Process mcpProcess;
    protected BufferedReader processReader;
    protected BufferedWriter processWriter;

    // Metrics
    protected final Timer requestTimer;
    protected final Counter successCounter;
    protected final Counter errorCounter;
    protected final Counter retryCounter;

    protected McpClient(ObjectMapper objectMapper, MeterRegistry meterRegistry, String serverName) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;

        // Initialize metrics
        this.requestTimer = Timer.builder("mcp.request.duration")
                .tag("server", serverName)
                .description("MCP request duration")
                .register(meterRegistry);

        this.successCounter = Counter.builder("mcp.request.success")
                .tag("server", serverName)
                .description("Successful MCP requests")
                .register(meterRegistry);

        this.errorCounter = Counter.builder("mcp.request.error")
                .tag("server", serverName)
                .description("Failed MCP requests")
                .register(meterRegistry);

        this.retryCounter = Counter.builder("mcp.request.retry")
                .tag("server", serverName)
                .description("Retried MCP requests")
                .register(meterRegistry);
    }

    /**
     * Start the MCP server process with given command and environment.
     */
    protected synchronized void startProcess(String command, List<String> args, Map<String, String> environment)
            throws IOException {
        if (mcpProcess != null && mcpProcess.isAlive()) {
            log.debug("MCP process already running");
            return;
        }

        log.info("Starting MCP server: {} {}", command, String.join(" ", args));

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(command);
        processBuilder.command().addAll(args);

        if (environment != null && !environment.isEmpty()) {
            processBuilder.environment().putAll(environment);
        }

        processBuilder.redirectErrorStream(false);

        try {
            mcpProcess = processBuilder.start();
            processReader = new BufferedReader(new InputStreamReader(mcpProcess.getInputStream()));
            processWriter = new BufferedWriter(new OutputStreamWriter(mcpProcess.getOutputStream()));

            log.info("MCP server process started successfully");

            // Start error stream consumer thread
            startErrorStreamConsumer();

        } catch (IOException e) {
            log.error("Failed to start MCP server process", e);
            throw e;
        }
    }

    /**
     * Send JSON-RPC request to MCP server and return response.
     */
    protected JsonNode sendRequest(String method, Map<String, Object> params, int timeoutMs)
            throws IOException, InterruptedException {

        try {
            return requestTimer.recordCallable(() -> {
                int requestId = requestIdCounter.getAndIncrement();

                Map<String, Object> request = Map.of(
                        "jsonrpc", "2.0",
                        "id", requestId,
                        "method", method,
                        "params", params != null ? params : Map.of()
                );

                String requestJson = objectMapper.writeValueAsString(request);
                log.debug("Sending MCP request: method={}, id={}", method, requestId);
                log.trace("Request JSON: {}", requestJson);

                synchronized (processWriter) {
                    processWriter.write(requestJson);
                    processWriter.newLine();
                    processWriter.flush();
                }

                // Read response with timeout
                String responseLine = readResponseWithTimeout(timeoutMs);

                if (responseLine == null) {
                    errorCounter.increment();
                    throw new IOException("No response received from MCP server within timeout");
                }

                log.trace("Response JSON: {}", responseLine);

                JsonNode response = objectMapper.readTree(responseLine);

                if (response.has("error")) {
                    errorCounter.increment();
                    String errorMessage = response.get("error").get("message").asText();
                    log.error("MCP server returned error: {}", errorMessage);
                    throw new IOException("MCP server error: " + errorMessage);
                }

                successCounter.increment();
                return response.get("result");
            });
        } catch (IOException | InterruptedException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Unexpected error during MCP request", e);
        }
    }

    /**
     * Read response from process with timeout.
     */
    private String readResponseWithTimeout(int timeoutMs) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (processReader.ready()) {
                return processReader.readLine();
            }
            Thread.sleep(50);
        }

        return null;
    }

    /**
     * Start thread to consume error stream to prevent buffer overflow.
     */
    private void startErrorStreamConsumer() {
        Thread errorConsumer = new Thread(() -> {
            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(mcpProcess.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    log.warn("MCP server stderr: {}", line);
                }
            } catch (IOException e) {
                log.debug("Error stream consumer stopped: {}", e.getMessage());
            }
        }, "mcp-error-consumer");
        errorConsumer.setDaemon(true);
        errorConsumer.start();
    }

    /**
     * Stop the MCP server process.
     */
    public synchronized void stopProcess() {
        if (mcpProcess != null && mcpProcess.isAlive()) {
            log.info("Stopping MCP server process");

            try {
                // Try graceful shutdown
                processWriter.close();
                processReader.close();

                if (!mcpProcess.waitFor(5, TimeUnit.SECONDS)) {
                    log.warn("MCP process did not terminate gracefully, forcing shutdown");
                    mcpProcess.destroyForcibly();
                }

                log.info("MCP server process stopped");
            } catch (Exception e) {
                log.error("Error stopping MCP process", e);
                mcpProcess.destroyForcibly();
            }
        }
    }

    /**
     * Check if MCP server process is running.
     */
    public boolean isRunning() {
        return mcpProcess != null && mcpProcess.isAlive();
    }

    /**
     * Execute request with retry logic.
     */
    protected JsonNode executeWithRetry(String method, Map<String, Object> params,
                                        int timeoutMs, int maxRetries)
            throws IOException, InterruptedException {
        int attempt = 0;
        IOException lastException = null;

        while (attempt < maxRetries) {
            try {
                if (!isRunning()) {
                    initializeServer();
                }

                return sendRequest(method, params, timeoutMs);

            } catch (IOException e) {
                lastException = e;
                attempt++;

                if (attempt < maxRetries) {
                    retryCounter.increment();
                    log.warn("MCP request failed (attempt {}/{}), retrying: {}",
                            attempt, maxRetries, e.getMessage());

                    // Restart process if it died
                    if (!isRunning()) {
                        log.info("MCP process died, restarting...");
                        stopProcess();
                        Thread.sleep(1000);
                    }
                } else {
                    log.error("MCP request failed after {} attempts", maxRetries);
                }
            }
        }

        throw lastException;
    }

    /**
     * Initialize the MCP server. Must be implemented by subclasses.
     */
    protected abstract void initializeServer() throws IOException;
}