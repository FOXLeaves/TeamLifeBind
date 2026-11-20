package com.teamlifebind.common;

/** Options must affect the selected activity; hidden options never appear as active settings. */
public final class CampaignMenuPolicy {
    private CampaignMenuPolicy(){}
    public static boolean visible(CampaignRules rules,String action){
        return switch(action){
            case "play"->true;
            case "pve"->rules.playMode().encounter();
            case "lives","credits"->rules.playMode()==PlayMode.RG||rules.playMode()==PlayMode.PVP&&rules.matchMode()==MatchMode.LIMITED_LIVES;
            case "dragon"->rules.playMode()==PlayMode.PVP&&rules.matchMode()!=MatchMode.LIMITED_LIVES;
            case "mode","objectives","point","border"->rules.playMode()==PlayMode.PVP;
            default->false;
        };
    }
}
