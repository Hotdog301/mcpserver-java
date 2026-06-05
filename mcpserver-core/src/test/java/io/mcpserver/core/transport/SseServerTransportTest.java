package io.mcpserver.core.transport;

import org.junit.jupiter.api.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link SseServerTransport}.
 * <p>Tests SSE endpoint, message posting, session management,
 * connection lifecycle, and resource cleanup.</p>
 */
@DisplayName("SseServerTransport Tests")
class SseServerTransportTest {

    private SseServerTransport transport;
    private final List<String> receivedMessages = Collections.synchronizedList(new ArrayList<>());
    private Consumer<String> messageHandler;

    @BeforeEach
    void setUp() {
        messageHandler = msg -> receivedMessages.add(msg);
    }

    @AfterEach
    void tearDown() {
        if (transport != null && transport.isRunning()) {
            transport.stop();
        }
    }

    // ---------------------------------------------------------------
    // Construction and Lifecycle
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Construction and Lifecycle")
    class ConstructionAndLifecycleTests {

        @Test
        @DisplayName("Should create and start on port 0 (random)")
        void shouldCreateAndStartOnRandomPort() throws IOException {
            transport = new SseServerTransport(0, messageHandler);
            assertThat(transport.isRunning()).isFalse();
        }

        @Test
        @DisplayName("Should transition to running after start()")
        void shouldTransitionToRunning() throws IOException {
            transport = new SseServerTransport(0, messageHandler);
            transport.start();

            assertThat(transport.isRunning()).isTrue();
            assertThat(transport.getPort()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should throw IllegalStateException on double start")
        void shouldThrowOnDoubleStart() throws IOException {
            transport = new SseServerTransport(0, messageHandler);
            transport.start();

            assertThatThrownBy(transport::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already running");
        }

        @Test
        @DisplayName("Should be idempotent on stop()")
        void shouldBeIdempotentOnStop() throws IOException {
            transport = new SseServerTransport(0, messageHandler);
            transport.stop(); // stop when not running
            transport.stop(); // again

            assertThat(transport.isRunning()).isFalse();
        }

        @Test
        @DisplayName("Should throw NullPointerException for null handler")
        void shouldThrowOnNullHandler() {
            assertThatThrownBy(() -> new SseServerTransport(0, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("messageHandler");
        }

        @Test
        @DisplayName("Should report port after start (even with port 0)")
        void shouldReportPort() throws IOException {
            transport = new SseServerTransport(0, messageHandler);
            transport.start();

            assertThat(transport.getPort()).isGreaterThan(0);
        }
    }

    // ---------------------------------------------------------------
    // SSE Endpoint
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("SSE Endpoint")
    class SseEndpointTests {

        private int port;

        @BeforeEach
        void startTransport() throws Exception {
            transport = new SseServerTransport(0, messageHandler);
            transport.start();
            port = transport.getPort();
        }

        @Test
        @DisplayName("Should accept GET /sse and return endpoint event")
        void shouldAcceptGetAndReturnEndpoint() throws Exception {
            HttpURLConnection conn = connect("http://localhost:" + port + "/sse", "GET");
            assertThat(conn.getResponseCode()).isEqualTo(200);
            assertThat(conn.getHeaderField("Content-Type")).contains("text/event-stream");

            String endpoint = readEndpoint(conn);
            assertThat(endpoint).startsWith("/message?sessionId=");

            conn.disconnect();
        }

        @Test
        @DisplayName("Should set SSE-specific headers")
        void shouldSetSseHeaders() throws Exception {
            HttpURLConnection conn = connect("http://localhost:" + port + "/sse", "GET");
            assertThat(conn.getHeaderField("Cache-Control")).isEqualTo("no-cache");
            assertThat(conn.getHeaderField("Connection")).isEqualTo("keep-alive");
            conn.disconnect();
        }

        @Test
        @DisplayName("Should reject POST to /sse with 405")
        void shouldRejectPostToSse() throws Exception {
            HttpURLConnection conn = connect("http://localhost:" + port + "/sse", "POST");
            conn.setDoOutput(true);
            assertThat(conn.getResponseCode()).isEqualTo(405);
        }
    }

    // ---------------------------------------------------------------
    // Message Endpoint
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Message Endpoint")
    class MessageEndpointTests {

        private int port;

        @BeforeEach
        void startTransport() throws Exception {
            transport = new SseServerTransport(0, messageHandler);
            transport.start();
            port = transport.getPort();
        }

        @Test
        @DisplayName("Should return 202 Accepted for POST /message")
        void shouldReturn202ForPost() throws Exception {
            HttpURLConnection sseConn = connect("http://localhost:" + port + "/sse", "GET");
            String endpoint = readEndpointSse(sseConn);

            String body = "{\"jsonrpc\":\"2.0\",\"method\":\"test\",\"id\":1}";
            HttpURLConnection msgConn = connect("http://localhost:" + port + endpoint, "POST");
            msgConn.setDoOutput(true);
            msgConn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

            assertThat(msgConn.getResponseCode()).isEqualTo(202);
            String response = new String(msgConn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(response).isEqualTo("accepted");

            msgConn.disconnect();
            sseConn.disconnect();
            assertThat(receivedMessages).contains(body);
        }

        @Test
        @DisplayName("Should reject GET to /message with 405")
        void shouldRejectGetToMessage() throws Exception {
            HttpURLConnection conn = connect("http://localhost:" + port + "/message", "GET");
            assertThat(conn.getResponseCode()).isEqualTo(405);
        }

        @Test
        @DisplayName("Should not call handler for empty body")
        void shouldNotCallHandlerForEmptyBody() throws Exception {
            HttpURLConnection sseConn = connect("http://localhost:" + port + "/sse", "GET");
            String endpoint = readEndpointSse(sseConn);

            receivedMessages.clear();
            HttpURLConnection msgConn = connect("http://localhost:" + port + endpoint, "POST");
            msgConn.setDoOutput(true);
            msgConn.getOutputStream().write("".getBytes(StandardCharsets.UTF_8));
            assertThat(msgConn.getResponseCode()).isEqualTo(202);

            msgConn.disconnect();
            sseConn.disconnect();
            assertThat(receivedMessages).isEmpty();
        }
    }

    // ---------------------------------------------------------------
    // Session Management
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Session Management")
    class SessionManagementTests {

        private int port;

        @BeforeEach
        void startTransport() throws Exception {
            transport = new SseServerTransport(0, messageHandler);
            transport.start();
            port = transport.getPort();
            transport.setOnSessionEstablished(() -> {});
        }

        @Test
        @DisplayName("Session ID should be null before any connection")
        void sessionIdShouldBeNullInitially() {
            assertThat(transport.getCurrentSessionId()).isNull();
        }

        @Test
        @DisplayName("Session ID should be set after SSE endpoint event is received")
        void sessionIdShouldBeSetAfterSseConnection() throws Exception {
            // Barrier: prevent handler from cleaning up while we assert
            CountDownLatch allowCleanup = new CountDownLatch(1);
            CountDownLatch sessionReady = new CountDownLatch(1);
            String[] capturedSessionId = new String[1];

            transport.setOnSessionEstablished(() -> {
                capturedSessionId[0] = transport.getCurrentSessionId();
                sessionReady.countDown();
            });

            HttpURLConnection conn = connect("http://localhost:" + port + "/sse", "GET");
            String endpoint = readEndpointSse(conn);

            assertThat(sessionReady.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(capturedSessionId[0]).isNotNull().hasSize(12);
            assertThat(capturedSessionId[0]).isEqualTo(extractSessionId(endpoint));

            conn.disconnect();
            Thread.sleep(500);
        }

        @Test
        @DisplayName("Should replace old session with new connection")
        void shouldReplaceOldSessionWithNewConnection() throws Exception {
            String[] session1 = new String[1];
            CountDownLatch latch1 = new CountDownLatch(1);
            transport.setOnSessionEstablished(() -> {
                session1[0] = transport.getCurrentSessionId();
                latch1.countDown();
            });

            HttpURLConnection conn1 = connect("http://localhost:" + port + "/sse", "GET");
            String endpoint1 = readEndpointSse(conn1);
            assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(session1[0]).isNotNull().isEqualTo(extractSessionId(endpoint1));

            String[] session2 = new String[1];
            CountDownLatch latch2 = new CountDownLatch(1);
            transport.setOnSessionEstablished(() -> {
                session2[0] = transport.getCurrentSessionId();
                latch2.countDown();
            });

            HttpURLConnection conn2 = connect("http://localhost:" + port + "/sse", "GET");
            String endpoint2 = readEndpointSse(conn2);
            assertThat(latch2.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(session1[0]).isNotEqualTo(session2[0]);

            conn1.disconnect();
            conn2.disconnect();
            Thread.sleep(500);
        }

        @Test
        @DisplayName("Session ID should be cleared after client disconnects")
        void sessionIdShouldBeClearedOnDisconnect() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            transport.setOnSessionEstablished(latch::countDown);

            HttpURLConnection conn = connect("http://localhost:" + port + "/sse", "GET");
            String endpoint = readEndpointSse(conn);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            conn.disconnect();
            Thread.sleep(500); // wait for cleanup

            assertThat(transport.getCurrentSessionId()).isNull();
        }
    }

    // ---------------------------------------------------------------
    // sendEvent
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("sendEvent")
    class SendEventTests {

        @Test
        @DisplayName("Should throw IOException when no client connected")
        void shouldThrowWhenNoClient() throws IOException {
            transport = new SseServerTransport(0, messageHandler);
            transport.start();

            assertThatThrownBy(() -> transport.sendEvent("test"))
                    .isInstanceOf(IOException.class)
                    .hasMessage("No connected SSE client");
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private HttpURLConnection connect(String url, String method) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        return conn;
    }

    /**
     * Read the SSE endpoint event from the connection without closing the stream.
     * This is a synchronization point: currentSessionId is set BEFORE the event is sent.
     * Does NOT close the connection or input stream so the session stays alive.
     */
    private String readEndpointSse(HttpURLConnection conn) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        String line1 = reader.readLine(); // "event: endpoint"
        String line2 = reader.readLine(); // "data: /message?sessionId=..."
        assertThat(line1).isEqualTo("event: endpoint");
        assertThat(line2).startsWith("data: /message?sessionId=");
        return line2.substring("data: ".length());
    }

    /**
     * Read the SSE endpoint event and close the stream. Use when you intend
     * to disconnect immediately after reading.
     */
    private String readEndpoint(HttpURLConnection conn) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // "event: endpoint"
            String dataLine = reader.readLine(); // "data: /message?sessionId=..."
            return dataLine.substring("data: ".length());
        }
    }

    private String extractSessionId(String endpoint) {
        int idx = endpoint.indexOf("sessionId=");
        return endpoint.substring(idx + "sessionId=".length());
    }

}
