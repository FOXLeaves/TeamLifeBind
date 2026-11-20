package com.teamlifebind.clientui;

import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.lang.reflect.Field;
import java.util.*;

/** Semantic projection only: the source widgets retain callbacks, validation and game state. */
final class ArchiveModel {
    record Control(Object source,AbstractSelectionList<?> list,String kind,String label,String detail,boolean active) {}
    final List<Control> controls=new ArrayList<>();
    final List<String> prose=new ArrayList<>();
    final Map<Object,GuiEventListener> relays=new IdentityHashMap<>();
    private GuiEventListener relay;
    private final Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
    static ArchiveModel read(Screen screen){var result=new ArchiveModel();for(var child:List.copyOf(screen.children()))result.visit(child,null);result.controls.removeIf(c->c.kind().equals("label")&&c.label().equals(screen.getTitle().getString()));result.prose.addAll(components(screen));ArchiveSpecials.append(screen,result.controls);
        ArchiveRecipes.project(screen,result);
        result.removeSearch(screen);return result;}
    private void removeSearch(Screen screen){
        for(var iterator=controls.iterator();iterator.hasNext();){
            var control=iterator.next();if(!(control.source() instanceof EditBox edit))continue;
            boolean search=Set.of("SelectWorldScreen","LanguageSelectScreen","KeyBindsScreen").contains(screen.getClass().getSimpleName())
                ||relays.get(edit) instanceof net.minecraft.client.gui.screens.recipebook.RecipeBookComponent<?>
                ||ArchiveNavigation.keys(edit.getMessage()).stream().anyMatch(key->key.toLowerCase(Locale.ROOT).contains("search"));
            if(!search)for(Class<?> type=screen.getClass();type!=null&&type!=Screen.class;type=type.getSuperclass())for(Field field:type.getDeclaredFields()){
                String name=field.getName().toLowerCase(Locale.ROOT);if(!name.contains("search"))continue;
                try{field.setAccessible(true);if(field.get(screen)==edit)search=true;}catch(ReflectiveOperationException|RuntimeException ignored){}
            }
            if(!search)continue;
            if(!edit.getValue().isEmpty())edit.setValue("");edit.setFocused(false);edit.visible=false;edit.active=false;
            if(screen.getFocused()==edit)screen.setFocused(null);iterator.remove();relays.remove(edit);
        }
    }
    private void visit(Object object,AbstractSelectionList<?> list){
        if(object==null||ArchivePolicy.hidden(object)||!seen.add(object)||seen.size()>5000)return;
        if(object instanceof ArchiveRefreshable refreshable)refreshable.tlb$refreshArchiveData();
        if(object instanceof AbstractSelectionList<?> selection){
            for(Object entry:List.copyOf(selection.children())){
                if(ArchivePolicy.hidden(entry))continue;
                if(entry instanceof ArchiveRefreshable refreshable)refreshable.tlb$refreshArchiveData();
                var nested=new ArrayList<GuiEventListener>();
                // 26.3 options/keybind entries are event containers. LayoutElement.visitWidgets
                // is deliberately empty on these wrappers; the actual labelled controls live in children().
                if(entry instanceof ContainerEventHandler group)nested.addAll(group.children());
                if(entry instanceof LayoutElement layout)layout.visitWidgets(widget->{if(!nested.contains(widget))nested.add(widget);});
                if(!nested.isEmpty()){
                    int start=controls.size();for(var widget:nested)visit(widget,selection);
                    if(entry.getClass().getSimpleName().equals("KeyEntry")){
                        var names=components(entry);String name=names.isEmpty()?"":names.getFirst();
                        if(!name.isBlank())for(int i=start;i<controls.size();i++){var c=controls.get(i);controls.set(i,new Control(c.source(),c.list(),c.kind(),name+" · "+c.label(),c.detail(),c.active()));}
                    }
                }
                else {boolean notice=entry.getClass().getSimpleName().matches(".*(Header|Loading|NoWorlds).*");controls.add(new Control(entry,selection,notice?"label":"entry",caption(entry),entryDetail(entry),!notice));}
            }return;
        }
        if(object instanceof AbstractWidget widget){
            if(!widget.visible)return;
            String type=widget instanceof TabButton?"tab":widget instanceof EditBox||widget instanceof MultiLineEditBox?"input":widget instanceof AbstractSliderButton?"slider":widget instanceof Checkbox?"check":widget instanceof CycleButton<?>?"cycle":widget instanceof AbstractButton||widget.getClass().getSimpleName().contains("Button")?"action":"label";
            String label=widget.getMessage().getString();if(label.isBlank())label=caption(widget);
            if(label.isBlank()&&type.equals("input"))label=ArchiveTheme.tr("control.input");
            if(!label.isBlank())controls.add(new Control(widget,list,type,label,tooltip(widget),widget.active));
            if(relay!=null)relays.put(widget,relay);
            if(object instanceof ContainerEventHandler group){
                var previous=relay;
                // TabButton itself only accepts focus; TabNavigationBar.setFocused performs selection.
                if(object instanceof net.minecraft.client.gui.components.tabs.TabNavigationBar)relay=group;
                try{for(var child:group.children())visit(child,list);}finally{relay=previous;}
            }
            return;
        }
        if(object instanceof ContainerEventHandler group){for(var child:group.children())visit(child,list);return;}
        if(!object.getClass().getName().startsWith("net.minecraft.client.gui."))return;
        try{Object visible=object.getClass().getMethod("isVisible").invoke(object);if(Boolean.FALSE.equals(visible))return;}catch(ReflectiveOperationException ignored){}
        var previous=relay;if(relay==null&&object instanceof GuiEventListener listener)relay=listener;
        try{for(Class<?> type=object.getClass();type!=null&&type!=Screen.class;type=type.getSuperclass())for(Field field:type.getDeclaredFields()){
            if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
            try{field.setAccessible(true);Object value=field.get(object);if(value instanceof Screen)continue;
                if(value instanceof GuiEventListener||value!=null&&value.getClass().getSimpleName().equals("RecipeBookPage"))visit(value,list);
                else if(value instanceof Iterable<?> values)for(Object child:values)if(child instanceof GuiEventListener)visit(child,list);
            }catch(ReflectiveOperationException|RuntimeException ignored){}
        }}finally{relay=previous;}
    }
    static String caption(Object object){
        if(object instanceof net.minecraft.client.gui.screens.recipebook.RecipeButton recipe&&!recipe.getDisplayStack().isEmpty())return recipe.getDisplayStack().getHoverName().getString();
        // Narration includes selection state and accessibility instructions; it is not a visual label.
        if(object instanceof net.minecraft.client.gui.screens.worldselection.WorldSelectionList.WorldListEntry entry)return entry.getLevelName();
        if(object instanceof net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.OnlineServerEntry entry)return entry.getServerData().name;
        if(object instanceof ObjectSelectionList.Entry<?>){var values=components(object);if(!values.isEmpty())return values.getFirst();}
        if(object instanceof NarratableEntry entry){
            var parts=new ArrayList<String>();
            try{entry.updateNarration(new NarrationElementOutput(){
                public void add(NarratedElementType type,NarrationThunk<?> text){if(type==NarratedElementType.TITLE)text.getText(parts::add);}
                public NarrationElementOutput nest(){return this;}
                public NarrationTrigger narrationTrigger(){return NarrationTrigger.SYSTEM;}
            });}catch(RuntimeException ignored){}
            if(!parts.isEmpty())return String.join(" · ",parts);
        }
        for(String name:List.of("getNarration","getNarrationMessage","getName","getDisplayName")){
            for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{var method=type.getDeclaredMethod(name);method.setAccessible(true);Object value=method.invoke(object);if(value instanceof Component c&&!c.getString().isBlank())return c.getString();if(value instanceof String s&&!s.isBlank())return s;}catch(ReflectiveOperationException|RuntimeException ignored){}
        }
        var components=components(object);return components.isEmpty()?ArchiveTheme.tr("control.entry"):components.getFirst();
    }
    static List<String> components(Object object){
        var result=new LinkedHashSet<String>();
        for(Class<?> type=object.getClass();type!=null&&type!=Screen.class;type=type.getSuperclass()){
            if(!type.getName().startsWith("net.minecraft."))continue;
            for(Field field:type.getDeclaredFields()){
                if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
                try{field.setAccessible(true);Object value=field.get(object);
                    if(value instanceof Component c&&!c.getString().isBlank())result.add(c.getString());
                    if(value instanceof Iterable<?> values){int lines=0;for(Object part:values){if(++lines>400)break;if(part instanceof Component c)result.add(c.getString());else if(part instanceof String s)result.add(s);}}
                    if(value!=null&&value.getClass().getSimpleName().contains("LevelSummary"))for(String method:List.of("getLevelName","getInfo")){
                        Object text=value.getClass().getMethod(method).invoke(value);if(text instanceof Component c)result.add(c.getString());else if(text instanceof String s)result.add(s);
                    }
                }catch(ReflectiveOperationException|RuntimeException ignored){}
            }
        }return List.copyOf(result);
    }
    private static String tooltip(AbstractWidget widget){
        try{Field holder=AbstractWidget.class.getDeclaredField("tooltip");holder.setAccessible(true);Object value=holder.get(widget);
            for(Field field:value.getClass().getDeclaredFields())if(field.getType()==Tooltip.class){field.setAccessible(true);Object tip=field.get(value);if(tip!=null)return String.join("\n",components(tip));}
        }catch(ReflectiveOperationException|RuntimeException ignored){}
        return "";
    }
    private static String entryDetail(Object entry){
        if(entry instanceof net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.OnlineServerEntry server){
            var data=server.getServerData();var parts=new LinkedHashSet<String>();
            if(data.motd!=null&&!data.motd.getString().isBlank())parts.add(data.motd.getString());
            if(data.status!=null&&!data.status.getString().isBlank())parts.add(data.status.getString());
            return String.join("\n",parts);
        }
        String title=caption(entry);return String.join("\n",components(entry).stream().filter(line->!line.equals(title)).toList());
    }
    static void select(Control control){
        if(control.list()==null)return;
        for(var method:AbstractSelectionList.class.getDeclaredMethods())if(method.getName().equals("setSelected")&&method.getParameterCount()==1){
            try{method.setAccessible(true);method.invoke(control.list(),control.source());return;}catch(ReflectiveOperationException|RuntimeException ignored){}
        }
    }
    static void activateEntry(Control control,boolean confirm){
        if(!(control.source() instanceof GuiEventListener entry)||!(control.source() instanceof LayoutElement rect)||control.list()==null)return;
        select(control);control.list().setFocused(entry);
        if(confirm&&entry.keyPressed(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN,0,0)))return;
        // World/server icon quadrants have join/reorder actions. Selecting the HTML row must not hit them.
        if(entry instanceof SelectableEntry)return;
        var input=new net.minecraft.client.input.MouseButtonEvent(rect.getX()+Math.max(40,rect.getWidth()/2),rect.getY()+rect.getHeight()/2,new net.minecraft.client.input.MouseButtonInfo(0,0));
        entry.mouseClicked(input,confirm);entry.mouseReleased(input);
    }
}
