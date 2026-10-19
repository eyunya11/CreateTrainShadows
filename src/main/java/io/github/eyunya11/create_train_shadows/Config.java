package io.github.eyunya11.create_train_shadows;

import net.neoforged.neoforge.common.ModConfigSpec;

// Client-side options. Entries are added in a later stage.
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
