package com.lobbykin;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.Instant;
import java.util.*;
public class NotificationPlannerTest {
 @Test public void baselineDoesNotSpamAndNewUnreadUpdatesNotifyOnce(){
  NotificationPlanner planner=new NotificationPlanner();SessionSnapshot data=SessionSnapshot.parse(new com.google.gson.Gson(), "{\"account\":\"p\",\"scope\":\"sessions:read\",\"capabilities\":{\"notifications_read\":true},\"sessions\":[]}");
  SessionSnapshot.Update first=new SessionSnapshot.Update();first.id="old";first.title="Old";data.notifications=new ArrayList<>(Arrays.asList(first));assertTrue(planner.accept(data,Instant.now()).isEmpty());
  SessionSnapshot.Update next=new SessionSnapshot.Update();next.id="new";next.title="Accepted";data.notifications.add(next);assertEquals(1,planner.accept(data,Instant.now()).size());assertTrue(planner.accept(data,Instant.now()).isEmpty());planner.reset();assertTrue(planner.accept(data,Instant.now()).isEmpty());
 }
 @Test public void reminderOnlyTargetsConfirmedFutureSessionsOncePerSchedule(){
  NotificationPlanner planner=new NotificationPlanner();SessionSnapshot data=SessionSnapshot.parse(new com.google.gson.Gson(), "{\"account\":\"p\",\"scope\":\"sessions:read\",\"sessions\":[{\"id\":\"11111111-1111-4111-8111-111111111111\",\"title\":\"Raid\",\"my_status\":\"pending\",\"start_at\":\"2026-10-28T12:10:00Z\"}]}");Instant now=Instant.parse("2026-10-28T12:00:00Z");assertTrue(planner.accept(data,now).isEmpty());data.sessions.get(0).my_status="accepted";assertEquals(1,planner.accept(data,now).size());assertTrue(planner.accept(data,now).isEmpty());data.sessions.get(0).start_at="2026-10-28T12:12:00Z";assertEquals(1,planner.accept(data,now).size());assertTrue(planner.accept(data,now.plusSeconds(900)).isEmpty());
 }
}
