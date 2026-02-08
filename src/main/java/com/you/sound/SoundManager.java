package com.you.sound;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.stb.STBVorbis.stb_vorbis_close;
import static org.lwjgl.stb.STBVorbis.stb_vorbis_get_info;
import static org.lwjgl.stb.STBVorbis.stb_vorbis_get_samples_short_interleaved;
import static org.lwjgl.stb.STBVorbis.stb_vorbis_open_memory;
import static org.lwjgl.stb.STBVorbis.stb_vorbis_stream_length_in_samples;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

import org.lwjgl.openal.*;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.openal.AL10.*;

public class SoundManager {
    private final Map<String, Integer> bufferByName = new HashMap<>();
    private final Queue<Integer> freeSources = new ArrayDeque<>();
    private final List<Integer> allSources = new ArrayList<>();
    private final Random rnd = new Random();

    // configuration
    private final int MAX_SOURCES = 64;        // tune to your target hardware
    private final float DEFAULT_PITCH_VARIANCE = 0.05f; // +-5%
    private final float DEFAULT_VOLUME_VARIANCE = 0.05f; // +-5%

    public void init() {
        // create AL context (should be done once by audio system)
        long device = ALC10.alcOpenDevice((ByteBuffer) null);
        if (device == MemoryUtil.NULL) throw new RuntimeException("Failed to open OpenAL device");
        ALCCapabilities alc = ALC.createCapabilities(device);
        long context = ALC10.alcCreateContext(device, (IntBuffer) null);
        ALC10.alcMakeContextCurrent(context);
        AL.createCapabilities(alc);

        // create source pool
        for (int i = 0; i < MAX_SOURCES; i++) {
            int src = alGenSources();
            if (alGetError() != AL_NO_ERROR) break;
            allSources.add(src);
            freeSources.add(src);
        }
        alDistanceModel(AL_INVERSE_DISTANCE_CLAMPED);
    }

    public void cleanup() {
        // stop and delete sources
        for (int src : allSources) {
            alSourceStop(src);
            alDeleteSources(src);
        }
        for (int buf : bufferByName.values()) {
            alDeleteBuffers(buf);
        }
        // teardown context/device - do from your audio shutdown path
    }

    /** Load an OGG file from the classpath or filesystem and cache it with a given name. */
    public void loadSound(String name, Path oggPath) throws IOException {
        if (bufferByName.containsKey(name)) return;
        ByteBuffer vorbis;
        vorbis = ioResourceToByteBuffer(oggPath.toString(), 32 * 1024);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            long decoder = stb_vorbis_open_memory(vorbis, error, null);
            if (decoder == MemoryUtil.NULL) {
                throw new RuntimeException("Failed to open Ogg: " + error.get(0));
            }

            STBVorbisInfo info = STBVorbisInfo.mallocStack(stack);
            stb_vorbis_get_info(decoder, info);

            int channels = info.channels();
            int sampleRate = info.sample_rate();

            int samples = stb_vorbis_stream_length_in_samples(decoder);
            ShortBuffer pcm = MemoryUtil.memAllocShort(samples * channels);
            stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);

            int format = (channels == 1) ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;
            int buffer = alGenBuffers();
            alBufferData(buffer, format, pcm, sampleRate);

            // free native memory
            MemoryUtil.memFree(pcm);
            stb_vorbis_close(decoder);

            if (alGetError() != AL_NO_ERROR) {
                throw new RuntimeException("OpenAL buffer error when loading " + name);
            }

            bufferByName.put(name, buffer);
        }
    }

    /** Plays a named sample at world coordinates (x,y,z) with optional base volume and pitch. */
    public void playAt(String name, float x, float y, float z, float baseVolume, float basePitch) {
        Integer buf = bufferByName.get(name);
        if (buf == null) return;

        Integer src = obtainSource();
        if (src == null) return; // no free source; drop sound or reuse LRU source (policy choice)

        // Apply small random variance so repeated sounds aren't identical
        float pitchVariance = 1.0f + ((rnd.nextFloat() * 2f - 1f) * DEFAULT_PITCH_VARIANCE);
        float volVariance = 1.0f + ((rnd.nextFloat() * 2f - 1f) * DEFAULT_VOLUME_VARIANCE);

        alSourceStop(src);
        alSourcei(src, AL_BUFFER, buf);
        alSourcef(src, AL_GAIN, clamp(baseVolume * volVariance, 0f, 1.4f)); // clamp to reasonable max
        alSourcef(src, AL_PITCH, basePitch * pitchVariance);
        alSource3f(src, AL_POSITION, x, y, z);

        // Enable distance attenuation by setting distance model on the listener (once in init)
        // Or set source reference distance / rolloff for per-source control:
        alSourcef(src, AL_REFERENCE_DISTANCE, 1.5f); // how soon attenuation starts
        alSourcef(src, AL_ROLLOFF_FACTOR, 1.0f);

        alSourcePlay(src);
    }

    private Integer obtainSource() {
        // Reclaim finished sources
        Iterator<Integer> it = allSources.iterator();
        while (it.hasNext()) {
            int candidate = it.next();
            int state = alGetSourcei(candidate, AL_SOURCE_STATE);
            if (state != AL_PLAYING && !freeSources.contains(candidate)) {
                freeSources.add(candidate);
            }
        }
        return freeSources.poll();
    }

    // Utility: clamp
    private static float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    // Helper to load file into ByteBuffer - from LWJGL demos
    private static ByteBuffer ioResourceToByteBuffer(String resource, int bufferSize) throws IOException {
        Path path = Paths.get(resource);
        if (!Files.isReadable(path)) {
            // try classpath fallback
            try (var stream = SoundManager.class.getResourceAsStream(resource)) {
                if (stream == null) throw new IOException("Resource not found: " + resource);
                byte[] bytes = stream.readAllBytes();
                ByteBuffer bb = MemoryUtil.memAlloc(bytes.length);
                bb.put(bytes).flip();
                return bb;
            }
        }
        try (SeekableByteChannel fc = Files.newByteChannel(path)) {
            ByteBuffer buffer = MemoryUtil.memAlloc((int) (fc.size() + 1));
            while (fc.read(buffer) != -1) ;
            buffer.flip();
            return buffer;
        }
    }
    
    public void setListener(
            float x, float y, float z,
            float forwardX, float forwardY, float forwardZ,
            float upX, float upY, float upZ
    ) {
        // Listener position in world space
        alListener3f(AL_POSITION, x, y, z);

        // Optional: listener velocity (0 is fine unless you want doppler)
        alListener3f(AL_VELOCITY, 0f, 0f, 0f);

        // Listener orientation:
        // OpenAL expects a float[6]:
        //   [ forward.x, forward.y, forward.z,
        //     up.x,      up.y,      up.z ]
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer orientation = stack.mallocFloat(6);
            orientation
                    .put(forwardX).put(forwardY).put(forwardZ)
                    .put(upX).put(upY).put(upZ)
                    .flip();

            alListenerfv(AL_ORIENTATION, orientation);
        }
    }
}
