package com.lobbykin;
import org.junit.Test;import static org.junit.Assert.*;import com.google.gson.*;import com.sun.net.httpserver.HttpServer;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import okhttp3.OkHttpClient;
public class BrowserPairingTest {
 @Test public void browserApprovalNeverCopiesOrPlacesAccessCredentialsInUrl()throws Exception{
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor();AtomicReference<JsonObject> request=new AtomicReference<>();AtomicReference<String> url=new AtomicReference<>(),credential=new AtomicReference<>();CountDownLatch connected=new CountDownLatch(1);
  server.createContext("/start",e->{JsonObject data=new Gson().fromJson(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),JsonObject.class);request.set(data);byte[] out=("{\"id\":\""+data.get("id").getAsString()+"\",\"name\":\"Copper Imp\"}").getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,out.length);e.getResponseBody().write(out);e.close();});
  server.createContext("/poll",e->{JsonObject data=new Gson().fromJson(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),JsonObject.class);assertEquals(request.get().get("challenge").getAsString(),BrowserPairing.hash(data.get("verifier").getAsString()));byte[] out="{\"status\":\"approved\"}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,out.length);e.getResponseBody().write(out);e.close();});server.start();
  BrowserPairing pairing=new BrowserPairing(new OkHttpClient(),executor,url::set,s->{},token->{credential.set(token);connected.countDown();},s->connected.countDown(),"http://127.0.0.1:"+server.getAddress().getPort()+"/");
  try{pairing.start();assertTrue(connected.await(8,TimeUnit.SECONDS));assertNotNull(credential.get());assertEquals(request.get().get("token_hash").getAsString(),BrowserPairing.hash(credential.get()));assertEquals("https://lobbykin.com/runelite.html#pair="+request.get().get("id").getAsString(),url.get());assertFalse(url.get().contains(credential.get()));assertEquals(3,request.get().size());}finally{pairing.cancel();executor.shutdownNow();server.stop(0);}
 }
 @Test public void canceledStartCannotOpenBrowserOrConnectLate()throws Exception{
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),unexpected=new CountDownLatch(1);
  server.createContext("/start",e->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);byte[] out="{}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,out.length);e.getResponseBody().write(out);}catch(Exception ignored){}finally{e.close();}});server.start();
  BrowserPairing pairing=new BrowserPairing(new OkHttpClient(),executor,s->unexpected.countDown(),s->{},s->unexpected.countDown(),s->unexpected.countDown(),"http://127.0.0.1:"+server.getAddress().getPort()+"/");
  try{pairing.start();assertTrue(entered.await(5,TimeUnit.SECONDS));pairing.cancel();release.countDown();assertFalse(unexpected.await(300,TimeUnit.MILLISECONDS));}finally{pairing.cancel();release.countDown();executor.shutdownNow();server.stop(0);}
 }
}
