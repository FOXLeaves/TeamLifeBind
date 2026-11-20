package com.teamlifebind.clientui;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Interruptible UI-only frame transition. Its clock never drives game or menu actions. */
public final class AuiInkTransition {
    public static final int OPEN_MILLIS = 480, CONTENT_MILLIS = 420, CLOSE_MILLIS = 280;
    public enum Mode { IDLE, OPEN, CONTENT, CLOSE }
    public record Region(float left, float top, float right, float bottom) {
        public static final Region FULL = new Region(0, 0, 1, 1);
    }
    public record Frame(long serial, float progress, float axis, float direction, Mode mode, Region region) {
        public static final Frame IDLE = new Frame(0, 1, 0, 1, Mode.IDLE, Region.FULL);
        public boolean effect() { return mode == Mode.CLOSE || mode != Mode.IDLE && progress < 1; }
    }
    private static final AtomicLong IDS = new AtomicLong();
    private long serial, started, duration;
    private boolean waitingForPresentation;
    private float axis, direction = 1;
    private Mode mode = Mode.IDLE;
    private Region region = Region.FULL;
    private final LongSupplier clock;
    public AuiInkTransition() { this(System::nanoTime); }
    AuiInkTransition(LongSupplier clock) { this.clock = clock; }

    public void start(Mode mode, Region region, boolean horizontal, int direction, int milliseconds) {
        this.serial = IDS.incrementAndGet(); this.started = clock.getAsLong();
        this.duration = milliseconds * 1_000_000L; this.mode = mode; this.region = region;
        this.axis = horizontal ? 1 : 0; this.direction = direction < 0 ? -1 : 1;
        this.waitingForPresentation = false;
    }
    /** Capture the outgoing UI now, but spend no animation time on CSS/font loading. */
    public void startWhenReady(Mode mode, Region region, boolean horizontal, int direction, int milliseconds) {
        start(mode, region, horizontal, direction, milliseconds);
        waitingForPresentation = true;
    }
    public Frame frame(boolean presentationReady) {
        if (waitingForPresentation && presentationReady) {
            waitingForPresentation = false;
            started = clock.getAsLong();
        }
        return frame();
    }
    public Frame frame() {
        if (mode == Mode.IDLE) return Frame.IDLE;
        float progress = waitingForPresentation ? 0 : Math.clamp((clock.getAsLong() - started) / (float)Math.max(1, duration), 0, 1);
        if (progress >= 1 && mode != Mode.CLOSE) return Frame.IDLE;
        return new Frame(serial, progress, axis, direction, mode, region);
    }
    public boolean busy() { return frame().effect(); }
    public boolean closingDone() { return !waitingForPresentation && mode == Mode.CLOSE && clock.getAsLong() - started >= duration; }
    public void finish() { mode = Mode.IDLE; waitingForPresentation = false; }
}
