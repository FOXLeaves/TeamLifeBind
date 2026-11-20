import com.teamlifebind.common.*;
import java.util.*;

public final class CampaignMenuPolicyProbe {
    public static void main(String[] args){
        for(var mode:PlayMode.values())for(var match:MatchMode.values()){
            var rules=new CampaignRules(match,3,DragonVictoryMode.LAST_HIT,false,false,mode,PveDifficulty.NORMAL);
            var visible=new HashSet<String>();for(String key:List.of("play","pve","mode","dragon","lives","objectives","border"))if(CampaignMenuPolicy.visible(rules,key))visible.add(key);
            var expected=new HashSet<String>(Set.of("play"));
            if(mode.encounter())expected.add("pve");if(mode==PlayMode.RG)expected.add("lives");
            if(mode==PlayMode.PVP){expected.addAll(List.of("mode","objectives","border"));expected.add(match==MatchMode.LIMITED_LIVES?"lives":"dragon");}
            if(!visible.equals(expected))throw new AssertionError(mode+" "+match+" exposes unrelated settings: "+visible);
        }
        System.out.println("CAMPAIGN_MENU_POLICY_PASS SUR/PVE/RG/PVP and all competitive submodes");
    }
}
