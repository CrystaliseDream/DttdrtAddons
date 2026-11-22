package com.dttdrt.addons.audio;

import com.dttdrt.addons.core.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Low-latency OpenAL hardware mixer reproducing authentic osu!lazer UI & Keyboard sound effects.
 * - Reads bundled 44.1kHz mono 16-bit PCM WAV resources directly from mod assets.
 * - Reuses Minecraft's active OpenAL context cleanly (auto-rebuilds AL buffers on F3+T or device swap).
 * - Matches osu!lazer's exact hover debounce (45ms), OsuSliderBar pitch curve + min/max low-pitch thump,
 *   random key-press-1..4 / caps feedback, and left stereo panning on settings PopIn / PopOut.
 * - Can be toggled instantly via ModConfig.uiSoundsEnabled (Custom -> Display -> UI Sound Effects).
 */
public final class OsuSoundEngine {

    private static final String ASSET_PREFIX = "/assets/dttdrtaddons/sounds/osu/";
    private static final String[] ALL_SAMPLES = new String[]{
            "settings_pop_in.wav",
            "overlay_pop_out.wav",
            "button_sidebar_hover.wav",
            "button_sidebar_select.wav",
            "button_hover.wav",
            "button_select.wav",
            "default_hover.wav",
            "default_select.wav",
            "check_on.wav",
            "check_off.wav",
            "notch_tick.wav",
            "dropdown_open.wav",
            "dropdown_close.wav",
            "key_press_1.wav",
            "key_press_2.wav",
            "key_press_3.wav",
            "key_press_4.wav",
            "key_caps.wav",
            "key_delete.wav",
            "key_confirm.wav",
            "key_invalid.wav",
            "select_all.wav"
    };

    private record PcmSample(ByteBuffer data, int alFormat, int sampleRate) {}

    private static final Map<String, PcmSample> DECODED_SAMPLES = new HashMap<>();
    private static final Map<String, Integer> AL_BUFFERS = new HashMap<>();
    private static final int SOURCE_POOL_SIZE = 16;
    private static int[] sourcePool;
    private static int sourceCursor = 0;

    private static boolean samplesDecoded = false;
    private static long activeAlcContext = 0L;

    private static final Random RNG = new Random();
    private static long lastHoverMs = 0L;
    private static long lastSliderMs = 0L;
    private static int lastSliderTickBucket = -1;
    private static long lastKeyMs = 0L;

    private OsuSoundEngine() {}

    public static void playPopIn() {
        play("settings_pop_in.wav", 0.62f, 1.0f, -0.22f);
    }

    public static void playPopOut() {
        play("overlay_pop_out.wav", 0.56f, 1.0f, -0.22f);
    }

    public static void playSidebarHover() {
        long now = System.currentTimeMillis();
        if (now - lastHoverMs < 45L) return;
        lastHoverMs = now;
        play("button_sidebar_hover.wav", 0.55f, randomPitch(0.02f), -0.25f);
    }

    public static void playSidebarSelect() {
        play("button_sidebar_select.wav", 0.66f, randomPitch(0.01f), -0.25f);
    }

    public static void playButtonHover() {
        long now = System.currentTimeMillis();
        if (now - lastHoverMs < 45L) return;
        lastHoverMs = now;
        play("button_hover.wav", 0.48f, randomPitch(0.02f), 0.0f);
    }

    public static void playButtonSelect() {
        play("button_select.wav", 0.65f, randomPitch(0.01f), 0.0f);
    }

    public static void playDefaultHover() {
        long now = System.currentTimeMillis();
        if (now - lastHoverMs < 45L) return;
        lastHoverMs = now;
        play("default_hover.wav", 0.44f, randomPitch(0.02f), 0.0f);
    }

    public static void playDefaultSelect() {
        play("default_select.wav", 0.60f, randomPitch(0.01f), 0.0f);
    }

    public static void playToggle(boolean checked) {
        play(checked ? "check_on.wav" : "check_off.wav", 0.68f, randomPitch(0.01f), 0.0f);
    }

    public static void playSliderTick(double normalizedValue) {
        long now = System.currentTimeMillis();
        if (now - lastSliderMs <= 30L) return;
        double clamped = Math.max(0.0, Math.min(1.0, normalizedValue));
        int bucket = (int) Math.round(clamped * 200.0);
        if (bucket == lastSliderTickBucket) return;

        lastSliderMs = now;
        lastSliderTickBucket = bucket;

        // Exact OsuSliderBar.cs frequency formula (pitch climbs 0.99 -> 1.19, drops -0.50 at endpoints)
        float pitch = 0.99f + RNG.nextFloat() * 0.02f + (float) clamped * 0.20f;
        if (clamped <= 0.002 || clamped >= 0.998) {
            pitch -= 0.50f;
        }
        play("notch_tick.wav", 0.55f, Math.max(0.45f, pitch), 0.0f);
    }

    public static void resetSliderTickState() {
        lastSliderTickBucket = -1;
    }

    public static void playDropdownOpen() {
        play("dropdown_open.wav", 0.60f, 1.0f, 0.15f);
    }

    public static void playDropdownClose() {
        play("dropdown_close.wav", 0.56f, 1.0f, 0.15f);
    }

    public static void playKeyAdd(char ch) {
        long now = System.currentTimeMillis();
        if (now - lastKeyMs < 15L) return;
        lastKeyMs = now;
        if (Character.isUpperCase(ch)) {
            play("key_caps.wav", 0.58f, randomPitch(0.02f), 0.0f);
        } else {
            int idx = 1 + RNG.nextInt(4);
            play("key_press_" + idx + ".wav", 0.52f, randomPitch(0.02f), 0.0f);
        }
    }

    public static void playKeyDelete() {
        long now = System.currentTimeMillis();
        if (now - lastKeyMs < 15L) return;
        lastKeyMs = now;
        play("key_delete.wav", 0.56f, randomPitch(0.015f), 0.0f);
    }

    public static void playKeyConfirm() {
        play("key_confirm.wav", 0.62f, 1.0f, 0.0f);
    }

    public static void playKeySelectAll() {
        play("select_all.wav", 0.56f, 1.0f, 0.0f);
    }

    public static void playKeyInvalid() {
        long now = System.currentTimeMillis();
        if (now - lastKeyMs < 35L) return;
        lastKeyMs = now;
        play("key_invalid.wav", 0.55f, 1.0f, 0.0f);
    }

    private static float randomPitch(float variation) {
        return 1.0f - variation + RNG.nextFloat() * (variation * 2.0f);
    }

    private static void play(String sampleName, float baseGain, float pitch, float stereoPan) {
        if (!ModConfig.getInstance().uiSoundsEnabled) return;
        try {
            long ctx = ALC10.alcGetCurrentContext();
            if (ctx == 0L) return;

            ensureDecoded();
            if (ctx != activeAlcContext || sourcePool == null) {
                rebuildOpenAlObjects(ctx);
            }

            Integer bufId = AL_BUFFERS.get(sampleName);
            if (bufId == null || bufId <= 0 || sourcePool == null) return;

            float masterVol = 1.0f;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null) {
                masterVol = Math.max(0.0f, Math.min(1.0f, mc.options.getFinalSoundSourceVolume(SoundSource.MASTER)));
            }
            float gain = baseGain * masterVol;
            if (gain <= 0.005f) return;

            int src = acquireSource();
            if (src <= 0) return;

            AL10.alSourceStop(src);
            AL10.alSourcei(src, AL10.AL_BUFFER, bufId);
            AL10.alSourcef(src, AL10.AL_PITCH, pitch);
            AL10.alSourcef(src, AL10.AL_GAIN, gain);
            AL10.alSource3f(src, AL10.AL_POSITION, stereoPan, 0.0f, -0.5f);
            AL10.alSourcePlay(src);
        } catch (Throwable ignored) {}
    }

    private static int acquireSource() {
        for (int s : sourcePool) {
            int state = AL10.alGetSourcei(s, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING && state != AL10.AL_PAUSED) {
                return s;
            }
        }
        int s = sourcePool[sourceCursor];
        sourceCursor = (sourceCursor + 1) % sourcePool.length;
        return s;
    }

    private static synchronized void rebuildOpenAlObjects(long ctx) {
        AL_BUFFERS.clear();
        sourcePool = new int[SOURCE_POOL_SIZE];
        for (int i = 0; i < SOURCE_POOL_SIZE; i++) {
            int src = AL10.alGenSources();
            AL10.alSourcei(src, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSourcef(src, AL10.AL_ROLLOFF_FACTOR, 0.0f);
            AL10.alSourcei(src, AL10.AL_LOOPING, AL10.AL_FALSE);
            sourcePool[i] = src;
        }
        for (Map.Entry<String, PcmSample> e : DECODED_SAMPLES.entrySet()) {
            PcmSample pcm = e.getValue();
            int buf = AL10.alGenBuffers();
            ByteBuffer dup = pcm.data().duplicate();
            dup.rewind();
            AL10.alBufferData(buf, pcm.alFormat(), dup, pcm.sampleRate());
            AL_BUFFERS.put(e.getKey(), buf);
        }
        activeAlcContext = ctx;
    }

    private static synchronized void ensureDecoded() {
        if (samplesDecoded) return;
        samplesDecoded = true;
        for (String name : ALL_SAMPLES) {
            try (InputStream is = OsuSoundEngine.class.getResourceAsStream(ASSET_PREFIX + name)) {
                if (is == null) continue;
                byte[] bytes = readAllBytes(is);
                PcmSample pcm = parseWav(bytes);
                if (pcm != null) {
                    DECODED_SAMPLES.put(name, pcm);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private static PcmSample parseWav(byte[] wav) {
        if (wav == null || wav.length < 44) return null;
        if (!"RIFF".equals(new String(wav, 0, 4, StandardCharsets.US_ASCII))
                || !"WAVE".equals(new String(wav, 8, 4, StandardCharsets.US_ASCII))) {
            return null;
        }

        ByteBuffer bb = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int channels = 1;
        int sampleRate = 44100;
        int bitsPerSample = 16;
        int dataOffset = -1;
        int dataSize = 0;

        int pos = 12;
        while (pos + 8 <= wav.length) {
            String chunkId = new String(wav, pos, 4, StandardCharsets.US_ASCII);
            int chunkSize = bb.getInt(pos + 4);
            int payloadPos = pos + 8;
            if (chunkSize < 0 || payloadPos + chunkSize > wav.length) break;

            if ("fmt ".equals(chunkId) && chunkSize >= 16) {
                channels = bb.getShort(payloadPos + 2) & 0xFFFF;
                sampleRate = bb.getInt(payloadPos + 4);
                bitsPerSample = bb.getShort(payloadPos + 14) & 0xFFFF;
            } else if ("data".equals(chunkId)) {
                dataOffset = payloadPos;
                dataSize = chunkSize;
                break;
            }
            pos = payloadPos + chunkSize + (chunkSize & 1);
        }

        if (dataOffset < 0 || dataSize <= 0) return null;

        int alFormat;
        if (channels == 1) {
            alFormat = (bitsPerSample == 8) ? AL10.AL_FORMAT_MONO8 : AL10.AL_FORMAT_MONO16;
        } else {
            alFormat = (bitsPerSample == 8) ? AL10.AL_FORMAT_STEREO8 : AL10.AL_FORMAT_STEREO16;
        }

        ByteBuffer direct = ByteBuffer.allocateDirect(dataSize).order(ByteOrder.LITTLE_ENDIAN);
        direct.put(wav, dataOffset, dataSize);
        direct.flip();
        return new PcmSample(direct, alFormat, sampleRate);
    }
}
