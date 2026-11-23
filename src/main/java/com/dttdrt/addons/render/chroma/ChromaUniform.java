package com.dttdrt.addons.render.chroma;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.renderer.DynamicUniformStorage;

import java.nio.ByteBuffer;

/**
 * Manages the per-frame std140 UBO storing chroma shader parameters.
 */
public final class ChromaUniform implements AutoCloseable {

    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putFloat()
            .putFloat()
            .putFloat()
            .putInt()
            .get();

    private final DynamicUniformStorage<UniformValue> storage =
            new DynamicUniformStorage<>("Dttdrt Chroma UBO", UNIFORM_SIZE, 2);

    public GpuBufferSlice writeWith(float chromaSize, float timeOffset, float saturation, int forwardDirection) {
        return storage.writeUniform(new UniformValue(chromaSize, timeOffset, saturation, forwardDirection));
    }

    public void clear() {
        storage.endFrame();
    }

    @Override
    public void close() {
        storage.close();
    }

    public record UniformValue(
            float chromaSize,
            float timeOffset,
            float saturation,
            int forwardDirection
    ) implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(chromaSize)
                    .putFloat(timeOffset)
                    .putFloat(saturation)
                    .putInt(forwardDirection);
        }
    }
}
