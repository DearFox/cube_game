package com.you.sound;

import java.nio.file.Path;
import java.util.*;

public class BlockSoundRegistry {
    private final SoundManager soundManager;

    private final Map<SoundMaterial, List<String>> placeSamples = new EnumMap<>(SoundMaterial.class);
    private final Map<SoundMaterial, List<String>> breakSamples = new EnumMap<>(SoundMaterial.class);

    private final Random rnd = new Random();

    public BlockSoundRegistry(SoundManager soundManager) {
        this.soundManager = soundManager;
    }

    public void register(SoundMaterial material, BlockSoundAction action, List<String> names, Path basePath) throws Exception {
        Map<SoundMaterial, List<String>> target = (action == BlockSoundAction.PLACE) ? placeSamples : breakSamples;

        target.put(material, new ArrayList<>(names));
        for (String s : names) {
            soundManager.loadSound(s, basePath.resolve(s + ".ogg"));
        }
    }

    public void play(SoundMaterial material, BlockSoundAction action, float x, float y, float z) {
        Map<SoundMaterial, List<String>> src = (action == BlockSoundAction.PLACE) ? placeSamples : breakSamples;
        List<String> list = src.get(material);
        if (list == null || list.isEmpty()) return;

        String sample = list.get(rnd.nextInt(list.size()));

        float baseVol = (action == BlockSoundAction.PLACE) ? 0.8f : 1.0f;
        soundManager.playAt(sample, x, y, z, baseVol, 1.0f);
    }
}
