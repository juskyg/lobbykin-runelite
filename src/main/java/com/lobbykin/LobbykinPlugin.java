package com.lobbykin;

import javax.inject.Inject;
import com.google.gson.Gson;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;
import net.runelite.client.task.Schedule;
import okhttp3.OkHttpClient;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ActorDeath;
import net.runelite.client.game.WorldService;
import net.runelite.client.util.WorldUtil;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.ChatMessageType;
import net.runelite.client.Notifier;
import net.runelite.client.util.Text;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

@PluginDescriptor(
    name = "Lobbykin",
    description = "Lobbykin parties, Ready, reminders and raid start, death, exit and completion reports",
    tags = {"group", "party", "panel"}
)
public class LobbykinPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar toolbar;
    @Inject private OkHttpClient http;
    @Inject private Gson gson;
    @Inject private Notifier notifier;
    @Inject private WorldService worldService;
    @Inject private ScheduledExecutorService executor;
    private WorldHopController hopper;
    private BrowserPairing pairing;
    private volatile RaidTracker tracker;
    private NotificationPlanner notifications;
    private volatile SessionConnection connection;

    private volatile LobbykinPanel panel;
    private NavigationButton navigation;
    private String previousName;
    private int previousWorld = -1;

    @Override
    protected void startUp() throws Exception
    {
        notifications=new NotificationPlanner();
        hopper=new WorldHopController(client,text->{LobbykinPanel p=panel;if(p!=null)SwingUtilities.invokeLater(()->p.showHopStatus(text));});
        pairing=new BrowserPairing(http,gson,executor,url->LinkBrowser.browse(url),text->{LobbykinPanel p=panel;if(p!=null)SwingUtilities.invokeLater(()->p.showPairing(text));},token->{SessionConnection c=connection;if(c!=null)c.connect(token);},text->{LobbykinPanel p=panel;if(p!=null)SwingUtilities.invokeLater(()->p.pairingFailed(text));});
        tracker=new RaidTracker(report->{SessionConnection c=connection;if(c!=null)c.report(report);},text->{
            LobbykinPanel p=panel;RaidTracker t=tracker;
            if(p!=null)SwingUtilities.invokeLater(()->{if(panel==p)p.showTrackingStatus(t==null?null:t.selectedGroup(),text,t!=null&&t.activeRun());});
        });
        onEdt(() -> {
            panel = new LobbykinPanel(() -> LinkBrowser.browse("https://lobbykin.com/"),
                () -> LinkBrowser.browse("https://lobbykin.com/runelite.html"),
                token -> { if(pairing!=null)pairing.cancel();SessionConnection c = connection; if (c != null) c.connect(token); }, () -> { SessionConnection c = connection; if(pairing!=null)pairing.cancel();if (c != null) c.disconnect(); },
                () -> refreshSessions(), id -> LinkBrowser.browse("https://lobbykin.com/?lobby=" + id),
                (id, ready) -> { SessionConnection c = connection; if (c != null) c.setReady(id, ready); });
            panel.configureV6((id,confirm)->{SessionConnection c=connection;if(c!=null)c.offerReply(id,confirm);},session->clientThread.invokeLater(()->{
                RaidTracker t=tracker;if(t==null)return;
                if(session.id.equals(t.selectedGroup()))t.stop(Instant.now());else t.arm(session.id,session.activity,insideRaid(session.activity),session.world,client.getWorld());
                refreshSessions();
            }));
            panel.configurePairCancel(()->{if(pairing!=null)pairing.cancel();});
            panel.configureV7(()->{if(pairing!=null)pairing.start();},this::hopToWorld);
            LobbykinPanel current = panel;
            connection = new SessionConnection(new SessionClient(http,gson), new SessionConnection.View()
            {
                private void show(Consumer<LobbykinPanel> action)
                {
                    SwingUtilities.invokeLater(() -> { if (panel == current) action.accept(current); });
                }
                public void connected(SessionSnapshot snapshot, Instant fetchedAt) {
                    for(String alert:notifications.accept(snapshot,fetchedAt))notifier.notify(alert);
                    clientThread.invokeLater(()->{RaidTracker t=tracker;if(t!=null&&t.selectedGroup()!=null){
                        SessionSnapshot.Session selected=snapshot.sessions.stream().filter(s->s.id.equals(t.selectedGroup())).findFirst().orElse(null);
                        if((selected!=null&&!selected.can_track)||(selected==null&&!t.keepActiveOnMissing(Instant.now())))t.stop(Instant.now());
                    }});
                    show(p -> p.showSessions(snapshot, fetchedAt));
                }
                public void disconnected(String message) { notifications.reset();clientThread.invokeLater(()->{RaidTracker t=tracker;if(t!=null)t.stop(Instant.now());});show(p -> p.showDisconnected(message)); }
                public void reportStatus(String group,String event,String state) {show(p->p.showReportStatus(group,event,state));}
                public void reportRejected(String group,String message) {clientThread.invokeLater(()->{RaidTracker t=tracker;if(t!=null&&group.equals(t.selectedGroup()))t.stop(Instant.now());});}
                public void error(String message) { show(p -> p.showError(message)); }
                public void loading() { show(LobbykinPanel::showLoading); }
            });
            navigation = NavigationButton.builder()
                .tooltip("Lobbykin")
                .icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
                .priority(8)
                .panel(panel)
                .build();
            toolbar.addNavigation(navigation);
        });
        clientThread.invokeLater(this::refreshPlayer);
    }

    @Override
    protected void shutDown() throws Exception
    {
        if(pairing!=null)pairing.cancel();pairing=null;if(hopper!=null)hopper.cancel();
        RaidTracker tracking=tracker;if(tracking!=null)tracking.stop(Instant.now());tracker=null;
        SessionConnection active = connection;
        connection = null;
        if (active != null) active.disconnect();
        onEdt(() -> {
            if (navigation != null)
            {
                toolbar.removeNavigation(navigation);
            }
            if(panel!=null)panel.dispose();
            panel = null;
            navigation = null;
        });
        previousName = null;
        previousWorld = -1;
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        refreshPlayer();
        if(hopper!=null)hopper.tick();
        RaidTracker t=tracker;Player local=client.getLocalPlayer();
        if(t!=null&&t.selectedRaid()!=null&&client.getGameState()==GameState.LOGGED_IN&&local!=null){
            String raid=t.selectedRaid();boolean inside=insideRaid(raid);int region=templateRegion();
            String diagnostic="Detector: "+raid+" · party="+("CoX".equals(raid)?0:client.getVarbitValue("ToB".equals(raid)?6440:14345))+" · instance="+client.isInInstancedRegion()+" · region="+region+" · in raid="+inside;
            String group=t.selectedGroup();LobbykinPanel p=panel;if(p!=null)SwingUtilities.invokeLater(()->{if(panel==p)p.showDetectorStatus(group,diagnostic);});
            t.observe(inside,!"ToA".equals(raid)||RaidPresence.toaStartRoom(region),local.getName(),client.getWorld(),Instant.now());
        }
    }

    private boolean insideRaid(String raid){
        if(client.getGameState()!=GameState.LOGGED_IN)return false;
        return RaidPresence.inside(raid,client.isInInstancedRegion(),
            "CoX".equals(raid)?0:client.getVarbitValue("ToB".equals(raid)?6440:14345),
            client.getVarbitValue(5432),client.getVarbitValue(5425),templateRegion());
    }

    private int templateRegion(){
        Player p=client.getLocalPlayer();if(p==null||p.getLocalLocation()==null)return -1;
        try{return WorldPoint.fromLocalInstance(client,p.getLocalLocation()).getRegionID();}catch(RuntimeException unavailable){return -1;}
    }

    @Subscribe
    public void onChatMessage(ChatMessage event){
        if(event.getType()!=ChatMessageType.GAMEMESSAGE&&event.getType()!=ChatMessageType.FRIENDSCHATNOTIFICATION)return;
        String text=Text.removeTags(event.getMessage());if(hopper!=null)hopper.gameMessage(text);
        RaidTracker t=tracker;if(t!=null){Player local=client.getLocalPlayer();if(local!=null&&t.selectedRaid()!=null)t.context(local.getName(),client.getWorld(),"CoX".equals(t.selectedRaid())?client.getVarbitValue(5432)==1:insideRaid(t.selectedRaid()));t.gameMessage(text,Instant.now());}
    }

    @Subscribe
    public void onActorDeath(ActorDeath event){if(event.getActor()==client.getLocalPlayer()){RaidTracker t=tracker;if(t!=null)t.died(Instant.now(),"actor_death");}}

    private void hopToWorld(int id){
        executor.execute(()->{
            net.runelite.http.api.worlds.WorldResult worlds=worldService.getWorlds();
            net.runelite.http.api.worlds.World found=worlds==null?null:worlds.findWorld(id);
            clientThread.invokeLater(()->{
                if(hopper==null||panel==null)return;
                if(found==null){SwingUtilities.invokeLater(()->{if(panel!=null)panel.showHopStatus("World list unavailable. Refresh and try again.");});return;}
                net.runelite.api.World target=client.createWorld();target.setId(found.getId());target.setAddress(found.getAddress());target.setActivity(found.getActivity());target.setPlayerCount(found.getPlayers());target.setLocation(found.getRegion().getId());target.setTypes(WorldUtil.toWorldTypes(found.getTypes()));hopper.request(target);
            });
        });
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if(event.getGameState()==GameState.CONNECTION_LOST||event.getGameState()==GameState.HOPPING||event.getGameState()==GameState.LOGIN_SCREEN){RaidTracker t=tracker;if(t!=null)t.interrupt("connection_lost",Instant.now());}
        refreshPlayer();
    }

    @Schedule(period = 30, unit = ChronoUnit.SECONDS, asynchronous = true)
    public void refreshSessions()
    {
        RaidTracker t=tracker;if(t!=null)t.heartbeat(Instant.now());
        SessionConnection active = connection;
        if (active != null) active.refresh();
    }

    private void refreshPlayer()
    {
        LobbykinPanel current = panel;
        if (current == null)
        {
            return;
        }
        Player local = client.getGameState() == GameState.LOGGED_IN ? client.getLocalPlayer() : null;
        String name = local == null ? null : local.getName();
        int world = name == null ? 0 : client.getWorld();
        if (java.util.Objects.equals(name, previousName) && world == previousWorld)
        {
            return;
        }
        previousName = name;
        previousWorld = world;
        SwingUtilities.invokeLater(() -> {
            if (panel == current)
            {
                current.showPlayer(name, world);
            }
        });
    }

    // RuneLite may already be on the EDT when starting/stopping a plugin.
    static void onEdt(Runnable action) throws Exception
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            action.run();
        }
        else
        {
            SwingUtilities.invokeAndWait(action);
        }
    }
}
