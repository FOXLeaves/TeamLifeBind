package com.teamlifebind.clientui;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.List;
import java.util.function.Consumer;

/** Checks real loading shader attachments, including the final fully transparent reveal. */
final class ArchiveLoadingProbe {
    private final Path directory;
    private final Consumer<Float> reveal;
    private final List<String> results;
    private int step=-1,waiting;
    ArchiveLoadingProbe(Path directory,Consumer<Float> reveal,List<String> results){this.directory=directory;this.reveal=reveal;this.results=results;}
    boolean tick() throws Exception {
        if(step<0){step=0;reveal.accept(0F);return false;}
        if(++waiting<5)return false;
        var target=ArchiveLoadingRenderer.lastTarget();
        if(target==null)throw new IllegalStateException("Loading GPU target was not rendered");
        int w=target.width,h=target.height,zero=0,opaque=0;
        int[] pixels=new int[w*h];var device=RenderSystem.getDevice();
        try(var buffer=device.createBuffer(()->"TLB loading alpha readback",GpuBuffer.USAGE_COPY_DST|GpuBuffer.USAGE_MAP_READ,(long)w*h*4)) {
            var encoder=device.createCommandEncoder();encoder.copyTextureToBuffer(target.getColorTexture(),buffer,0,()->{},0);
            try(var fence=encoder.createFence()){encoder.submit();if(!fence.awaitCompletion(5_000_000_000L))throw new IllegalStateException("Loading GPU readback timed out");}
            try(var map=buffer.map(true,false)){
                var bytes=map.data();
                for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                    int at=((h-y-1)*w+x)*4,r=Byte.toUnsignedInt(bytes.get(at)),g=Byte.toUnsignedInt(bytes.get(at+1)),b=Byte.toUnsignedInt(bytes.get(at+2)),a=Byte.toUnsignedInt(bytes.get(at+3));
                    if(r>a||g>a||b>a)throw new IllegalStateException("Loading shader broke premultiplied alpha");
                    if(a==0)zero++;if(a==255)opaque++;
                    pixels[y*w+x]=a==0?0:a<<24|Math.min(255,r*255/a)<<16|Math.min(255,g*255/a)<<8|Math.min(255,b*255/a);
                }
            }
        }
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,w,h,pixels,0,w);
        ImageIO.write(image,"PNG",directory.resolve("load-"+step+".png").toFile());
        if(step==0&&opaque!=w*h)throw new IllegalStateException("Initial loading cover incomplete");
        if(step==8&&(zero<w*h/5||opaque<w*h/5))throw new IllegalStateException("Loading reveal is not a spatial wipe");
        if(step==16&&zero!=w*h)throw new IllegalStateException("Loading cover did not become fully transparent");
        if(step==16){results.add("PASS LOADING_GPU 17 real frames; opaque start, spatial reveal, premultiplied alpha, transparent finish");reveal.accept(0F);return true;}
        step++;waiting=0;reveal.accept(step/16F);return false;
    }
}
