import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.sdl.SDLError.*;

/** Actual driver compilation and rendering of the production GLSL, without a visible window. */
public final class MagicShaderGpuProbe {
    static Path source, output;
    static int volume, composite, vao, vertexBuffer, uniformBuffer;
    static int assertions;
    static int shapeOverride = -1;
    static int qualitySteps = 32;
    static int school = 8, frameBytes;
    static final List<String> evidence = new ArrayList<>();
    static final Vector3f DEFAULT_CENTER = new Vector3f(0, -1.65f, -8);
    static final Vector3f DEFAULT_AXIS = new Vector3f(0, 1, 0);
    static final float[] CYAN = {0.08f, 0.75f, 1f};
    record Frame(Matrix4f inverse, boolean zeroToOne, int width, int height) {}
    record Capture(float[] volume, float[] color, int width, int height) {}

    public static void main(String[] args) throws Exception {
        source = args.length > 2 ? Path.of(args[2]) : Path.of(args[0], "fabric/src/main/resources/assets/teamlifebind/shaders/core");
        frameBytes = Files.readString(source.resolve("magic_volume.fsh")).contains("vec4 Identity;") ? 192 : 176;
        output = Path.of(args[1]);
        Files.createDirectories(output.resolve("preview"));
        check(SDL_Init(SDL_INIT_VIDEO), "SDL video initialization: " + SDL_GetError());
        long window = 0, context = 0;
        try {
            SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 3);
            SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 3);
            SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_CORE);
            SDL_GL_SetAttribute(SDL_GL_DOUBLEBUFFER, 0);
            window = SDL_CreateWindow("TLB offscreen shader verification", 32, 32, SDL_WINDOW_OPENGL | SDL_WINDOW_HIDDEN);
            check(window != 0, "SDL hidden window: " + SDL_GetError());
            check((SDL_GetWindowFlags(window) & SDL_WINDOW_HIDDEN) != 0, "Context window stays hidden");
            context = SDL_GL_CreateContext(window);
            check(context != 0 && SDL_GL_MakeCurrent(window, context), "Hidden OpenGL context: " + SDL_GetError());
            GL.createCapabilities();
            note("GL_VENDOR=" + glGetString(GL_VENDOR));
            note("GL_RENDERER=" + glGetString(GL_RENDERER));
            note("GL_VERSION=" + glGetString(GL_VERSION));
            note("No reference JAR loaded; original production GLSL compiled without substitutions.");
            volume = program("magic_volume"); composite = program("magic_composite");
            for (int program : new int[]{volume, composite}) {
                int block = glGetUniformBlockIndex(program, "MagicFrame");
                check(block != GL_INVALID_INDEX, "MagicFrame active in program " + program);
                check(glGetActiveUniformBlocki(program, block, GL_UNIFORM_BLOCK_DATA_SIZE) == frameBytes, "MagicFrame std140 size is " + frameBytes);
                glUniformBlockBinding(program, block, 0);
            }
            invalidShaderDiagnostics();
            vao = glGenVertexArrays(); glBindVertexArray(vao);
            vertexBuffer = glGenBuffers(); glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
            glBufferData(GL_ARRAY_BUFFER, new float[]{-1,-1,0, 3,-1,0, -1,3,0}, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, false, 12, 0);
            uniformBuffer = glGenBuffers(); glBindBuffer(GL_UNIFORM_BUFFER, uniformBuffer);
            glBufferData(GL_UNIFORM_BUFFER, frameBytes, GL_DYNAMIC_DRAW); glBindBufferBase(GL_UNIFORM_BUFFER, 0, uniformBuffer);
            if (args.length > 3 && args[3].equals("identity")) { identityChecks(); profileChecks(); deliveryChecks(); }
            else { runChecks(); identityChecks(); profileChecks(); deliveryChecks(); }
            check(glGetError() == GL_NO_ERROR, "No final OpenGL errors");
            note("PASS: " + assertions + " assertions");
            Files.writeString(output.resolve("gpu-summary.txt"), String.join("\n", evidence) + "\n");
        } finally {
            if (context != 0) SDL_GL_DestroyContext(context);
            if (window != 0) SDL_DestroyWindow(window);
            SDL_Quit();
        }
    }

    static void runChecks() throws Exception {
        Capture clear = render(192, 108, false, 13, 1, 4, .55f, 2, 0, DEFAULT_CENTER, DEFAULT_AXIS, 2.4f, 2.4f, CYAN, 4);
        check(energy(clear.volume) > 1, "Shield produces actual GPU emission");
        Capture blocked = render(192,108,false,13,1,4,.55f,2,1,DEFAULT_CENTER,DEFAULT_AXIS,2.4f,2.4f,CYAN,4);
        check(max(blocked.volume) < 1e-6, "Foreground opaque depth fully occludes volume");
        Capture blockedZO = render(192,108,true,13,1,4,.55f,2,1,DEFAULT_CENTER,DEFAULT_AXIS,2.4f,2.4f,CYAN,4);
        check(max(blockedZO.volume) < 1e-6, "Zero-to-one reversed-Z foreground occludes volume");
        Capture clearZO = render(192,108,true,13,1,4,.55f,2,0,DEFAULT_CENTER,DEFAULT_AXIS,2.4f,2.4f,CYAN,4);
        check(meanDifference(clear.volume, clearZO.volume) < .0008, "GL and zero-to-one reversed-Z reconstruct the same effect");
        Capture partial = render(192,108,false,13,1,4,.55f,2,2,DEFAULT_CENTER,DEFAULT_AXIS,2.4f,2.4f,CYAN,4);
        check(regionEnergy(partial.volume,192,108,0,92) < .00001, "Foreground half-wall rejects hidden-side volume");
        check(regionEnergy(partial.volume,192,108,99,192) > .1, "Visible half remains visible");
        check(meanBackgroundDifference(partial, 0, 91) < .00001, "Bilateral glow does not paint foreground wall");
        Capture initial = render(192,108,false,13,1,4,0,2,0,DEFAULT_CENTER,DEFAULT_AXIS,2.4f,2.4f,CYAN,4);
        check(max(initial.volume) == 0, "Age zero has no premature flash");
        check(meanBackgroundDifference(initial,0,192) < .00001, "Empty effect leaves scene unchanged");
        Capture expired = render(192,108,false,13,1,4,2.5f,2,0,DEFAULT_CENTER,DEFAULT_AXIS,2.4f,2.4f,CYAN,4);
        check(max(expired.volume) == 0, "Expired effect fully disappears");
        Capture behind = render(192,108,false,13,1,4,.55f,2,0,new Vector3f(0,0,8),DEFAULT_AXIS,2.4f,2.4f,CYAN,4);
        check(max(behind.volume) == 0, "Behind-camera bounds do not leak on screen");
        Capture inside = render(192,108,false,13,1,4,.55f,2,0,new Vector3f(0,0,-.15f),DEFAULT_AXIS,2.4f,2.4f,CYAN,4);
        check(energy(inside.volume) > .01, "Camera inside a volume renders finitely");
        Set<Long> images = new HashSet<>();
        for (int mechanic = 0; mechanic < 20; mechanic++) {
            Capture previous = null;
            for (int phase = 0; phase < 4; phase++) {
                Capture c = render(160,90,false,mechanic,phase,phase%2==0?8:4,.55f,2,0,DEFAULT_CENTER,
                                   new Vector3f(.7f,.2f,-.4f).normalize(),2.4f,3.8f,CYAN,mechanic*11);
                check(energy(c.volume) > .001, "Mechanic " + mechanic + " phase " + phase + " nonempty");
                if (previous != null) check(meanDifference(c.volume,previous.volume) > .00001, "Mechanic " + mechanic + " phase transition changes image");
                previous = c;
                images.add(fingerprint(c.volume));
            }
        }
        check(images.size() >= 70, "Geometric profile/phase renders are distinct: " + images.size() + "/80");
        note("MECHANIC_PHASE_IMAGES=" + images.size() + "/80 distinct");
        Set<Long> shapes = new HashSet<>();
        for(int shape=0;shape<12;shape++) {
            shapeOverride=shape;
            Capture c=render(240,135,false,9,0,5,.55f,2,0,new Vector3f(0,-2.4f,-8),DEFAULT_AXIS,3.4f,3.4f,CYAN,17);
            check(energy(c.volume)>.001,"Seal shape "+shape+" nonempty");shapes.add(fingerprint(c.volume));
        }
        shapeOverride=-1;
        check(shapes.size()==12,"All twelve analytic seals produce distinct GPU images");
        note("SEAL_IMAGES=" + shapes.size() + "/12 distinct");
        preview("01-casting-seal",9,0,4,.65f,new Vector3f(0,-2.40f,-8),DEFAULT_AXIS,3.4f,3.4f,new float[]{.09f,.6f,1f},3);
        preview("02-lattice-shield",13,1,5,.55f,new Vector3f(0,-.7f,-8),DEFAULT_AXIS,2.65f,2.65f,CYAN,16);
        preview("03-accelerator-beam",1,1,7,.55f,new Vector3f(0,-.5f,-8),new Vector3f(.80f,.15f,.30f).normalize(),1.4f,4.8f,new float[]{.85f,.18f,.06f},23);
        preview("04-vortex",7,1,7,.65f,new Vector3f(0,-.7f,-8),DEFAULT_AXIS,2.9f,3.4f,new float[]{.4f,.14f,1f},52);
        preview("05-super-domain",19,2,8,.70f,new Vector3f(0,-2.4f,-8),DEFAULT_AXIS,4.1f,4.1f,new float[]{1f,.55f,.06f},283);
        preview("06-super-orbit",4,1,8,.8f,new Vector3f(0,-.3f,-8),DEFAULT_AXIS,3.1f,3.1f,new float[]{.5f,.16f,1f},294);
        save(render(960,540,false,13,1,5,.55f,2,2,new Vector3f(0,-.7f,-8),DEFAULT_AXIS,2.65f,2.65f,CYAN,16),"07-depth-half-wall");
        contactSheet();
        benchmark(1,960,540,48);benchmark(8,960,540,48);
        benchmark(8,960,540,24);benchmark(8,640,360,24);
    }

    static void identityChecks() throws Exception {
        String[] labels={"FIRE","WATER","ICE","WIND","EARTH","LIGHTNING","LIGHT","SHADOW","ARCANE","NATURE","GRAVITY","TIME","BLOOD","SPIRIT"};
        int oldSchool=school;
        List<String> failures=new ArrayList<>();
        for(int mechanic=0;mechanic<20;mechanic++) {
            List<Capture> captures=new ArrayList<>();
            BufferedImage sheet=new BufferedImage(7*320,2*210+70,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=sheet.createGraphics();g.setColor(new Color(10,14,23));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
            g.setFont(new Font("Dialog",Font.BOLD,20));g.setColor(Color.WHITE);
            g.drawString("SAME "+mechanicName(mechanic)+" / same geometry, phase, seed, WHITE tint / ONLY school identity changes",20,28);
            g.setFont(new Font("Dialog",Font.PLAIN,14));g.drawString("Real GPU output; synthetic color + depth scene. Structural differences must survive removing all school colors.",20,52);
            for(school=0;school<14;school++) {
                Capture c=render(320,180,false,mechanic,1,5,.65f,2,0,new Vector3f(0,-.5f,-8),
                    mechanic==1?new Vector3f(.9f,.25f,.2f).normalize():DEFAULT_AXIS,2.5f,3.9f,new float[]{.8f,.8f,.8f},17);
                captures.add(c);check(energy(c.volume)>.1,"School "+school+" mechanic "+mechanic+" visible");
                String name="identity-"+mechanic+"-"+school;save(c,name,false);
                int x=(school%7)*320,y=70+(school/7)*210;
                g.drawImage(ImageIO.read(output.resolve("preview/"+name+".png").toFile()),x,y,null);
                g.setColor(Color.WHITE);g.drawString(labels[school],x+12,y+198);
            }
            g.dispose();ImageIO.write(sheet,"png",output.resolve("preview/identity-"+mechanic+"-monochrome.png").toFile());
            double closest=1;String pair="";
            for(int a=0;a<14;a++)for(int b=a+1;b<14;b++) {
                double difference=silhouetteDifference(captures.get(a).volume,captures.get(b).volume);
                if(difference<closest){closest=difference;pair=labels[a]+"/"+labels[b];}
                if(difference<.08)failures.add("mechanic "+mechanic+" "+labels[a]+"/"+labels[b]+" silhouette delta="+difference);
            }
            note("SCHOOL_IDENTITY mechanic="+mechanic+" min_normalized_silhouette_delta="+closest+" closest="+pair+" required>=0.08");
        }
        school=oldSchool;
        note("SCHOOL_IDENTITY_FAILURES="+failures.size());
        if(!failures.isEmpty())note("FIRST_IDENTITY_FAILURE="+failures.get(0));
        check(failures.isEmpty(),"Schools must change visible structure at identical color, camera, mechanic, shape and seed; "+failures.size()+" collapsed pairs");
    }
    static String mechanicName(int value) {
        return new String[]{"BOLT","BEAM","CONE","NOVA","ORBIT","CHAIN","WAVE","VORTEX","METEOR","MINE","DASH","BLINK","WALL","SHIELD","SUMMON","MARK","DRAIN","CHANNEL","ECHO","DOMAIN"}[value];
    }
    static void profileChecks() throws Exception {
        int oldSchool=school;school=8;
        List<String> failures=new ArrayList<>();
        for(int mechanic:new int[]{1,13,19})for(int phase=1;phase<4;phase++) {
            List<Capture> captures=new ArrayList<>();
            for(int shape=0;shape<12;shape++) {
                shapeOverride=shape;
                Capture c=render(192,108,false,mechanic,phase,5,.65f,2,0,new Vector3f(0,-.5f,-8),
                    mechanic==1?new Vector3f(.9f,.25f,.2f).normalize():DEFAULT_AXIS,2.5f,3.9f,new float[]{.8f,.8f,.8f},17);
                captures.add(c);check(energy(c.volume)>.1,"Profile "+shape+" phase "+phase+" mechanism "+mechanic+" visible");
            }
            double closest=1;
            for(int a=0;a<12;a++)for(int b=a+1;b<12;b++) {
                double difference=silhouetteDifference(captures.get(a).volume,captures.get(b).volume);
                closest=Math.min(closest,difference);
                if(difference<.01) failures.add("mechanic="+mechanic+" phase="+phase+" shapes="+a+"/"+b+" silhouette delta="+difference);
            }
            note("PHASE_PROFILE mechanic="+mechanic+" phase="+phase+" min_normalized_silhouette_delta="+closest+" required>=0.01");
        }
        shapeOverride=-1;school=oldSchool;
        note("PROFILE_FAILURES="+failures.size());
        if(!failures.isEmpty())note("FIRST_PROFILE_FAILURE="+failures.get(0));
        check(failures.isEmpty(),"Travel, impact and finish profiles must all change occupied geometry; "+failures.size()+" collapsed pairs");
    }
    static void deliveryChecks() throws Exception {
        int oldSchool=school;List<String> failures=new ArrayList<>();
        for(school=0;school<14;school++) {
            List<Capture> captures=new ArrayList<>();
            BufferedImage sheet=school==8?new BufferedImage(5*320,4*210+70,BufferedImage.TYPE_INT_RGB):null;
            Graphics2D g=sheet==null?null:sheet.createGraphics();
            if(g!=null) {
                g.setColor(new Color(10,14,23));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
                g.setColor(Color.WHITE);g.setFont(new Font("Dialog",Font.BOLD,20));
                g.drawString("ARCANE / same camera, color, shape, seed / twenty delivery mechanisms",20,28);
                g.setFont(new Font("Dialog",Font.PLAIN,14));
                g.drawString("Real GPU output; the delivery transforms the school structure to match the spell's world-space action.",20,52);
            }
            for(int mechanic=0;mechanic<20;mechanic++) {
                Capture c=render(320,180,false,mechanic,1,5,.65f,2,0,new Vector3f(0,-.5f,-8),
                    new Vector3f(.9f,.25f,.2f).normalize(),2.5f,3.9f,new float[]{.8f,.8f,.8f},17);
                captures.add(c);check(energy(c.volume)>.1,"School "+school+" delivery "+mechanic+" visible");
                if(g!=null) {
                    String name="delivery-arcane-"+mechanic;save(c,name,false);
                    int x=(mechanic%5)*320,y=70+(mechanic/5)*210;
                    g.drawImage(ImageIO.read(output.resolve("preview/"+name+".png").toFile()),x,y,null);
                    g.setColor(Color.WHITE);g.drawString(mechanicName(mechanic),x+12,y+198);
                }
            }
            if(g!=null){g.dispose();ImageIO.write(sheet,"png",output.resolve("preview/delivery-arcane-monochrome.png").toFile());}
            double closest=1;String pair="";
            for(int a=0;a<20;a++)for(int b=a+1;b<20;b++) {
                double difference=silhouetteDifference(captures.get(a).volume,captures.get(b).volume);
                if(difference<closest){closest=difference;pair=mechanicName(a)+"/"+mechanicName(b);}
                if(difference<.03) failures.add("school="+school+" delivery="+a+"/"+b+" silhouette delta="+difference);
            }
            note("DELIVERY_IDENTITY school="+school+" min_normalized_silhouette_delta="+closest+" closest="+pair+" required>=0.03");
        }
        school=oldSchool;note("DELIVERY_FAILURES="+failures.size());
        if(!failures.isEmpty())note("FIRST_DELIVERY_FAILURE="+failures.get(0));
        check(failures.isEmpty(),"A school's twenty delivery mechanics must retain different visible structures; "+failures.size()+" collapsed pairs");
    }
    static double silhouetteDifference(float[] a,float[] b) {
        float peakA=0,peakB=0;for(int i=0;i<a.length;i+=4){peakA=Math.max(peakA,a[i]+a[i+1]+a[i+2]);peakB=Math.max(peakB,b[i]+b[i+1]+b[i+2]);}
        int union=0,different=0;for(int i=0;i<a.length;i+=4){boolean x=a[i]+a[i+1]+a[i+2]>.10f*peakA,y=b[i]+b[i+1]+b[i+2]>.10f*peakB;if(x||y)union++;if(x!=y)different++;}
        return different/(double)Math.max(1,union);
    }

    static Capture render(int w,int h,boolean zo,int mechanic,int phase,int tier,float age,float duration,int depthMode,
                          Vector3f center,Vector3f axis,float radius,float extent,float[] tint,float seed) {
        Frame f = frame(w,h,zo);
        int depth=texture(w,h,GL_R32F,GL_RED,depth(f,depthMode));
        int glow=texture(w,h,GL_RGBA16F,GL_RGBA,null), glowFbo=fbo(glow);
        int result=texture(w,h,GL_RGBA32F,GL_RGBA,background(w,h)), resultFbo=fbo(result);
        uniforms(f,center,axis,radius,extent,tint,tier,age,duration,phase,mechanic,seed);
        glViewport(0,0,w,h); glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE); glDisable(GL_BLEND);
        glBindFramebuffer(GL_FRAMEBUFFER,glowFbo); glClearColor(0,0,0,0); glClear(GL_COLOR_BUFFER_BIT);
        glUseProgram(volume); sample(volume,"SceneDepth",0,depth); glDrawArrays(GL_TRIANGLES,0,3);
        float[] emission=read(w,h);
        glBindFramebuffer(GL_FRAMEBUFFER,resultFbo); glUseProgram(composite);
        sample(composite,"MagicColor",0,glow); sample(composite,"SceneDepth",1,depth);
        glEnable(GL_BLEND); glBlendFunc(GL_ONE,GL_ONE); glDrawArrays(GL_TRIANGLES,0,3); glDisable(GL_BLEND);
        float[] color=read(w,h);
        check(glGetError()==GL_NO_ERROR,"GL pass " + mechanic + "/" + phase + " succeeds");
        check(finite(emission)&&finite(color),"Finite output " + mechanic + "/" + phase);
        glDeleteFramebuffers(glowFbo); glDeleteFramebuffers(resultFbo);
        glDeleteTextures(depth);glDeleteTextures(glow);glDeleteTextures(result);
        return new Capture(emission,color,w,h);
    }

    static Frame frame(int w,int h,boolean zo) {
        Matrix4f p=new Matrix4f().setPerspective((float)Math.toRadians(60),w/(float)h,256f,.05f,zo);
        Matrix4f v=new Matrix4f().lookAt(0,0,0,0,-.15f,-1,0,1,0);
        return new Frame(p.mul(v).invert(),zo,w,h);
    }
    static FloatBuffer depth(Frame f,int mode) {
        FloatBuffer b=BufferUtils.createFloatBuffer(f.width*f.height);
        Matrix4f vp=new Matrix4f(f.inverse).invert();
        for(int y=0;y<f.height;y++)for(int x=0;x<f.width;x++) {
            Vector4f p=new Vector4f((x+.5f)/f.width*2-1,(y+.5f)/f.height*2-1,f.zeroToOne?0:-1,1);
            f.inverse.transform(p);Vector3f ray=new Vector3f(p.x/p.w,p.y/p.w,p.z/p.w).normalize();
            float distance=ray.y<0?-2.5f/ray.y:1e6f;
            if(mode==1||(mode==2&&x<f.width/2))distance=Math.min(distance,-3f/ray.z);
            if(distance>999999){b.put(0);continue;}
            Vector4f point=new Vector4f(ray.x*distance,ray.y*distance,ray.z*distance,1);vp.transform(point);
            float d=point.z/point.w;if(!f.zeroToOne)d=d*.5f+.5f;b.put(Math.max(0,d));
        }
        return b.flip();
    }
    static void uniforms(Frame f,Vector3f center,Vector3f axis,float radius,float extent,float[] tint,int tier,float age,float duration,int phase,int mechanic,float seed) {
        ByteBuffer data=BufferUtils.createByteBuffer(frameBytes);FloatBuffer b=data.asFloatBuffer();f.inverse.get(0,b);b.position(16);
        b.put(new float[]{f.width,f.height,age,f.zeroToOne?1:0, center.x,center.y,center.z,radius,
                         axis.x,axis.y,axis.z,extent,tint[0],tint[1],tint[2],tier,age,duration,phase,mechanic,
                         shapeOverride<0?3:shapeOverride,shapeOverride<0?4:shapeOverride,
                         shapeOverride<0?7:shapeOverride,shapeOverride<0?11:shapeOverride,1,seed,1,qualitySteps});
        if (frameBytes == 192) b.put(new float[]{school,mechanic,0,0});
        glBindBuffer(GL_UNIFORM_BUFFER,uniformBuffer);glBufferSubData(GL_UNIFORM_BUFFER,0,data);
    }
    static FloatBuffer background(int w,int h) {
        FloatBuffer b=BufferUtils.createFloatBuffer(w*h*4);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            float line=((x/40+y/40)%2==0?.005f:0);
            b.put(.018f+line).put(.026f+line).put(.045f+line).put(1);
        }
        return b.flip();
    }
    static int texture(int w,int h,int format,int external,FloatBuffer data) {
        int t=glGenTextures();glBindTexture(GL_TEXTURE_2D,t);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D,0,format,w,h,0,external,GL_FLOAT,data);return t;
    }
    static int fbo(int t) {
        int f=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,f);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,t,0);
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"Framebuffer complete");return f;
    }
    static float[] read(int w,int h) {float[] b=new float[w*h*4];glReadPixels(0,0,w,h,GL_RGBA,GL_FLOAT,b);return b;}
    static void sample(int p,String name,int unit,int t){glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,t);glUniform1i(glGetUniformLocation(p,name),unit);}
    static int shader(int type,String text,String name) {
        int s=glCreateShader(type);glShaderSource(s,text);glCompileShader(s);
        if(glGetShaderi(s,GL_COMPILE_STATUS)==GL_FALSE)throw new IllegalStateException(name+": "+glGetShaderInfoLog(s));return s;
    }
    static int program(String name)throws Exception{
        String vs=Files.readString(source.resolve(name+".vsh")),fs=Files.readString(source.resolve(name+".fsh"));
        for(String ext:new String[]{".vsh",".fsh"}) note("SHA256 "+name+ext+"="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source.resolve(name+ext)))));
        int v=shader(GL_VERTEX_SHADER,vs,name+".vsh"),f=shader(GL_FRAGMENT_SHADER,fs,name+".fsh");int p=glCreateProgram();
        glAttachShader(p,v);glAttachShader(p,f);glLinkProgram(p);check(glGetProgrami(p,GL_LINK_STATUS)!=GL_FALSE,name+" driver link: "+glGetProgramInfoLog(p));
        glDeleteShader(v);glDeleteShader(f);return p;
    }
    static void invalidShaderDiagnostics() {
        int bad=glCreateShader(GL_FRAGMENT_SHADER);glShaderSource(bad,"#version 330\nvoid main(){ invalid_probe_token; }\n");glCompileShader(bad);
        check(glGetShaderi(bad,GL_COMPILE_STATUS)==GL_FALSE,"Invalid shader is rejected");
        String diagnostic=glGetShaderInfoLog(bad);check(!diagnostic.isBlank(),"Invalid shader returns diagnostic");
        note("Expected invalid GLSL diagnostic: "+diagnostic.replace('\n',' '));glDeleteShader(bad);
    }
    static void preview(String name,int mechanic,int phase,int tier,float age,Vector3f center,Vector3f axis,float radius,float extent,float[] tint,int seed)throws Exception{
        save(render(960,540,false,mechanic,phase,tier,age,2,0,center,axis,radius,extent,tint,seed),name);
    }
    static void save(Capture c,String name)throws Exception{
        save(c,name,true);
    }
    static void save(Capture c,String name,boolean announce)throws Exception{
        BufferedImage image=new BufferedImage(c.width,c.height,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<c.height;y++)for(int x=0;x<c.width;x++){
            int i=(y*c.width+x)*4;int rgb=0;
            for(int channel=0;channel<3;channel++)rgb=(rgb<<8)|Math.min(255,Math.max(0,Math.round(c.color[i+channel]*255)));
            image.setRGB(x,c.height-1-y,rgb);
        }
        ImageIO.write(image,"png",output.resolve("preview/"+name+".png").toFile());if(announce)note("PREVIEW="+name+".png");
    }
    static void contactSheet()throws Exception {
        BufferedImage sheet=new BufferedImage(1440,700,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=sheet.createGraphics();g.setColor(new Color(10,14,23));g.fillRect(0,0,1440,700);
        g.setColor(new Color(220,230,245));g.setFont(new Font("Dialog",Font.BOLD,24));
        g.drawString("TeamLifeBind | actual GPU shader preview",24,36);
        g.setFont(new Font("Dialog",Font.PLAIN,14));g.setColor(new Color(140,160,185));
        g.drawString("Original production GLSL / synthetic color + depth scene / no particles / not a Minecraft screenshot",24,62);
        String[] names={"01-casting-seal","02-lattice-shield","03-accelerator-beam","04-vortex","05-super-domain","06-super-orbit"};
        String[] labels={"Casting seal","Lattice shield","Accelerator beam","Volumetric vortex","Super-tier domain","Super-tier armillary"};
        for(int i=0;i<6;i++) {
            int x=(i%3)*480,y=90+(i/3)*300;
            g.drawImage(ImageIO.read(output.resolve("preview/"+names[i]+".png").toFile()),x,y,480,270,null);
            g.setColor(new Color(180,200,220));g.drawString(labels[i],x+18,y+290);
        }
        g.dispose();ImageIO.write(sheet,"png",output.resolve("preview/magic-shader-gpu-contact-sheet.png").toFile());
    }
    static void benchmark(int events,int width,int height,int steps)throws Exception {
        Frame frame=frame(width,height,false);qualitySteps=steps;
        int depth=texture(width,height,GL_R32F,GL_RED,depth(frame,0));
        int glow=texture(width,height,GL_RGBA16F,GL_RGBA,null),glowFbo=fbo(glow);
        int result=texture(width,height,GL_RGBA16F,GL_RGBA,null),resultFbo=fbo(result);
        int[] queries=new int[20];long[] cpuTimes=new long[20];
        for(int i=0;i<5;i++)benchmarkFrame(frame,events,depth,glow,glowFbo,resultFbo);
        glFinish(); // End warmup; no per-frame finish or query-result stalls below.
        for(int i=0;i<20;i++) {
            queries[i]=glGenQueries();long start=System.nanoTime();glBeginQuery(GL_TIME_ELAPSED,queries[i]);
            benchmarkFrame(frame,events,depth,glow,glowFbo,resultFbo);glEndQuery(GL_TIME_ELAPSED);
            cpuTimes[i]=System.nanoTime()-start;
        }
        glFlush();long deadline=System.nanoTime()+30_000_000_000L;
        while(glGetQueryObjecti(queries[19],GL_QUERY_RESULT_AVAILABLE)==GL_FALSE) {
            if(System.nanoTime()>deadline)throw new AssertionError("GPU timer did not complete in 30 seconds");
            Thread.sleep(1);
        }
        double[] gpu=new double[20];
        for(int i=0;i<20;i++){gpu[i]=glGetQueryObjectui64(queries[i],GL_QUERY_RESULT)/1_000_000.0;glDeleteQueries(queries[i]);}
        Arrays.sort(gpu);Arrays.sort(cpuTimes);
        note(String.format(Locale.ROOT,"GPU_BENCH events=%d size=%dx%d steps=%d warmup=5 frames=20 gpu_ms_p50=%.3f gpu_ms_p95=%.3f cpu_submit_ms_p50=%.3f",events,width,height,steps,gpu[9],gpu[18],cpuTimes[9]/1_000_000.0));
        glDeleteFramebuffers(glowFbo);glDeleteFramebuffers(resultFbo);glDeleteTextures(depth);glDeleteTextures(glow);glDeleteTextures(result);
        qualitySteps=32;check(glGetError()==GL_NO_ERROR,"GPU benchmark "+events+" completed without GL error");
    }
    static void benchmarkFrame(Frame frame,int events,int depth,int glow,int glowFbo,int resultFbo) {
        glViewport(0,0,frame.width,frame.height);glBindFramebuffer(GL_FRAMEBUFFER,glowFbo);glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);
        glUseProgram(volume);sample(volume,"SceneDepth",0,depth);glEnable(GL_BLEND);glBlendFunc(GL_ONE,GL_ONE);
        int[] mechanics={19,13,1,7,4,17,6,11};
        for(int i=0;i<events;i++) {
            Vector3f center=new Vector3f((i%3-1)*1.3f,-1.2f+(i%2)*.7f,-7.0f-(i/3)*.5f);
            uniforms(frame,center,new Vector3f(.35f,.65f,-.2f).normalize(),2.7f,3.8f,CYAN,8,.7f,2,1,mechanics[i],i*31);
            glDrawArrays(GL_TRIANGLES,0,3);
        }
        glDisable(GL_BLEND);glBindFramebuffer(GL_FRAMEBUFFER,resultFbo);glClear(GL_COLOR_BUFFER_BIT);
        glUseProgram(composite);sample(composite,"MagicColor",0,glow);sample(composite,"SceneDepth",1,depth);glDrawArrays(GL_TRIANGLES,0,3);
    }
    static double energy(float[]a){double sum=0;for(int i=0;i<a.length;i+=4)sum+=a[i]+a[i+1]+a[i+2];return sum;}
    static float max(float[]a){float max=0;for(float f:a)max=Math.max(max,f);return max;}
    static boolean finite(float[]a){for(float f:a)if(!Float.isFinite(f))return false;return true;}
    static double meanDifference(float[]a,float[]b){double sum=0;for(int i=0;i<a.length;i++)sum+=Math.abs(a[i]-b[i]);return sum/a.length;}
    static double regionEnergy(float[]a,int w,int h,int minX,int maxX){double sum=0;for(int y=0;y<h;y++)for(int x=minX;x<maxX;x++){int i=(y*w+x)*4;sum+=a[i]+a[i+1]+a[i+2];}return sum;}
    static double meanBackgroundDifference(Capture c,int lo,int hi){FloatBuffer base=background(c.width,c.height);double sum=0;for(int y=0;y<c.height;y++)for(int x=lo;x<hi;x++)for(int j=0;j<3;j++){int i=(y*c.width+x)*4+j;sum+=Math.abs(c.color[i]-base.get(i));}return sum/((hi-lo)*c.height*3);}
    static long fingerprint(float[]a){long h=0xcbf29ce484222325L;for(int i=0;i<a.length;i+=4){h^=Math.round(a[i]*4096);h*=0x100000001b3L;}return h;}
    static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    static void note(String s){System.out.println(s);evidence.add(s);}
}
