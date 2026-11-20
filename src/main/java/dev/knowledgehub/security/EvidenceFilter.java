package dev.knowledgehub.security;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class EvidenceFilter {
    private static final Pattern INJECTION=Pattern.compile("(?is)(ignore.{0,80}(instru|previous|usu.rio|user)|responda\\s+sempre|system\\s*prompt|<\\|(?:system|im_start)\\|>|reveal.{0,40}(secret|key))");
    public boolean allowed(String text) { return !INJECTION.matcher(text).find(); }
}
