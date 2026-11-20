package dev.knowledgehub.ingestion;

import java.util.List;
public interface DocumentParser {
    record Page(int number,String text) {}
    List<Page> parse(byte[] content,String mediaType);
}
