package com.teamlifebind.clientui;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Normalized positions use the remaining viewport after scaling, so resized windows stay usable. */
public final class HudLayoutPreferences {
    public record Placement(double x,double y,double scale){
        public Placement{
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(scale))throw new IllegalArgumentException("Non-finite HUD placement");
            x=Math.clamp(x,0,1);y=Math.clamp(y,0,1);scale=Math.clamp(scale,.6,1.8);
        }
    }
    private final Path file;
    private final Map<String,Placement> values=new LinkedHashMap<>();
    public HudLayoutPreferences(Path file){this.file=file;load();}
    private void load(){
        if(!Files.isRegularFile(file))return;
        try(var reader=Files.newBufferedReader(file)){
            var properties=new Properties();properties.load(reader);
            for(String key:properties.stringPropertyNames())if(key.endsWith(".x"))try{
                String id=key.substring(0,key.length()-2);
                values.put(id,new Placement(Double.parseDouble(properties.getProperty(key)),Double.parseDouble(properties.getProperty(id+".y")),Double.parseDouble(properties.getProperty(id+".scale"))));
            }catch(IllegalArgumentException|NullPointerException ignored){}
        }catch(IOException|IllegalArgumentException failure){System.getLogger("TeamLifeBind/UI").log(System.Logger.Level.WARNING,"Cannot read HUD layout",failure);}
    }
    public Placement get(String id){return values.get(id);}
    public void put(String id,Placement placement){values.put(id,Objects.requireNonNull(placement));}
    public void remove(String id){values.remove(id);}
    public Map<String,Placement> snapshot(){return Map.copyOf(values);}
    public void replace(Map<String,Placement> snapshot){values.clear();values.putAll(snapshot);}
    public void clear(){values.clear();}
    public void save()throws IOException{
        Files.createDirectories(file.getParent());var properties=new Properties();
        values.forEach((id,value)->{properties.setProperty(id+".x",Double.toString(value.x()));properties.setProperty(id+".y",Double.toString(value.y()));properties.setProperty(id+".scale",Double.toString(value.scale()));});
        Path pending=file.resolveSibling(file.getFileName()+".tmp");
        try(var writer=Files.newBufferedWriter(pending)){properties.store(writer,"TeamLifeBind HUD positions and individual scales");}
        try{Files.move(pending,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ignored){Files.move(pending,file,StandardCopyOption.REPLACE_EXISTING);}
    }
}
