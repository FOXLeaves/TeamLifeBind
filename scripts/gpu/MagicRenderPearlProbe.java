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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.*;
import static org.lwjgl.sdl.SDLInit.*;

/** Actual production renderer, shader loader, transient UBOs, RenderPearl passes and GPU readback. */
public final class MagicRenderPearlProbe {
    static final int WIDTH=384, HEIGHT=216;
    static Class<?> production;
    static Constructor<?> packetCtor;
    static Method accept;
    static Map<Long,Object> scenes;
    static Minecraft minecraft;
    static ClientLevel clientLevel;
    static ClientLevel otherLevel;
    static ClientLevel.ClientLevelData clientData;
    static LocalPlayer player;
    static LevelRenderState level;
    static net.minecraft.client.DeltaTracker.Timer timer;
    static net.minecraft.client.Camera liveCamera;
    static GameRenderer renderer;
    static CameraRenderState camera;
    static TextureTarget target;
    static GpuDevice device;
    static int assertions;
    static void check(boolean value,String description){assertions++;if(!value)throw new AssertionError(description);}
    static void set(Object object,String name,Object value)throws Exception{
        Class<?> type=object.getClass();
        while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);return;}catch(NoSuchFieldException ignored){type=type.getSuperclass();}}
        throw new NoSuchFieldException(name);
    }
    static Object field(String name)throws Exception{Field f=production.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    static void invoke(String name)throws Exception{production.getMethod(name).invoke(null);}
    static void insert(SpellDefinition spell,int phase,int mechanic,Vec3 cameraPosition,long received,float age)throws Exception{
        Vec3 origin=new Vec3(0,-1.65,-8).add(cameraPosition);
        Vec3 end=origin;
        if(mechanic==0||mechanic==1||mechanic==2||mechanic==5||mechanic==10||mechanic==16||mechanic==17){
            origin=new Vec3(-2,-1,-8).add(cameraPosition);end=new Vec3(2,0,-8).add(cameraPosition);
        }
        packet(spell,phase,mechanic,new UUID(1,2),origin,end,received,age);
        production.getMethod("projection",Matrix4f.class).invoke(null,camera.projectionMatrix);
    }
    static void packet(SpellDefinition spell,int phase,int mechanic,UUID caster,Vec3 origin,Vec3 end,long received,float age)throws Exception{
        clientData.setGameTime(received);
        Object packet=packetCtor.newInstance(256L,caster,Level.OVERWORLD.identifier(),spell.id(),0,phase,mechanic,age,40,
            origin.x,origin.y,origin.z,end.x,end.y,end.z,2.4f);
        accept.invoke(null,packet);
        clientData.setGameTime(level.gameTime);
        production.getMethod("projection",Matrix4f.class).invoke(null,camera.projectionMatrix);
    }
    static void movePlayer(Vec3 previous,Vec3 current)throws Exception{
        set(player,"position",current);player.xOld=previous.x;player.yOld=previous.y;player.zOld=previous.z;
        // Entity.getPosition uses the physics xo/yo/zo history. Models use xOld/yOld/zOld instead.
        player.xo=previous.x-2;player.yo=previous.y-1;player.zo=previous.z;
    }
    static long pixelDifference(byte[] first,byte[] second){
        long difference=0;
        for(int i=0;i<first.length;i++)if(i%4<3)difference+=java.lang.Math.abs(Byte.toUnsignedInt(first[i])-Byte.toUnsignedInt(second[i]));
        return difference;
    }
    static void placement(String label,SpellDefinition spell,int phase,int mechanic,Vec3 origin,Vec3 end,float age)throws Exception{
        Object raw=scenes.get(256L);byte[] actual=render(0);
        check(scenes.get(256L)==raw,"Rendering must preserve the authoritative raw scene: "+label);
        packet(spell,phase,mechanic,new UUID(99,100),origin,end,level.gameTime,age);
        byte[] expected=render(0);scenes.put(256L,raw);
        long difference=pixelDifference(actual,expected);
        check(energy(expected)>1000,"Reference must contain visible magic: "+label);
        check(difference==0,"Current caster placement differs from reference: "+label+" diff="+difference);
        System.out.println("ACTUAL_FOLLOW_EDGE="+label+" DIFF="+difference);
    }
    /** Real Entity interpolation + production accept/render, with only one scene packet every three ticks. */
    static void following(Path out,String platform)throws Exception{
        UUID caster=new UUID(7,9),untracked=new UUID(99,100);
        player.setUUID(caster);set(player,"level",clientLevel);set(clientLevel,"players",new ArrayList<>(List.of(player)));
        set(minecraft,"player",player);
        Vec3 start=new Vec3(0,-1.65,-8),step=new Vec3(.6,.2,0);
        long maximumLag=0;int compared=0;
        // Each phase is tested against an otherwise identical server scene placed at the current interpolated position.
        for(int kind=0;kind<3;kind++){
            int phase=kind==0?0:1,mechanic=kind==2?13:4;
            SpellDefinition spell=SpellCatalogue.all().get(mechanic);
            Vec3 offset=new Vec3(0,phase==0?.12:1,0),direction=new Vec3(0,0,-1);
            invoke("clear");movePlayer(start,start);level.gameTime=100;level.worldPartialTicks=0;
            packet(spell,phase,mechanic,caster,start.add(offset),start.add(offset).add(direction),100,11);
            for(int frame=1;frame<=7;frame++){
                int tick=frame/2;float partial=(frame%2)*.5f;
                Vec3 previous=start.add(step.scale(tick)),current=start.add(step.scale(tick+1));
                movePlayer(previous,current);level.gameTime=100+tick;clientData.setGameTime(level.gameTime);partial(partial);
                if(frame==6)packet(spell,phase,mechanic,caster,current.add(offset),current.add(offset).add(direction),level.gameTime,11+tick);
                production.getMethod("projection",Matrix4f.class).invoke(null,camera.projectionMatrix);
                Object actualScene=scenes.get(256L);byte[] actual=render(0);
                check(scenes.get(256L)==actualScene,"Per-frame follow must not mutate the raw server scene");
                Vec3 interpolated=previous.lerp(current,partial),expectedOrigin=interpolated.add(offset);
                packet(spell,phase,mechanic,untracked,expectedOrigin,expectedOrigin.add(direction),level.gameTime,11+tick);
                byte[] expected=render(0);scenes.put(256L,actualScene);
                long lag=pixelDifference(actual,expected);maximumLag=java.lang.Math.max(maximumLag,lag);compared++;
                check(energy(expected)>1000,"Moving reference scene must remain visible");
                if(frame==1&&kind==0){image(actual,out.resolve(platform+"-follow-first-actual.png"));image(expected,out.resolve(platform+"-follow-first-reference.png"));}
                if(frame==5&&kind==2){image(actual,out.resolve(platform+"-follow-actual.png"));image(expected,out.resolve(platform+"-follow-reference.png"));}
                System.out.println("ACTUAL_FOLLOW_FRAME="+kind+":"+frame+" DIFF="+lag+" INTERPOLATED="+interpolated);
                check(lag==0,"FOLLOW LAG REPRO: phase="+phase+" mechanic="+mechanic+" frame="+frame+" pixels differ from current interpolated caster by "+lag);
            }
        }
        // Fixed world magic must not accidentally inherit the caster correction.
        for(int[] fixed:new int[][]{{1,9},{1,19},{1,14},{1,15},{1,0},{1,3},{2,13},{3,4}}){
            int phase=fixed[0],mechanic=fixed[1];SpellDefinition spell=SpellCatalogue.all().get(mechanic);
            invoke("clear");movePlayer(start,start);level.gameTime=100;clientData.setGameTime(100);level.worldPartialTicks=0;
            Vec3 origin=start.add(0,1,0),end=origin.add(0,0,-1);
            packet(spell,phase,mechanic,caster,origin,end,100,11);Object actualScene=scenes.get(256L);
            movePlayer(start.add(step.scale(2)),start.add(step.scale(3)));level.gameTime=102;clientData.setGameTime(102);partial(.5f);
            byte[] actual=render(0);
            packet(spell,phase,mechanic,untracked,origin,end,102,13);byte[] expected=render(0);scenes.put(256L,actualScene);
            check(energy(expected)>1000,"Fixed reference scene must remain visible: "+phase+":"+mechanic);
            check(pixelDifference(actual,expected)==0,"Fixed magic must stay at world anchor: "+phase+":"+mechanic);compared++;
        }
        compared+=followEdges(caster,start,step);
        System.out.println("PRODUCTION_FOLLOW_FRAMES="+compared+" MAX_PIXEL_DIFF="+maximumLag);
        invoke("clear");set(clientLevel,"players",new ArrayList<>());set(minecraft,"player",null);level.gameTime=100;clientData.setGameTime(100);partial(.5f);
    }
    static int followEdges(UUID caster,Vec3 start,Vec3 step)throws Exception{
        int compared=0;SpellDefinition shield=SpellCatalogue.all().get(13),channel=SpellCatalogue.all().get(17);
        Vec3 offset=new Vec3(0,1,0),direction=new Vec3(0,0,-1),old=start.add(step.scale(2)),current=start.add(step.scale(3));
        // Models and the camera use deliberately different histories; their extracted partial ticks can also differ.
        for(boolean firstPerson:new boolean[]{false,true}){
            invoke("clear");movePlayer(start,start);level.gameTime=100;partial(0);
            packet(shield,1,13,caster,start.add(offset),start.add(offset).add(direction),100,11);
            movePlayer(old,current);level.gameTime=102;clientData.setGameTime(102);partial(0);
            set(timer,"deltaTickResidual",.75F);camera.cameraEntityPartialTicks=.25F;camera.isFirstPerson=firstPerson;liveCamera.setEntity(player);
            Vec3 feet=firstPerson?new Vec3(player.xo,player.yo,player.zo).lerp(current,.25):old.lerp(current,.25);
            placement(firstPerson?"camera-first-person-xo":"camera-third-person-xOld",shield,1,13,feet.add(offset),feet.add(offset).add(direction),13);compared++;
        }
        liveCamera.setEntity(null);camera.isFirstPerson=false;
        // World time can freeze while players still move; use their own current render partial tick.
        invoke("clear");movePlayer(start,start);level.gameTime=100;partial(0);
        packet(shield,1,13,caster,start.add(offset),start.add(offset).add(direction),100,11);
        movePlayer(old,current);level.gameTime=102;clientData.setGameTime(102);partial(0);
        clientLevel.tickRateManager().setFrozen(true);set(timer,"frozen",true);set(timer,"deltaTickResidual",.75F);
        Vec3 feet=old.lerp(current,.75);
        placement("world-frozen-player-moving",shield,1,13,feet.add(offset),feet.add(offset).add(direction),13);compared++;
        clientLevel.tickRateManager().setFrozen(false);set(timer,"frozen",false);
        // Paused rendering preserves its own residual instead of advancing by the world or realtime residual.
        set(timer,"paused",true);set(timer,"pausedDeltaTickResidual",.4F);
        feet=old.lerp(current,.4F);
        placement("paused-partial-position",shield,1,13,feet.add(offset),feet.add(offset).add(direction),13);compared++;
        set(timer,"paused",false);
        // A channeled beam may move only its caster endpoint, preserving the server hit point.
        invoke("clear");movePlayer(start,start);level.gameTime=100;partial(0);
        Vec3 channelOrigin=start.add(0,player.getEyeHeight(),0),hit=new Vec3(2,0,-12);
        packet(channel,1,17,caster,channelOrigin,hit,100,11);
        movePlayer(old,current);level.gameTime=102;clientData.setGameTime(102);partial(.5F);
        placement("channel-moving-source-fixed-hit",channel,1,17,old.lerp(current,.5).add(0,player.getEyeHeight(),0),hit,13);compared++;
        liveCamera.setEntity(player);camera.isFirstPerson=true;camera.cameraEntityPartialTicks=.25F;
        placement("channel-first-person-smooth-eye",channel,1,17,camera.pos,hit,13);compared++;
        liveCamera.setEntity(null);camera.isFirstPerson=false;
        // Missing, removed and cross-dimension entities must not pull an existing world scene.
        for(int unavailable=0;unavailable<3;unavailable++){
            invoke("clear");movePlayer(start,start);level.gameTime=100;partial(0);
            Vec3 origin=start.add(offset),end=origin.add(direction);packet(shield,1,13,caster,origin,end,100,11);
            movePlayer(old,current);level.gameTime=102;clientData.setGameTime(102);partial(.5F);
            if(unavailable==0)set(clientLevel,"players",new ArrayList<>());
            if(unavailable==1)set(player,"removalReason",Entity.RemovalReason.DISCARDED);
            if(unavailable==2)set(player,"level",otherLevel);
            placement("unavailable-caster-"+unavailable,shield,1,13,origin,end,13);compared++;
            set(clientLevel,"players",new ArrayList<>(List.of(player)));set(player,"removalReason",null);set(player,"level",clientLevel);
        }
        // First-frame/spawn positions and teleports must not interpolate across uninitialized or remote coordinates.
        invoke("clear");movePlayer(start,start);level.gameTime=100;partial(0);
        packet(shield,1,13,caster,start.add(offset),start.add(offset).add(direction),100,11);
        player.tickCount=0;movePlayer(new Vec3(2000,500,2000),current);level.gameTime=102;clientData.setGameTime(102);partial(.5F);
        placement("spawn-uninitialized-history",shield,1,13,current.add(offset),current.add(offset).add(direction),13);compared++;
        player.tickCount=40;movePlayer(current,current);
        placement("teleport-reset-history",shield,1,13,current.add(offset),current.add(offset).add(direction),13);compared++;
        // An old sparse packet can be outside the frustum/range while its current caster has entered view.
        // Attachment must happen before either cull, otherwise a perfectly visible player loses the effect.
        for(int phase:new int[]{0,1}){
            invoke("clear");movePlayer(start,start);level.gameTime=100;clientData.setGameTime(100);partial(.5F);
            Vec3 attach=new Vec3(0,phase==0?.12:1,0),obsolete=new Vec3(300,-1.65,-8).add(attach);
            packet(shield,phase,13,caster,obsolete,obsolete.add(direction),100,11);
            placement("caster-entered-view-before-cull-phase-"+phase,shield,phase,13,start.add(attach),start.add(attach).add(direction),11);compared++;
        }
        return compared;
    }
    static void partial(float value)throws Exception{
        level.worldPartialTicks=value;camera.cameraEntityPartialTicks=value;
        set(timer,"deltaTickResidual",value);set(timer,"pausedDeltaTickResidual",value);
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
            // NeoForge registry bootstrap reads loader metadata during static initialization.
            // Supply a client environment with no external mods; MC/RenderPearl code remains real.
            Class<?> fml=Class.forName("net.neoforged.fml.loading.FMLLoader");Object loader=unsafe.allocateInstance(fml);
            set(loader,"production",true);
            set(loader,"dist",Enum.valueOf((Class)Class.forName("net.neoforged.api.distmarker.Dist"),"CLIENT"));
            Class<?> modList=Class.forName("net.neoforged.fml.loading.LoadingModList");
            Object emptyMods=modList.getMethod("of",List.class,List.class,List.class,List.class,List.class,Map.class)
                .invoke(null,List.of(),List.of(),List.of(),List.of(),List.of(),Map.of());
            set(loader,"loadingModList",emptyMods);
            Field current=fml.getDeclaredField("current");current.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicReference<Object>)current.get(null)).set(loader);
        }
        GlBackend backend=new GlBackend();backend.loadLibrary();device=backend.createDevice(new GpuDebugOptions(3,true,true,true));
        try{
            RenderSystem.initRenderer(device);
            System.out.println("ACTUAL_PLATFORM="+platform+" DEVICE="+device.getDeviceInfo().name());
            minecraft=(Minecraft)unsafe.allocateInstance(Minecraft.class);
            Field instance=Minecraft.class.getDeclaredField("instance");instance.setAccessible(true);instance.set(null,minecraft);
            set(minecraft,"resourceManager",new ReloadableResourceManager(PackType.CLIENT_RESOURCES){
                @Override public Optional<Resource> getResource(Identifier location){
                    Path path=root.resolve(platform+"/src/main/resources/assets").resolve(location.getNamespace()).resolve(location.getPath());
                    return Files.exists(path)?Optional.of(new Resource(null,()->Files.newInputStream(path))):Optional.empty();
                }
            });
            production=Class.forName("com.teamlifebind."+platform+".MagicShaderRenderer");
            Class<?> packetType=Class.forName("com.teamlifebind."+platform+".MagicScenePayload");packetCtor=packetType.getDeclaredConstructors()[0];packetCtor.setAccessible(true);
            accept=production.getMethod("accept",packetType);
            java.io.PrintStream stdout=System.out,stderr=System.err;
            net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
            System.setOut(stdout);System.setErr(stderr);
            clientLevel=(ClientLevel)unsafe.allocateInstance(ClientLevel.class);
            otherLevel=(ClientLevel)unsafe.allocateInstance(ClientLevel.class);set(otherLevel,"dimension",Level.NETHER);
            clientData=new ClientLevel.ClientLevelData(Difficulty.NORMAL,false,false);clientData.setGameTime(100);
            set(clientLevel,"clientLevelData",clientData);set(clientLevel,"levelData",clientData);set(clientLevel,"dimension",Level.OVERWORLD);
            set(clientLevel,"players",new ArrayList<>());set(clientLevel,"tickRateManager",new net.minecraft.world.TickRateManager());set(minecraft,"level",clientLevel);
            player=(LocalPlayer)unsafe.allocateInstance(LocalPlayer.class);
            player.tickCount=40;set(player,"eyeHeight",1.62F);
            timer=new net.minecraft.client.DeltaTracker.Timer(20,0,ignored->50);set(minecraft,"deltaTracker",timer);
            target=new TextureTarget("magic production regression",WIDTH,HEIGHT,GpuFormat.RGBA8_UNORM,GpuFormat.D32_FLOAT);
            Method ensure=production.getDeclaredMethod("ensureResources",RenderTarget.class);ensure.setAccessible(true);ensure.invoke(null,target);
            renderer=(GameRenderer)unsafe.allocateInstance(GameRenderer.class);
            liveCamera=new net.minecraft.client.Camera();set(renderer,"mainCamera",liveCamera);set(minecraft,"gameRenderer",renderer);
            GameRenderState state=(GameRenderState)unsafe.allocateInstance(GameRenderState.class);
            level=(LevelRenderState)unsafe.allocateInstance(LevelRenderState.class);
            camera=new CameraRenderState();camera.initialized=true;camera.pos=Vec3.ZERO;
            camera.viewRotationMatrix=new Matrix4f().lookAt(0,0,0,0,-.15f,-1,0,1,0);
            camera.projectionMatrix=new Matrix4f().setPerspective((float)java.lang.Math.toRadians(60),(float)WIDTH/HEIGHT,256,.05f,device.getDeviceInfo().isZZeroToOne());
            // The default CameraRenderState contains an uninitialized frustum. A real extracted frame has this populated.
            camera.cullFrustum=new net.minecraft.client.renderer.culling.Frustum(camera.viewRotationMatrix,camera.projectionMatrix);
            camera.cullFrustum.prepare(0,0,0);
            level.cameraRenderState=camera;level.gameTime=100;level.worldPartialTicks=.5f;
            set(state,"levelRenderState",level);set(renderer,"gameRenderState",state);set(renderer,"mainRenderTarget",target);
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
            following(out,platform);
            invoke("close");check(field("cache")==null&&field("effectTarget")==null&&scenes.isEmpty(),"Close must release GPU targets and scenes");
            target.destroyBuffers();
            System.out.println("PASS production RenderPearl "+platform+" assertions="+assertions);
        }finally{device.close();backend.unloadLibrary();SDL_Quit();}
    }
}
