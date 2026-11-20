package dev.knowledgehub.chunking;

import dev.knowledgehub.ingestion.DocumentParser.Page;
import dev.knowledgehub.shared.*;
import java.text.Normalizer;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ParagraphChunker {
    public record Piece(int index,int page,String heading,String text) {}
    private final HubSettings settings;
    public ParagraphChunker(HubSettings settings) { this.settings=settings; }
    public List<Piece> split(List<Page> pages) {
        List<Piece> result=new ArrayList<>();
        for(Page page:pages) {
            String text=Normalizer.normalize(page.text(),Normalizer.Form.NFKC).replace("\r\n","\n").replace('\r','\n').strip();
            String heading="";
            for(String line:text.lines().toList()) if(line.startsWith("#")) { heading=line.replaceFirst("^#+\\s*",""); break; }
            if(heading.length()>300) heading=heading.substring(0,300);
            for(int start=0;start<text.length();) {
                int end=Math.min(start+settings.chunkCharacters(),text.length());
                if(end<text.length()) {
                    int boundary=text.lastIndexOf("\n\n",end);
                    if(boundary>start+settings.chunkCharacters()/2) end=boundary;
                    else { int space=text.lastIndexOf(' ',end); if(space>start+settings.chunkCharacters()/2) end=space; }
                }
                String body=text.substring(start,end).strip();
                if(!body.isBlank()) result.add(new Piece(result.size(),page.number(),heading,body));
                if(result.size()>settings.maxChunks()) throw new HubException("CHUNK_LIMIT",413);
                if(end==text.length()) break;
                start=Math.max(start+1,end-settings.chunkOverlap());
            }
        }
        if(result.isEmpty()) throw new HubException("EMPTY_DOCUMENT",422);
        return result;
    }
}
