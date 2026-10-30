package com.lobbykin;

/** Numeric client state + resolved instance-template regions. Never drives game input. */
final class RaidPresence
{
    private RaidPresence() {}
    // ToA Plugin Hub RaidRoom / RaidStateTracker and RuneLite Discord area mapping.
    // 13454 is the public lobby, NOT a raid. 14672 is the reward tomb (still inside).
    static boolean inside(String raid, boolean instance, int party, int coxDungeon, int coxState, int region)
    {
        if ("CoX".equals(raid)) return coxDungeon == 1 && coxState > 0;
        if ("ToA".equals(raid)) return toaRoom(region);
        return "ToB".equals(raid) && instance && (party == 2 || party == 3);
    }
    static boolean toaStartRoom(int region){return region!=14672&&toaRoom(region);}
    static boolean toaRoom(int region)
    {
        switch(region){
            case 14160: case 15698: case 15700: case 14162: case 14164:
            case 15186: case 15188: case 14674: case 14676: case 15184:
            case 15696: case 14672: return true;
            default:return false;
        }
    }
}
