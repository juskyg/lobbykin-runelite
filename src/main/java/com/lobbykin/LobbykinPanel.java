package com.lobbykin;

import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.util.ImageUtil;

/** One focused current party; future sessions and settings stay in disclosures. */
final class LobbykinPanel extends PluginPanel {
    private static final Color ORANGE=new Color(255,146,87),TEAL=new Color(136,212,160),YELLOW=new Color(255,210,140),SLATE=new Color(28,35,48),MUTED=new Color(173,183,197),BACKGROUND=new Color(17,21,28),LINE=new Color(48,59,74);
    private static final Font UI_FONT=new Font(Font.SANS_SERIF,Font.PLAIN,13);
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault());
    private final JLabel player=label("Not logged in"),world=label("World: —");
    private final JTextArea connection=note("Connect to see your party."),trackingStatus=note("Raid tracking: off.");
    private final JPasswordField accessCode=new JPasswordField();
    private final JButton refresh=new JButton("Refresh sessions"),disconnect=new JButton("Disconnect"),browserConnect=new JButton("Connect with Lobbykin");
    private final JPanel content=stack(),sessions=stack();
    private final JLabel freshness=label("Not connected");
    private final Map<String,Icon> activityIcons=new HashMap<>();
    private final Consumer<String> openSession;
    private Runnable openWebsite=()->{};
    private final BiConsumer<String,Boolean> readyAction;
    private BiConsumer<String,Boolean> offerAction=(id,confirm)->{};
    private Consumer<SessionSnapshot.Session> trackAction=session->{};
    private IntConsumer hopAction=id->{};
    private Runnable browserAction=()->{},browserCancelAction=()->{};
    private boolean pairingPending;
    private final List<JButton> actionButtons=new ArrayList<>();
    private final Map<JButton,Instant> offers=new HashMap<>();
    private final Set<String> expanded=new HashSet<>();
    private final javax.swing.Timer deadlineTimer=new javax.swing.Timer(1000,e->updateDeadlines());
    private String trackedGroup,selectedActiveId,localName;
    private int localWorld;
    private Instant lastRefresh;
    private SessionSnapshot data;
    private boolean loading,trackingActive;
    private String detectorGroup,detectorText="Detector: awaiting client state",uploadGroup,startDelivery="Start upload: not sent",lastDelivery="Last upload: none";
    private SessionSnapshot.Session pinnedSession;

    LobbykinPanel(Runnable openWebsite){this(openWebsite,()->{},token->{},()->{},()->{},id->{},(id,ready)->{});}
    LobbykinPanel(Runnable openWebsite,Runnable getCode,Consumer<String> connect,Runnable disconnectAction,Runnable refreshAction,Consumer<String> openSession,BiConsumer<String,Boolean> readyAction){
        this.openSession=openSession;this.readyAction=readyAction;this.openWebsite=openWebsite;
        setLayout(new BorderLayout());setBackground(BACKGROUND);setBorder(BorderFactory.createEmptyBorder(10,8,12,8));
        JPanel brand=new JPanel(new BorderLayout(8,0));brand.setOpaque(false);
        JLabel logo=new JLabel(new ImageIcon(ImageUtil.loadImageResource(getClass(),"brand-imp.png").getScaledInstance(28,28,Image.SCALE_SMOOTH)));logo.getAccessibleContext().setAccessibleName("Lobbykin logo");
        JLabel title=label("Lobbykin");title.setForeground(new Color(244,242,237));title.setFont(title.getFont().deriveFont(Font.BOLD,20f));brand.add(logo,BorderLayout.WEST);brand.add(title,BorderLayout.CENTER);
        JButton website=button("↗",false,openWebsite);website.setToolTipText("Open Lobbykin in browser");website.getAccessibleContext().setAccessibleName("Open Lobbykin in browser");website.setPreferredSize(new Dimension(26,28));brand.add(website,BorderLayout.EAST);content.add(brand);
        JPanel identity=new JPanel(new BorderLayout(4,0));identity.setOpaque(false);player.setFont(player.getFont().deriveFont(Font.BOLD,12f));identity.add(player,BorderLayout.CENTER);world.setForeground(MUTED);world.setFont(world.getFont().deriveFont(12f));identity.add(world,BorderLayout.EAST);content.add(identity);
        connection.setForeground(MUTED);connection.setFont(connection.getFont().deriveFont(13f));content.add(connection);
        browserConnect.addActionListener(e->{if(pairingPending){browserCancelAction.run();pairingFailed("Connection canceled.");}else browserAction.run();});style(browserConnect,true);content.add(browserConnect);
        content.add(sessions);
        JPanel account=stack();JPanel toolbar=new JPanel(new GridLayout(1,1,5,0));toolbar.setOpaque(false);refresh.addActionListener(e->refreshAction.run());disconnect.addActionListener(e->disconnectAction.run());style(disconnect,false);toolbar.add(disconnect);account.add(toolbar);refresh.setEnabled(false);disconnect.setEnabled(false);
        JPanel manual=stack();JLabel label=label("Lobbykin access code");label.setLabelFor(accessCode);manual.add(label);manual.add(accessCode);
        Runnable submit=()->{char[] chars=accessCode.getPassword();try{connect.accept(new String(chars).trim());}finally{Arrays.fill(chars,'\0');accessCode.setText("");}};
        accessCode.setFont(UI_FONT);accessCode.addActionListener(e->submit.run());manual.add(button("Connect account",false,submit));manual.add(button("Get access code ↗",false,getCode));
        account.add(disclosure("manual","Manual connection",manual));account.add(note("Permissions and connected devices are managed on Lobbykin. Access stays in memory; browser approval is needed after restarting."));account.add(button("Manage access ↗",false,getCode));
        freshness.setForeground(MUTED);freshness.setFont(freshness.getFont().deriveFont(12f));content.add(freshness);style(refresh,false);refresh.setText("↻ Refresh");refresh.setToolTipText("Refresh your party and scheduled sessions");content.add(refresh);content.add(disclosure("account","Account & settings",account));add(content,BorderLayout.NORTH);
        sessions.add(note("Your current party will appear here."));
    }
    void configureV6(BiConsumer<String,Boolean> offers,Consumer<SessionSnapshot.Session> tracking){offerAction=offers;trackAction=tracking;}
    void configureV7(Runnable connect,IntConsumer hop){browserAction=connect;hopAction=hop;}
    void showTrackingStatus(String group,String text){showTrackingStatus(group,text,false);}
    void showTrackingStatus(String group,String text,boolean active){
        boolean changed=!Objects.equals(trackedGroup,group)||!Objects.equals(trackingStatus.getText(),text)||trackingActive!=active;
        if(group!=null&&!Objects.equals(group,trackedGroup)&&!Objects.equals(group,uploadGroup)){startDelivery="Start upload: not sent";lastDelivery="Last upload: none";}
        trackedGroup=group;trackingActive=active;trackingStatus.setText(text);
        if(group==null&&!"Raid tracking: off.".equals(text)){connection.setText(text);connection.setForeground(YELLOW);}
        if(group==null)pinnedSession=null;
        else if(data!=null)for(SessionSnapshot.Session s:data.sessions)if(s.id.equals(group))pinnedSession=s;
        if(changed&&data!=null&&!loading)render();
    }
    void showDetectorStatus(String group,String text){if(!Objects.equals(detectorGroup,group)||!Objects.equals(detectorText,text)){detectorGroup=group;detectorText=text;if(data!=null&&!loading)render();}}
    void showReportStatus(String group,String event,String state){uploadGroup=group;String at=DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.now());lastDelivery="Upload: "+event+" · "+state+" · "+at;if("started".equals(event))startDelivery="Start upload: "+state+" · "+at;if("completed".equals(event)&&state.startsWith("ACK")&&Objects.equals(group,trackedGroup)&&trackingStatus.getText().startsWith("Completion signal recorded"))trackingStatus.setText("Raid completed · saved to Lobbykin.");if(data!=null&&!loading)render();}
    void showHopStatus(String message){connection.setText(message);}
    void configurePairCancel(Runnable cancel){browserCancelAction=cancel;}
    void showPairing(String message){pairingPending=true;connection.setText(message);browserConnect.setText("Cancel connection");browserConnect.setEnabled(true);}
    void pairingFailed(String message){pairingPending=false;connection.setText(message);browserConnect.setText("Connect with Lobbykin");browserConnect.setEnabled(true);}
    void dispose(){deadlineTimer.stop();}
    void showPlayer(String name,int worldNumber){localName=name;localWorld=worldNumber;player.setText(name==null?"Not logged in":name);world.setText(name==null?"World: —":"World: "+worldNumber);if(data!=null&&!loading)render();}
    void showSessions(SessionSnapshot value,Instant fetchedAt){
        pairingPending=false;data=value;lastRefresh=fetchedAt;loading=false;connection.setForeground(TEAL);connection.setText("● Connected");freshness.setText("Updated "+TIME.format(fetchedAt));connection.setToolTipText("Updated "+TIME.format(fetchedAt));browserConnect.setVisible(false);refresh.setEnabled(true);disconnect.setEnabled(true);render();
    }
    static boolean playingNow(SessionSnapshot.Session s,Instant now){
        if(!"host".equals(s.my_status)&&!"accepted".equals(s.my_status))return false;
        if("started".equals(s.raid_phase))return true;
        if(s.start_at==null)return true;
        try{return !Instant.parse(s.start_at).isAfter(now);}catch(Exception error){return false;}
    }
    private void render(){
        int savedScroll=getScrollPane()==null?0:getScrollPane().getVerticalScrollBar().getValue();
        sessions.removeAll();actionButtons.clear();offers.clear();deadlineTimer.stop();Instant now=Instant.now();
        List<SessionSnapshot.Session> active=new ArrayList<>(),scheduled=new ArrayList<>(),applications=new ArrayList<>();
        List<SessionSnapshot.Session> displayed=new ArrayList<>(data.sessions);
        if(trackingActive&&pinnedSession!=null&&Objects.equals(trackedGroup,pinnedSession.id)&&displayed.stream().noneMatch(s->s.id.equals(trackedGroup)))displayed.add(pinnedSession);
        for(SessionSnapshot.Session session:displayed){if(playingNow(session,now))active.add(session);else if("host".equals(session.my_status)||"accepted".equals(session.my_status))scheduled.add(session);else applications.add(session);}
        active.sort(Comparator.comparing((SessionSnapshot.Session s)->!"started".equals(s.raid_phase)).thenComparing(s->!s.id.equals(trackedGroup)));
        scheduled.sort(Comparator.comparing(s->s.start_at==null?"":s.start_at));
        sessions.add(heading("PLAYING NOW"));
        if(active.isEmpty()){JPanel empty=surface(12);empty.add(bold("Your next adventure"));empty.add(note("No active party right now. Open Lobbykin to find your next group."));empty.add(button("Find a group ↗",true,()->openWebsite.run()));sessions.add(empty);}
        else{
            if(active.stream().noneMatch(s->s.id.equals(selectedActiveId)))selectedActiveId=active.get(0).id;
            if(active.size()>1){JComboBox<String> chooser=new JComboBox<>(active.stream().map(s->s.activity+" · "+s.title).toArray(String[]::new));chooser.setFont(UI_FONT);chooser.getAccessibleContext().setAccessibleName("Choose current party");for(int i=0;i<active.size();i++)if(active.get(i).id.equals(selectedActiveId))chooser.setSelectedIndex(i);chooser.addActionListener(e->{selectedActiveId=active.get(chooser.getSelectedIndex()).id;render();});sessions.add(chooser);}
            for(SessionSnapshot.Session s:active)if(s.id.equals(selectedActiveId))sessions.add(card(s,true));
        }
        JPanel future=stack();if(scheduled.isEmpty())future.add(note("No scheduled sessions."));else for(SessionSnapshot.Session s:scheduled)future.add(card(s,false));sessions.add(disclosure("scheduled","Scheduled ("+scheduled.size()+")",future));
        if(!applications.isEmpty()){
            JPanel pending=stack();for(SessionSnapshot.Session s:applications)pending.add(card(s,false));
            if(applications.stream().anyMatch(s->s.can_reply_offer))expanded.add("applications");sessions.add(disclosure("applications","Applications ("+applications.size()+")",pending));
        }
        if(!data.notifications.isEmpty()){JPanel updates=stack();for(SessionSnapshot.Update update:data.notifications.subList(0,Math.min(5,data.notifications.size()))){updates.add(bold(update.title));updates.add(note(update.body));}sessions.add(disclosure("updates","Updates ("+data.notifications.size()+")",updates));}
        updateDeadlines();deadlineTimer.start();if(loading)disableActions();revalidate();repaint();
        SwingUtilities.invokeLater(()->{if(getScrollPane()!=null)getScrollPane().getVerticalScrollBar().setValue(savedScroll);});
    }
    private JPanel card(SessionSnapshot.Session s,boolean current){
        JPanel card=surface(9);
        boolean pinned=data.sessions.stream().noneMatch(x->x.id.equals(s.id));
        JPanel activityRow=new JPanel(new BorderLayout(7,0));activityRow.setOpaque(false);
        Icon art=activityIcon(s.activity);if(art!=null){JLabel icon=new JLabel(art);icon.getAccessibleContext().setAccessibleName(activityName(s.activity));activityRow.add(icon,BorderLayout.WEST);}
        JTextArea name=bold(activityName(s.activity));name.setFont(name.getFont().deriveFont(Font.BOLD,14f));activityRow.add(name,BorderLayout.CENTER);card.add(activityRow);
        if(s.title!=null&&!s.title.equals(activityName(s.activity)))card.add(note(s.title));
        JLabel activity=label(value(s.activity)+("—".equals(value(s.session_type))?"":" · "+s.session_type));activity.setForeground(MUTED);activity.setFont(activity.getFont().deriveFont(13f));card.add(activity);
        if(current){JLabel phase=label(phaseLabel(s.raid_phase));phase.setForeground("started".equals(s.raid_phase)?TEAL:YELLOW);phase.setFont(phase.getFont().deriveFont(Font.BOLD,13f));card.add(phase);}
        else card.add(note((s.start_at==null?"Playing now":"Starts "+date(s.start_at))+" · "+value(s.my_status)));
        JPanel worldRow=new JPanel(new BorderLayout(5,0));worldRow.setOpaque(false);worldRow.add(label("World "+value(s.world)),BorderLayout.WEST);
        if(s.world!=null&&!pinned){JButton hop=button(localWorld==s.world&&localName!=null?"You are here":"Hop to "+s.world,false,()->hopAction.accept(s.world));hop.setEnabled(localName!=null&&localWorld!=s.world);if(localName!=null&&localWorld!=s.world)actionButtons.add(hop);worldRow.add(hop,BorderLayout.EAST);}card.add(worldRow);
        if(current&&s.tracking!=null){
            JPanel totals=new JPanel(new VerticalStackLayout(4));totals.setBackground(BACKGROUND);totals.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
            String[] labels={"Deaths","Failed / left","Completed"};String[] values={value(s.tracking.deaths),value(s.tracking.failed_runs),String.valueOf(s.tracking.completed_runs)};
            for(int i=0;i<labels.length;i++){JPanel metric=new JPanel(new BorderLayout(4,0));metric.setOpaque(false);JLabel label=label(labels[i]);label.setFont(UI_FONT.deriveFont(Font.BOLD,13f));metric.add(label,BorderLayout.WEST);JLabel number=label(values[i]);number.setFont(UI_FONT.deriveFont(Font.BOLD,14f));number.setForeground(i==2?TEAL:new Color(244,242,237));metric.add(number,BorderLayout.EAST);totals.add(metric);}card.add(totals);
            if(s.tracking.deaths==null||s.tracking.failed_runs==null)card.add(note("— = server total unavailable"));}
        if(s.members!=null&&!s.members.isEmpty()){
            JPanel roster=new JPanel(new VerticalStackLayout(0));roster.setOpaque(false);
            for(SessionSnapshot.Member member:s.members){JPanel row=new JPanel(new BorderLayout(5,0));row.setOpaque(false);row.setBorder(BorderFactory.createMatteBorder(0,0,1,0,LINE));row.setPreferredSize(new Dimension(170,28));
                JLabel nameLabel=label(member.name+(member.name.equalsIgnoreCase(localName==null?"":localName)?" · you":""));nameLabel.setFont(nameLabel.getFont().deriveFont(12f));nameLabel.setMinimumSize(new Dimension(0,24));nameLabel.setToolTipText(member.name+" · "+value(member.role));row.add(nameLabel,BorderLayout.CENTER);
                JLabel state=label(member.ready?"Ready":"Waiting");state.setForeground(member.ready?TEAL:YELLOW);state.setFont(state.getFont().deriveFont(Font.BOLD,13f));row.add(state,BorderLayout.EAST);roster.add(row);
            }
            if(current&&"started".equals(s.raid_phase))card.add(disclosure("roster:"+s.id,"Party members ("+s.members.size()+")",roster));
            else{card.add(heading("PARTY · "+value(s.ready_count)+" / "+value(s.team_size)+" READY"));card.add(roster);}
        }else if(s.ready_count!=null)card.add(note("Ready: "+s.ready_count+" / "+s.team_size+" · roster unavailable"));
        JPanel details=stack();
        if(!pinned&&s.can_set_ready&&s.my_ready!=null){boolean desired=!s.my_ready;JButton ready=button(desired?"Mark ready":"Mark not ready",desired,()->{disableActions();readyAction.accept(s.id,desired);});actionButtons.add(ready);if(current&&"started".equals(s.raid_phase))details.add(ready);else card.add(ready);}
        if(s.can_reply_offer&&s.offer_until!=null)try{Instant until=Instant.parse(s.offer_until);JButton accept=button("Accept place",true,()->{disableActions();offerAction.accept(s.id,true);}),decline=button("Decline offer",false,()->{disableActions();offerAction.accept(s.id,false);});accept.setEnabled(until.isAfter(Instant.now()));decline.setEnabled(until.isAfter(Instant.now()));decline.putClientProperty("decline",true);offers.put(accept,until);offers.put(decline,until);actionButtons.add(accept);actionButtons.add(decline);card.add(accept);card.add(decline);}catch(Exception ignored){}
        if(current){
            boolean armed=s.id.equals(trackedGroup),wrongWorld=s.world!=null&&localName!=null&&localWorld!=s.world;
            JTextArea state=note(armed?trackingStatus.getText():s.can_track?"NOT TRACKING · select Track this session before entering the raid.":Arrays.asList("CoX","ToB","ToA").contains(s.activity)?"Raid tracking unavailable · enable Raid reports in Manage access.":"Raid tracking supports CoX, ToB and ToA.");
            state.setForeground(armed?TEAL:YELLOW);card.add(state);
            if(wrongWorld) {JTextArea warning=note("Wrong world: you are on "+localWorld+". This group uses "+s.world+". Hop first to track this session.");warning.setForeground(YELLOW);card.add(warning);}
            if(pinned)card.add(note("Session temporarily unavailable. Keeping this active attempt; server confirmation is required. Ready and Hop are disabled."));
            if(s.can_track||armed){JButton track=button(armed?"Stop tracking":"Track this session",!armed,()->trackAction.accept(s));track.setEnabled(armed||(localName!=null&&!wrongWorld&&!pinned));if(!armed)actionButtons.add(track);card.add(track);}
        }
        if(current&&(s.id.equals(uploadGroup)||s.id.equals(trackedGroup))){JPanel diagnostics=stack();if(s.id.equals(trackedGroup))diagnostics.add(note(detectorText));diagnostics.add(note(startDelivery));diagnostics.add(note(lastDelivery));details.add(disclosure("diagnostics:"+s.id,"Technical diagnostics",diagnostics));}
        if(s.tracking!=null){if(!s.tracking.runs.isEmpty()){SessionSnapshot.Run run=s.tracking.runs.get(0);card.add(note("Run #"+run.sequence+" · "+phaseLabel(run.status)+(run.duration_seconds==null?"":" · "+run.duration_seconds/60+":"+String.format("%02d",run.duration_seconds%60))));}}
        details.add(note("Role: "+value(s.my_role)+"\nVoice: "+value(s.voice)+"\nMeet: "+value(s.meeting_point)));
        
        if(s.tracking!=null){details.add(note("Failed / left counts ended attempts with a recorded raid exit. Deaths alone and network interruptions are not failures. Not official KC."));if(s.tracking.interrupted_runs!=null)details.add(note("Other interrupted attempts: "+s.tracking.interrupted_runs));for(SessionSnapshot.Run r:s.tracking.runs)if(r.report_count>0)details.add(note("Run #"+r.sequence+" · "+r.report_count+" completion report"+(r.report_count==1?"":"s")));for(SessionSnapshot.Participant p:s.tracking.participants)details.add(note(p.name+" · "+participantState(p)+" · deaths: "+p.deaths));details.add(note("Experimental game reports; not official KC or proof of every member's participation."));}
        card.add(disclosure("details:"+s.id,"Session details",details));if(current)card.add(button("Open lobby ↗","started".equals(s.raid_phase),()->openSession.accept(s.id)));else card.add(button("Open session ↗",false,()->openSession.accept(s.id)));return card;
    }
    static String participantState(SessionSnapshot.Participant p){
        if("completed".equals(p.state))return "Completed";
        if("active".equals(p.state))return "In progress";
        if("left_raid".equals(p.end_reason))return "Left raid";
        if("manual_stop".equals(p.end_reason))return "Tracking stopped";
        return "Tracking interrupted";
    }
    static String phaseLabel(String phase){if("started".equals(phase)||"active".equals(phase))return "RAID STARTED";if("completed".equals(phase))return "RAID COMPLETED";if("interrupted".equals(phase)||"tracking_interrupted".equals(phase))return "TRACKING INTERRUPTED";return "WAITING TO START";}
    void showDisconnected(String message){pairingPending=false;browserConnect.setText("Connect with Lobbykin");data=null;loading=false;accessCode.setText("");lastRefresh=null;freshness.setText("Not connected");connection.setForeground(MUTED);connection.setText(message);browserConnect.setVisible(true);browserConnect.setEnabled(true);refresh.setEnabled(false);disconnect.setEnabled(false);sessions.removeAll();actionButtons.clear();offers.clear();deadlineTimer.stop();sessions.add(note("Connect to see your current party and scheduled sessions."));expanded.clear();trackedGroup=null;trackingActive=false;pinnedSession=null;selectedActiveId=null;detectorGroup=null;uploadGroup=null;startDelivery="Start upload: not sent";lastDelivery="Last upload: none";revalidate();repaint();}
    void showError(String message){loading=true;disableActions();connection.setForeground(YELLOW);freshness.setText("Connection lost · refresh to retry");connection.setText("Not current · "+message+(lastRefresh==null?"":"\nLast update: "+TIME.format(lastRefresh)));refresh.setEnabled(true);disconnect.setEnabled(true);}
    void showLoading(){loading=true;disableActions();refresh.setEnabled(false);disconnect.setEnabled(true);}
    private void disableActions(){for(JButton b:actionButtons)b.setEnabled(false);}
    private void updateDeadlines(){for(Map.Entry<JButton,Instant> entry:offers.entrySet()){long seconds=entry.getValue().getEpochSecond()-Instant.now().getEpochSecond();if(seconds<=0){entry.getKey().setEnabled(false);entry.getKey().setText("Offer expired · refresh");}else if(!Boolean.TRUE.equals(entry.getKey().getClientProperty("decline")))entry.getKey().setText("Accept place ("+seconds+"s)");}}
    private JPanel disclosure(String key,String title,JPanel body){JPanel section=stack();JToggleButton toggle=new JToggleButton();toggle.setBackground(SLATE);toggle.setForeground(new Color(244,242,237));toggle.setBorder(BorderFactory.createEmptyBorder(9,8,9,8));toggle.setFont(UI_FONT.deriveFont(Font.BOLD,13f));boolean open=expanded.contains(key);toggle.setSelected(open);toggle.setText((open?"▾ ":"▸ ")+title);toggle.setHorizontalAlignment(SwingConstants.LEFT);toggle.setFocusPainted(true);body.setVisible(open);toggle.getAccessibleContext().setAccessibleDescription(open?"Expanded":"Collapsed");toggle.addActionListener(e->{boolean visible=toggle.isSelected();if(visible)expanded.add(key);else expanded.remove(key);body.setVisible(visible);toggle.setText((visible?"▾ ":"▸ ")+title);toggle.getAccessibleContext().setAccessibleDescription(visible?"Expanded":"Collapsed");revalidate();repaint();});section.add(toggle);section.add(body);return section;}
    private Icon activityIcon(String activity){
        if(activity==null)return null;if(activityIcons.containsKey(activity))return activityIcons.get(activity);
        String file=activity.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]","");java.net.URL url=getClass().getResource("activities/"+file+".png");
        Icon result=url==null?null:new ImageIcon(new ImageIcon(url).getImage().getScaledInstance(32,32,Image.SCALE_SMOOTH));activityIcons.put(activity,result);return result;
    }
    static String activityName(String activity){if(activity==null)return "Party session";switch(activity){case "CoX":return "Chambers of Xeric";case "ToB":return "Theatre of Blood";case "ToA":return "Tombs of Amascut";case "TFA":return "The Fractured Archive";case "Corp":return "Corporeal Beast";case "BA":return "Barbarian Assault";case "Royal Titans":return "Royal Titans";default:return activity;}}
    private static JPanel surface(int padding){JPanel p=new JPanel(new VerticalStackLayout(7)){@Override protected void paintComponent(Graphics g){Graphics2D g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g2.setColor(SLATE);g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);g2.dispose();super.paintComponent(g);}};p.setOpaque(false);p.setBorder(BorderFactory.createEmptyBorder(padding,padding,padding,padding));return p;}
    private static String date(String text){try{return TIME.format(Instant.parse(text));}catch(Exception e){return text;}}
    private static String value(Object v){return v==null||v.toString().isEmpty()?"—":v.toString();}
    private static JPanel stack(){JPanel p=new JPanel(new VerticalStackLayout(7));p.setOpaque(false);return p;}
    private static JLabel label(String text){JLabel l=new JLabel(text);l.setFont(UI_FONT);l.putClientProperty("html.disable",true);return l;}
    private static JTextArea bold(String text){JTextArea t=note(text);t.setFont(t.getFont().deriveFont(Font.BOLD,15f));return t;}
    private static JLabel heading(String text){JLabel l=label(text);l.setForeground(MUTED);l.setFont(l.getFont().deriveFont(Font.BOLD,12f));return l;}
    private static JTextArea note(String text){JTextArea t=new JTextArea(text);t.setColumns(20);t.setLineWrap(true);t.setWrapStyleWord(true);t.setEditable(false);t.setFocusable(false);t.setOpaque(false);t.setFont(UI_FONT);t.setForeground(new Color(244,242,237));return t;}
    private static JButton button(String text,boolean primary,Runnable action){JButton b=new JButton(text);style(b,primary);b.addActionListener(e->action.run());return b;}
    private static void style(JButton b,boolean primary){b.setFocusPainted(true);b.setMargin(new Insets(7,6,7,6));b.setFont(UI_FONT);b.setBackground(SLATE);b.setForeground(new Color(244,242,237));if(primary){b.setBackground(ORANGE);b.setForeground(new Color(16,21,28));b.setFont(b.getFont().deriveFont(Font.BOLD));}}
}
