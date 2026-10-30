package com.lobbykin;
import org.junit.Test;
import static org.junit.Assert.*;
public class RaidPresenceTest {
 @Test public void tobLobbyAndUnrelatedInstancesNeverStartTracking(){
  assertFalse(RaidPresence.inside("ToB",true,0,0,0,-1));assertFalse(RaidPresence.inside("ToB",true,1,0,0,-1));
  assertFalse(RaidPresence.inside("ToB",false,2,0,0,-1));assertFalse(RaidPresence.inside("ToB",true,9,0,0,-1));
 }
 @Test public void tobAndCoxKeepTheirOwnNumericStateRules(){
  assertTrue(RaidPresence.inside("ToB",true,2,0,0,-1));assertTrue(RaidPresence.inside("ToB",true,3,0,0,-1));
  assertFalse(RaidPresence.inside("CoX",true,0,1,0,-1));assertTrue(RaidPresence.inside("CoX",true,0,1,1,-1));assertFalse(RaidPresence.inside("CoX",true,0,0,1,-1));
 }
 @Test public void toaSoloEntryUsesTemplateRoomsRegardlessOfPartyOrHudState(){
  for(int state:new int[]{0,1,2,3})for(int room:new int[]{14160,15698,15700,14162,14164,15186,15188,14674,14676,15184,15696,14672})
    assertTrue("room="+room+" party="+state,RaidPresence.inside("ToA",true,state,0,0,room));
  assertFalse(RaidPresence.toaStartRoom(14672));assertTrue(RaidPresence.toaStartRoom(14160));
  // The ToA detector has no HUD prerequisite; non-instance coords are valid in the original map too.
  assertTrue(RaidPresence.inside("ToA",false,1,0,0,14160));
  assertFalse(RaidPresence.inside("ToA",true,2,0,0,13454));assertFalse(RaidPresence.inside("ToA",true,2,0,0,-1));assertFalse(RaidPresence.inside("ToA",true,2,0,0,12850));
 }
}
