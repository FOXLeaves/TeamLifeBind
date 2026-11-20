package com.sighs.apricityui.neoforge;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
public final class ReflectionUtils {
    private static final String[] ELEMENTS = {"A","Audio","Body","Canvas","Div","Head","Html","Iframe","Img","Input","Model","Option","Path","Pre","Select","Span","Sprite","Svg","TextArea","Texture","ThemeSwitcher","Translation","Container","Ingredient","Item","Recipe","Slot"};
    public static void addScanPackage(String name) {}
    public static void addScanPackages(String... names) {}
    public static void findAnnotationClasses(Class<? extends Annotation> type,
            Predicate<Map<String,Object>> predicate, Consumer<Class<?>> consumer, Runnable done) {
        try {
            for (String name : ELEMENTS) {
                Class<?> candidate = Class.forName("com.sighs.apricityui.element." + name);
                if (candidate.isAnnotationPresent(type)) consumer.accept(candidate);
            }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("AUI element registration failed", e); }
        finally { done.run(); }
    }
    public static void findAnnotationStaticMethod(Class<? extends Annotation> type, Object filter,
            Consumer<Method> consumer, Runnable done) { done.run(); }
}
