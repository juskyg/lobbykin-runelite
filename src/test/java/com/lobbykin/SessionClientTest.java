package com.lobbykin;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

public class SessionClientTest
{
    private static final String TOKEN = "lk_rl_" + "a".repeat(64);
    private static final String JSON = "{\"account\":\"GIM Jusky\",\"scope\":\"sessions:read\",\"sessions\":[]}";

    @Test
    public void narrowReadSendsBearerAndRejectsBroadCredentials() throws Exception
    {
        assertFalse(SessionClient.validToken("a.website.jwt"));
        assertFalse(SessionClient.validToken("https://example.com"));
        AtomicReference<String> auth = new AtomicReference<>();
        HttpServer server = server(200, JSON, auth);
        try
        {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<SessionSnapshot> snapshot = new AtomicReference<>();
            client(server).read(TOKEN, new SessionClient.Listener()
            {
                public void success(SessionSnapshot data) { snapshot.set(data); done.countDown(); }
                public void failure(boolean unauthorized, String message) { done.countDown(); }
            });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals("Bearer " + TOKEN, auth.get());
            assertEquals("GIM Jusky", snapshot.get().account);
            assertTrue(snapshot.get().sessions.isEmpty());
        }
        finally { server.stop(0); }
    }

    @Test
    public void revocationDisconnectsAndResponseCannotSupplyAnExternalLink() throws Exception
    {
        assertThrows(IllegalArgumentException.class, () -> SessionSnapshot.parse("{\"account\":\"a\",\"scope\":\"sessions:read\",\"sessions\":[{\"id\":\"https://evil.invalid\",\"title\":\"x\"}]}"));
        HttpServer server = server(401, "{}", new AtomicReference<>());
        try
        {
            CountDownLatch done = new CountDownLatch(1);
            AtomicBoolean unauthorized = new AtomicBoolean();
            client(server).read(TOKEN, new SessionClient.Listener()
            {
                public void success(SessionSnapshot data) { done.countDown(); }
                public void failure(boolean revoked, String message) { unauthorized.set(revoked); assertFalse(message.contains(TOKEN)); done.countDown(); }
            });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertTrue(unauthorized.get());
        }
        finally { server.stop(0); }
    }

    @Test
    public void disconnectCancelsPendingReplyAndCannotRestoreOldAccount() throws Exception
    {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sessions", exchange -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); byte[] body=JSON.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body); }
            catch (Exception ignored) { }
            finally { exchange.close(); }
        });
        server.start();
        AtomicInteger connected = new AtomicInteger();
        CountDownLatch unexpected = new CountDownLatch(1);
        SessionConnection connection = new SessionConnection(client(server), new SessionConnection.View()
        {
            public void connected(SessionSnapshot data, Instant fetchedAt) { connected.incrementAndGet(); unexpected.countDown(); }
            public void disconnected(String message) { }
            public void error(String message) { unexpected.countDown(); }
            public void loading() { }
        });
        try
        {
            connection.connect(TOKEN);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            connection.disconnect();
            release.countDown();
            assertFalse(unexpected.await(300, TimeUnit.MILLISECONDS));
            assertEquals(0, connected.get());
        }
        finally { connection.disconnect(); release.countDown(); server.stop(0); }
    }

    @Test
    public void oversizedResponsesFailWithoutEchoingCredentials() throws Exception
    {
        HttpServer server = server(200, "a".repeat(131073), new AtomicReference<>());
        try
        {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> error = new AtomicReference<>();
            client(server).read(TOKEN, new SessionClient.Listener()
            {
                public void success(SessionSnapshot data) { done.countDown(); }
                public void failure(boolean revoked, String message) { error.set(message); done.countDown(); }
            });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertNotNull(error.get());
            assertFalse(error.get().contains(TOKEN));
        }
        finally { server.stop(0); }
    }

    @Test
    public void readyWritesExplicitStateAndPublishesOnlyConfirmedReply() throws Exception
    {
        String group = "11111111-1111-4111-8111-111111111111";
        String snapshot = "{\"account\":\"Player\",\"scope\":\"sessions:read\",\"sessions\":[{\"id\":\"" + group + "\",\"title\":\"Session\",\"my_ready\":false,\"can_set_ready\":true}]}";
        CountDownLatch first = new CountDownLatch(1), confirmed = new CountDownLatch(1), entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger updates = new AtomicInteger(), writes = new AtomicInteger();
        AtomicReference<SessionSnapshot> latest = new AtomicReference<>();
        AtomicReference<String> sent = new AtomicReference<>();
        HttpServer server = server(200, snapshot, new AtomicReference<>());
        server.createContext("/ready", exchange -> {
            writes.incrementAndGet();
            sent.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); byte[] body=snapshot.replace("my_ready\":false", "my_ready\":true").getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body); }
            catch (Exception ignored) { }
            finally { exchange.close(); }
        });
        SessionConnection connection = new SessionConnection(client(server), new SessionConnection.View()
        {
            public void connected(SessionSnapshot data, Instant time) { latest.set(data);if(updates.incrementAndGet()==1)first.countDown();else confirmed.countDown(); }
            public void disconnected(String message) { }
            public void error(String message) { confirmed.countDown(); }
            public void loading() { }
        });
        try
        {
            connection.connect(TOKEN);
            assertTrue(first.await(5, TimeUnit.SECONDS));
            connection.setReady(group,true);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(Boolean.FALSE, latest.get().sessions.get(0).my_ready);
            connection.setReady(group,true);
            assertEquals(1,writes.get());
            com.google.gson.JsonObject body = new com.google.gson.Gson().fromJson(sent.get(),com.google.gson.JsonObject.class);
            assertEquals(2,body.size());
            assertEquals(group,body.get("group_id").getAsString());
            assertTrue(body.get("ready").getAsBoolean());
            release.countDown();
            assertTrue(confirmed.await(5,TimeUnit.SECONDS));
            assertEquals(Boolean.TRUE,latest.get().sessions.get(0).my_ready);
        }
        finally { release.countDown();connection.disconnect();server.stop(0); }
    }

    @Test public void eventQueueAndServerAckHaveIndependentCredentialFreeDiagnostics()throws Exception {
        java.util.List<String> states=new java.util.concurrent.CopyOnWriteArrayList<>();CountDownLatch connected=new CountDownLatch(1),ack=new CountDownLatch(1);
        HttpServer server=server(200,JSON,new AtomicReference<>());
        server.createContext("/event",exchange->{byte[] bytes=JSON.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        SessionConnection connection=new SessionConnection(client(server),new SessionConnection.View(){
            public void connected(SessionSnapshot data,Instant at){connected.countDown();}public void disconnected(String text){}public void error(String text){}public void loading(){}
            public void reportStatus(String group,String event,String state){states.add(event+":"+state);assertFalse(state.contains(TOKEN));if(state.startsWith("ACK"))ack.countDown();}
        });
        try{connection.connect(TOKEN);assertTrue(connected.await(5,TimeUnit.SECONDS));com.google.gson.JsonObject event=new com.google.gson.JsonObject();event.addProperty("group_id","11111111-1111-4111-8111-111111111111");event.addProperty("client_run","22222222-2222-4222-8222-222222222222");event.addProperty("event","started");event.addProperty("observed_at",Instant.now().toString());connection.report(event);assertTrue(ack.await(5,TimeUnit.SECONDS));assertEquals("started:queued",states.get(0));assertTrue(states.get(1).startsWith("started:ACK"));}finally{connection.disconnect();server.stop(0);}
    }

    private static SessionClient client(HttpServer server)
    {
        return new SessionClient(new OkHttpClient(), "http://127.0.0.1:" + server.getAddress().getPort() + "/sessions");
    }

    private static HttpServer server(int status, String body, AtomicReference<String> auth) throws Exception
    {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sessions", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }
}
