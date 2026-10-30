package com.lobbykin;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Experimental observer. Never drives game input; only explicitly armed sessions. */
final class RaidTracker
{
    private static final Pattern PERSONAL_KC = Pattern.compile("^Your (?:completed |completion count for )(Theatre of Blood|Tombs of Amascut)(?::? (?:Entry|Story|Hard|Expert) Mode)? count is: [0-9,]+\\.?$");
    private static final Pattern TOTAL_TIME = Pattern.compile("^(Theatre of Blood|Tombs of Amascut)(?::? (?:Entry|Story|Hard|Expert) Mode)? total completion time: ([0-9]+(?::[0-9]{2}){1,2}(?:\\.[0-9]+)?).*");
    private static final Pattern COX_TIME = Pattern.compile(".*Duration: *([0-9]+(?::[0-9]{2}){1,2}(?:\\.[0-9]+)?).*", Pattern.CASE_INSENSITIVE);
    private final Consumer<JsonObject> reports;
    private final Consumer<String> status;
    private String group, raid, run, rsn;
    private Instant started,lastDeath;
    private String contextName;private int contextWorld;private boolean contextInside;
    private int world, leftTicks, outsideTicks;
    private Integer expectedWorld;
    private boolean waitForExit;

    RaidTracker(Consumer<JsonObject> reports, Consumer<String> status)
    {
        this.reports = reports;
        this.status = status;
    }

    synchronized boolean arm(String group, String raid, boolean alreadyInside)
    {
        if (!java.util.Arrays.asList("CoX", "ToB", "ToA").contains(raid)) return false;
        if (alreadyInside) { status.accept("Leave the current raid before arming a session."); return false; }
        stop(Instant.now());
        this.group=group; this.raid=raid; waitForExit=false;
        status.accept("Armed for " + raid + ". Enter the selected party's raid to start tracking.");
        return true;
    }

    synchronized boolean arm(String group,String raid,boolean alreadyInside,Integer expectedWorld,int currentWorld){
        if(expectedWorld!=null&&expectedWorld!=currentWorld){status.accept("Wrong world: hop to "+expectedWorld+" before tracking this session.");return false;}
        boolean armed=arm(group,raid,alreadyInside);if(armed)this.expectedWorld=expectedWorld;return armed;
    }
    synchronized boolean activeRun(){return run!=null;}
    synchronized boolean keepActiveOnMissing(Instant now){return run!=null&&started!=null&&now.isBefore(started.plusSeconds(21600));}
    synchronized void context(String name,int world,boolean inside){contextName=name;contextWorld=world;contextInside=inside;}
    synchronized String selectedGroup() { return group; }
    synchronized String selectedRaid() { return raid; }

    synchronized void observe(boolean inside,String player,int currentWorld,Instant now){observe(inside,inside,player,currentWorld,now);}
    synchronized void observe(boolean inside, boolean startEligible, String player, int currentWorld, Instant now)
    {
        context(player,currentWorld,inside);
        if (group == null) return;
        if (run != null && (!java.util.Objects.equals(player,rsn) || currentWorld != world))
        { interrupt("connection_lost",now); return; }
        if (run == null)
        {
            if (!inside) { if (++outsideTicks >= 5) waitForExit=false; return; }
            outsideTicks=0;
            if (waitForExit || player == null || !startEligible) return;
            if(expectedWorld!=null&&expectedWorld!=currentWorld){status.accept("Wrong world: hop to "+expectedWorld+" before entering the selected raid.");return;}
            start(now,"entry");
        }
        else if (inside) leftTicks=0;
        else if (++leftTicks >= 10) interrupt("left_raid",now);
    }

    synchronized void gameMessage(String text, Instant now)
    {
        if("CoX".equals(raid)&&text.equals("The raid has begun!")&&run==null&&!waitForExit&&contextInside&&contextName!=null){start(now,"game_message");return;}
        if(run==null)return;
        if(text.equals("Oh dear, you are dead!")||text.equals("You have died.")){died(now,"game_message");return;}
        if(text.equals("You have left the raid.")||text.equals("You leave the raid.")){interrupt("left_raid",now);return;}
        Integer duration=null;
        boolean completion=false;
        if ("CoX".equals(raid) && text.startsWith("Congratulations - your raid is complete!"))
        {
            completion=true;
            Matcher timer=COX_TIME.matcher(text);if(timer.matches())duration=parseDuration(timer.group(1));
        }
        else
        {
            Matcher total=TOTAL_TIME.matcher(text),kc=PERSONAL_KC.matcher(text);
            if (total.matches() && matchesRaid(total.group(1))) {completion=true;duration=parseDuration(total.group(2));}
            else if (kc.matches() && matchesRaid(kc.group(1))) completion=true;
        }
        if (!completion) return;
        String source=duration==null?"observed":"game_message";
        if(duration==null)duration=(int)Math.min(21600,Math.max(0,Duration.between(started,now).getSeconds()));
        reports.accept(report("completed","game_message",now,duration,source));
        run=null;waitForExit=true;outsideTicks=0;
        status.accept("Completion signal recorded · waiting for server confirmation. Session remains open.");
    }

    private void start(Instant now,String source){
        if(expectedWorld!=null&&expectedWorld!=contextWorld){status.accept("Wrong world: hop to "+expectedWorld+" before tracking this raid.");return;}
        run=UUID.randomUUID().toString();rsn=contextName;world=contextWorld;started=now;leftTicks=0;outsideTicks=0;lastDeath=null;
        reports.accept(report("started",source,now,null,"observed"));status.accept("Raid started · "+raid+("game_message".equals(source)?" · game message":" · entry observed"));
    }
    synchronized void died(Instant now,String source){
        if(run==null||(lastDeath!=null&&now.isBefore(lastDeath.plusSeconds(5))))return;
        lastDeath=now;reports.accept(report("died",source,now,null,"observed"));status.accept("Death recorded · the raid remains in progress.");
    }

    private boolean matchesRaid(String label)
    {
        return "ToB".equals(raid)&&label.equals("Theatre of Blood") || "ToA".equals(raid)&&label.equals("Tombs of Amascut");
    }

    synchronized void heartbeat(Instant now)
    {
        if(run!=null)reports.accept(report("heartbeat","heartbeat",now,null,"observed"));
    }

    synchronized void interrupt(String reason, Instant now)
    {
        if(run!=null)reports.accept(report("interrupted",reason,now,null,"observed"));
        run=null;waitForExit=true;outsideTicks=0;leftTicks=0;
        if(group!=null)status.accept("left_raid".equals(reason)?"Left raid · no completion recorded. Session remains open.":"Tracking interrupted · no completion inferred. Leave the raid before a new attempt.");
        if("connection_lost".equals(reason)){group=null;raid=null;status.accept("Tracking stopped after connection / character / world change. Leave the raid and select a session again.");}
    }

    synchronized void stop(Instant now)
    {
        if(run!=null)reports.accept(report("interrupted","manual_stop",now,null,"observed"));
        group=null;raid=null;run=null;expectedWorld=null;waitForExit=false;
        status.accept("Raid tracking: off.");
    }

    private JsonObject report(String event,String source,Instant now,Integer seconds,String durationSource)
    {
        JsonObject data=new JsonObject();data.addProperty("group_id",group);data.addProperty("client_run",run);
        data.addProperty("raid",raid);data.addProperty("event",event);data.addProperty("rsn",rsn);data.addProperty("world",world);
        data.addProperty("observed_at",now.toString());data.addProperty("duration_seconds",seconds);data.addProperty("duration_source",durationSource);data.addProperty("source",source);
        return data;
    }

    static Integer parseDuration(String value)
    {
        try
        {
            String[] parts=value.split(":");if(parts.length<2||parts.length>3)return null;
            double seconds=Double.parseDouble(parts[parts.length-1]);int minutes=Integer.parseInt(parts[parts.length-2]);
            if(seconds<0||seconds>=60||minutes<0||(parts.length==3&&minutes>=60))return null;
            double total=seconds+60*minutes+(parts.length==3?3600*Integer.parseInt(parts[0]):0);
            return total>=0&&total<=21600?(int)Math.round(total):null;
        }
        catch(NumberFormatException error){return null;}
    }
}
