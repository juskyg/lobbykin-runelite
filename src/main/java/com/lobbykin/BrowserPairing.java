package com.lobbykin;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import okhttp3.*;

/** Browser approval with a private proof. Raw credentials never enter a URL or browser. */
final class BrowserPairing {
 private final OkHttpClient http;private final ScheduledExecutorService executor;
 private final Consumer<String> open,status,connected,failure;private final String endpoint;
 private Call pending;private ScheduledFuture<?> timer;private long generation;private String id,verifier,token;private Instant expires;
 BrowserPairing(OkHttpClient http,ScheduledExecutorService executor,Consumer<String> open,Consumer<String> status,Consumer<String> connected,Consumer<String> failure){this(http,executor,open,status,connected,failure,"https://lobbykin.com/api/runelite/pair/");}
 BrowserPairing(OkHttpClient http,ScheduledExecutorService executor,Consumer<String> open,Consumer<String> status,Consumer<String> connected,Consumer<String> failure,String endpoint){this.http=http.newBuilder().followRedirects(false).followSslRedirects(false).callTimeout(10,TimeUnit.SECONDS).build();this.executor=executor;this.open=open;this.status=status;this.connected=connected;this.failure=failure;this.endpoint=endpoint;}
 synchronized void start(){
  cancel();long expected=generation;id=UUID.randomUUID().toString();verifier=random();token="lk_rl_"+random();expires=Instant.now().plusSeconds(300);status.accept("Opening secure Lobbykin connection…");
  JsonObject body=new JsonObject();body.addProperty("id",id);body.addProperty("challenge",hash(verifier));body.addProperty("token_hash",hash(token));
  send("start",body,expected,result->{
   if(!id.equals(result.get("id").getAsString()))throw new IllegalArgumentException();String name=result.get("name").getAsString();if(!name.matches("[A-Za-z ]{3,40}"))throw new IllegalArgumentException();
   status.accept("Approve "+name+" in your browser. Nothing to copy.");open.accept("https://lobbykin.com/runelite.html#pair="+id);
   timer=executor.scheduleWithFixedDelay(()->poll(expected),5,5,TimeUnit.SECONDS);
  },false);
 }
 private synchronized void poll(long expected){
  if(expected!=generation||pending!=null)return;
  if(Instant.now().isAfter(expires)){fail("Connection request expired. Click Connect with Lobbykin again.");return;}
  JsonObject body=new JsonObject();body.addProperty("id",id);body.addProperty("verifier",verifier);
  send("poll",body,expected,result->{
   String state=result.get("status").getAsString();
   if("approved".equals(state)){String credential=token;cancel();connected.accept(credential);}
   else if(!"pending".equals(state))fail("Connection request expired or was revoked. Please try again.");
  },true);
 }
 private void send(String action,JsonObject body,long expected,Consumer<JsonObject> success,boolean retry){
  Request request=new Request.Builder().url(endpoint+action).header("Content-Type","application/json").header("Cache-Control","no-store").header("User-Agent","Lobbykin-RuneLite/0.9.4-rc1").post(RequestBody.create(MediaType.parse("application/json"),body.toString())).build();
  pending=http.newCall(request);pending.enqueue(new Callback(){
   public void onFailure(Call call,IOException error){synchronized(BrowserPairing.this){if(expected!=generation)return;pending=null;if(!retry)fail("Could not connect to Lobbykin. Check your connection and try again.");}}
   public void onResponse(Call call,Response response){try(Response result=response){
    StringBuilder text=new StringBuilder();if(!result.isSuccessful()||result.body()==null||result.body().contentLength()>4096)throw new IOException();
    try(Reader reader=result.body().charStream()){char[] buffer=new char[1024];int count;while((count=reader.read(buffer))!=-1){if(text.length()+count>4096)throw new IOException();text.append(buffer,0,count);}}
    JsonObject data=new Gson().fromJson(text.toString(),JsonObject.class);
    synchronized(BrowserPairing.this){if(expected!=generation)return;pending=null;success.accept(data);}
   }catch(Exception error){synchronized(BrowserPairing.this){if(expected!=generation)return;pending=null;if(!retry||response.code()==400||response.code()==401)fail("Could not complete browser approval. Start the connection again.");}}}
  });
 }
 private void fail(String message){cancel();failure.accept(message);}
 synchronized void cancel(){generation++;if(pending!=null)pending.cancel();pending=null;if(timer!=null)timer.cancel(false);timer=null;id=null;verifier=null;token=null;expires=null;}
 static String hash(String value){try{return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static String random(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return hex(bytes);}
 private static String hex(byte[] bytes){StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format("%02x",b&255));return value.toString();}
}
