package com.teashoe.typeshift.client;

import com.teashoe.typeshift.core.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

public final class TypeshiftClient implements ClientModInitializer {
    private static DictionaryLoader.Data data;
    private static ConversionEngine engine;
    private static UserDictionary userDictionary;
    private static final ChatLanguageMemory chatLanguage = new ChatLanguageMemory();
    @Override
    public void onInitializeClient() {
        try {
            var config = AutoLangConfig.load(FabricLoader.getInstance().getConfigDir().resolve("typeshift.properties"));
            data = new DictionaryLoader().load();
            userDictionary = new UserDictionary(FabricLoader.getInstance().getConfigDir().resolve("typeshift-words.properties"));
            engine = new ConversionEngine(data, config, userDictionary);
        } catch (Exception ex) {
            LoggerFactory.getLogger("TypeShift").error("TypeShift disabled: configuration or dictionary could not be loaded", ex);
        }
    }
    public static InputController newController() {
        return engine == null ? null : new InputController(engine, new CommandProtector(data.protectedWords()), chatLanguage);
    }
    public static UserDictionary userDictionary() { return userDictionary; }
}
