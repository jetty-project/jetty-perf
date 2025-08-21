package org.eclipse.jetty.perf.cometd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.websocket.WebSocketContainer;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.atomic.LongAdder;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogReader;
import org.cometd.bayeux.Channel;
import org.cometd.bayeux.ChannelId;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.benchmark.Atomics;
import org.cometd.benchmark.Config;
import org.cometd.benchmark.MonitoringQueuedThreadPool;
import org.cometd.client.BayeuxClient;
import org.cometd.client.ext.AckExtension;
import org.cometd.client.http.jetty.JettyHttpClientTransport;
import org.cometd.client.transport.ClientTransport;
import org.cometd.client.transport.TransportListener;
import org.cometd.client.websocket.jakarta.WebSocketTransport;
import org.cometd.client.websocket.jetty.JettyWebSocketTransport;
import org.cometd.common.JacksonJSONContextClient;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.transport.HttpClientTransportOverHTTP;
import org.eclipse.jetty.ee11.websocket.jakarta.client.JakartaWebSocketClientContainerProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpHeaderValue;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.transport.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.perf.util.LatencyRecorder;
import org.eclipse.jetty.toolchain.perf.PlatformMonitor;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.util.NanoTime;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.eclipse.jetty.util.component.Container;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.WebSocketClient;

public class CometDLoadClient
{
    private static final String START_FIELD = "start";
    private static final String HISTOGRAM_FILENAME = "perf.hlog";

    private final LatencyRecorder latencyRecorder;
    private final PlatformMonitor monitor = new PlatformMonitor();
    private final AtomicLong ids = new AtomicLong();
    private final List<LoadBayeuxClient> bayeuxClients = new BlockingArrayQueue<>();
    private final ConcurrentMap<String, ChannelId> channelIds = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, AtomicInteger> roomMap = new ConcurrentHashMap<>();
    private final AtomicLong start = new AtomicLong();
    private final AtomicLong end = new AtomicLong();
    private final AtomicLong responses = new AtomicLong();
    private final AtomicLong messages = new AtomicLong();
    private final AtomicLong minLatency = new AtomicLong();
    private final AtomicLong maxLatency = new AtomicLong();
    private final AtomicLong totLatency = new AtomicLong();
    private final AtomicStampedReference<String> maxTime = new AtomicStampedReference<>(null, 0);
    private final Map<String, AtomicStampedReference<Long>> sendTimes = new ConcurrentHashMap<>();
    private final Map<String, AtomicStampedReference<List<Long>>> arrivalTimes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
    private final MonitoringQueuedThreadPool threadPool = new MonitoringQueuedThreadPool(0);
    private final DynamicConnectionStatistics connectionStatistics = new DynamicConnectionStatistics();
    private HttpClient httpClient;
    private WebSocketClient webSocketClient;
    private WebSocketContainer webSocketContainer;
    private LatencyListener latencyListener;
    private HandshakeListener handshakeListener;
    private DisconnectListener disconnectListener;

    String host = "localhost";
    int port = 8080;
    boolean tls = false;
    int selectors = 1;
    int maxThreads = 256;
    ClientTransportType transport = ClientTransportType.LONG_POLLING;
    boolean http2 = false;
    boolean perMessageDeflate = false;
    String context = Config.CONTEXT_PATH;
    String channel = "/a";
    int rooms = 100;
    int roomsPerClient = 10;
    boolean ackExtension = false;
    int iterations = 1;
    int clients = 1000;
    int batches = 1000;
    int batchSize = 10;
    long batchPause = 10000;
    int messageSize = 50;
    boolean randomize = false;
    String file = "./result.json";

    public CometDLoadClient(boolean recordHistogram) throws Exception
    {
        latencyRecorder = recordHistogram ? new LatencyRecorder(HISTOGRAM_FILENAME) : null;
    }

    public void disconnect()
    {
        for (LoadBayeuxClient bayeuxClient : bayeuxClients)
        {
            disconnectClient(bayeuxClient);
        }
        bayeuxClients.clear();
    }

    public void run() throws Exception
    {
        String host = this.host;

        int port = this.port;

        boolean tls = this.tls;

        int selectors = this.selectors;

        int maxThreads = this.maxThreads;

        ClientTransportType transport = this.transport;

        String contextPath = this.context;
        String url = (tls ? "https" : "http") + "://" + host + ":" + port + contextPath + Config.COMETD_PATH;

        String channel = this.channel;
        channel = Config.CHANNEL_PREFIX + (channel.startsWith("/") ? channel.substring(1) : channel);

        int rooms = this.rooms;

        int roomsPerClient = this.roomsPerClient;

        boolean ackExtension = this.ackExtension;

        MBeanContainer mbeanContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
        mbeanContainer.beanAdded(null, this);

        threadPool.setMaxThreads(maxThreads);
        threadPool.setDaemon(true);
        threadPool.start();
        mbeanContainer.beanAdded(null, threadPool);

        ClientConnector clientConnector = new ClientConnector();
        clientConnector.setExecutor(threadPool);
        clientConnector.setSelectors(selectors);
        clientConnector.setSslContextFactory(new SslContextFactory.Client(true));
        HttpClientTransport httpClientTransport = new HttpClientTransportOverHTTP(clientConnector);
        if (http2)
        {
            HTTP2Client http2Client = new HTTP2Client(clientConnector);
            httpClientTransport = new HttpClientTransportOverHTTP2(http2Client);
        }
        httpClient = new HttpClient(httpClientTransport);
        httpClient.setMaxConnectionsPerDestination(60000);
        httpClient.setMaxRequestsQueuedPerDestination(10000);
        httpClient.setIdleTimeout(Config.META_CONNECT_TIMEOUT + 2 * Config.MAX_NETWORK_DELAY);
        httpClient.setSocketAddressResolver(new SocketAddressResolver.Sync());
        httpClient.addBean(mbeanContainer);
        httpClient.addBean(connectionStatistics);
        LifeCycle.start(httpClient);
        mbeanContainer.beanAdded(null, httpClient);

        webSocketClient = new WebSocketClient(httpClient);
        webSocketClient.setInputBufferSize(8 * 1024);
        webSocketClient.setMaxTextMessageSize(Integer.MAX_VALUE);
        webSocketClient.addBean(mbeanContainer);
        webSocketClient.addBean(connectionStatistics);
        LifeCycle.start(webSocketClient);
        mbeanContainer.beanAdded(null, webSocketClient);

        webSocketContainer = JakartaWebSocketClientContainerProvider.getContainer(httpClient);
        Container.addBean(webSocketContainer, mbeanContainer);
        Container.addBean(webSocketContainer, connectionStatistics);
        mbeanContainer.beanAdded(null, webSocketContainer);

        latencyListener = new LatencyListener();
        handshakeListener = new HandshakeListener(channel, rooms, roomsPerClient);
        disconnectListener = new DisconnectListener();

        LoadBayeuxClient statsClient = new LoadBayeuxClient(url, scheduler, newClientTransport(transport));
        statsClient.handshake();

        int clients = this.clients;
        int batches = this.batches;
        int batchSize = this.batchSize;
        long batchPause = this.batchPause;
        int messageSize = this.messageSize;
        boolean randomize = this.randomize;

        while (true)
        {
            System.err.println();
            System.err.println("-----");

            if (iterations-- == 0)
            {
                break;
            }

            System.err.println("Waiting for clients to be ready...");

            // Create or remove the necessary bayeux clients
            int currentClients = bayeuxClients.size();
            if (currentClients < clients)
            {
                for (int i = 0; i < clients - currentClients; ++i)
                {
                    bayeuxClients.add(handshakeClient(url, transport, ackExtension));
                }
            }

            int currentSize = bayeuxClients.size();

            System.err.printf("Clients ready: %d%n", currentSize);

            reset();

            char[] chars = new char[messageSize];
            Arrays.fill(chars, 'x');
            String chat = new String(chars);

            // Send a message to the server to signal the start of the test.
            statsClient.begin();

            if (latencyRecorder != null)
                latencyRecorder.startRecording();

            PlatformMonitor.Start start = monitor.start();
            System.err.println();
            System.err.println(start);
            System.err.printf("Testing %d clients in %d rooms, %d rooms/client%n", bayeuxClients.size(), rooms, roomsPerClient);
            System.err.printf("Sending %d batches of %dx%d bytes messages every %d \u00B5s%n", batches, batchSize, messageSize, batchPause);

            long begin = NanoTime.now();
            long expected = runBatches(channel, batches, batchSize, TimeUnit.MICROSECONDS.toNanos(batchPause), chat, randomize);
            long sendElapsed = NanoTime.since(begin);

            PlatformMonitor.Stop stop = monitor.stop();
            System.err.println(stop);

            waitForMessages(expected);

            long messages = this.messages.get();
            long receiveElapsed = this.end.get() - this.start.get();
            if (receiveElapsed == 0L)
                receiveElapsed = 1L;

            // Send a message to the server to signal the end of the test.
            statsClient.end();

            Histogram histogram = printResults(messages, expected, sendElapsed, receiveElapsed);
//            if (!interactive)
            {
                Map<String, Object> run = new LinkedHashMap<>();
                Map<String, Object> config = new LinkedHashMap<>();
                run.put("config", config);
                config.put("cores", start.cores);
                config.put("totalMemory", new Measure(start.gibiBytes(start.totalMemory), "GiB"));
                config.put("os", start.os);
                config.put("jvm", start.jvm);
                config.put("totalHeap", new Measure(start.gibiBytes(start.heap.getMax()), "GiB"));
                config.put("date", new Date(start.date).toString());
                config.put("transport", transport.getName());
                config.put("clients", bayeuxClients.size());
                config.put("rooms", rooms);
                config.put("roomsPerClient", roomsPerClient);
                config.put("batches", batches);
                config.put("batchSize", batchSize);
                config.put("batchPause", new Measure(batchPause, "\u00B5s"));
                config.put("messageSize", new Measure(messageSize, "B"));
                Map<String, Object> results = new LinkedHashMap<>();
                run.put("results", results);
                results.put("cpu", new Measure(stop.percent(stop.cpuTime, stop.time) / start.cores, "%"));
                results.put("jitTime", new Measure(stop.jitTime, "ms"));
                results.put("messages", messages);
                results.put("sendTime", new Measure(TimeUnit.NANOSECONDS.toMillis(sendElapsed), "ms"));
                results.put("sendRate", new Measure(messages * 1000L * 1000 * 1000 / sendElapsed, "messages/s"));
                results.put("receiveTime", new Measure(TimeUnit.NANOSECONDS.toMillis(receiveElapsed), "ms"));
                results.put("receiveRate", new Measure(messages * 1000L * 1000 * 1000 / receiveElapsed, "messages/s"));
                Map<String, Object> latency = new LinkedHashMap<>();
                results.put("latency", latency);
                latency.put("min", new Measure(TimeUnit.NANOSECONDS.toMicros(histogram.getMinValue()), "\u00B5s"));
                latency.put("p50", new Measure(TimeUnit.NANOSECONDS.toMicros(histogram.getValueAtPercentile(50D)), "\u00B5s"));
                latency.put("p99", new Measure(TimeUnit.NANOSECONDS.toMicros(histogram.getValueAtPercentile(99D)), "\u00B5s"));
                latency.put("max", new Measure(TimeUnit.NANOSECONDS.toMicros(histogram.getMaxValue()), "\u00B5s"));
                Map<String, Object> threadPool = new LinkedHashMap<>();
                results.put("threadPool", threadPool);
                threadPool.put("tasks", this.threadPool.getTasks());
                threadPool.put("queueSizeMax", this.threadPool.getMaxQueueSize());
                threadPool.put("activeThreadsMax", this.threadPool.getMaxActiveThreads());
                threadPool.put("queueLatencyAverage", new Measure(TimeUnit.NANOSECONDS.toMillis(this.threadPool.getAverageQueueLatency()), "ms"));
                threadPool.put("queueLatencyMax", new Measure(TimeUnit.NANOSECONDS.toMillis(this.threadPool.getMaxQueueLatency()), "ms"));
                threadPool.put("taskTimeAverage", new Measure(TimeUnit.NANOSECONDS.toMillis(this.threadPool.getAverageTaskLatency()), "ms"));
                threadPool.put("taskTimeMax", new Measure(TimeUnit.NANOSECONDS.toMillis(this.threadPool.getMaxTaskLatency()), "ms"));
                Map<String, Object> gc = new LinkedHashMap<>();
                results.put("gc", gc);
                gc.put("youngCount", stop.youngCount);
                gc.put("youngTime", new Measure(stop.youngTime, "ms"));
                gc.put("oldCount", stop.oldCount);
                gc.put("oldTime", new Measure(stop.oldTime, "ms"));
                gc.put("youngGarbage", new Measure(stop.mebiBytes(stop.edenBytes + stop.survivorBytes), "MiB"));
                gc.put("oldGarbage", new Measure(stop.mebiBytes(stop.tenuredBytes), "MiB"));
                saveResults(run, file);
            }
        }

        statsClient.exit();

        if (latencyRecorder != null)
            latencyRecorder.stopRecording();

        LifeCycle.stop(webSocketContainer);
        LifeCycle.stop(webSocketClient);
        LifeCycle.stop(httpClient);

        scheduler.shutdown();
    }

    private long runBatches(String channel, int batchCount, int batchSize, long batchPauseNanos, String chat, boolean randomize)
    {
        long begin = NanoTime.now();
        int index = -1;
        long expected = 0;
        for (int i = 1; i <= batchCount; ++i)
        {
            long pause = i * batchPauseNanos - NanoTime.since(begin);
            if (pause > 0)
            {
                nanoSleep(pause);
            }

            if (randomize)
            {
                index = nextRandom(bayeuxClients.size());
            }
            else
            {
                ++index;
                if (index == bayeuxClients.size())
                {
                    index = 0;
                }
            }
            LoadBayeuxClient client = bayeuxClients.get(index);
            expected += sendBatch(client, channel, batchSize, chat);
        }
        return expected;
    }

    protected LoadBayeuxClient handshakeClient(String url, ClientTransportType transport, boolean ackExtension)
    {
        LoadBayeuxClient client = new LoadBayeuxClient(url, scheduler, newClientTransport(transport));
        if (ackExtension)
        {
            client.addExtension(new AckExtension());
        }
        client.getChannel(Channel.META_HANDSHAKE).addListener(handshakeListener);
        client.getChannel(Channel.META_DISCONNECT).addListener(disconnectListener);
        client.handshake();
        client.waitForInit();
        return client;
    }

    protected void disconnectClient(LoadBayeuxClient client)
    {
        client.disconnect();
        client.waitFor(1000, BayeuxClient.State.DISCONNECTED);
    }

    private void nanoSleep(long pause)
    {
        try
        {
            TimeUnit.NANOSECONDS.sleep(pause);
        }
        catch (InterruptedException x)
        {
            Thread.currentThread().interrupt();
            throw new RuntimeException(x);
        }
    }

    private long sendBatch(LoadBayeuxClient client, String channel, int batchSize, String chat)
    {
        long expected = 0;
        List<Integer> rooms = new ArrayList<>(roomMap.keySet());
        client.startBatch();
        for (int b = 0; b < batchSize; ++b)
        {
            int room = -1;
            AtomicInteger clientsPerRoom = null;
            while (clientsPerRoom == null || clientsPerRoom.get() == 0)
            {
                room = rooms.get(nextRandom(rooms.size()));
                clientsPerRoom = roomMap.get(room);
            }
            Map<String, Object> message = new HashMap<>(5);
            // Additional fields to simulate a chat message
            message.put("room", room);
            message.put("user", client.hashCode());
            message.put("chat", chat);
            // Mandatory fields to record latencies
            message.put(START_FIELD, NanoTime.now());
            message.put(Config.ID_FIELD, ids.incrementAndGet() + channel);
            ClientSessionChannel clientChannel = client.getChannel(getChannelId(channel + "/" + room));
            clientChannel.publish(message);
            clientChannel.release();
            expected += clientsPerRoom.get();
        }
        client.endBatch();
        return expected;
    }

    private ClientTransport newClientTransport(ClientTransportType clientTransportType)
    {
        switch (clientTransportType)
        {
            case LONG_POLLING ->
            {
                Map<String, Object> options = new HashMap<>();
                options.put(ClientTransport.JSON_CONTEXT_OPTION, new JacksonJSONContextClient());
                options.put(ClientTransport.MAX_NETWORK_DELAY_OPTION, Config.MAX_NETWORK_DELAY);
                options.put(ClientTransport.MAX_MESSAGE_SIZE_OPTION, Integer.MAX_VALUE);
                return new JettyHttpClientTransport(options, httpClient)
                {
                    @Override
                    protected void customize(Request request)
                    {
                        super.customize(request);
                        if (request.getPath().endsWith("/disconnect"))
                        {
                            request.headers(headers -> headers.put(HttpHeader.CONNECTION, HttpHeaderValue.CLOSE));
                        }
                    }
                };
            }
            case JAKARTA_WEBSOCKET ->
            {
                Map<String, Object> options = new HashMap<>();
                options.put(ClientTransport.JSON_CONTEXT_OPTION, new JacksonJSONContextClient());
                options.put(ClientTransport.MAX_NETWORK_DELAY_OPTION, Config.MAX_NETWORK_DELAY);
                options.put(ClientTransport.MAX_MESSAGE_SIZE_OPTION, Integer.MAX_VALUE);
                // Differently from HTTP where the idle timeout is adjusted if it is a /meta/connect
                // for WebSocket we need an idle timeout that is longer than the /meta/connect timeout.
                options.put(WebSocketTransport.IDLE_TIMEOUT_OPTION, Config.META_CONNECT_TIMEOUT + httpClient.getIdleTimeout());
                options.put(WebSocketTransport.PERMESSAGE_DEFLATE_OPTION, perMessageDeflate);
                return new WebSocketTransport(options, scheduler, webSocketContainer);
            }
            case JETTY_WEBSOCKET ->
            {
                Map<String, Object> options = new HashMap<>();
                options.put(ClientTransport.JSON_CONTEXT_OPTION, new JacksonJSONContextClient());
                options.put(ClientTransport.MAX_NETWORK_DELAY_OPTION, Config.MAX_NETWORK_DELAY);
                options.put(ClientTransport.MAX_MESSAGE_SIZE_OPTION, Integer.MAX_VALUE);
                // Differently from HTTP where the idle timeout is adjusted if it is a /meta/connect
                // for WebSocket we need an idle timeout that is longer than the /meta/connect timeout.
                options.put(JettyWebSocketTransport.IDLE_TIMEOUT_OPTION, Config.META_CONNECT_TIMEOUT + httpClient.getIdleTimeout());
                options.put(WebSocketTransport.PERMESSAGE_DEFLATE_OPTION, perMessageDeflate);
                return new JettyWebSocketTransport(options, scheduler, webSocketClient);
            }
            default ->
            {
                throw new IllegalArgumentException();
            }
        }
    }

    private int nextRandom(int limit)
    {
        return ThreadLocalRandom.current().nextInt(limit);
    }

    private void updateLatencies(long startTime, long sendTime, long arrivalTime, long endTime)
    {
        long wallLatency = endTime - startTime;
        if (latencyRecorder != null)
            latencyRecorder.recordValue(wallLatency);

        long latency = TimeUnit.MICROSECONDS.toNanos(TimeUnit.NANOSECONDS.toMicros(arrivalTime - sendTime));
        Atomics.updateMin(minLatency, latency);
        Atomics.updateMax(maxLatency, latency);
        totLatency.addAndGet(latency);
    }

    private void waitForMessages(long expected) throws InterruptedException
    {
        long arrived = messages.get();
        long lastArrived = 0;
        int maxRetries = 20;
        int retries = maxRetries;
        while (arrived < expected)
        {
            System.err.printf("Waiting for messages to arrive %d/%d%n", arrived, expected);
            Thread.sleep(500);
            if (lastArrived == arrived)
            {
                --retries;
                if (retries == 0)
                {
                    break;
                }
            }
            else
            {
                lastArrived = arrived;
                retries = maxRetries;
            }
            arrived = messages.get();
        }
        if (arrived < expected)
        {
            System.err.printf("Interrupting wait for messages %d/%d%n", arrived, expected);
        }
        else
        {
            System.err.printf("All messages arrived %d/%d%n", arrived, expected);
        }
    }

    private Histogram printResults(long messageCount, long expectedCount, long sendElapsed, long receiveElapsed)
    {
        System.err.printf("Messages - Success/Expected = %d/%d%n", messageCount, expectedCount);

        DynamicConnectionStatistics.Data data = connectionStatistics.collect();

        if (sendElapsed > 0)
        {
            long batchRate = batches * 1000L * 1000 * 1000 / sendElapsed;
            float uploadRate = data.sentBytes * 1000F * 1000 * 1000 / sendElapsed / 1024 / 1024;
            System.err.printf("Outgoing: Elapsed = %d ms | Rate = %d messages/s - %d batches/s - %.3f MiB/s%n",
                TimeUnit.NANOSECONDS.toMillis(sendElapsed),
                batchSize * batchRate,
                batchRate,
                uploadRate
            );
        }

        if (receiveElapsed > 0)
        {
            float downloadRate = data.receivedBytes * 1000F * 1000 * 1000 / receiveElapsed / 1024 / 1024;
            System.err.printf("Incoming - Elapsed = %d ms | Rate = %d messages/s - %d batches/s(%.2f%%) - %.3f MiB/s%n",
                TimeUnit.NANOSECONDS.toMillis(receiveElapsed),
                messageCount * 1000L * 1000 * 1000 / receiveElapsed,
                responses.get() * 1000L * 1000 * 1000 / receiveElapsed,
                100F * responses.get() / messageCount,
                downloadRate
            );
        }

        Histogram histogram = new Histogram(3);
        try (HistogramLogReader reader = new HistogramLogReader(HISTOGRAM_FILENAME))
        {
            while (reader.hasNext())
            {
                Histogram h = (Histogram) reader.nextIntervalHistogram();
                if (h != null)
                    histogram.add(h);
            }
        }
        catch (Exception e)
        {
            System.err.println("Error collecting histogram: " + e);
        }

        System.err.printf("Messages - Network Latency Min/Ave/Max = %d/%d/%d ms%n",
            TimeUnit.NANOSECONDS.toMillis(minLatency.get()),
            messageCount == 0 ? -1 : TimeUnit.NANOSECONDS.toMillis(totLatency.get() / messageCount),
            TimeUnit.NANOSECONDS.toMillis(maxLatency.get()));

        System.err.printf("Slowest Message ID = %s time = %d ms%n", maxTime.getReference(), maxTime.getStamp());

        Config.printThreadPool("Thread Pool", threadPool);

        return histogram;
    }

    private void saveResults(Map<String, Object> run, String path)
    {
        try
        {
            File file = new File(path);
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(file, run);
            System.err.printf("Results saved to file %s%n", file.getAbsolutePath());
        }
        catch (IOException x)
        {
            System.err.printf("Could not save results to file %s%n", path);
        }
    }

    private void reset()
    {
        threadPool.reset();
        start.set(0L);
        end.set(0L);
        responses.set(0L);
        messages.set(0L);
        minLatency.set(Long.MAX_VALUE);
        maxLatency.set(0L);
        totLatency.set(0L);
        maxTime.set(null, 0);
        sendTimes.clear();
        arrivalTimes.clear();
        connectionStatistics.reset();
    }

    private class HandshakeListener implements ClientSessionChannel.MessageListener
    {
        private static final String SESSION_ID_ATTRIBUTE = "session_id";
        private final String channel;
        private final int rooms;
        private final int roomsPerClient;

        private HandshakeListener(String channel, int rooms, int roomsPerClient)
        {
            this.channel = channel;
            this.rooms = rooms;
            this.roomsPerClient = roomsPerClient;
        }

        @Override
        public void onMessage(ClientSessionChannel handshakeChannel, Message handshakeReply)
        {
            if (handshakeReply.isSuccessful())
            {
                LoadBayeuxClient client = (LoadBayeuxClient)handshakeChannel.getSession();
                String sessionId = (String)client.getAttribute(SESSION_ID_ATTRIBUTE);
                if (sessionId == null)
                {
                    client.setAttribute(SESSION_ID_ATTRIBUTE, client.getId());
                    client.batch(() ->
                    {
                        List<Integer> roomsSubscribedTo = new ArrayList<>();
                        for (int j = 0; j < roomsPerClient; ++j)
                        {
                            // Avoid to subscribe the same client twice to the same room
                            int room = nextRandom(rooms);
                            while (roomsSubscribedTo.contains(room))
                            {
                                room = nextRandom(rooms);
                            }
                            roomsSubscribedTo.add(room);
                            client.setupRoom(room);
                            client.getChannel(channel + "/" + room).subscribe(latencyListener);
                        }
                        client.init();
                    });
                }
                else
                {
                    System.err.printf("Second handshake for client %s: old session %s, new session %s%n", this, sessionId, client.getId());
                }
            }
        }
    }

    private class DisconnectListener implements ClientSessionChannel.MessageListener
    {
        @Override
        public void onMessage(ClientSessionChannel channel, Message message)
        {
            if (message.isSuccessful())
            {
                LoadBayeuxClient client = (LoadBayeuxClient)channel.getSession();
                client.destroy();
            }
        }
    }

    private class LatencyListener implements ClientSessionChannel.MessageListener
    {
        @Override
        public void onMessage(ClientSessionChannel channel, Message message)
        {
            Map<String, Object> data = message.getDataAsMap();
            if (data != null)
            {
                long startTime = ((Number)data.get(START_FIELD)).longValue();
                long endTime = NanoTime.now();
                start.compareAndSet(0, endTime);
                end.set(endTime);
                messages.incrementAndGet();

                String id = (String)data.get(Config.ID_FIELD);

                AtomicStampedReference<Long> sendTimeRef = sendTimes.get(id);
                long sendTime = sendTimeRef.getReference();
                // Update count atomically
                if (Atomics.decrement(sendTimeRef) == 0)
                {
                    sendTimes.remove(id);
                }

                AtomicStampedReference<List<Long>> arrivalTimeRef = arrivalTimes.get(id);
                long arrivalTime = arrivalTimeRef.getReference().remove(0);
                // Update count atomically
                if (Atomics.decrement(arrivalTimeRef) == 0)
                {
                    arrivalTimes.remove(id);
                }

                long delayMs = NanoTime.millisElapsed(startTime, endTime);
                Atomics.updateMax(maxTime, id, (int)delayMs);

                updateLatencies(startTime, sendTime, arrivalTime, endTime);
            }
            else
            {
                throw new IllegalStateException("No 'data' field in message " + message);
            }
        }
    }

    private class LoadBayeuxClient extends BayeuxClient
    {
        private final List<Integer> subscriptions = new ArrayList<>();
        private final CountDownLatch initLatch = new CountDownLatch(1);

        private LoadBayeuxClient(String url, ScheduledExecutorService scheduler, ClientTransport transport)
        {
            super(url, scheduler, transport);
            addTransportListener(new TransportListener()
            {
                @Override
                public void onSending(List<? extends Message> messages)
                {
                    recordSentMessages(messages);
                }

                @Override
                public void onMessages(List<Message.Mutable> messages)
                {
                    recordReceivedMessages(messages);
                }
            });
        }

        public void setupRoom(int room)
        {
            AtomicInteger clientsPerRoom = roomMap.get(room);
            if (clientsPerRoom == null)
            {
                clientsPerRoom = new AtomicInteger();
                AtomicInteger existing = roomMap.putIfAbsent(room, clientsPerRoom);
                if (existing != null)
                {
                    clientsPerRoom = existing;
                }
            }
            clientsPerRoom.incrementAndGet();
            subscriptions.add(room);
        }

        public void init()
        {
            getChannel("/service/init").publish(new HashMap<>(), message -> initLatch.countDown());
        }

        public void waitForInit()
        {
            try
            {
                initLatch.await();
            }
            catch (InterruptedException x)
            {
                Thread.currentThread().interrupt();
                throw new RuntimeException(x);
            }
        }

        public void destroy()
        {
            for (Integer room : subscriptions)
            {
                AtomicInteger clientsPerRoom = roomMap.get(room);
                clientsPerRoom.decrementAndGet();
            }
            subscriptions.clear();
        }

        public void begin() throws InterruptedException
        {
            notifyServer("/service/statistics/start");
        }

        public void end() throws InterruptedException
        {
            notifyServer("/service/statistics/stop");
        }

        public void exit() throws InterruptedException
        {
            notifyServer("/service/statistics/exit");
        }

        private void notifyServer(String channelName) throws InterruptedException
        {
            CountDownLatch latch = new CountDownLatch(1);
            ClientSessionChannel channel = getChannel(channelName);
            channel.publish(new HashMap<String, Object>(1), message -> latch.countDown());
            latch.await();
        }

        private void recordSentMessages(List<? extends Message> messages)
        {
            long now = NanoTime.now();
            for (Message message : messages)
            {
                Map<String, Object> data = message.getDataAsMap();
                if (data != null && message.getChannelId().isBroadcast())
                {
                    int room = (Integer)data.get("room");
                    int clientsInRoom = roomMap.get(room).get();
                    String id = (String)data.get(Config.ID_FIELD);
                    sendTimes.put(id, new AtomicStampedReference<>(now, clientsInRoom));
                    arrivalTimes.put(id, new AtomicStampedReference<>(new BlockingArrayQueue<>(), clientsInRoom));
                }
            }
        }

        private void recordReceivedMessages(List<Message.Mutable> messages)
        {
            long now = NanoTime.now();
            boolean response = false;
            for (Message message : messages)
            {
                Map<String, Object> data = message.getDataAsMap();
                if (data != null)
                {
                    response = true;
                    String id = (String)data.get(Config.ID_FIELD);
                    arrivalTimes.get(id).getReference().add(now);
                }
            }
            if (response)
            {
                responses.incrementAndGet();
            }
        }
    }

    private ChannelId getChannelId(String channelName)
    {
        ChannelId result = channelIds.get(channelName);
        if (result == null)
        {
            result = new ChannelId(channelName);
            ChannelId existing = channelIds.putIfAbsent(channelName, result);
            if (existing != null)
            {
                result = existing;
            }
        }
        return result;
    }

    private enum ClientTransportType
    {
        LONG_POLLING("long-polling"), JAKARTA_WEBSOCKET("jakarta-websocket"), JETTY_WEBSOCKET("jetty-websocket");

        private final String name;

        private ClientTransportType(String name)
        {
            this.name = name;
        }

        public String getName()
        {
            return name;
        }
    }

    private static class Measure extends HashMap<String, Object>
    {
        public Measure(Object value, String unit)
        {
            super(2);
            put("value", value);
            put("unit", unit);
        }
    }

    private static class DynamicConnectionStatistics implements Connection.Listener
    {
        private final Set<Connection> _connections = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private final LongAdder _rcvdBytes = new LongAdder();
        private final LongAdder _sentBytes = new LongAdder();
        private Data _lastData = new Data(0, 0);

        @Override
        public void onOpened(Connection connection)
        {
            _connections.add(connection);
        }

        @Override
        public void onClosed(Connection connection)
        {
            _connections.remove(connection);
            collect(connection);
        }

        public void reset()
        {
            _lastData = new Data(_rcvdBytes.sumThenReset(), _sentBytes.sumThenReset());
        }

        public Data collect()
        {
            _connections.forEach(this::collect);
            return new Data(_rcvdBytes.longValue() - _lastData.receivedBytes,
                _sentBytes.longValue() - _lastData.sentBytes);
        }

        private void collect(Connection connection)
        {
            long bytesIn = connection.getBytesIn();
            if (bytesIn > 0)
            {
                _rcvdBytes.add(bytesIn);
            }
            long bytesOut = connection.getBytesOut();
            if (bytesOut > 0)
            {
                _sentBytes.add(bytesOut);
            }
        }

        public static class Data
        {
            public final long receivedBytes;
            public final long sentBytes;

            private Data(long receivedBytes, long sentBytes)
            {
                this.receivedBytes = receivedBytes;
                this.sentBytes = sentBytes;
            }
        }
    }
}
