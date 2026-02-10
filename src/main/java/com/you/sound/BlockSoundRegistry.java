package com.you.sound;

import java.nio.file.Path;
import java.util.*;

public class BlockSoundRegistry {
    private final SoundManager soundManager;

    // action -> (material -> samples)
    private final EnumMap<BlockSoundAction, EnumMap<SoundMaterial, List<String>>> samples =
            new EnumMap<>(BlockSoundAction.class);

    private final Random rnd = new Random();

    public BlockSoundRegistry(SoundManager soundManager) {
        this.soundManager = soundManager;

        // ensure all actions have a map, so we never null-check later
        for (BlockSoundAction a : BlockSoundAction.values()) {
            samples.put(a, new EnumMap<>(SoundMaterial.class));
        }
    }

    public void register(SoundMaterial material, BlockSoundAction action, List<String> names, Path basePath) throws Exception {
        if (material == null) throw new IllegalArgumentException("material == null");
        if (action == null) throw new IllegalArgumentException("action == null");
        if (names == null || names.isEmpty()) return;

        // Store a copy (so caller modifications won't affect us)
        List<String> copy = new ArrayList<>(names);
        samples.get(action).put(material, copy);

        // Load buffers once (SoundManager can ignore duplicates if already loaded)
        for (String s : copy) {
            soundManager.loadSound(s, basePath.resolve(s + ".ogg"));
        }
    }

    public void play(SoundMaterial material, BlockSoundAction action, float x, float y, float z) {
        if (material == null || action == null) return;

        List<String> list = samples.get(action).get(material);
        if (list == null || list.isEmpty()) return;

        String sample = list.get(rnd.nextInt(list.size()));

        float baseVol;
        switch (action) {
            case PLACE -> baseVol = 0.80f;
            case STEP  -> baseVol = 0.55f;
            case BREAK -> baseVol = 1.00f;
            default    -> baseVol = 0.80f;
        }

        soundManager.playAt(sample, x, y, z, baseVol, 1.0f);
    }

    /** Optional helper: if you want to "add" sounds without replacing existing registration. */
    public void add(SoundMaterial material, BlockSoundAction action, List<String> names, Path basePath) throws Exception {
        if (material == null || action == null || names == null || names.isEmpty()) return;

        EnumMap<SoundMaterial, List<String>> byMat = samples.get(action);
        List<String> list = byMat.computeIfAbsent(material, k -> new ArrayList<>());

        for (String s : names) {
            list.add(s);
            soundManager.loadSound(s, basePath.resolve(s + ".ogg"));
        }
    }
}
