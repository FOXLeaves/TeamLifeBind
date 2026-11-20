import com.teamlifebind.aui.resource.Font;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;

/** Use the shipping font-run planner rather than checking only the font file's cmap. */
public final class ArchiveFontProbe {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]),fonts=root.resolve("client-ui/src/main/resources/assets/tlb_aui/apricity/teamlifebind/fonts");
        Font.registerFont("TLBArchive",fonts.resolve("archive-serif.ttf"));
        for(String family:List.of("TLBUnicode","TLBUnicodeUpper")){
            Path file=fonts.resolve(family.equals("TLBUnicode")?"unicode-fallback.otf":"unicode-upper.otf");
            if(Files.exists(file))Font.registerFont(family,file);
        }
        var languages=JsonParser.parseString(Files.readString(root.resolve("scripts/fixtures/archive-languages.json"))).getAsJsonObject().getAsJsonArray("languages");
        var missing=new TreeSet<Integer>();int tested=0;
        for(var language:languages){
            var entry=language.getAsJsonObject();String text=entry.get("name").getAsString()+" ("+entry.get("region").getAsString()+")";
            for(var run:Font.planFontRuns("TLBArchive, serif",java.awt.Font.PLAIN,18,text))for(int cp:run.text().codePoints().toArray())if(!Character.isWhitespace(cp)&&!run.font().canDisplay(cp))missing.add(cp);
            tested++;
        }
        if(!missing.isEmpty())throw new AssertionError("Language names render missing-glyph boxes for "+missing.size()+" codepoints: "+missing.stream().map(cp->"U+"+Integer.toHexString(cp)).toList());
        System.out.println("ARCHIVE_LANGUAGE_GLYPHS_PASS nativeLanguageNames="+tested+" missingGlyphs=0");
    }
}
