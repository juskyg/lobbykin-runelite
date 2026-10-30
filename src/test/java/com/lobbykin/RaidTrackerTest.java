package com.lobbykin;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.Instant;
import java.util.*;
import com.google.gson.JsonObject;
public class RaidTrackerTest {
 private final String group="11111111-1111-4111-8111-111111111111";
 private final Instant now=Instant.parse("2026-10-28T12:00:00Z");
 @Test public void supportedSystemSignalsCountOnceAndNextAttemptGetsNewId(){
  String[] raids={"CoX","ToB","ToA"},messages={"Congratulations - your raid is complete! Duration: 28:42","Theatre of Blood total completion time: 24:40.20. Personal best: 20:00","Your completed Tombs of Amascut: Expert Mode count is: 45."};
  for(int i=0;i<raids.length;i++){
   List<JsonObject> events=new ArrayList<>();RaidTracker t=new RaidTracker(events::add,s->{});
   assertTrue(t.arm(group,raids[i],false));t.observe(true,"Player",349,now);
   t.gameMessage("Challenge complete: The Wardens. Duration: 2:00",now.plusSeconds(100));assertEquals(1,events.size());
   t.gameMessage(messages[i],now.plusSeconds(1700));t.gameMessage(messages[i],now.plusSeconds(1701));assertEquals(2,events.size());
   assertEquals("completed",events.get(1).get("event").getAsString());assertEquals(events.get(0).get("client_run"),events.get(1).get("client_run"));
   t.observe(true,"Player",349,now.plusSeconds(1702));assertEquals(2,events.size());for(int k=0;k<5;k++)t.observe(false,"Player",349,now.plusSeconds(1703+k));t.observe(true,"Player",349,now.plusSeconds(1800));assertEquals(3,events.size());assertNotEquals(events.get(0).get("client_run"),events.get(2).get("client_run"));
  }
 }
 @Test public void interruptionNeverMeansCompleteAndRequiresExitBeforeNextAttempt(){
  List<JsonObject> events=new ArrayList<>();RaidTracker t=new RaidTracker(events::add,s->{});assertFalse(t.arm(group,"ToA",true));assertFalse(t.arm(group,"Nex",false));assertTrue(t.arm(group,"CoX",false));
  t.gameMessage("Congratulations - your raid is complete!",now);assertTrue(events.isEmpty());
  t.observe(true,"Player",349,now);t.interrupt("connection_lost",now.plusSeconds(30));t.gameMessage("Congratulations - your raid is complete!",now.plusSeconds(31));assertEquals(2,events.size());assertEquals("interrupted",events.get(1).get("event").getAsString());
  t.observe(true,"Player",349,now.plusSeconds(32));assertEquals(2,events.size());t.stop(now);assertNull(t.selectedGroup());
 }
 @Test public void wrongRaidSignalAndShortRoomTransitionDoNotComplete(){
  List<JsonObject> events=new ArrayList<>();RaidTracker t=new RaidTracker(events::add,s->{});t.arm(group,"ToA",false);t.observe(true,"Player",349,now);
  t.gameMessage("Your completed Theatre of Blood count is: 73.",now.plusSeconds(10));for(int i=0;i<4;i++)t.observe(false,"Player",349,now.plusSeconds(20+i));assertEquals(1,events.size());t.observe(true,"Player",349,now.plusSeconds(24));t.observe(true,"Other",349,now.plusSeconds(25));assertEquals("interrupted",events.get(1).get("event").getAsString());
 }
 @Test public void coxSystemStartAndDeathKeepTheAttemptActive(){
  List<JsonObject> events=new ArrayList<>();RaidTracker t=new RaidTracker(events::add,s->{});t.arm(group,"CoX",false);t.context("Player",349,true);t.gameMessage("The raid has begun!",now);assertEquals("game_message",events.get(0).get("source").getAsString());
  t.died(now.plusSeconds(30),"actor_death");t.gameMessage("Oh dear, you are dead!",now.plusSeconds(31));assertEquals(2,events.size());assertEquals("died",events.get(1).get("event").getAsString());
  t.heartbeat(now.plusSeconds(40));assertEquals("heartbeat",events.get(2).get("event").getAsString());t.gameMessage("Congratulations - your raid is complete!",now.plusSeconds(1200));assertEquals("completed",events.get(3).get("event").getAsString());
 }
 @Test public void allModesFinishOnceDespiteMultipleSystemMessages(){
  for(String raid:Arrays.asList("ToA","ToB"))for(String mode:Arrays.asList("",": Entry Mode",": Hard Mode",": Expert Mode"," Entry Mode")){
   List<JsonObject> e=new ArrayList<>();RaidTracker t=new RaidTracker(e::add,s->{});t.arm(group,raid,false);t.observe(true,"Player",349,now);
   String name=raid.equals("ToA")?"Tombs of Amascut":"Theatre of Blood";
   t.gameMessage(name+mode+" total completion time: 25:30.60. Personal best: 24:00",now.plusSeconds(1500));
   t.gameMessage("Your completed "+name+mode+" count is: 1,234.",now.plusSeconds(1501));
   assertEquals(2,e.size());assertEquals("completed",e.get(1).get("event").getAsString());assertEquals(1531,e.get(1).get("duration_seconds").getAsInt());
  }
 }
 @Test public void localDeathsAndExitDoNotCreateCompletionOrRestartInside(){
  for(String raid:Arrays.asList("CoX","ToB","ToA")){
   List<JsonObject> e=new ArrayList<>();RaidTracker t=new RaidTracker(e::add,s->{});t.arm(group,raid,false);t.observe(true,"Player",349,now);
   t.died(now.plusSeconds(10),"actor_death");t.gameMessage("You have died.",now.plusSeconds(11));t.died(now.plusSeconds(20),"actor_death");
   assertEquals(3,e.size());t.heartbeat(now.plusSeconds(21));assertEquals("heartbeat",e.get(3).get("event").getAsString());
   t.gameMessage("You have left the raid.",now.plusSeconds(22));assertEquals("left_raid",e.get(4).get("source").getAsString());
   t.gameMessage("Congratulations - your raid is complete!",now.plusSeconds(23));t.observe(true,"Player",349,now.plusSeconds(24));assertEquals(5,e.size());
   for(int i=0;i<5;i++)t.observe(false,"Player",349,now.plusSeconds(25+i));t.observe(true,"Player",349,now.plusSeconds(30));assertEquals(6,e.size());assertNotEquals(e.get(0).get("client_run"),e.get(5).get("client_run"));
  }
 }
 @Test public void loadingGapIsNotAnExitButSustainedAbsenceIs(){
  List<JsonObject> e=new ArrayList<>();RaidTracker t=new RaidTracker(e::add,s->{});t.arm(group,"ToA",false);t.observe(true,"Player",349,now);
  for(int i=0;i<9;i++)t.observe(false,"Player",349,now.plusMillis(600*i));assertEquals(1,e.size());t.observe(true,"Player",349,now.plusSeconds(6));assertEquals(1,e.size());
  for(int i=0;i<10;i++)t.observe(false,"Player",349,now.plusSeconds(10).plusMillis(600*i));assertEquals(2,e.size());assertEquals("left_raid",e.get(1).get("source").getAsString());
 }
 @Test public void stoppingOrChangingWorldNeverCountsCompletion(){
  List<JsonObject> e=new ArrayList<>();RaidTracker t=new RaidTracker(e::add,s->{});t.arm(group,"ToB",false);t.observe(true,"Player",349,now);t.observe(true,"Player",350,now.plusSeconds(30));
  assertEquals("connection_lost",e.get(1).get("source").getAsString());assertNull(t.selectedGroup());t.observe(true,"Player",350,now.plusSeconds(31));assertEquals(2,e.size());
 }
 @Test public void wrongWorldCannotArmOrSendAStartButMatchingWorldCan(){
  List<JsonObject> e=new ArrayList<>();List<String> messages=new ArrayList<>();RaidTracker t=new RaidTracker(e::add,messages::add);
  assertFalse(t.arm(group,"ToA",false,360,461));assertNull(t.selectedGroup());assertTrue(messages.get(messages.size()-1).contains("360"));
  assertTrue(t.arm(group,"ToA",false,360,360));t.observe(true,"Player",461,now);assertTrue(e.isEmpty());t.observe(true,"Player",360,now.plusSeconds(1));assertEquals("started",e.get(0).get("event").getAsString());
 }
 @Test public void activeAttemptSurvivesMissingListingOnlyWithinBoundedWindow(){
  List<JsonObject> e=new ArrayList<>();RaidTracker t=new RaidTracker(e::add,s->{});t.arm(group,"ToA",false);assertFalse(t.keepActiveOnMissing(now));t.observe(true,"Player",360,now);assertTrue(t.activeRun());assertTrue(t.keepActiveOnMissing(now.plusSeconds(1200)));assertFalse(t.keepActiveOnMissing(now.plusSeconds(21600)));
  t.gameMessage("Tombs of Amascut total completion time: 20:00",now.plusSeconds(1200));assertFalse(t.activeRun());assertFalse(t.keepActiveOnMissing(now.plusSeconds(1201)));assertEquals("completed",e.get(1).get("event").getAsString());
 }
 @Test public void toaSoloTemplateRoomFlowStartsWithoutPartyTwoAndCompletesOnce(){
  List<JsonObject> e=new ArrayList<>();RaidTracker t=new RaidTracker(e::add,s->{});t.arm(group,"ToA",false,360,360);
  t.observe(RaidPresence.inside("ToA",true,1,0,0,13454),false,"Player",360,now);assertTrue(e.isEmpty());
  t.observe(RaidPresence.inside("ToA",true,1,0,0,14160),true,"Player",360,now.plusSeconds(1));assertEquals("started",e.get(0).get("event").getAsString());
  t.died(now.plusSeconds(20),"actor_death");for(int i=0;i<3;i++)t.observe(false,false,"Player",360,now.plusSeconds(21+i));t.observe(true,true,"Player",360,now.plusSeconds(24));assertEquals(2,e.size());
  t.observe(true,false,"Player",360,now.plusSeconds(1200));t.gameMessage("Your completed Tombs of Amascut: Entry Mode count is: 1.",now.plusSeconds(1201));assertEquals("completed",e.get(2).get("event").getAsString());
  t.gameMessage("Tombs of Amascut: Entry Mode total completion time: 20:00",now.plusSeconds(1202));assertEquals(3,e.size());
  t.stop(now.plusSeconds(1203));t.arm(group,"ToA",false);t.observe(true,false,"Player",360,now.plusSeconds(1204));assertEquals(3,e.size());
 }
 @Test public void parsesHoursAndDecimalsButRejectsInvalidDurations(){assertEquals(Integer.valueOf(1480),RaidTracker.parseDuration("24:40.20"));assertEquals(Integer.valueOf(3723),RaidTracker.parseDuration("1:02:03"));assertNull(RaidTracker.parseDuration("1:99"));assertNull(RaidTracker.parseDuration("broken"));assertNull(RaidTracker.parseDuration("7:00:00"));}
}
