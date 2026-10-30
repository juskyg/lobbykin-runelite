package com.lobbykin;
import java.time.Instant;
import java.util.Collection;
import java.util.function.Consumer;
import net.runelite.api.*;
import net.runelite.api.gameval.InterfaceID;
/** Each request represents one explicit click. Never bypasses the game's hop restrictions. */
final class WorldHopController {
 private final Client client;private final Consumer<String> status;private World target;private int attempts;private Instant requested;
 WorldHopController(Client client,Consumer<String> status){this.client=client;this.status=status;}
 static boolean safe(Collection<WorldType> types){return types!=null&&types.stream().noneMatch(t->java.util.Arrays.asList("PVP","HIGH_RISK","DEADMAN","BOUNTY","TOURNAMENT_WORLD","BETA_WORLD","NOSAVE_MODE","SEASONAL","PVP_ARENA").contains(t.name()));}
 void request(World world){
  if(client.getGameState()!=GameState.LOGGED_IN){status.accept("Log into the game before hopping.");return;}
  if(world.getId()==client.getWorld()){status.accept("You are already on World "+world.getId()+".");return;}
  if(!safe(world.getTypes())){status.accept("Special / dangerous world. Use the game's world switcher and review its warning.");return;}
  if(world.getPlayerCount()<0||world.getPlayerCount()>=2000){status.accept("This world is offline or full.");return;}
  if(client.isInInstancedRegion()){status.accept("Leave the instance before hopping to the party's world.");return;}
  target=world;attempts=0;requested=Instant.now();status.accept("Opening world switcher for World "+world.getId()+"…");tick();
 }
 void tick(){if(target==null)return;if(client.getGameState()!=GameState.LOGGED_IN||Instant.now().isAfter(requested.plusSeconds(10))){cancel();return;}if(client.getWidget(InterfaceID.Worldswitcher.BUTTONS)==null){if(attempts++>=3){cancel();status.accept("Could not open the world switcher. Finish your current action and try again.");return;}client.openWorldHopper();}else{World chosen=target;cancel();client.hopToWorld(chosen);status.accept("Hop requested · the game will confirm the world change.");}}
 void gameMessage(String text){if("Please finish what you're doing before using the World Switcher.".equals(text)){cancel();status.accept("Finish your current action before hopping.");}}
 void cancel(){target=null;attempts=0;}
}
