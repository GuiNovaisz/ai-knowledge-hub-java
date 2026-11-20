package dev.knowledgehub.ingestion;

import dev.knowledgehub.shared.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class SafeDocumentParser implements DocumentParser {
    private final HubSettings settings;
    public SafeDocumentParser(HubSettings settings) { this.settings=settings; }
    public static String validate(byte[] bytes,String filename,String declared) {
        if(bytes.length==0) throw new HubException("EMPTY_DOCUMENT",422);
        if(bytes.length>5*1024*1024) throw new HubException("UPLOAD_TOO_LARGE",413);
        String name=filename.toLowerCase(Locale.ROOT);
        boolean pdf=bytes.length>5 && new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-");
        if(name.endsWith(".pdf") && pdf && "application/pdf".equals(declared)) return "application/pdf";
        if((name.endsWith(".txt") || name.endsWith(".md")) && !pdf && declared!=null && Set.of("text/plain","text/markdown").contains(declared)) {
            try {
                String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                if(text.codePoints().anyMatch(c->Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t')) throw new HubException("BINARY_TEXT_REJECTED",415);
                return declared;
            } catch(CharacterCodingException e) { throw new HubException("INVALID_UTF8",415); }
        }
        throw new HubException("UNSUPPORTED_MEDIA_TYPE",415);
    }
    public static String safeFilename(String name) {
        if(name==null || name.isBlank() || name.length()>180 || name.contains("/") || name.contains("\\") || name.contains("..") || name.codePoints().anyMatch(Character::isISOControl))
            throw new HubException("INVALID_FILENAME",400);
        return name.replaceAll("[^\\p{L}\\p{N} ._()-]","_");
    }
    @Override public List<Page> parse(byte[] bytes,String type) {
        if(!type.equals("application/pdf")) {
            String text=new String(bytes,StandardCharsets.UTF_8);
            check(text.length()); return List.of(new Page(1,text));
        }
        try(var pdf=Loader.loadPDF(bytes)) {
            if(pdf.isEncrypted()) throw new HubException("ENCRYPTED_PDF",422);
            if(pdf.getNumberOfPages()>settings.maxPages()) throw new HubException("PAGE_LIMIT",413);
            List<Page> result=new ArrayList<>(); int chars=0;
            PDFTextStripper stripper=new PDFTextStripper() {
                private int extracted;
                @Override protected void writeString(String text) throws IOException {
                    extracted+=text.length(); check(extracted); super.writeString(text);
                }
            };
            for(int n=1;n<=pdf.getNumberOfPages();n++) {
                stripper.setStartPage(n); stripper.setEndPage(n);
                String text=stripper.getText(pdf); chars+=text.length(); check(chars);
                result.add(new Page(n,text));
            }
            return result;
        } catch(IOException e) { throw new HubException("CORRUPT_PDF",422); }
    }
    private void check(int size) { if(size>settings.maxCharacters()) throw new HubException("CHARACTER_LIMIT",413); }
}
