package dev.knowledgehub.knowledgebase;

import dev.knowledgehub.document.CorpusStore;
import dev.knowledgehub.ingestion.IngestionService;
import dev.knowledgehub.generation.QueryService;
import dev.knowledgehub.retrieval.ChunkRetriever;
import dev.knowledgehub.shared.HubException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeApi {
    public record CreateKb(@NotBlank @Size(max=120) String name,@NotNull @Size(max=1000) String description) {}
    public record Query(@NotBlank @Size(max=2000) String query,@Min(1) @Max(20) int topK,UUID documentId) {}
    public record RetrievalQuery(@NotBlank @Size(max=2000) String query,@Min(1) @Max(20) int topK,UUID documentId,@NotNull ChunkRetriever.Strategy strategy) {}
    private final CorpusStore store;
    private final IngestionService ingestion;
    private final QueryService questions;
    private final ChunkRetriever retriever;
    public KnowledgeApi(CorpusStore store,IngestionService ingestion,QueryService questions,ChunkRetriever retriever) {
        this.store=store;this.ingestion=ingestion;this.questions=questions;this.retriever=retriever;
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Object create(@Valid @RequestBody CreateKb request) { return store.create(request.name(),request.description()); }
    @GetMapping("/{id}") public Object get(@PathVariable UUID id) { return store.kb(id); }
    @PostMapping(value="/{id}/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object upload(@PathVariable UUID id,@RequestParam("file") MultipartFile file) throws IOException {
        if(file.getSize()>5*1024*1024) throw new HubException("UPLOAD_TOO_LARGE",413);
        return ingestion.upload(id,file.getOriginalFilename(),file.getContentType(),file.getBytes());
    }
    @GetMapping("/{id}/documents") public Object documents(@PathVariable UUID id) { return store.documents(id); }
    @GetMapping("/{id}/documents/{documentId}") public Object document(@PathVariable UUID id,@PathVariable UUID documentId) { return store.document(id,documentId); }
    @PostMapping("/{id}/documents/{documentId}/reindex") public Object reindex(@PathVariable UUID id,@PathVariable UUID documentId) { return ingestion.reindex(id,documentId); }
    @PostMapping("/{id}/query") public Object query(@PathVariable UUID id,@Valid @RequestBody Query query) { return questions.query(id,query.query(),query.topK(),query.documentId()); }
    @PostMapping("/{id}/retrieval") public Object retrieval(@PathVariable UUID id,@Valid @RequestBody RetrievalQuery query) { return retriever.retrieve(id,query.query(),query.topK(),query.documentId(),query.strategy()); }
}
