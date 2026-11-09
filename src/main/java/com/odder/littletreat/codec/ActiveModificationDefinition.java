package com.odder.littletreat.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.odder.littletreat.Config;
import com.odder.littletreat.init.Attachments;
import com.odder.littletreat.payload.SyncModificationsPayload;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ActiveModificationDefinition{
    public static final StreamCodec<RegistryFriendlyByteBuf, ActiveModificationDefinition> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderRegistry(Registries.ITEM), ActiveModificationDefinition::source,
            AttributeModificationDefinition.STREAM_CODEC.apply(ByteBufCodecs.list()), ActiveModificationDefinition::defs,
            ByteBufCodecs.VAR_INT, ActiveModificationDefinition::remainingTicks,
            ActiveModificationDefinition::new
    );

    public static final Codec<ActiveModificationDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("source").forGetter(ActiveModificationDefinition::source),
        AttributeModificationDefinition.CODEC.listOf().fieldOf("def").forGetter(ActiveModificationDefinition::defs),
        Codec.INT.fieldOf("remainingTicks").forGetter(ActiveModificationDefinition::remainingTicks)
    ).apply(inst, ActiveModificationDefinition::new));

    public static void addActiveModification(ActiveModificationDefinition modDef, ServerPlayer player){
        // copy incoming since it's immutable
        List<ActiveModificationDefinition> activeMods = new ArrayList<>(player.getData(Attachments.ACTIVE_MODIFICATIONS));
        activeMods.add(modDef);
        player.setData(Attachments.ACTIVE_MODIFICATIONS, activeMods);
        SyncModificationsPayload.syncToClient(player);
    }

    public static void removeActiveModification(ActiveModificationDefinition modDef, ServerPlayer player){
        var newSet = new ArrayList<>(player.getData(Attachments.ACTIVE_MODIFICATIONS));
        if (newSet.remove(modDef)) {
            for (AttributeModificationDefinition attrDef : modDef.defs()) {
                AttributeInstance attr = player.getAttribute(attrDef.attribute());
                attr.removeModifier(AttributeModificationDefinition.createIdFor(modDef.source, attrDef));
            }
            player.setData(Attachments.ACTIVE_MODIFICATIONS, newSet);
            SyncModificationsPayload.syncToClient(player);
        }
    }

    public static void clearActiveModifications(ServerPlayer player) {
        updateActiveModifications(Collections.emptyList(), player);
    }

    public static void updateActiveModifications(Collection<ActiveModificationDefinition> mods, ServerPlayer player) {
        player.setData(Attachments.ACTIVE_MODIFICATIONS, mods.stream().toList());
        SyncModificationsPayload.syncToClient(player);
    }

    public static int calculateDuration(Collection<AttributeModificationDefinition> mods) {
        return Math.toIntExact(Math.round(AttributeModificationDefinition.getMaxDuration(mods) * Config.GLOBAL_TIME_MULTIPLIER.get()));
    }

    public Holder<Item> source;
    public List<AttributeModificationDefinition> defs;
    public int remainingTicks;

    private final ItemStack item;
    private final int maxDuration;

    public ActiveModificationDefinition(Holder<Item> source, Collection<AttributeModificationDefinition> defs) {
        this.source = source;
        this.defs = defs.stream().toList();
        this.maxDuration = calculateDuration(defs);
        this.remainingTicks = maxDuration;

        this.item = new ItemStack(BuiltInRegistries.ITEM.get(source.getKey().location()));
    }

    public ActiveModificationDefinition(Holder<Item> source, Collection<AttributeModificationDefinition> defs, int remainingTicks) {
        this.source = source;
        this.defs = defs.stream().toList();
        this.maxDuration = calculateDuration(defs);
        this.remainingTicks = remainingTicks;

        this.item = new ItemStack(BuiltInRegistries.ITEM.get(source.getKey().location()));
    }

    public Component getDisplay() {
        MutableComponent base = Component.literal(source().getRegisteredName()).append(String.format("ticks=%d/%d", remainingTicks, maxDuration)).append(": ");

        for(AttributeModificationDefinition attrDef : defs){
            base.append(attrDef.format());
        }

        return base;
    }

    public float getRemainingPercent() {
        return (float) this.remainingTicks / maxDuration;
    }

    public void resetTicks() {
        this.remainingTicks = maxDuration;
    }

    public ItemStack getItem() {
        return item;
    }

    public Holder<Item> source() { return source; }

    public List<AttributeModificationDefinition> defs() {
        return defs;
    }

    public int remainingTicks() {
        return remainingTicks;
    }

    public void progressTicks(int ticks) {
        remainingTicks = Math.max(remainingTicks - ticks, 0);
    }

    public boolean isFinished() {
        return remainingTicks <= 0;
    }
}
