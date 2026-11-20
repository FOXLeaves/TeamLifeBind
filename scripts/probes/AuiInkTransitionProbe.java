package com.teamlifebind.clientui;

import java.util.concurrent.atomic.AtomicLong;

/** A stalled font publication must not consume any part of the visible liquid wipe. */
public final class AuiInkTransitionProbe {
    public static void main(String[] args) {
        var clock=new AtomicLong();var ink=new AuiInkTransition(clock::get);
        ink.startWhenReady(AuiInkTransition.Mode.CONTENT,AuiInkTransition.Region.FULL,false,1,AuiInkTransition.CONTENT_MILLIS);
        long serial=ink.frame().serial();clock.set(5_000_000_000L);
        check(ink.busy()&&ink.frame(false).progress()==0,"CSS/font wait consumed the transition");
        check(ink.frame(true).progress()==0&&ink.frame().serial()==serial,"Ready changed the captured outgoing texture or skipped the first frame");
        clock.addAndGet(AuiInkTransition.CONTENT_MILLIS*500_000L);
        check(Math.abs(ink.frame(true).progress()-.5)<.001,"Visible content wipe no longer follows the original 420ms duration");
        clock.addAndGet(AuiInkTransition.CONTENT_MILLIS*500_000L);
        check(!ink.busy(),"Completed wipe remained active");
        ink.startWhenReady(AuiInkTransition.Mode.OPEN,AuiInkTransition.Region.FULL,false,1,AuiInkTransition.OPEN_MILLIS);
        clock.addAndGet(2_000_000_000L);ink.finish();
        check(!ink.frame(true).effect(),"Cancelled pending transition restarted after font publication");
        ink.start(AuiInkTransition.Mode.CONTENT,AuiInkTransition.Region.FULL,false,-1,AuiInkTransition.CLOSE_MILLIS);
        clock.addAndGet(140_000_000L);
        check(Math.abs(ink.frame().progress()-.5)<.001,"Closing did not keep the original reverse 280ms wipe");
        ink.startWhenReady(AuiInkTransition.Mode.CONTENT,AuiInkTransition.Region.FULL,true,-1,AuiInkTransition.CONTENT_MILLIS);
        check(ink.frame().serial()!=serial&&ink.frame().progress()==0,"Interrupted transition did not capture a new starting frame");
        System.out.println("AUI_VISIBLE_INK_TIMING_PASS delayed CSS/fonts, original 420ms contents, 280ms close, cancellation and interruption");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
