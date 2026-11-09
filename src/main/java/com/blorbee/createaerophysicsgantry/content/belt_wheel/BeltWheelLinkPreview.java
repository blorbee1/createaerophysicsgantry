package com.blorbee.createaerophysicsgantry.content.belt_wheel;

import com.blorbee.createaerophysicsgantry.CreateAeroPhysicsGantry;
import com.blorbee.createaerophysicsgantry.compat.simulated.SimulatedHelper;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = CreateAeroPhysicsGantry.MOD_ID, value = Dist.CLIENT)
public class BeltWheelLinkPreview {
    private static final int GREEN = 0x68C586;
    private static final int RED = 0xE05555;
    private static final int WHITE = 0xFFFFFF;

    private static final float LINE_WIDTH = 1 / 16f;

    @Nullable
    private static BeltWheelBlock.Selection activeSelection(Player player, long now) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof BeltConnectorItem))
                continue;
            BeltWheelBlock.Selection selection = BeltWheelBlock.readSelection(stack);
            if (selection != null && selection.expiresAt() >= now)
                return selection;
        }
        return null;
    }

    private static void showOBB(String key, BeltWheelBlockEntity be, int color) {
        Vec3 c = Vec3.atCenterOf(be.getBlockPos());
        Vec3[] corners = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            Vec3 local = c.add((i & 1) == 0 ? -0.5 : 0.5, (i & 2) == 0 ? -0.5 : 0.5, (i & 4) == 0 ? -0.5 : 0.5);
            Vec3 world = SimulatedHelper.toContainingWorldPosition(be, local);
            corners[i] = world == null ? local : world;
        }

        int edge = 0;
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((i & bit) != 0)
                    continue;
                Outliner.getInstance().showLine(key + edge++, corners[i], corners[i | bit])
                    .colored(color)
                    .lineWidth(LINE_WIDTH);
            }
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || mc.screen != null)
            return;

        BeltWheelBlock.Selection selection = activeSelection(player, level.getGameTime());
        if (selection == null)
            return;

        BeltWheelBlockEntity first = SimulatedHelper.findBlockEntity(level, selection.subLevel(), selection.pos(), BeltWheelBlockEntity.class);
        if (first == null || first.isRemoved())
            return;

        Vec3 start = first.getWorldAnchorPosition();
        showOBB("belt_wheel_first", first, WHITE);

        HitResult hit = mc.hitResult;
        BeltWheelBlockEntity target = null;
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK
            && level.getBlockEntity(bhr.getBlockPos()) instanceof BeltWheelBlockEntity second
            && !(second.getBlockPos().equals(first.getBlockPos())
                && Objects.equals(SimulatedHelper.getContainingSubLevelId(second), selection.subLevel()))
        ) {
            target = second;
        }

        if (target == null) {
            if (hit != null)
                Outliner.getInstance().showLine("belt_wheel_link", start, hit.getLocation())
                    .colored(WHITE)
                    .lineWidth(LINE_WIDTH);
            return;
        }

        Vec3 end = target.getWorldAnchorPosition();
        String error = BeltWheelBlock.checkNewLink(first, target);
        int color = error == null ? GREEN : RED;

        showOBB("belt_wheel_target", target, color);
        Outliner.getInstance().showLine("belt_wheel_link", start, end)
            .colored(color)
            .lineWidth(LINE_WIDTH);

        if (level.getGameTime() % 10 == 0) {
            Component msg = error == null
                ? Component.translatable(BeltWheelBlock.TRANSLATE_KEY_PREFIX + ".preview_ok", String.format("%.1f", start.distanceTo(end)))
                .withStyle(ChatFormatting.GREEN)
                : Component.translatable(error).withStyle(ChatFormatting.RED);
            player.displayClientMessage(msg, true);
        }
    }
}
