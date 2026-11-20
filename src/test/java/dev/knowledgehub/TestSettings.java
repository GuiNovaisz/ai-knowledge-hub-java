package dev.knowledgehub;
import dev.knowledgehub.shared.HubSettings;
public final class TestSettings {
    private TestSettings() {}
    public static HubSettings settings() { return new HubSettings(768,"nomic-v1.5-768-v1",1200,180,200000,100,300,6000,0.65,1); }
}
