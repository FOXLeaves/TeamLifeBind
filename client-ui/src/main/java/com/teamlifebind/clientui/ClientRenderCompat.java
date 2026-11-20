package com.teamlifebind.clientui;

import java.lang.reflect.Method;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Matrix4f;

/** Optional renderer integrations. No Sodium or Iris classes are required to load the mod. */
public final class ClientRenderCompat {
    private ClientRenderCompat(){}
    private static final class IrisBridge {
        static final Object api;
        static final Method enabled,shadow;
        static {
            Object instance=null;Method active=null,shadowPass=null;
            try {
                var type=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                instance=type.getMethod("getInstance").invoke(null);
                active=type.getMethod("isShaderPackInUse");shadowPass=type.getMethod("isRenderingShadowPass");
            }catch(ClassNotFoundException ignored){}
            catch(ReflectiveOperationException|LinkageError problem){org.slf4j.LoggerFactory.getLogger("TeamLifeBind/RenderCompat").warn("Iris API is unavailable; using the standard rendering path",problem);}
            api=instance;enabled=active;shadow=shadowPass;
        }
    }
    private static boolean irisFlag(Method method){
        if(method==null||IrisBridge.api==null)return false;
        try{return Boolean.TRUE.equals(method.invoke(IrisBridge.api));}
        catch(ReflectiveOperationException|LinkageError ignored){return false;}
    }
    public static boolean shaderPackInUse(){return irisFlag(IrisBridge.enabled);}
    public static boolean shadowPass(){return irisFlag(IrisBridge.shadow);}
    public static boolean externalOptions(Screen screen){
        if(screen==null)return false;
        String name=screen.getClass().getName();
        return name.startsWith("net.caffeinemc.mods.sodium.client.gui.")||name.startsWith("net.irisshaders.iris.gui.");
    }
    /** Encode clip range in bit zero and forward depth in bit one, keeping the existing 192-byte UBO. */
    public static float depthEncoding(Matrix4f inverse,boolean zeroToOne){
        double low=distanceSquared(inverse,zeroToOne?0:-1),high=distanceSquared(inverse,1);
        return (zeroToOne?1:0)+(low>high?0:2);
    }
    private static double distanceSquared(Matrix4f inverse,float clipZ){
        double x=inverse.m20()*clipZ+inverse.m30(),y=inverse.m21()*clipZ+inverse.m31(),z=inverse.m22()*clipZ+inverse.m32();
        double w=inverse.m23()*clipZ+inverse.m33();
        return (x*x+y*y+z*z)/Math.max(w*w,1e-20);
    }
}
