package com.odder.littletreat.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.odder.littletreat.LittleTreat;
import com.odder.littletreat.codec.ActiveModificationDefinition;
import com.odder.littletreat.init.Attachments;
import com.odder.littletreat.payload.SyncModificationsPayload;
import com.odder.littletreat.processing.FoodDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.apache.logging.log4j.core.jmx.Server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class TreatCommands {
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("littletreat")
                        .requires(src -> src.hasPermission(2))
                        .then(getClearCommand())
                        .then(getAvailableAttributesCommand())
                        .then(getRegenAmountCommand())
                        .then(getForceSyncCommand())
                        .then(getDumpModificationsCommand())
                        .then(getDumpAttributeModifiersCommand(event))
                        .then(GiveIngredientsCommand.build(event.getBuildContext()))
        );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> getDumpAttributeModifiersCommand(RegisterCommandsEvent event) {
        return Commands.literal("dumpattributemodifiers")
                .then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.argument("attribute", ResourceArgument.resource(event.getBuildContext(), Registries.ATTRIBUTE))
                        .executes(ctx -> dumpAttributeModifiers(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), ResourceArgument.getAttribute(ctx, "attribute")))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> getDumpModificationsCommand() {
        return Commands.literal("dumpmodifications")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> dumpModifications(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> getForceSyncCommand() {
        return Commands.literal("forcesync")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> forceSyncPlayers(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> getClearCommand() {
        return Commands.literal("clearmods")
                .executes(ctx -> clearPlayers(List.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> clearPlayers(EntityArgument.getPlayers(ctx, "targets"))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> getAvailableAttributesCommand() {
        return Commands.literal("attributes")
                .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(ctx -> dumpAttributes(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> getRegenAmountCommand() {
        return Commands.literal("possibleregen")
                .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(ctx -> checkRegen(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))));
    }

    private static int dumpAttributeModifiers(CommandSourceStack source, Collection<ServerPlayer> targets, Holder.Reference<Attribute> attribute) {
        source.sendSystemMessage(Component.literal(attribute.getRegisteredName()));

        for (ServerPlayer player : targets) {
            source.sendSystemMessage(Component.literal(player.getScoreboardName()));
            AttributeInstance attr = player.getAttribute(attribute);
            for (var mod : attr.getModifiers()) {
                String msg = String.format("%s / %s / %s", mod.id(), mod.amount(), mod.operation().getSerializedName());
                source.sendSystemMessage(Component.literal(msg));
            }
        }

        return 0;
    }

    private static int dumpModifications(CommandSourceStack src, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            Collection<ActiveModificationDefinition> mods = player.getData(Attachments.ACTIVE_MODIFICATIONS);
            Collection<Component> modComponents = mods.stream().map(ActiveModificationDefinition::getDisplay).toList();
            var base = Component.empty().append(player.getDisplayName()).append(" has [");
            for (Component component : modComponents) {
                base.append(component).append(", ");
            }
            base.append("]");
            src.sendSystemMessage(base);
        }

        return 0;
    }

    private static int forceSyncPlayers(CommandSourceStack src, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            SyncModificationsPayload.syncToClient(player);
            src.sendSystemMessage(Component.literal("Synced client ").append(player.getDisplayName()));
        }

        return 0;
    }

    private static int checkRegen(CommandSourceStack src, Collection<ServerPlayer> players) {
       for (ServerPlayer player : players) {
           src.sendSystemMessage(Component.literal(String.format("%s = %.1f", player.getDisplayName().getString(), LittleTreat.healthRegen.getMaxNaturalRegenForPlayer(player))));
       }

        return 0;
    }

    private static int dumpAttributes(CommandSourceStack src, Collection<? extends Entity> entities) {
        var registry = BuiltInRegistries.ATTRIBUTE;

        for (var entity : entities) {
            if (entity instanceof LivingEntity livingEntity) {
                src.sendSystemMessage(Component.literal(livingEntity.getDisplayName().getString()));
                var entityAttributes = ((LivingEntity) entity).getAttributes();
                var filteredEntityAttributes = new ArrayList<Holder<Attribute>>();
                for (var set : registry.entrySet()) {
                    registry.getHolder(set.getKey()).ifPresent(holder -> {
                        if (entityAttributes.hasAttribute(holder)) {
                            filteredEntityAttributes.add(holder);
                        }
                    });
                }
                var keys = filteredEntityAttributes.stream().map(attr -> attr.getKey().location().toString()).collect(Collectors.joining(", "));
                src.sendSystemMessage(Component.literal(keys));
            }
        }

        return 0;
    }

    private static int clearPlayers(Collection<ServerPlayer> players) {
        for(ServerPlayer player : players) {
            FoodDispatcher.INSTANCE.clearAllBuffs(player);
        }
        return 0;
    }
}
