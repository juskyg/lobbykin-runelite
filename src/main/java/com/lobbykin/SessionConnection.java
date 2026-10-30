package com.lobbykin;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import com.google.gson.JsonObject;
import okhttp3.Call;

/** Serial requests; stable report IDs survive transient failures in memory only. */
final class SessionConnection
{
    interface View {
        void connected(SessionSnapshot snapshot, Instant fetchedAt);
        void disconnected(String message);
        void error(String message);
        void loading();
        default void reportRejected(String group,String message) {}
        default void reportStatus(String group,String event,String state) {}
    }
    private final SessionClient client;
    private final View view;
    private String token;
    private long generation;
    private Call pending;
    private boolean busy;
    private SessionSnapshot snapshot;
    private final Deque<JsonObject> reports=new ArrayDeque<>();
    SessionConnection(SessionClient client,View view){this.client=client;this.view=view;}
    synchronized void connect(String candidate){
        disconnectInternal();
        if(!SessionClient.validToken(candidate)){view.disconnected("Paste the access code from lobbykin.com.");return;}
        token=candidate;view.disconnected("Connecting…");refresh();
    }
    synchronized void refresh(){
        if(token==null||busy)return;
        while(!reports.isEmpty()&&Instant.parse(reports.peek().get("observed_at").getAsString()).isBefore(Instant.now().minusSeconds(570)))reports.remove();
        request(reports.isEmpty()?"sessions":"event",reports.peek());
    }
    synchronized void setReady(String group,boolean ready){
        if(token==null||busy||snapshot==null||snapshot.sessions.stream().noneMatch(s->s.id.equals(group)&&s.can_set_ready))return;
        JsonObject body=new JsonObject();body.addProperty("group_id",group);body.addProperty("ready",ready);request("ready",body);
    }
    synchronized void offerReply(String group,boolean confirm){
        if(token==null||busy||snapshot==null||snapshot.sessions.stream().noneMatch(s->s.id.equals(group)&&s.can_reply_offer&&future(s.offer_until)))return;
        JsonObject body=new JsonObject();body.addProperty("group_id",group);body.addProperty("confirm",confirm);request("offer",body);
    }
    private static boolean future(String time){try{return Instant.parse(time).isAfter(Instant.now());}catch(Exception e){return false;}}
    synchronized void report(JsonObject body){
        if(token==null)return;
        boolean heartbeat="heartbeat".equals(body.get("event").getAsString());
        if(heartbeat&&(busy||!reports.isEmpty()))return;
        if(reports.size()>=64){view.error("Raid report queue is full. Stop tracking and reconnect.");return;}
        reports.add(body.deepCopy());view.reportStatus(body.get("group_id").getAsString(),body.get("event").getAsString(),"queued");if(!busy)refresh();
    }
    private void request(String action,JsonObject body){
        busy=true;long expected=generation;view.loading();
        SessionClient.Listener listener=new SessionClient.Listener(){
            public void success(SessionSnapshot data){synchronized(SessionConnection.this){
                if(expected!=generation)return;busy=false;pending=null;snapshot=data;
                if("event".equals(action)&&reports.peek()==body){reports.remove();view.reportStatus(body.get("group_id").getAsString(),body.get("event").getAsString(),"ACK · server confirmed");}
                view.connected(data,Instant.now());if(!reports.isEmpty())refresh();
            }}
            public void rejected(String message){synchronized(SessionConnection.this){
                if(expected!=generation)return;
                if("event".equals(action)){reports.removeIf(r->r.get("client_run").equals(body.get("client_run")));view.reportRejected(body.get("group_id").getAsString(),message);}
                busy=false;pending=null;snapshot=null;if("event".equals(action))view.reportStatus(body.get("group_id").getAsString(),body.get("event").getAsString(),"rejected · server denied");view.error(message);
            }}
            public void failure(boolean unauthorized,String message){synchronized(SessionConnection.this){
                if(expected!=generation)return;busy=false;pending=null;snapshot=null;
                if("event".equals(action))view.reportStatus(body.get("group_id").getAsString(),body.get("event").getAsString(),unauthorized?"rejected · access revoked":"error · retry queued");
                if(unauthorized){disconnectInternal();view.disconnected(message);}else view.error(message);
            }}
        };
        if("ready".equals(action))pending=client.setReady(token,body.get("group_id").getAsString(),body.get("ready").getAsBoolean(),listener);
        else if("offer".equals(action))pending=client.offerReply(token,body.get("group_id").getAsString(),body.get("confirm").getAsBoolean(),listener);
        else if("event".equals(action))pending=client.report(token,body,listener);
        else pending=client.read(token,listener);
    }
    synchronized void disconnect(){disconnectInternal();view.disconnected("Website account: not linked.");}
    private void disconnectInternal(){generation++;token=null;snapshot=null;busy=false;reports.clear();if(pending!=null)pending.cancel();pending=null;}
}
