/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2022. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */
package dan200.computercraft.client;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.vertex.PoseStack;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.client.pocket.ClientPocketComputers;
import dan200.computercraft.client.render.CableHighlightRenderer;
import dan200.computercraft.client.render.ItemPocketRenderer;
import dan200.computercraft.client.render.ItemPrintoutRenderer;
import dan200.computercraft.client.render.MonitorHighlightRenderer;
import dan200.computercraft.client.sound.SpeakerManager;
import dan200.computercraft.shared.CommonHooks;
import dan200.computercraft.shared.command.CommandComputerCraft;
import dan200.computercraft.shared.computer.core.ServerContext;
import dan200.computercraft.shared.media.items.ItemPrintout;
import dan200.computercraft.shared.peripheral.monitor.ClientMonitor;
import dan200.computercraft.shared.peripheral.monitor.TileMonitor;
import dan200.computercraft.shared.pocket.items.ItemPocketComputer;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;
import dan200.computercraft.shared.util.PauseAwareTimer;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.File;
import java.util.function.Consumer;

/**
 * Event listeners for client-only code.
 * <p>
 * This is the client-only version of {@link CommonHooks}, and so should be where all client-specific event handlers are
 * defined.
 */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void onTick() {
        FrameInfo.onTick();
    }

    public static void onRenderTick() {
        PauseAwareTimer.tick(Minecraft.getInstance().isPaused());
        FrameInfo.onRenderTick();
    }

    public static void onWorldUnload() {
        ClientMonitor.destroyAll();
        SpeakerManager.reset();
        ClientPocketComputers.reset();
    }

    public static boolean onChatMessage(String message) {
        return handleOpenComputerCommand(message);
    }

    public static boolean drawHighlight(PoseStack transform, MultiBufferSource bufferSource, Camera camera, BlockHitResult hit) {
        return CableHighlightRenderer.drawHighlight(transform, bufferSource, camera, hit)
            || MonitorHighlightRenderer.drawHighlight(transform, bufferSource, camera, hit);
    }

    public static boolean onRenderHeldItem(
        PoseStack transform, MultiBufferSource render, int lightTexture, InteractionHand hand,
        float pitch, float equipProgress, float swingProgress, ItemStack stack
    ) {
        if (stack.getItem() instanceof ItemPocketComputer) {
            ItemPocketRenderer.INSTANCE.renderItemFirstPerson(transform, render, lightTexture, hand, pitch, equipProgress, swingProgress, stack);
            return true;
        }
        if (stack.getItem() instanceof ItemPrintout) {
            ItemPrintoutRenderer.INSTANCE.renderItemFirstPerson(transform, render, lightTexture, hand, pitch, equipProgress, swingProgress, stack);
            return true;
        }

        return false;
    }

    public static boolean onRenderItemFrame(PoseStack transform, MultiBufferSource render, ItemFrame frame, ItemStack stack, int light) {
        if (stack.getItem() instanceof ItemPrintout) {
            ItemPrintoutRenderer.onRenderInFrame(transform, render, frame, stack, light);
            return true;
        }

        return false;
    }

    public static void onPlayStreaming(SoundEngine engine, Channel channel, AudioStream stream) {
        SpeakerManager.onPlayStreaming(engine, channel, stream);
    }

    /**
     * Handle the {@link CommandComputerCraft#OPEN_COMPUTER} "clientside command". This isn't a true command, as we
     * don't want it to actually be visible to the user.
     *
     * @param message The current chat message.
     * @return Whether to cancel sending this message.
     */
    private static boolean handleOpenComputerCommand(String message) {
        if (!message.startsWith(CommandComputerCraft.OPEN_COMPUTER)) return false;

        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return false;

        var idStr = message.substring(CommandComputerCraft.OPEN_COMPUTER.length()).trim();
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException ignore) {
            return false;
        }

        var file = new File(ServerContext.get(server).storageDir().toFile(), "computer/" + id);
        if (!file.isDirectory()) return false;

        Util.getPlatform().openFile(file);
        return true;
    }

    /**
     * Add additional information about the currently targeted block to the debug screen.
     *
     * @param addText A callback which adds a single line of text.
     */
    public static void addDebugInfo(Consumer<String> addText) {
        var minecraft = Minecraft.getInstance();
        if (!minecraft.options.renderDebug || minecraft.level == null) return;
        if (minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK) return;

        var tile = minecraft.level.getBlockEntity(((BlockHitResult) minecraft.hitResult).getBlockPos());

        if (tile instanceof TileMonitor monitor) {
            addText.accept("");
            addText.accept(
                String.format("Targeted monitor: (%d, %d), %d x %d", monitor.getXIndex(), monitor.getYIndex(), monitor.getWidth(), monitor.getHeight())
            );
        } else if (tile instanceof TileTurtle turtle) {
            addText.accept("");
            addText.accept("Targeted turtle:");
            addText.accept(String.format("Id: %d", turtle.getComputerID()));
            addTurtleUpgrade(addText, turtle, TurtleSide.LEFT);
            addTurtleUpgrade(addText, turtle, TurtleSide.RIGHT);
        }
    }

    private static void addTurtleUpgrade(Consumer<String> out, TileTurtle turtle, TurtleSide side) {
        var upgrade = turtle.getUpgrade(side);
        if (upgrade != null) out.accept(String.format("Upgrade[%s]: %s", side, upgrade.getUpgradeID()));
    }
}
