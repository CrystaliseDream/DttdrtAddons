package com.dttdrt.addons.features.custom;

import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.feature.Feature;
import com.dttdrt.addons.render.chroma.ChromaRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Client-side visual custom name replacement (chat, tablist, scoreboard, nametags, GUIs)
 * with visual color/chroma/bold/italic/underline configuration.
 */
public class CustomNameFeature implements Feature {

    @Override
    public String getId() {
        return "custom_name";
    }

    @Override
    public String getName() {
        return "Custom Name";
    }

    @Override
    public String getCategory() {
        return "Custom";
    }

    @Override
    public boolean isEnabled() {
        return ModConfig.getInstance().customNameEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        ModConfig.getInstance().customNameEnabled = enabled;
        ModConfig.save();
    }

    @Override
    public void init() {
        // Driven through Font mixins and ChromaRenderState client ticks.
    }

    @Override
    public void clientTick(Minecraft client) {
        // Driven continuously through ClientTickEvents -> ChromaRenderState.onClientTick().
    }

    public static boolean isEnabledStatic() {
        if (!WhitelistManager.isAuthorized()) {
            return false;
        }
        ModConfig cfg = ModConfig.getInstance();
        return cfg.customNameEnabled && cfg.customNameText != null && !cfg.customNameText.isEmpty();
    }

    public static String getRealUsername() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getUser() == null) {
            return null;
        }
        String name = mc.getUser().getName();
        return (name == null || name.isEmpty()) ? null : name;
    }

    public static boolean shouldTransform(String text) {
        if (text == null || text.isEmpty() || !isEnabledStatic()) {
            return false;
        }
        String realName = getRealUsername();
        return realName != null && text.contains(realName);
    }

    public static Style buildNicknameStyle(Style baseStyle) {
        ModConfig cfg = ModConfig.getInstance();
        Style style = baseStyle != null ? baseStyle : Style.EMPTY;
        TextColor color;
        if ("CHROMA".equalsIgnoreCase(cfg.customNameColorMode) && ChromaRenderState.isChromaFeatureActive()) {
            color = ChromaRenderState.getChromaTextColor();
        } else {
            color = TextColor.fromRgb(cfg.customNameColor & 0x00FFFFFF);
        }
        return style
                .withColor(color)
                .withBold(cfg.customNameBold)
                .withItalic(cfg.customNameItalic)
                .withUnderlined(cfg.customNameUnderlined);
    }

    public static FormattedCharSequence buildPreviewSequence() {
        ModConfig cfg = ModConfig.getInstance();
        String nick = (cfg.customNameText != null && !cfg.customNameText.isEmpty())
                ? cfg.customNameText
                : (getRealUsername() != null ? getRealUsername() : "Player");
        Style style = buildNicknameStyle(Style.EMPTY);
        return new TransformedSequence(literalRun(nick, style));
    }

    public static FormattedCharSequence buildChromaSampleSequence() {
        Style style = ChromaRenderState.isChromaFeatureActive()
                ? ChromaRenderState.getChromaStyle()
                : Style.EMPTY.withColor(0xFFFFFF);
        return new TransformedSequence(literalRun("Chroma Preview 123", style));
    }

    public static FormattedCharSequence replaceStringToSequence(String text) {
        if (text == null) {
            return FormattedCharSequence.EMPTY;
        }
        String realName = getRealUsername();
        if (realName == null || !text.contains(realName)) {
            return FormattedCharSequence.forward(text, Style.EMPTY);
        }
        List<StyledCodePoint> original = new ArrayList<>(text.length());
        StringDecomposer.iterateFormatted(text, Style.EMPTY, (index, style, codePoint) -> {
            original.add(new StyledCodePoint(codePoint, style));
            return true;
        });
        return replaceCollectedSequence(original, realName);
    }

    public static FormattedCharSequence replaceInSequence(FormattedCharSequence seq) {
        if (seq == null || seq instanceof TransformedSequence || !isEnabledStatic()) {
            return seq;
        }
        String realName = getRealUsername();
        if (realName == null) {
            return seq;
        }

        StringBuilder plain = new StringBuilder();
        seq.accept((index, style, codePoint) -> {
            plain.appendCodePoint(codePoint);
            return true;
        });
        if (plain.indexOf(realName) < 0) {
            return seq;
        }

        List<StyledCodePoint> original = new ArrayList<>(plain.length());
        seq.accept((index, style, codePoint) -> {
            original.add(new StyledCodePoint(codePoint, style));
            return true;
        });
        return replaceCollectedSequence(original, realName);
    }

    public static FormattedText replaceInFormattedText(FormattedText text) {
        if (text == null || text instanceof TransformedFormattedText || !isEnabledStatic()) {
            return text;
        }
        String realName = getRealUsername();
        if (realName == null) {
            return text;
        }

        StringBuilder plain = new StringBuilder();
        text.visit((style, content) -> {
            plain.append(content);
            return Optional.empty();
        }, Style.EMPTY);
        if (plain.indexOf(realName) < 0) {
            return text;
        }

        String nick = ModConfig.getInstance().customNameText;
        String full = plain.toString();
        List<int[]> matchRanges = findNonOverlappingMatches(full, realName);
        if (matchRanges.isEmpty()) {
            return text;
        }

        List<TextChunk> chunks = new ArrayList<>();
        text.visit((style, content) -> {
            if (!content.isEmpty()) {
                chunks.add(new TextChunk(style, content));
            }
            return Optional.empty();
        }, Style.EMPTY);

        MutableComponent out = Component.empty();
        int globalOffset = 0;
        int matchIdx = 0;
        int[] activeMatch = matchRanges.get(0);

        for (TextChunk chunk : chunks) {
            String content = chunk.text;
            int localPos = 0;
            int chunkLen = content.length();

            while (localPos < chunkLen) {
                int absPos = globalOffset + localPos;
                while (activeMatch != null && absPos >= activeMatch[1]) {
                    matchIdx++;
                    activeMatch = matchIdx < matchRanges.size() ? matchRanges.get(matchIdx) : null;
                }
                if (activeMatch == null) {
                    out.append(Component.literal(content.substring(localPos)).withStyle(chunk.style));
                    break;
                }
                if (absPos < activeMatch[0]) {
                    int untilMatch = Math.min(chunkLen, localPos + (activeMatch[0] - absPos));
                    out.append(Component.literal(content.substring(localPos, untilMatch)).withStyle(chunk.style));
                    localPos = untilMatch;
                } else {
                    if (absPos == activeMatch[0]) {
                        out.append(Component.literal(nick).withStyle(buildNicknameStyle(chunk.style)));
                    }
                    int skipEnd = Math.min(chunkLen, localPos + (activeMatch[1] - absPos));
                    localPos = skipEnd;
                }
            }
            globalOffset += chunkLen;
        }

        return new TransformedFormattedText(out);
    }

    public static List<FormattedCharSequence> markTransformedLines(List<FormattedCharSequence> lines) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }
        List<FormattedCharSequence> out = new ArrayList<>(lines.size());
        for (FormattedCharSequence line : lines) {
            out.add(line instanceof TransformedSequence ? line : new WrappedTransformedSequence(line));
        }
        return out;
    }

    public static List<FormattedText> markTransformedTexts(List<FormattedText> texts) {
        if (texts == null || texts.isEmpty()) {
            return texts;
        }
        List<FormattedText> out = new ArrayList<>(texts.size());
        for (FormattedText ft : texts) {
            out.add(markTransformedText(ft));
        }
        return out;
    }

    public static FormattedText markTransformedText(FormattedText text) {
        if (text == null || text instanceof TransformedFormattedText) {
            return text;
        }
        return new TransformedFormattedText(text);
    }

    private static FormattedCharSequence replaceCollectedSequence(List<StyledCodePoint> original, String realName) {
        StringBuilder sb = new StringBuilder(original.size());
        int[] cpAtChar = new int[original.size() * 2 + 2];
        int charCount = 0;
        for (int cpIdx = 0; cpIdx < original.size(); cpIdx++) {
            int cp = original.get(cpIdx).codePoint;
            int len = Character.charCount(cp);
            sb.appendCodePoint(cp);
            if (charCount + len > cpAtChar.length) {
                cpAtChar = java.util.Arrays.copyOf(cpAtChar, cpAtChar.length * 2 + len);
            }
            for (int k = 0; k < len; k++) {
                cpAtChar[charCount++] = cpIdx;
            }
        }

        String full = sb.toString();
        List<int[]> matches = findNonOverlappingMatches(full, realName);
        if (matches.isEmpty()) {
            return new TransformedSequence(original);
        }

        String nick = ModConfig.getInstance().customNameText;
        List<StyledCodePoint> rebuilt = new ArrayList<>(original.size() + nick.length());
        int curCp = 0;
        for (int[] m : matches) {
            int startCp = cpAtChar[m[0]];
            int endCp = cpAtChar[m[1] - 1] + 1;
            while (curCp < startCp) {
                rebuilt.add(original.get(curCp++));
            }
            Style baseStyle = original.get(startCp).style;
            rebuilt.addAll(literalRun(nick, buildNicknameStyle(baseStyle)));
            curCp = endCp;
        }
        while (curCp < original.size()) {
            rebuilt.add(original.get(curCp++));
        }
        return new TransformedSequence(rebuilt);
    }

    private static List<StyledCodePoint> literalRun(String text, Style style) {
        List<StyledCodePoint> out = new ArrayList<>(text.length());
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            out.add(new StyledCodePoint(cp, style));
            i += Character.charCount(cp);
        }
        return out;
    }

    private static List<int[]> findNonOverlappingMatches(String haystack, String needle) {
        List<int[]> ranges = new ArrayList<>();
        int idx = 0;
        int nLen = needle.length();
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            ranges.add(new int[]{idx, idx + nLen});
            idx += nLen;
        }
        return ranges;
    }

    private record StyledCodePoint(int codePoint, Style style) {
    }

    private record TextChunk(Style style, String text) {
    }

    private static class TransformedSequence implements FormattedCharSequence {
        private final List<StyledCodePoint> codePoints;

        private TransformedSequence(List<StyledCodePoint> codePoints) {
            this.codePoints = codePoints;
        }

        @Override
        public boolean accept(FormattedCharSink sink) {
            for (int i = 0; i < codePoints.size(); i++) {
                StyledCodePoint scp = codePoints.get(i);
                if (!sink.accept(i, scp.style, scp.codePoint)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class WrappedTransformedSequence extends TransformedSequence {
        private final FormattedCharSequence delegate;

        private WrappedTransformedSequence(FormattedCharSequence delegate) {
            super(List.of());
            this.delegate = delegate;
        }

        @Override
        public boolean accept(FormattedCharSink sink) {
            return delegate.accept(sink);
        }
    }

    private static final class TransformedFormattedText implements FormattedText {
        private final FormattedText delegate;

        private TransformedFormattedText(FormattedText delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> Optional<T> visit(ContentConsumer<T> visitor) {
            return delegate.visit(visitor);
        }

        @Override
        public <T> Optional<T> visit(StyledContentConsumer<T> visitor, Style style) {
            return delegate.visit(visitor, style);
        }
    }
}
