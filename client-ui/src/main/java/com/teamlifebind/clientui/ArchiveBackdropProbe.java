package com.teamlifebind.clientui;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Actual GPU atmosphere coverage: time changes the image; reduced motion freezes it. */
final class ArchiveBackdropProbe {
    private final Path output;
    private final List<String> results;
    private int phase,ticks;
    private long presented;
    private int[] first,still;
    private float frozen;
    ArchiveBackdropProbe(Path output,List<String> results){this.output=output;this.results=results;ArchiveBackdrop.clockForTest(0F);}
    boolean tick() throws Exception {
        if(++ticks>400)throw new IllegalStateException("Orbital background GPU probe timed out");
        var state=ArchiveBackdropRenderer.lastState();var target=ArchiveBackdropRenderer.lastTarget();
        if(state==null||target==null)return false;
        if(phase==0&&state.clock()==0){
            if(target.width>1280||target.height>720)throw new IllegalStateException("Atmosphere exceeds its resolution budget");
            first=AuiInkSelfTest.readback(target,output.resolve("backdrop-0.png"));
            if(Arrays.stream(first).anyMatch(pixel->pixel>>>24!=255))throw new IllegalStateException("Front-end atmosphere contains accidental transparency");
            long highlights=Arrays.stream(first).filter(pixel->(pixel>>16&255)+(pixel>>8&255)+(pixel&255)>120).count();
            if(highlights<100)throw new IllegalStateException("Orbital background is blank or too dark");
            ArchiveBackdrop.clockForTest(12F);phase=1;return false;
        }
        if(phase==1&&state.clock()==12){
            var changed=AuiInkSelfTest.readback(target,output.resolve("backdrop-12.png"));int differences=0;
            for(int i=0;i<first.length;i++)if(first[i]!=changed[i])differences++;
            if(differences<1000)throw new IllegalStateException("Background clock does not animate the actual shader");
            AuiMenuSettings.toggleMotion();frozen=ArchiveBackdrop.activeSeconds();ArchiveBackdrop.clockForTest(null);phase=2;return false;
        }
        if(phase==2&&state.clock()==frozen){
            still=AuiInkSelfTest.readback(target,output.resolve("backdrop-still-0.png"));presented=ArchiveBackdropRenderer.presentations();phase=3;return false;
        }
        if(phase==3&&ArchiveBackdropRenderer.presentations()>presented+5){
            var after=AuiInkSelfTest.readback(target,output.resolve("backdrop-still-1.png"));
            if(ArchiveBackdrop.activeSeconds()!=frozen||!Arrays.equals(still,after))throw new IllegalStateException("Reduced motion did not freeze the atmosphere");
            AuiMenuSettings.toggleMotion();
            results.add("PASS ORBITAL_BACKDROP actualGpu=animated reducedMotion=identical resolutionCap=1280x720 worldAttachment=none");return true;
        }
        return false;
    }
}
