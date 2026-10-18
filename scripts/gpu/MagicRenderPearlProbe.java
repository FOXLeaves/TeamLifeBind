import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.joml.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.*;
import net.minecraft.world.phys.Vec3;
import com.teamlifebind.common.*;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.*;
import com.mojang.renderpearl.backend.opengl.GlBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.*;
import static org.lwjgl.sdl.SDLInit.*;

/** Actual production renderer, shader loader, transient UBOs, RenderPearl passes and GPU readback. */
public final class MagicRenderPearlProbe {
    static final int WIDTH=384, HEIGHT=216;
    static Class<?> production;
    static Constructor<?> sceneCtor;
    static Map<Long,Object> scenes;
    static GameRenderer renderer;
    static CameraRenderState camera;
    static TextureTarget target;
    static GpuDevice device;
    static int assertions;
    static void check(boolean value,String description){assertions++;if(!value)throw new AssertionError(description);}
    static void set(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    static Object field(String name)throws Exception{Field f=production.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    static void invoke(String name)throws Exception{production.getMethod(name).invoke(null);}
    static void insert(SpellDefinition spell,int phase,int mechanic,Vec3 cameraPosition,long received,float age)throws Exception{
        Vec3 origin=new Vec3(0,-1.65,-8).add(cameraPosition);
        Vec3 end=origin;
        if(mechanic==0||mechanic==1||mechanic==2||mechanic==5||mechanic==10||mechanic==16||mechanic==17){
            origin=new Vec3(-2,-1,-8).add(cameraPosition);end=new Vec3(2,0,-8).add(cameraPosition);
        }
        scenes.put(256L,sceneCtor.newInstance(256L,new UUID(1,2),spell,phase,mechanic,origin,end,2.4f,age,40,received));
        production.getMethod("projection",Matrix4f.class).invoke(null,camera.projectionMatrix);
    }
    static byte[] render(double depth)throws Exception{
        var encoder=device.createCommandEncoder();
        encoder.clearColorAndDepthTextures(target.getColorTexture(),new Vector4f(),target.getDepthTexture(),depth);encoder.submit();
        production.getMethod("render",GameRenderer.class).invoke(null,renderer);
        check(!(boolean)field("failed"),"Production shader renderer disabled itself after an exception");
        try(var readback=device.createBuffer(()->"magic regression readback",GpuBuffer.USAGE_COPY_DST|GpuBuffer.USAGE_MAP_READ,WIDTH*HEIGHT*4)){
            encoder=device.createCommandEncoder();encoder.copyTextureToBuffer(target.getColorTexture(),readback,0,()->{},0);
            try(var fence=encoder.createFence()){encoder.submit();check(fence.awaitCompletion(5_000_000_000L),"GPU readback timeout");}
            try(var mapping=readback.map(true,false)){byte[] bytes=new byte[WIDTH*HEIGHT*4];mapping.data().get(bytes);return bytes;}
        }
    }
    static long radiance()throws Exception{
        TextureTarget effect=(TextureTarget)field("effectTarget");int size=effect.width*effect.height*8;
        try(var readback=device.createBuffer(()->"magic HDR regression readback",GpuBuffer.USAGE_COPY_DST|GpuBuffer.USAGE_MAP_READ,size)){
            var encoder=device.createCommandEncoder();encoder.copyTextureToBuffer(effect.getColorTexture(),readback,0,()->{},0);
            try(var fence=encoder.createFence()){encoder.submit();check(fence.awaitCompletion(5_000_000_000L),"HDR GPU readback timeout");}
            try(var mapping=readback.map(true,false)){long value=0;var bytes=mapping.data();for(int i=0;i<size;i++)if(i%8<6)value+=Byte.toUnsignedInt(bytes.get(i));return value;}
        }
    }
    static long energy(byte[] bytes){long sum=0;for(int i=0;i<bytes.length;i++)if(i%4<3)sum+=Byte.toUnsignedInt(bytes[i]);return sum;}
    static void image(byte[] bytes,Path file)throws Exception{
        BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){
            int i=((HEIGHT-1-y)*WIDTH+x)*4;
            image.setRGB(x,y,Byte.toUnsignedInt(bytes[i])<<16|Byte.toUnsignedInt(bytes[i+1])<<8|Byte.toUnsignedInt(bytes[i+2]));
        }
        ImageIO.write(image,"png",file.toFile());
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]),out=Path.of(args[2]);String platform=args[1];
        Files.createDirectories(out);
        check(SDL_Init(SDL_INIT_VIDEO),"SDL initialization");RenderSystem.initRenderThread();
        Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)uf.get(null);
        if(platform.equals("neoforge")){
            // NeoForge's SharedConstants reads loader environment during static initialization.
            // Supply those two immutable environment fields; all MC/RenderPearl rendering code remains real.
            Class<?> fml=Class.forName("net.neoforged.fml.loading.FMLLoader");Object loader=unsafe.allocateInstance(fml);
            set(loader,"production",true);
            set(loader,"dist",Enum.valueOf((Class)Class.forName("net.neoforged.api.distmarker.Dist"),"CLIENT"));
            Field current=fml.getDeclaredField("current");current.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicReference<Object>)current.get(null)).set(loader);
        }
        GlBackend backend=new GlBackend();backend.loadLibrary();device=backend.createDevice(new GpuDebugOptions(3,true,true,true));
        try{
            RenderSystem.initRenderer(device);
            System.out.println("ACTUAL_PLATFORM="+platform+" DEVICE="+device.getDeviceInfo().name());
            Minecraft minecraft=(Minecraft)unsafe.allocateInstance(Minecraft.class);
            Field instance=Minecraft.class.getDeclaredField("instance");instance.setAccessible(true);instance.set(null,minecraft);
            set(minecraft,"resourceManager",new ReloadableResourceManager(PackType.CLIENT_RESOURCES){
                @Override public Optional<Resource> getResource(Identifier location){
                    Path path=root.resolve(platform+"/src/main/resources/assets").resolve(location.getNamespace()).resolve(location.getPath());
                    return Files.exists(path)?Optional.of(new Resource(null,()->Files.newInputStream(path))):Optional.empty();
                }
            });
            production=Class.forName("com.teamlifebind."+platform+".MagicShaderRenderer");
            target=new TextureTarget("magic production regression",WIDTH,HEIGHT,GpuFormat.RGBA8_UNORM,GpuFormat.D32_FLOAT);
            Method ensure=production.getDeclaredMethod("ensureResources",RenderTarget.class);ensure.setAccessible(true);ensure.invoke(null,target);
            renderer=(GameRenderer)unsafe.allocateInstance(GameRenderer.class);
            GameRenderState state=(GameRenderState)unsafe.allocateInstance(GameRenderState.class);
            LevelRenderState level=(LevelRenderState)unsafe.allocateInstance(LevelRenderState.class);
            camera=new CameraRenderState();camera.initialized=true;camera.pos=Vec3.ZERO;
            camera.viewRotationMatrix=new Matrix4f().lookAt(0,0,0,0,-.15f,-1,0,1,0);
            camera.projectionMatrix=new Matrix4f().setPerspective((float)java.lang.Math.toRadians(60),(float)WIDTH/HEIGHT,256,.05f,device.getDeviceInfo().isZZeroToOne());
            // The default CameraRenderState contains an uninitialized frustum. A real extracted frame has this populated.
            camera.cullFrustum=new net.minecraft.client.renderer.culling.Frustum(camera.viewRotationMatrix,camera.projectionMatrix);
            camera.cullFrustum.prepare(0,0,0);
            level.cameraRenderState=camera;level.gameTime=100;level.worldPartialTicks=.5f;
            set(state,"levelRenderState",level);set(renderer,"gameRenderState",state);set(renderer,"mainRenderTarget",target);
            Class<?> sceneType=Class.forName(production.getName()+"$Scene");sceneCtor=sceneType.getDeclaredConstructors()[0];sceneCtor.setAccessible(true);
            scenes=(Map<Long,Object>)field("SCENES");
            SpellDefinition shield=SpellCatalogue.all().get(13);
            insert(shield,1,13,Vec3.ZERO,100,11);
            byte[] visible=render(0);long baseline=energy(visible);
            long hdr=radiance();System.out.println("PRODUCTION_RADIANCE_BYTES="+hdr);
            System.out.println("PRODUCTION_RENDER_ENERGY="+baseline);
            check(baseline>1000,"USER SYMPTOM REPRO: actual production render produced no visible magic pixels");
            check(hdr>1000,"Production HDR volume must emit radiance before composite");
            image(visible,out.resolve(platform+"-production-shield.png"));
            check(energy(render(1))==0,"World foreground depth must fully occlude magic");
            invoke("clear");check(energy(render(0))==0,"Cleared spell must leave no pixels");
            insert(shield,1,13,Vec3.ZERO,50,11);check(energy(render(0))==0,"Stale scene must leave no pixels");
            invoke("clear");insert(shield,1,13,Vec3.ZERO,100,55);check(energy(render(0))==0,"Expired scene must leave no pixels");
            invoke("clear");invoke("requestReload");insert(shield,1,13,Vec3.ZERO,100,11);
            check(energy(render(0))==baseline,"Resource reload must rebuild pipeline and preserve identical output");
            Vec3 distant=new Vec3(20_000_000,100,20_000_000);camera.pos=distant;camera.cullFrustum.prepare(distant.x,distant.y,distant.z);
            invoke("clear");insert(shield,1,13,distant,100,11);
            check(Arrays.equals(visible,render(0)),"Camera-relative shader must be stable at large world coordinates");
            camera.pos=Vec3.ZERO;camera.cullFrustum.prepare(0,0,0);
            long minEnergy=Long.MAX_VALUE;int spellCount=0;
            for(SpellDefinition spell:SpellCatalogue.all()){
                invoke("clear");insert(spell,1,spell.primaryMechanic().ordinal(),Vec3.ZERO,100,11);
                long value=energy(render(0));check(value>1000,"Spell invisible in production: "+spell.id());minEnergy=java.lang.Math.min(minEnergy,value);spellCount++;
                if(spellCount==300)image(render(0),out.resolve(platform+"-production-super.png"));
            }
            System.out.println("PRODUCTION_SPELLS_VISIBLE="+spellCount+" MIN_ENERGY="+minEnergy);
            invoke("close");check(field("cache")==null&&field("effectTarget")==null&&scenes.isEmpty(),"Close must release GPU targets and scenes");
            target.destroyBuffers();
            System.out.println("PASS production RenderPearl "+platform+" assertions="+assertions);
        }finally{device.close();backend.unloadLibrary();SDL_Quit();}
    }
}
