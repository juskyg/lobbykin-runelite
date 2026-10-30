package com.lobbykin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Poll deltas only; initial inbox history is displayed without notification spam. */
final class NotificationPlanner
{
    private final Set<String> seen=new LinkedHashSet<>(),reminded=new LinkedHashSet<>();
    private boolean initialized;
    synchronized List<String> accept(SessionSnapshot snapshot,Instant now)
    {
        List<String> alerts=new ArrayList<>();
        if(snapshot.capabilities!=null&&snapshot.capabilities.notifications_read)
        {
            for(SessionSnapshot.Update update:snapshot.notifications)
            {
                boolean fresh=seen.add(update.id);
                if(initialized&&fresh&&update.read_at==null)alerts.add("Lobbykin: "+update.title);
            }
        }
        initialized=true;
        for(SessionSnapshot.Session session:snapshot.sessions)
        {
            if(!"host".equals(session.my_status)&&!"accepted".equals(session.my_status))continue;
            if(session.start_at==null)continue;
            try
            {
                Instant start=Instant.parse(session.start_at);long seconds=start.getEpochSecond()-now.getEpochSecond();
                if(seconds>0&&seconds<=900&&reminded.add(session.id+"|"+session.start_at))alerts.add("Lobbykin: "+session.title+" starts in "+((seconds+59)/60)+" min.");
            }
            catch(Exception ignored){ }
        }
        while(seen.size()>500)seen.remove(seen.iterator().next());
        while(reminded.size()>100)reminded.remove(reminded.iterator().next());
        return alerts;
    }
    synchronized void reset(){seen.clear();reminded.clear();initialized=false;}
}
