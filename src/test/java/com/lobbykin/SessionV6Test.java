package com.lobbykin;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.*;
import okhttp3.OkHttpClient;
public class SessionV6Test {
 private static final String TOKEN="lk_rl_"+"a".repeat(64),GROUP="11111111-1111-4111-8111-111111111111";
 @Test public void offersUseTheirOwnEndpointAndBooleanPayload()throws Exception{
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicReference<String> body=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);
  server.createContext("/offer",e->{body.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));byte[] data="{\"account\":\"P\",\"scope\":\"sessions:read\",\"sessions\":[]}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,data.length);e.getResponseBody().write(data);e.close();});server.start();
  try{new SessionClient(new OkHttpClient(),new com.google.gson.Gson(),"http://127.0.0.1:"+server.getAddress().getPort()+"/sessions").offerReply(TOKEN,GROUP,false,new SessionClient.Listener(){public void success(SessionSnapshot s){done.countDown();}public void failure(boolean u,String m){done.countDown();}});assertTrue(done.await(5,TimeUnit.SECONDS));JsonObject value=new JsonParser().parse(body.get()).getAsJsonObject();assertEquals(2,value.size());assertFalse(value.get("confirm").getAsBoolean());assertEquals(GROUP,value.get("group_id").getAsString());}finally{server.stop(0);}
 }
 @Test public void transientReportRetryRetainsTheSameRunIdAndPayload()throws Exception{
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);String json="{\"account\":\"P\",\"scope\":\"sessions:read\",\"sessions\":[]}";
  AtomicInteger attempts=new AtomicInteger();AtomicReference<String> first=new AtomicReference<>(),second=new AtomicReference<>();CountDownLatch connected=new CountDownLatch(1),failed=new CountDownLatch(1),confirmed=new CountDownLatch(1);
  server.createContext("/sessions",e->{byte[] data=json.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,data.length);e.getResponseBody().write(data);e.close();});
  server.createContext("/event",e->{String body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);int count=attempts.incrementAndGet();if(count==1)first.set(body);else second.set(body);byte[] data=(count==1?"{}":json).getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(count==1?503:200,data.length);e.getResponseBody().write(data);e.close();});server.start();
  SessionConnection connection=new SessionConnection(new SessionClient(new OkHttpClient(),new com.google.gson.Gson(),"http://127.0.0.1:"+server.getAddress().getPort()+"/sessions"),new SessionConnection.View(){public void connected(SessionSnapshot s,Instant t){if(attempts.get()==0)connected.countDown();else confirmed.countDown();}public void disconnected(String m){}public void error(String m){failed.countDown();}public void loading(){}});
  try{connection.connect(TOKEN);assertTrue(connected.await(5,TimeUnit.SECONDS));JsonObject event=new JsonObject();event.addProperty("group_id",GROUP);event.addProperty("observed_at",Instant.now().toString());event.addProperty("event","started");event.addProperty("client_run","22222222-2222-4222-8222-222222222222");connection.report(event);assertTrue(failed.await(5,TimeUnit.SECONDS));assertEquals(1,attempts.get());connection.refresh();assertTrue(confirmed.await(5,TimeUnit.SECONDS));assertEquals(first.get(),second.get());connection.refresh();}finally{connection.disconnect();server.stop(0);}
 }
}
