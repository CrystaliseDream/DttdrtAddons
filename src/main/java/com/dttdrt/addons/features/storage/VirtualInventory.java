package com.dttdrt.addons.features.storage;

import com.mojang.serialization.DataResult;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Cached inventory contents of a single Ender Chest or Backpack page.
 * Serializes item stacks to Base64-compressed NBT via ItemStack.OPTIONAL_CODEC.
 */
public final class VirtualInventory {

    private static final String INVENTORY_KEY = "INVENTORY";
    private final List<ItemStack> stacks;
    private final int rows;
    private volatile CompletableFuture<String> serializedBase64Future;

    public VirtualInventory(List<ItemStack> stacks) {
        if (stacks == null) {
            this.stacks = List.of();
            this.rows = 0;
        } else {
            List<ItemStack> copy = new ArrayList<>(stacks.size());
            for (ItemStack s : stacks) {
                copy.add(s == null ? ItemStack.EMPTY : s.copy());
            }
            this.stacks = Collections.unmodifiableList(copy);
            this.rows = this.stacks.size() / 9;
        }
        this.serializedBase64Future = CompletableFuture.supplyAsync(() -> encodeToBase64(this.stacks));
    }

    public List<ItemStack> stacks() {
        return stacks;
    }

    public int rows() {
        return rows;
    }

    public CompletableFuture<String> getSerializedFuture() {
        return serializedBase64Future;
    }

    public String serializeBlocking() {
        try {
            return serializedBase64Future.get();
        } catch (Exception e) {
            return encodeToBase64(stacks);
        }
    }

    private static RegistryOps<Tag> getOps() {
        Minecraft mc = Minecraft.getInstance();
        RegistryAccess access = (mc != null && mc.level != null)
                ? mc.level.registryAccess()
                : RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        return access.createSerializationContext(NbtOps.INSTANCE);
    }

    private static String encodeToBase64(List<ItemStack> items) {
        try {
            RegistryOps<Tag> ops = getOps();
            ListTag list = new ListTag();
            for (ItemStack stack : items) {
                ItemStack safe = stack != null ? stack : ItemStack.EMPTY;
                try {
                    DataResult<Tag> res = ItemStack.OPTIONAL_CODEC.encodeStart(ops, safe);
                    list.add(res.result().orElseGet(CompoundTag::new));
                } catch (Exception ignored) {
                    list.add(new CompoundTag());
                }
            }
            CompoundTag root = new CompoundTag();
            root.put(INVENTORY_KEY, list);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    public static VirtualInventory deserialize(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(base64);
            CompoundTag root = NbtIo.readCompressed(
                    new ByteArrayInputStream(raw), NbtAccounter.create(100_000_000L));
            Optional<ListTag> opt = root.getList(INVENTORY_KEY);
            if (opt.isEmpty()) {
                return null;
            }
            ListTag list = opt.get();
            RegistryOps<Tag> ops = getOps();
            List<ItemStack> stacks = new ArrayList<>(list.size());
            for (Tag tag : list) {
                try {
                    ItemStack decoded = ItemStack.OPTIONAL_CODEC.parse(ops, tag)
                            .result()
                            .orElse(ItemStack.EMPTY);
                    stacks.add(decoded);
                } catch (Exception ignored) {
                    stacks.add(ItemStack.EMPTY);
                }
            }
            if (stacks.isEmpty() || stacks.size() % 9 != 0) {
                return null;
            }
            return new VirtualInventory(stacks);
        } catch (Exception e) {
            return null;
        }
    }
}
