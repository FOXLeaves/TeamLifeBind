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
    static final int WIDTH=Integer.getInteger("tlb.probe.width",384), HEIGHT=Integer.getInteger("tlb.probe.height",216);
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
    static boolean suppressDetails;
    static boolean isolateDetails;
    static boolean suppressDelivery;
    static boolean suppressCombat;
    static boolean isolateCombat;
    static boolean suppressRestoration;
    static boolean isolateRestoration;
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
    static void attackEvent(long id,SpellDefinition spell,int phase,Vec3 origin,Vec3 end,float radius)throws Exception{
        attackEvent(id,spell,phase,spell.primaryMechanic().ordinal(),origin,end,radius);
    }
    static void attackEvent(long id,SpellDefinition spell,int phase,int mechanic,Vec3 origin,Vec3 end,float radius)throws Exception{
        clientData.setGameTime(level.gameTime);
        accept.invoke(null,packetCtor.newInstance(id,new UUID(99,100),Level.OVERWORLD.identifier(),spell.id(),0,phase,
            mechanic,phase==1?8F:2F,phase==1?40:10,origin.x,origin.y,origin.z,end.x,end.y,end.z,radius));
        production.getMethod("projection",Matrix4f.class).invoke(null,camera.projectionMatrix);
    }
    /** The user's camera is inside a cast volume while a second volume carries the attack. */
    static void attackStages(Path out,String platform)throws Exception{
        SpellDefinition spell=SpellCatalogue.byId("lightning_beam");
        Vec3 feet=new Vec3(0,-1.5,0),start=new Vec3(0,-.2,-1),end=new Vec3(0,-.2,-14);
        invoke("clear");attackEvent(512,spell,1,start,end,.8F);
        byte[] attack=render(0);check(energy(attack)>1000,"Attack is visible when rendered alone");
        invoke("clear");attackEvent(256,spell,0,feet,feet.add(0,0,-1),2.4F);
        byte[] cast=render(0);check(energy(cast)>1000,"Nearby cast is visible when rendered alone");
        attackEvent(512,spell,1,start,end,.8F);byte[] combined=render(0);
        scenes.remove(512L);byte[] sameResolutionCast=render(0);
        long difference=pixelDifference(sameResolutionCast,combined);
        scenes.remove(256L);attackEvent(512,spell,1,start,end,.8F);
        byte[] sameResolutionAttack=render(0);
        check(pixelDifference(sameResolutionAttack,combined)>1000,"Cast remains visible alongside the attack at the same resolution");
        image(sameResolutionCast,out.resolve(platform+"-cast-only.png"));image(attack,out.resolve(platform+"-attack-only.png"));image(combined,out.resolve(platform+"-cast-and-attack.png"));
        System.out.println("PRODUCTION_ATTACK_COEXIST width="+WIDTH+" height="+HEIGHT+" attackEnergy="+energy(attack)+" addedAttack="+difference);
        check(difference>1000,"ATTACK VFX REPRO: casting volume hides the whole attack, added RGB="+difference);
        var coverage=new com.google.gson.JsonArray();
        long minimum=Long.MAX_VALUE;int stageCount=0;
        for(SpellDefinition entry:SpellCatalogue.all()){
            var row=new com.google.gson.JsonObject();row.addProperty("spell",entry.id());
            var operators=new com.google.gson.JsonArray();
            for(MagicMechanic mechanic:entry.mechanics()){
              var operator=new com.google.gson.JsonObject();operator.addProperty("mechanic",mechanic.name());
              for(int phase:new int[]{1,2}){
                invoke("clear");attackEvent(256,entry,0,feet,feet.add(0,0,-1),2.4F);
                boolean segment=phase==1&&Set.of(0,1,2,5,10,16,17).contains(mechanic.ordinal());
                Vec3 attackOrigin=new Vec3(segment?-2:0,-1.2,-8),attackEnd=segment?new Vec3(2,-.2,-8):attackOrigin;
                attackEvent(512,entry,phase,mechanic.ordinal(),attackOrigin,attackEnd,2.4F);
                byte[] both=render(0);scenes.remove(512L);byte[] onlyCast=render(0);
                long contribution=pixelDifference(both,onlyCast);minimum=java.lang.Math.min(minimum,contribution);
                check(contribution>1000,"Attack stage hidden by cast: "+entry.id()+":"+mechanic+" phase="+phase+" RGB="+contribution);
                operator.addProperty(phase==1?"travelAddedRgb":"impactAddedRgb",contribution);stageCount++;
                if(entry.id().equals("fire_bolt")||entry.id().equals("lightning_beam")||entry.id().equals("oceanic_judgement"))
                    image(both,out.resolve(platform+"-"+entry.id()+"-"+mechanic.name().toLowerCase(Locale.ROOT)+"-phase-"+phase+".png"));
              }
              operators.add(operator);
            }
            row.add("operators",operators);
            coverage.add(row);
            if(coverage.size()%50==0)System.out.println("PRODUCTION_ATTACK_PROGRESS="+coverage.size());
        }
        Files.writeString(out.resolve("attack-coverage.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(coverage));
        System.out.println("PRODUCTION_ATTACK_CATALOGUE="+coverage.size()+" STAGES="+stageCount+" MIN_ADDED_RGB="+minimum);
        attackPressure(spell,feet,start,end);
    }
    static void attackPressure(SpellDefinition spell,Vec3 feet,Vec3 start,Vec3 end)throws Exception{
        for(int fps:new int[]{20,30,60}){
            set(minecraft,"fps",fps);invoke("clear");
            for(int i=0;i<7;i++)attackEvent(768+i,spell,0,feet,feet.add(0,0,-1),2.4F);
            attackEvent(512,spell,1,start,end,.8F);byte[] both=render(0);
            int layers=(int)field("resolutionLayers");TextureTarget effect=(TextureTarget)field("effectTarget");
            int draws=fps<24?2:fps<40?4:8;
            check((long)effect.width*effect.height*draws<=1_382_400,"Overlapping scene budget at fps="+fps);
            scenes.remove(512L);byte[] castOnly=render(0);
            check(pixelDifference(both,castOnly)>1000,"Crowded casts cannot suppress the attack at fps="+fps);
            check((int)field("resolutionLayers")==layers,"Quality hysteresis avoids reallocating after one scene disappears");
        }
        Object depth=field("depthCopy");scenes.clear();level.gameTime=121;
        attackEvent(512,spell,1,start,end,.8F);render(0);
        check((int)field("resolutionLayers")==1,"Quality recovers after the 20-tick hold");
        check(field("depthCopy")==depth,"Changing effect resolution does not reallocate world depth");
        level.gameTime=100;clientData.setGameTime(100);
        set(minecraft,"fps",60);invoke("clear");
        // An expired, camera-enclosing cast must not occupy the active scene or pixel budget.
        accept.invoke(null,packetCtor.newInstance(256L,new UUID(99,100),Level.OVERWORLD.identifier(),spell.id(),0,0,
            spell.primaryMechanic().ordinal(),9F,10,feet.x,feet.y,feet.z,feet.x,feet.y,feet.z-1,2.4F));
        level.gameTime=102;attackEvent(512,spell,1,start,end,.8F);
        byte[] visible=render(0);check(energy(visible)>1000,"Attack after expired cast is visible");
        check((int)field("resolutionLayers")==1,"Expired cast does not lower attack resolution");
        scenes.remove(256L);check(pixelDifference(visible,render(0))==0,"Expired cast cannot alter attack output");
        level.gameTime=100;clientData.setGameTime(100);invoke("clear");
    }
    static void worldEvent(String platform,int kind)throws Exception{
        Vec3 start=new Vec3(0,-.3,-7),end=start.add(0,1,0);float radius=1.8F;
        if(kind==0){start=new Vec3(-.6,-.3,-5);end=new Vec3(.6,-.1,-5);radius=.5F;}
        if(kind==1){start=new Vec3(-3,-.5,-8);end=new Vec3(3,.5,-8);radius=.14F;}
        if(kind==4){start=new Vec3(0,-1.5,-10);end=new Vec3(0,6,-10);radius=1F;}
        if(kind==20||kind==22){start=new Vec3(0,-1.5,-7);end=start.add(0,1,0);radius=1.8F;}
        worldEvent(platform,kind,start,end,radius);
    }
    static void worldEvent(String platform,int kind,Vec3 start,Vec3 end,float radius)throws Exception{
        worldEvent(platform,kind,new UUID(99,100),start,end,radius);
    }
    static void worldEvent(String platform,int kind,UUID owner,Vec3 start,Vec3 end,float radius)throws Exception{
        Class<?> type=Class.forName("com.teamlifebind."+platform+".WorldVfxPayload");
        Object packet=type.getDeclaredConstructors()[0].newInstance(2048L,owner,Level.OVERWORLD.identifier(),kind,
            start.x,start.y,start.z,end.x,end.y,end.z,radius);
        clientData.setGameTime(level.gameTime);production.getMethod("acceptWorld",type).invoke(null,packet);
        production.getMethod("projection",Matrix4f.class).invoke(null,camera.projectionMatrix);
    }
    static void worldEffects(Path out,String platform)throws Exception{
        String[] names={"muzzle","tracer","impact","explosion","beacon","supply_open","heal","mana","revive","boss","victory","relic",
            "team_signal","team_anchor","totem_rescue","scout_pulse","control_point","control_capture",
            "spell_learn","mana_growth","encounter_spawn","wave_clear","route_lock","border_warning"};
        for(int kind=0;kind<names.length;kind++){
            invoke("clear");level.gameTime=100;partial(.5F);worldEvent(platform,kind);
            level.gameTime=kind<2?101:106;clientData.setGameTime(level.gameTime);
            byte[] visible=render(0);check(energy(visible)>1000,"World VFX is visible: "+names[kind]);
            image(visible,out.resolve("world-"+names[kind]+".png"));
            check(energy(render(1))==0,"World geometry occludes "+names[kind]);
            level.gameTime++;byte[] next=render(0);
            check(pixelDifference(visible,next)>100,"World VFX animates: "+names[kind]);
            level.gameTime=160;check(energy(render(0))==0,"World VFX expires: "+names[kind]);
            System.out.println("PRODUCTION_WORLD_VFX="+names[kind]+" ENERGY="+energy(visible));
        }
        for(int length:new int[]{100,192}){
            invoke("clear");level.gameTime=100;partial(.5F);
            Vec3 end=new Vec3(1,-.3,-8),start=end.add(-length,0,0);
            worldEvent(platform,1,start,end,.14F);
            Object scene=((Map<?,?>)field("WORLD_SCENES")).values().iterator().next();
            var boundsMethod=scene.getClass().getDeclaredMethod("bounds");boundsMethod.setAccessible(true);
            var bounds=(net.minecraft.world.phys.AABB)boundsMethod.invoke(scene);
            check(bounds.contains(start)&&bounds.contains(end),"Long tracer bounds include both endpoints: "+length);
            byte[] visible=render(0);long endpoint=0;
            for(int y=0;y<HEIGHT;y++)for(int x=WIDTH*53/100;x<WIDTH*55/100;x++){
                int index=(y*WIDTH+x)*4;
                endpoint+=Byte.toUnsignedInt(visible[index])+Byte.toUnsignedInt(visible[index+1])+Byte.toUnsignedInt(visible[index+2]);
            }
            check(endpoint>300,"Long tracer reaches its visible endpoint instead of ending at the magic length cap: "+length);
            check(energy(render(1))==0,"Long tracer endpoint obeys depth: "+length);
        }
        // New events remain distinct after removing both body and color; thin details
        // must also make an independently measurable contribution to the composition.
        List<byte[]> shapes=new ArrayList<>();long leastDetail=Long.MAX_VALUE;double minShape=1;
        for(int kind=18;kind<names.length;kind++){
            invoke("clear");level.gameTime=100;partial(.5F);worldEvent(platform,kind);level.gameTime=106;
            byte[] complete=render(0);
            suppressDetails=true;invoke("requestReload");worldEvent(platform,kind);level.gameTime=112;
            byte[] body=render(0);long added=pixelDifference(complete,body);
            // Reload clears the scene cache; reset both event age and fractional tick.
            suppressDetails=false;isolateDetails=true;invoke("requestReload");
            level.gameTime=100;worldEvent(platform,kind);level.gameTime=106;byte[] outline=render(0);shapes.add(outline);
            check(added>1000,"New world details add visible pixels: "+names[kind]);leastDetail=java.lang.Math.min(leastDetail,added);
            check(energy(outline)>1000,"World event has a distinct native-resolution outline: "+names[kind]);
            check(energy(render(1))==0,"New world engraving obeys depth: "+names[kind]);
            isolateDetails=false;invoke("requestReload");
        }
        for(int a=0;a<shapes.size();a++)for(int b=a+1;b<shapes.size();b++){
            double difference=maskDifference(shapes.get(a),shapes.get(b));minShape=java.lang.Math.min(minShape,difference);
            check(difference>.08,"World progression outlines differ: "+names[a+18]+"/"+names[b+18]);
        }
        System.out.println("PRODUCTION_JOURNEY_DETAILS=6 MONOCHROME_PAIRS=15 MIN_MASK_DIFFERENCE="+minShape+" MIN_ADDED_RGB="+leastDetail);
        worldFollowing(platform);
        // Persistent markers retain a single scene and animate past the first lifetime.
        for(int kind:new int[]{4,16}){
            invoke("clear");
            for(int tick=100;tick<200;tick+=20){level.gameTime=tick;worldEvent(platform,kind);}
            check(((Map<?,?>)field("WORLD_SCENES")).size()==1,"Marker refresh replaces its existing scene: "+kind);
            check(energy(render(0))>1000,"Refreshed marker survives multiple lifetimes: "+kind);
        }
        // Details are a separately verified contribution to the final full-size framebuffer.
        invoke("clear");level.gameTime=100;partial(.5F);
        SpellDefinition spell=SpellCatalogue.byId("arcane_domain");
        insert(spell,0,19,Vec3.ZERO,100,11);byte[] detailed=render(0);
        suppressDetails=true;invoke("requestReload");insert(spell,0,19,Vec3.ZERO,100,11);byte[] soft=render(0);
        long detail=pixelDifference(detailed,soft);
        check(detail>1000,"Native-resolution detail layer contributes real pixels");
        image(detailed,out.resolve("magic-hd-runes.png"));image(soft,out.resolve("magic-volume-only.png"));
        suppressDetails=false;invoke("requestReload");insert(spell,0,19,Vec3.ZERO,100,11);
        check(pixelDifference(detailed,render(0))==0,"Detail resource reload is deterministic");
        System.out.println("PRODUCTION_HD_DETAIL_ADDED_RGB="+detail);
        invoke("clear");check(((Map<?,?>)field("WORLD_SCENES")).isEmpty(),"World and magic scenes clear together");
        check(energy(render(0))==0,"Cleared world effects leave no pixels");
    }
    static final int[] COMBAT_KINDS={0,1,2,3,4,5,9,10};
    static final String[] COMBAT_NAMES={"muzzle","tracer","impact","explosion","beacon","supply_open","boss","victory"};
    static final int[] COMBAT_TICKS={4,5,12,26,24,32,40,48};
    static void combatEvent(String platform,int index,float life)throws Exception{
        invoke("clear");level.gameTime=100;partial(0);
        // Frame the whole tall marker, including its top diamond, at the preview distance.
        if(COMBAT_KINDS[index]==4)worldEvent(platform,4,new Vec3(0,-1.8,-12),new Vec3(0,3.5,-12),1F);
        else worldEvent(platform,COMBAT_KINDS[index]);
        float ticks=COMBAT_TICKS[index]*life;
        level.gameTime=100+(int)ticks;partial(ticks-(int)ticks);clientData.setGameTime(level.gameTime);
    }
    static void combatDetails(Path out,String platform)throws Exception{
        var coverage=new com.google.gson.JsonArray();List<byte[]> baseline=new ArrayList<>();
        suppressCombat=true;invoke("requestReload");
        for(int i=0;i<COMBAT_KINDS.length;i++){combatEvent(platform,i,.35F);baseline.add(render(0));}
        suppressCombat=false;invoke("requestReload");long minAdded=Long.MAX_VALUE;
        for(int i=0;i<COMBAT_KINDS.length;i++){
            combatEvent(platform,i,.35F);byte[] visible=render(0);
            long added=pixelDifference(visible,baseline.get(i));minAdded=java.lang.Math.min(minAdded,added);
            check(added>1000,"Combat detail contributes to the production frame: "+COMBAT_NAMES[i]+" RGB="+added);
            check(energy(render(1))==0,"Combat detail obeys foreground depth: "+COMBAT_NAMES[i]);
            image(visible,out.resolve("combat-"+COMBAT_NAMES[i]+".png"));
            level.gameTime=200;check(energy(render(0))==0,"Combat detail expires: "+COMBAT_NAMES[i]);
            byte[] previous=null;
            for(int stage=0;stage<3;stage++){
                combatEvent(platform,i,new float[]{.10F,.40F,.72F}[stage]);byte[] frame=render(0);
                check(energy(frame)>1000,"Combat timeline is visible: "+COMBAT_NAMES[i]+":"+stage);
                if(previous!=null)check(pixelDifference(previous,frame)>100,"Combat geometry evolves: "+COMBAT_NAMES[i]+":"+stage);
                image(frame,out.resolve("combat-"+COMBAT_NAMES[i]+"-stage-"+stage+".png"));previous=frame;
            }
            var row=new com.google.gson.JsonObject();row.addProperty("kind",COMBAT_NAMES[i]);row.addProperty("id",COMBAT_KINDS[i]);
            row.addProperty("addedRgb",added);row.addProperty("stages",3);coverage.add(row);
        }
        baseline.clear();isolateDetails=true;isolateCombat=true;invoke("requestReload");
        List<byte[]> masks=new ArrayList<>();double minMask=1;
        for(int i=0;i<COMBAT_KINDS.length;i++){
            combatEvent(platform,i,.35F);byte[] shape=render(0);masks.add(shape);
            check(energy(shape)>1000,"New combat structure survives without old graphics or color: "+COMBAT_NAMES[i]);
            check(energy(render(1))==0,"Isolated combat structure obeys depth: "+COMBAT_NAMES[i]);
            image(shape,out.resolve("combat-"+COMBAT_NAMES[i]+"-outline.png"));
        }
        for(int a=0;a<masks.size();a++)for(int b=a+1;b<masks.size();b++){
            double difference=maskDifference(masks.get(a),masks.get(b));minMask=java.lang.Math.min(minMask,difference);
            check(difference>.08,"New monochrome combat structures differ: "+COMBAT_NAMES[a]+"/"+COMBAT_NAMES[b]);
        }
        masks.clear();
        // Only the new explosion shell is drawn here. Its far wall must survive when
        // the camera enters it, while a real depth plane hides just the back half.
        for(boolean inside:new boolean[]{false,true}){
            invoke("clear");level.gameTime=100;partial(0);
            Vec3 center=new Vec3(0,0,inside?-.1:-5);
            worldEvent(platform,3,center,center.add(0,1,0),2.4F);level.gameTime=110;
            byte[] full=render(0);check(energy(full)>1000,"Explosion shell visible from "+(inside?"inside":"outside"));
            check(energy(render(1))==0,"Explosion shell obeys depth from both sides");
            if(!inside){
                Vector4f clip=camera.projectionMatrix.transform(new Vector4f(0,0,-5,1));
                double depth=clip.z/clip.w;if(!device.getDeviceInfo().isZZeroToOne())depth=depth*.5+.5;
                byte[] partial=render(depth);
                check(energy(partial)>1000&&energy(partial)<energy(full),"Depth plane preserves front shell and hides its rear");
            }
        }
        // Actual weapon coordinates and three different shot axes.
        for(int kind:new int[]{0,1,2})for(Vec3 axis:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1))){
            invoke("clear");level.gameTime=100;partial(.5F);Vec3 start=new Vec3(0,-.3,-5);
            worldEvent(platform,kind,start,start.add(axis.scale(kind==1?3:kind==0?.45:1)),kind==1?.12F:kind==0?.28F:.65F);
            level.gameTime=101;check(energy(render(0))>100,"Weapon detail follows a visible real-scale shot axis: "+kind+":"+axis);
            check(energy(render(1))==0,"Weapon detail depth follows its shot axis: "+kind+":"+axis);
        }
        for(int length:new int[]{100,192}){
            invoke("clear");level.gameTime=100;partial(0);Vec3 end=new Vec3(1,-.3,-8);
            worldEvent(platform,1,end.add(-length,0,0),end,.14F);level.gameTime=104;partial(.25F);
            check(energy(render(0))>100,"New compression front reaches a long tracer endpoint: "+length);
            check(energy(render(1))==0,"Long compression front obeys depth: "+length);
        }
        isolateDetails=false;isolateCombat=false;invoke("requestReload");combatEvent(platform,5,.35F);byte[] before=render(0);
        invoke("requestReload");combatEvent(platform,5,.35F);
        check(pixelDifference(before,render(0))==0,"Combat resource reload is deterministic");
        invoke("clear");check(energy(render(0))==0,"Cleared combat effects leave no pixels");
        Files.writeString(out.resolve("combat-coverage.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(coverage));
        System.out.println("PRODUCTION_COMBAT_DETAILS=8 MONOCHROME_PAIRS=28 TIMELINE_FRAMES=24 SHOT_AXES=9 MIN_ADDED_RGB="+minAdded+" MIN_MASK_DIFFERENCE="+minMask);
        System.out.println("PRODUCTION_COMBAT_FRAMEBUFFER="+WIDTH+"x"+HEIGHT);
    }
    static final int[] RESTORATION_KINDS={6,7,8,11};
    static final String[] RESTORATION_NAMES={"heal","mana","revive","relic"};
    static final int[] RESTORATION_TICKS={28,28,36,36};
    static final float[] RESTORATION_RADII={1.4F,1.4F,1.8F,1.7F};
    static void restorationEvent(String platform,int index,float life)throws Exception{
        restorationEvent(platform,index,life,new Vec3(0,-.3,-6),RESTORATION_RADII[index]);
    }
    static void restorationEvent(String platform,int index,float life,Vec3 center,float radius)throws Exception{
        invoke("clear");level.gameTime=100;partial(0);
        worldEvent(platform,RESTORATION_KINDS[index],center,center.add(0,1,0),radius);
        float ticks=RESTORATION_TICKS[index]*life;
        level.gameTime=100+(int)ticks;partial(ticks-(int)ticks);clientData.setGameTime(level.gameTime);
    }
    static void restorationDetails(Path out,String platform)throws Exception{
        var coverage=new com.google.gson.JsonArray();List<byte[]> baseline=new ArrayList<>();
        suppressRestoration=true;invoke("requestReload");
        for(int i=0;i<4;i++){restorationEvent(platform,i,.4F);baseline.add(render(0));}
        suppressRestoration=false;invoke("requestReload");long minAdded=Long.MAX_VALUE;
        for(int i=0;i<4;i++){
            restorationEvent(platform,i,.4F);byte[] visible=render(0);
            long added=pixelDifference(visible,baseline.get(i));minAdded=java.lang.Math.min(minAdded,added);
            check(added>1000,"Recovery detail contributes visible pixels: "+RESTORATION_NAMES[i]+" RGB="+added);
            check(energy(render(1))==0,"Recovery detail obeys world depth: "+RESTORATION_NAMES[i]);
            image(visible,out.resolve("restoration-"+RESTORATION_NAMES[i]+".png"));
            level.gameTime=200;check(energy(render(0))==0,"Recovery effect expires: "+RESTORATION_NAMES[i]);
            byte[] previous=null;
            for(int stage=0;stage<3;stage++){
                restorationEvent(platform,i,new float[]{.12F,.42F,.74F}[stage]);byte[] frame=render(0);
                check(energy(frame)>1000,"Recovery timeline remains visible: "+RESTORATION_NAMES[i]+":"+stage);
                if(previous!=null)check(pixelDifference(previous,frame)>100,"Recovery structure evolves: "+RESTORATION_NAMES[i]+":"+stage);
                image(frame,out.resolve("restoration-"+RESTORATION_NAMES[i]+"-stage-"+stage+".png"));previous=frame;
            }
            var row=new com.google.gson.JsonObject();row.addProperty("kind",RESTORATION_NAMES[i]);row.addProperty("id",RESTORATION_KINDS[i]);
            row.addProperty("addedRgb",added);row.addProperty("stages",3);coverage.add(row);
        }
        baseline.clear();isolateDetails=true;isolateRestoration=true;invoke("requestReload");
        List<byte[]> masks=new ArrayList<>();double minMask=1;
        for(int i=0;i<4;i++){
            // Identical radius, position and white colour: geometry alone differentiates these scenes.
            restorationEvent(platform,i,.4F,new Vec3(0,-.3,-6),1.7F);byte[] outline=render(0);masks.add(outline);
            check(energy(outline)>1000,"Recovery outline survives without its body or colour: "+RESTORATION_NAMES[i]);
            check(outsideRestorationFootprint(outline,new Vec3(0,-.3,-6),1.7F)==0,
                "Grazing recovery planes must not leave distant streaks: "+RESTORATION_NAMES[i]);
            image(outline,out.resolve("restoration-"+RESTORATION_NAMES[i]+"-outline.png"));
            restorationEvent(platform,i,.4F,new Vec3(0,-.3,-.08),1.7F);
            check(energy(render(0))>1000,"Recovery detail remains visible from inside: "+RESTORATION_NAMES[i]);
            check(energy(render(1))==0,"Inside recovery effect obeys depth: "+RESTORATION_NAMES[i]);
            restorationEvent(platform,i,.4F,new Vec3(0,-.3,-5),1.7F);long full=energy(render(0));
            Vector4f clip=camera.projectionMatrix.transform(new Vector4f(0,0,-5,1));
            double depth=clip.z/clip.w;if(!device.getDeviceInfo().isZZeroToOne())depth=depth*.5+.5;
            long front=energy(render(depth));
            check(front>1000&&front<full,"Depth plane preserves only the foreground recovery geometry: "+RESTORATION_NAMES[i]);
        }
        for(int a=0;a<4;a++)for(int b=a+1;b<4;b++){
            double difference=maskDifference(masks.get(a),masks.get(b));minMask=java.lang.Math.min(minMask,difference);
            check(difference>.08,"Monochrome recovery structures differ: "+RESTORATION_NAMES[a]+"/"+RESTORATION_NAMES[b]);
        }
        masks.clear();isolateDetails=false;isolateRestoration=false;invoke("requestReload");
        restorationFollowing(platform);
        restorationEvent(platform,2,.4F);byte[] before=render(0);
        invoke("requestReload");restorationEvent(platform,2,.4F);
        check(pixelDifference(before,render(0))==0,"Recovery resource reload is deterministic");
        invoke("clear");check(energy(render(0))==0,"Cleared recovery effects leave no pixels");
        Files.writeString(out.resolve("restoration-coverage.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(coverage));
        System.out.println("PRODUCTION_RESTORATION_DETAILS=4 MONOCHROME_PAIRS=6 TIMELINE_FRAMES=12 INSIDE_VIEWS=4 PARTIAL_DEPTH=4 MIN_ADDED_RGB="+minAdded+" MIN_MASK_DIFFERENCE="+minMask);
        System.out.println("PRODUCTION_RESTORATION_FRAMEBUFFER="+WIDTH+"x"+HEIGHT);
    }
    static long outsideRestorationFootprint(byte[] pixels,Vec3 center,float radius){
        Matrix4f inverse=new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix).invert();
        Vector4f projected=new Vector4f();Vec3 relative=center.subtract(camera.pos);long energy=0;
        // Check the image against a world-space bounding sphere, allowing a small AA margin.
        double bound=radius*1.43,centerSquared=relative.lengthSqr();
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){
            int index=(y*WIDTH+x)*4,rgb=Byte.toUnsignedInt(pixels[index])+Byte.toUnsignedInt(pixels[index+1])+Byte.toUnsignedInt(pixels[index+2]);
            if(rgb==0)continue;
            projected.set((x+.5F)/WIDTH*2-1,(y+.5F)/HEIGHT*2-1,device.getDeviceInfo().isZZeroToOne()?0:-1,1);
            inverse.transform(projected);
            double dx=projected.x/projected.w,dy=projected.y/projected.w,dz=projected.z/projected.w;
            double length=java.lang.Math.sqrt(dx*dx+dy*dy+dz*dz);
            double along=(relative.x*dx+relative.y*dy+relative.z*dz)/length;
            if(along<=0||centerSquared-along*along>bound*bound)energy+=rgb;
        }
        return energy;
    }
    @SuppressWarnings("unchecked") static void restorationFollowing(String platform)throws Exception{
        Map<Object,Object> events=(Map<Object,Object>)field("WORLD_SCENES");
        UUID tracked=new UUID(7,9),untracked=new UUID(99,100);
        player.setUUID(tracked);set(player,"level",clientLevel);set(clientLevel,"players",new ArrayList<>(List.of(player)));set(minecraft,"player",player);
        Vec3 feet=new Vec3(0,-1.3,-6),step=new Vec3(.30,.12,.08);int compared=0;
        for(int i=0;i<4;i++){
            invoke("clear");level.gameTime=100;partial(0);movePlayer(feet,feet);Vec3 origin=feet.add(0,1,0);
            worldEvent(platform,RESTORATION_KINDS[i],tracked,origin,origin.add(0,1,0),RESTORATION_RADII[i]);
            Object key=events.keySet().iterator().next(),raw=events.get(key);
            // One packet only: compare two fractional positions within each of three later ticks.
            for(int frame=1;frame<=3;frame++)for(float fraction:new float[]{.25F,.75F}){
                Vec3 previous=feet.add(step.scale(frame-1)),current=feet.add(step.scale(frame));movePlayer(previous,current);
                level.gameTime=100+frame*3;partial(fraction);byte[] actual=render(0);
                check(events.get(key)==raw,"Recovery follow does not mutate its authoritative scene: "+RESTORATION_NAMES[i]);
                Vec3 expected=previous.lerp(current,fraction).add(0,1,0);
                level.gameTime=100;worldEvent(platform,RESTORATION_KINDS[i],untracked,expected,expected.add(0,1,0),RESTORATION_RADII[i]);
                level.gameTime=100+frame*3;byte[] reference=render(0);
                check(energy(reference)>1000,"Recovery follow reference is visible: "+RESTORATION_NAMES[i]);
                check(pixelDifference(actual,reference)==0,"Recovery follows the current fractional player position: "+RESTORATION_NAMES[i]+":"+frame+":"+fraction);
                events.put(key,raw);compared++;
            }
        }
        invoke("clear");set(clientLevel,"players",new ArrayList<>());set(minecraft,"player",null);level.gameTime=100;clientData.setGameTime(100);partial(.5F);
        System.out.println("PRODUCTION_RESTORATION_FOLLOW_FRAMES="+compared+" MAX_PIXEL_DIFF=0");
    }
    static double maskDifference(byte[] first,byte[] second){
        int union=0,different=0;
        for(int i=0;i<first.length;i+=4){
            boolean a=Byte.toUnsignedInt(first[i])+Byte.toUnsignedInt(first[i+1])+Byte.toUnsignedInt(first[i+2])>18;
            boolean b=Byte.toUnsignedInt(second[i])+Byte.toUnsignedInt(second[i+1])+Byte.toUnsignedInt(second[i+2])>18;
            if(a||b)union++;if(a!=b)different++;
        }
        return union==0?0.0:(double)different/union;
    }
    @SuppressWarnings("unchecked") static void worldFollowing(String platform)throws Exception{
        Map<Object,Object> events=(Map<Object,Object>)field("WORLD_SCENES");
        UUID tracked=new UUID(7,9),untracked=new UUID(99,100);
        player.setUUID(tracked);set(player,"level",clientLevel);set(clientLevel,"players",new ArrayList<>(List.of(player)));set(minecraft,"player",player);
        Vec3 feet=new Vec3(0,-1.3,-7),step=new Vec3(.3,.1,0);int compared=0;
        for(int kind=18;kind<24;kind++){
            boolean attached=kind==18||kind==19||kind==21||kind==23;
            Vec3 origin=attached?feet.add(0,1,0):feet;
            invoke("clear");level.gameTime=100;partial(.5F);movePlayer(feet,feet);
            worldEvent(platform,kind,tracked,origin,origin.add(0,1,0),1.5F);
            Object key=events.keySet().iterator().next(),raw=events.get(key);
            for(int frame=1;frame<=3;frame++){
                Vec3 previous=feet.add(step.scale(frame-1)),current=feet.add(step.scale(frame));movePlayer(previous,current);
                level.gameTime=100+frame;partial(.5F);byte[] actual=render(0);
                check(events.get(key)==raw,"World follow preserves authoritative scene: "+kind);
                Vec3 expected=attached?previous.lerp(current,.5).add(0,1,0):origin;
                level.gameTime=100;worldEvent(platform,kind,untracked,expected,expected.add(0,1,0),1.5F);
                level.gameTime=100+frame;byte[] reference=render(0);
                check(energy(reference)>1000,"World follow reference is visible: "+kind);
                check(pixelDifference(actual,reference)==0,"World attachment/anchor follows current placement: "+kind+":"+frame);
                events.put(key,raw);compared++;
            }
        }
        invoke("clear");set(clientLevel,"players",new ArrayList<>());set(minecraft,"player",null);level.gameTime=100;clientData.setGameTime(100);partial(.5F);
        System.out.println("PRODUCTION_WORLD_FOLLOW_FRAMES="+compared+" MAX_PIXEL_DIFF=0");
    }
    static void schoolEvent(SpellDefinition spell,int phase)throws Exception{
        level.gameTime=100;partial(.5F);invoke("clear");
        Vec3 origin=new Vec3(0,phase==2?-.3:-1.7,-6),end=phase==2?origin.add(0,0,1):origin;
        packet(spell,phase,phase==2?3:19,new UUID(99,100),origin,end,100,11);
    }
    static void schoolDetails(Path out,String platform)throws Exception{
        List<SpellDefinition> spells=new ArrayList<>();
        for(MagicSchool school:MagicSchool.values())spells.add(SpellCatalogue.all().stream().filter(s->s.school()==school).findFirst().orElseThrow());
        isolateDetails=true;invoke("requestReload");double min=1;
        for(int phase:new int[]{2,1}){
            List<byte[]> masks=new ArrayList<>();
            for(SpellDefinition spell:spells){
                schoolEvent(spell,phase);byte[] pixels=render(0);masks.add(pixels);
                check(energy(pixels)>1000,"School details contribute pixels without volume body: "+spell.school()+" phase="+phase);
                check(energy(render(1))==0,"School details obey depth: "+spell.school()+" phase="+phase);
                level.gameTime=103;check(pixelDifference(pixels,render(0))>100,"School details animate: "+spell.school()+" phase="+phase);
            }
            for(int a=0;a<masks.size();a++)for(int b=a+1;b<masks.size();b++){
                double difference=maskDifference(masks.get(a),masks.get(b));min=java.lang.Math.min(min,difference);
                check(difference>.08,"Monochrome school shapes differ: "+spells.get(a).school()+"/"+spells.get(b).school()+" phase="+phase+" difference="+difference);
            }
        }
        isolateDetails=false;invoke("requestReload");
        for(SpellDefinition spell:spells)for(int phase:new int[]{2,1}){
            schoolEvent(spell,phase);byte[] pixels=render(0);
            check(energy(pixels)>1000,"Production school composition visible: "+spell.school());
            image(pixels,out.resolve("school-"+spell.school().name().toLowerCase(Locale.ROOT)+(phase==2?"-impact":"-domain")+".png"));
        }
        System.out.println("PRODUCTION_SCHOOL_DETAILS=14 PHASES=2 MONOCHROME_PAIRS=182 MIN_MASK_DIFFERENCE="+min);
    }
    static void deliveryEvent(SpellDefinition spell,int mechanic,int phase,float age)throws Exception{
        level.gameTime=100;partial(.5F);invoke("clear");
        boolean segment=phase==1&&Set.of(0,1,2,5,10,16,17).contains(mechanic);
        boolean floor=Set.of(6,7,9,12,14,18,19).contains(mechanic);
        Vec3 origin=new Vec3(segment?-2:0,floor?-1.65:-.8,-7);
        Vec3 end=segment?new Vec3(2,.1,-7):origin.add(mechanic==8?new Vec3(0,-1,0):new Vec3(0,0,-1));
        packet(spell,phase,mechanic,new UUID(99,100),origin,end,100,age);
    }
    static void deliveryDetails(Path out,String platform)throws Exception{
        // Fix the spell, school and color: these cases must differ by mechanism geometry.
        SpellDefinition spell=SpellCatalogue.byId("fire_bolt");
        var coverage=new com.google.gson.JsonArray();
        List<byte[]> baseline=new ArrayList<>();
        suppressDelivery=true;invoke("requestReload");
        for(MagicMechanic mechanic:MagicMechanic.values()){
            deliveryEvent(spell,mechanic.ordinal(),1,11);baseline.add(render(0));
        }
        suppressDelivery=false;invoke("requestReload");long minimum=Long.MAX_VALUE;
        for(MagicMechanic mechanic:MagicMechanic.values()){
            deliveryEvent(spell,mechanic.ordinal(),1,11);byte[] visible=render(0);
            long contribution=pixelDifference(visible,baseline.get(mechanic.ordinal()));
            minimum=java.lang.Math.min(minimum,contribution);
            check(contribution>1000,"New attack geometry adds visible pixels: "+mechanic+" RGB="+contribution);
            check(energy(render(1))==0,"Attack geometry respects world depth: "+mechanic);
            level.gameTime=103;check(pixelDifference(visible,render(0))>100,"Attack structure animates: "+mechanic);
            level.gameTime=160;check(energy(render(0))==0,"Attack structure expires: "+mechanic);
            MagicSchool school=MagicSchool.values()[mechanic.ordinal()%MagicSchool.values().length];
            SpellDefinition preview=SpellCatalogue.all().stream().filter(s->s.school()==school&&s.primaryMechanic()==mechanic&&!s.superTier()).findFirst().orElseThrow();
            deliveryEvent(preview,mechanic.ordinal(),1,11);byte[] composition=render(0);
            image(composition,out.resolve("delivery-"+mechanic.name().toLowerCase(Locale.ROOT)+".png"));
            var row=new com.google.gson.JsonObject();row.addProperty("mechanic",mechanic.name());row.addProperty("addedRgb",contribution);
            row.addProperty("previewSpell",preview.id());row.addProperty("previewName",preview.zhName());coverage.add(row);
        }
        baseline.clear();isolateDetails=true;invoke("requestReload");List<byte[]> masks=new ArrayList<>();double minMask=1;
        for(MagicMechanic mechanic:MagicMechanic.values()){
            deliveryEvent(spell,mechanic.ordinal(),1,11);byte[] pixels=render(0);masks.add(pixels);
            check(energy(pixels)>1000,"Attack detail survives without the volume and color: "+mechanic);
        }
        for(int a=0;a<masks.size();a++)for(int b=a+1;b<masks.size();b++){
            double difference=maskDifference(masks.get(a),masks.get(b));minMask=java.lang.Math.min(minMask,difference);
            check(difference>.08,"Monochrome attack structures differ: "+MagicMechanic.values()[a]+"/"+MagicMechanic.values()[b]+" diff="+difference);
        }
        masks.clear();isolateDetails=false;
        for(int tier:new int[]{5,8}){
            SpellDefinition high=SpellCatalogue.all().stream().filter(s->s.tier()==tier).findFirst().orElseThrow();
            for(int phase=0;phase<4;phase++){
                suppressDelivery=true;invoke("requestReload");deliveryEvent(high,19,phase,11);byte[] before=render(0);
                suppressDelivery=false;invoke("requestReload");deliveryEvent(high,19,phase,11);byte[] visible=render(0);
                check(pixelDifference(visible,before)>1000,"High-tier structure contributes: "+tier+":"+phase);
                check(energy(render(1))==0,"High-tier structure obeys depth: "+tier+":"+phase);
                level.gameTime=103;check(pixelDifference(visible,render(0))>100,"High-tier structure evolves: "+tier+":"+phase);
                level.gameTime=160;check(energy(render(0))==0,"High-tier structure expires: "+tier+":"+phase);
                image(visible,out.resolve("tier-"+tier+"-phase-"+phase+".png"));
            }
        }
        // Inside a shell, the far intersection must still be drawn and clipped by depth.
        for(int mechanic:new int[]{13,19}){
            invoke("clear");level.gameTime=100;partial(.5F);isolateDetails=true;invoke("requestReload");
            Vec3 center=new Vec3(0,-.3,-.2);
            packet(spell,1,mechanic,new UUID(99,100),center,center.add(0,0,-1),100,11);
            check(energy(render(0))>1000,"Inside-shell detail remains visible: "+mechanic);
            check(energy(render(1))==0,"Inside-shell detail obeys depth: "+mechanic);
        }
        isolateDetails=false;invoke("requestReload");deliveryEvent(spell,1,1,11);byte[] before=render(0);
        invoke("requestReload");deliveryEvent(spell,1,1,11);
        check(pixelDifference(before,render(0))==0,"Delivery resource reload is deterministic");
        Files.writeString(out.resolve("delivery-coverage.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(coverage));
        System.out.println("PRODUCTION_DELIVERY_DETAILS=20 MONOCHROME_PAIRS=190 HIGH_TIER_STAGES=8 MIN_ADDED_RGB="+minimum+" MIN_MASK_DIFFERENCE="+minMask);
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
    static void depthConventions(Path out,String platform)throws Exception{
        var spell=SpellCatalogue.all().get(13);
        for(boolean reversed:new boolean[]{true,false}){
            camera.projectionMatrix=new Matrix4f().setPerspective((float)java.lang.Math.toRadians(60),(float)WIDTH/HEIGHT,
                reversed?256:.05f,reversed?.05f:256,device.getDeviceInfo().isZZeroToOne());
            camera.cullFrustum=new net.minecraft.client.renderer.culling.Frustum(camera.viewRotationMatrix,camera.projectionMatrix);
            camera.cullFrustum.prepare(0,0,0);
            for(int mechanic:new int[]{2,8,13}){
                invoke("clear");insert(spell,1,mechanic,Vec3.ZERO,100,11);
                long visible=energy(render(reversed?0:1));
                check(visible>1000,"Magic vanished with "+(reversed?"reversed":"forward")+" world depth: "+mechanic);
                image(render(reversed?0:1),out.resolve(platform+"-"+(reversed?"reversed":"forward")+"-"+mechanic+".png"));
                check(energy(render(reversed?1:0))==0,"COMPAT REPRO: foreground does not occlude magic with "+(reversed?"reversed":"forward")+" world depth: "+mechanic);
                System.out.println("PRODUCTION_DEPTH_MODE="+(reversed?"reversed":"forward")+" MECHANIC="+mechanic+" VISIBLE="+visible+" FOREGROUND=occluded");
            }
            for(int kind:new int[]{0,1,4,7,18,23}){
                invoke("clear");level.gameTime=100;partial(.5F);worldEvent(platform,kind);
                level.gameTime=kind<2?101:106;clientData.setGameTime(level.gameTime);
                check(energy(render(reversed?0:1))>1000,"World detail vanished with "+(reversed?"reversed":"forward")+" depth: "+kind);
                check(energy(render(reversed?1:0))==0,"World detail leaks through foreground with "+(reversed?"reversed":"forward")+" depth: "+kind);
            }
            level.gameTime=100;clientData.setGameTime(100);
        }
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
                    if((suppressRestoration||isolateRestoration)&&location.getPath().endsWith("world_detail.fsh"))
                        return Optional.of(new Resource(null,()->{
                            String shader=Files.readString(path);
                            if(suppressRestoration)shader=shader.replace("vec2 restoration=world?restorationDetail(","vec2 restoration=world?0.0*restorationDetail(");
                            if(isolateRestoration)shader=shader.replace("vec3 tint=TintTier.rgb","vec3 tint=vec3(1)")
                                .replace("vec2 restoration=world?restorationDetail(","light=0.0;accent=0.0;vec2 restoration=world?restorationDetail(");
                            return new java.io.ByteArrayInputStream(shader.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        }));
                    if((suppressCombat||isolateCombat)&&location.getPath().endsWith("world_detail.fsh"))
                        return Optional.of(new Resource(null,()->{
                            String shader=Files.readString(path);
                            if(suppressCombat)shader=shader.replace("vec2 combat=world?combatDetail(","vec2 combat=world?0.0*combatDetail(");
                            if(isolateCombat)shader=shader.replace("vec3 tint=TintTier.rgb","vec3 tint=vec3(1)")
                                .replace("vec2 combat=world?combatDetail(","light=0.0;accent=0.0;vec2 combat=world?combatDetail(");
                            return new java.io.ByteArrayInputStream(shader.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        }));
                    if(suppressDelivery&&location.getPath().endsWith("world_detail.fsh"))
                        return Optional.of(new Resource(null,()->new java.io.ByteArrayInputStream(Files.readString(path)
                            .replace("light+=deliveryDetail(","light+=0.0*deliveryDetail(")
                            .replace("accent+=exaltedDetail(","accent+=0.0*exaltedDetail(").getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                    if(isolateDetails&&location.getPath().endsWith("magic_volume.fsh"))
                        return Optional.of(new Resource(null,()->new java.io.ByteArrayInputStream((Files.readString(path).replace("void main()", "void bodyMain()")+"\nvoid main(){fragColor=vec4(0);}\n").getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                    if(isolateDetails&&location.getPath().endsWith("world_detail.fsh"))
                        return Optional.of(new Resource(null,()->new java.io.ByteArrayInputStream(Files.readString(path).replace("vec3 tint=TintTier.rgb", "vec3 tint=vec3(1)").getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                    if(suppressDetails&&location.getPath().endsWith("world_detail.fsh"))
                        return Optional.of(new Resource(null,()->new java.io.ByteArrayInputStream(Files.readString(path).replace("fragColor=vec4(color,0);","fragColor=vec4(0);").getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                    return Files.exists(path)?Optional.of(new Resource(null,()->Files.newInputStream(path))):Optional.empty();
                }
            });
            production=Class.forName("com.teamlifebind."+platform+".MagicShaderRenderer");
            try(var bytes=production.getResourceAsStream("MagicShaderRenderer.class")){
                String digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes()));
                System.out.println("PRODUCTION_RENDERER_SHA256="+digest);
            }
            for(String shader:List.of("magic_volume.vsh","magic_volume.fsh","magic_composite.vsh","magic_composite.fsh","world_detail.fsh")){
                byte[] bytes=Files.readAllBytes(root.resolve(platform+"/src/main/resources/assets/teamlifebind/shaders/core/"+shader));
                System.out.println("PRODUCTION_SHADER_SHA256="+shader+":"+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
            }
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
            if(Boolean.getBoolean("tlb.probe.depthConventions")){
                depthConventions(out,platform);invoke("close");target.destroyBuffers();
                System.out.println("PASS depth conventions "+platform+" assertions="+assertions);return;
            }
            if(Boolean.getBoolean("tlb.probe.restorationDetails")){
                restorationDetails(out,platform);invoke("close");target.destroyBuffers();
                System.out.println("PASS restoration details "+platform+" assertions="+assertions);return;
            }
            if(Boolean.getBoolean("tlb.probe.combatDetails")){
                combatDetails(out,platform);invoke("close");target.destroyBuffers();
                System.out.println("PASS combat details "+platform+" assertions="+assertions);return;
            }
            if(Boolean.getBoolean("tlb.probe.worldEffects")){
                worldEffects(out,platform);invoke("close");target.destroyBuffers();
                System.out.println("PASS world VFX "+platform+" assertions="+assertions);return;
            }
            if(Boolean.getBoolean("tlb.probe.schoolDetails")){
                schoolDetails(out,platform);invoke("close");target.destroyBuffers();
                System.out.println("PASS school details "+platform+" assertions="+assertions);return;
            }
            if(Boolean.getBoolean("tlb.probe.deliveryDetails")){
                deliveryDetails(out,platform);invoke("close");target.destroyBuffers();
                System.out.println("PASS delivery details "+platform+" assertions="+assertions);return;
            }
            if(Boolean.getBoolean("tlb.probe.attackStages")){
                attackStages(out,platform);invoke("close");target.destroyBuffers();
                System.out.println("PASS attack stages "+platform+" assertions="+assertions);return;
            }
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
