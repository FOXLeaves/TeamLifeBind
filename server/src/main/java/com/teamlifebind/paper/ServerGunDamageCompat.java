package com.teamlifebind.paper;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Player;

/** Keeps the struck dragon part in the ordinary native damage path across server APIs. */
final class ServerGunDamageCompat {
    private ServerGunDamageCompat() { }

    private static final ClassValue<Method> HANDLES = new ClassValue<>() {
        @Override protected Method computeValue(Class<?> type) {
            try { return type.getMethod("getHandle"); }
            catch (ReflectiveOperationException failure) { throw incompatible(failure); }
        }
    };

    private static final ClassValue<PartAccess> PARTS = new ClassValue<>() {
        @Override protected PartAccess computeValue(Class<?> type) {
            Method hurt = null;
            Field name = null;
            for (Method candidate : type.getMethods()) {
                Class<?>[] parameters = candidate.getParameterTypes();
                if (candidate.getReturnType() != boolean.class || parameters.length != 3 || parameters[2] != float.class
                    || parameters[0].isPrimitive() || parameters[1].isPrimitive()) continue;
                if (hurt != null) throw incompatible(new NoSuchMethodException("Ambiguous dragon part damage method"));
                hurt = candidate;
            }
            for (Field candidate : type.getDeclaredFields()) {
                if (candidate.getType() == String.class && Modifier.isPublic(candidate.getModifiers()) && !Modifier.isStatic(candidate.getModifiers())) {
                    if (name != null) throw incompatible(new NoSuchFieldException("Ambiguous dragon part name"));
                    name = candidate;
                }
            }
            if (hurt == null || name == null) throw incompatible(new NoSuchMethodException("Dragon part damage/name unavailable"));
            return new PartAccess(hurt, name);
        }
    };

    static boolean isHead(EnderDragonPart part) {
        Object nativePart = handle(part);
        try { return "head".equals(PARTS.get(nativePart.getClass()).name().get(nativePart)); }
        catch (IllegalAccessException failure) { throw incompatible(failure); }
    }

    static boolean isUnscaled(EnderDragonPart part) {
        Object nativePart = handle(part);
        try {
            Object name = PARTS.get(nativePart.getClass()).name().get(nativePart);
            return "head".equals(name) || "neck".equals(name);
        } catch (IllegalAccessException failure) { throw incompatible(failure); }
    }

    static void damage(EnderDragonPart part, Player shooter, double amount) {
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(shooter).withCausingEntity(shooter).build();
        damage(part, source, amount);
    }

    static void magicDamage(EnderDragonPart part, Player caster, double amount) {
        damage(part, DamageSource.builder(DamageType.INDIRECT_MAGIC).withDirectEntity(caster).withCausingEntity(caster).build(), amount);
    }

    private static void damage(EnderDragonPart part, DamageSource source, double amount) {
        Object nativePart = handle(part);
        Method hurt = PARTS.get(nativePart.getClass()).hurt();
        try {
            hurt.invoke(nativePart, handle(part.getWorld()), handle(source), (float) amount);
        } catch (ReflectiveOperationException failure) { throw incompatible(failure); }
    }

    private static Object handle(Object wrapper) {
        try { return HANDLES.get(wrapper.getClass()).invoke(wrapper); }
        catch (ReflectiveOperationException failure) { throw incompatible(failure); }
    }

    private static IllegalStateException incompatible(Throwable failure) {
        Throwable cause = failure instanceof InvocationTargetException invocation && invocation.getCause() != null ? invocation.getCause() : failure;
        return new IllegalStateException("Native dragon part damage is unavailable on this server", cause);
    }

    private record PartAccess(Method hurt, Field name) { }
}
