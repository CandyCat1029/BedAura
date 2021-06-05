package meow.candycat.uwu.module.modules.combat;

import meow.candycat.eventsystem.listener.EventHandler;
import meow.candycat.eventsystem.listener.Listener;
import meow.candycat.uwu.command.Command;
import meow.candycat.uwu.event.events.EventPlayerPostMotionUpdate;
import meow.candycat.uwu.event.events.EventPlayerPreMotionUpdateBeforeAimbot;
import meow.candycat.uwu.event.events.PacketEvent;
import meow.candycat.uwu.event.events.RenderEvent;
import meow.candycat.uwu.gui.font.CFontRenderer;
import meow.candycat.uwu.module.Module;
import meow.candycat.uwu.module.ModuleManager;
import meow.candycat.uwu.module.modules.player.Scaffold;
import meow.candycat.uwu.module.modules.util.ResistanceDetector;
import meow.candycat.uwu.setting.Setting;
import meow.candycat.uwu.setting.Settings;
import meow.candycat.uwu.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL11.GL_QUADS;

@Module.Info(name = "BedAura", category = Module.Category.COMBAT)
public class BedAura extends Module {
    public Setting<Boolean> autoSwitch = register(Settings.b("AutoSwitch"));
    public Setting<Double> range = register(Settings.d("Range", 5.0));
    public Setting<Double> distance = register(Settings.doubleBuilder("EnemyBedDist").withMinimum(1.0).withValue(8.0).withMaximum(13.0).build());
    public Setting<Boolean> prediction = register(Settings.b("Prediction", true));
    public Setting<Integer> predictedTicks = register(Settings.integerBuilder("PredictedTicks").withMinimum(0).withValue(0).withMaximum(20).withVisibility(v -> prediction.getValue()).build());
    public Setting<Integer> minDmg = register(Settings.integerBuilder("MinDmg").withMinimum(0).withValue(4).withMaximum(20).build());
    public Setting<Integer> minDifference = register(Settings.integerBuilder("MinAdvantage").withMinimum(0).withValue(4).withMaximum(20).build());
    public Setting<Integer> maxSelfDmg = register(Settings.integerBuilder("MaxSelfDmg").withMinimum(0).withValue(4).withMaximum(20).build());
    public boolean shouldPlace = false;
    public BlockPos blockPos = null;
    public EnumFacing direction = null;
    EntityPlayer renderEnt = null;

    public void onUpdate() {
        MultiThreading.runAsync(() -> {
            //if (ModuleManager.getModuleByName("Elevator").isEnabled()) return;
            List<EntityPlayer> entities = new ArrayList<>(mc.world.playerEntities.stream().filter(e -> !Friends.isFriend(e.getName()) && e != mc.player && e.getHealth() > 0 && !e.isDead).collect(Collectors.toList()));
            List<BlockPos> sphereBlocks = getSphere(mc.player.getPosition(), range.getValue().floatValue(), range.getValue().intValue(), false, true, 0);
            if (mc.player != null && mc.world != null && prediction.getValue()) {
                for (Entity x : entities) {
                    if (x.getDistance(mc.player) > 15) continue;
                    if (!(x instanceof EntityPlayer)) continue;
                    float f = x.width / 2.0F, f1 = x.height;
                    x.setEntityBoundingBox(new AxisAlignedBB(x.posX - (double) f, x.posY, x.posZ - (double) f, x.posX + (double) f, x.posY + (double) f1, x.posZ + (double) f));
                    Entity y = EntityUtil.getPredictedPosition(x, predictedTicks.getValue());
                    x.setEntityBoundingBox(y.getEntityBoundingBox());
                }
            }
            List<BedSaver> bedPos = canPlaceBed(entities, sphereBlocks);
            double damage = minDmg.getValue();
            for (EntityPlayer entity : entities) {
                if (entity.isCreative()) {
                    continue;
                }
                for (BedSaver pos : bedPos) {
                    if (entity.getDistanceSq(pos.blockPos) > Math.pow(distance.getValue(), 2)) {
                        continue;
                    }
                    for (int i = 0; i < pos.canPlaceDirection.size(); i++) {
                        BlockPos boost2 = pos.blockPos.add(0, 1, 0).offset(pos.canPlaceDirection.get(i));
                        double d = calculateDamage(boost2.x + 0.5, boost2.y + 0.5, boost2.z + 0.5, entity);
                        if (d < pos.selfDamage.get(i) && d <= entity.getHealth() + entity.getAbsorptionAmount()) {
                            continue;
                        }
                        if (d < damage || d - pos.selfDamage.get(i) < minDifference.getValue()) {
                            continue;
                        }
                        damage = d;
                        blockPos = pos.blockPos;
                        direction = pos.canPlaceDirection.get(i);
                        renderEnt = entity;
                    }
                }
            }
            if (damage == minDmg.getValue()) {
                return;
            }
            shouldPlace = true;
            if (direction == EnumFacing.EAST) {
                ((Aimbot) ModuleManager.getModuleByName("Aimbot")).setRotation(-91, mc.player.rotationPitch);
            } else if (direction == EnumFacing.NORTH)
                ((Aimbot) ModuleManager.getModuleByName("Aimbot")).setRotation(179, mc.player.rotationPitch);
            else if (direction == EnumFacing.WEST)
                ((Aimbot) ModuleManager.getModuleByName("Aimbot")).setRotation(89, mc.player.rotationPitch);
            else
                ((Aimbot) ModuleManager.getModuleByName("Aimbot")).setRotation(-1, mc.player.rotationPitch);

        });
    }

    // -1 = south
    // 89 = west
    //179 = north
    // - 91 = east
    @EventHandler
    private Listener<EventPlayerPostMotionUpdate> awa = new Listener<>(e -> {
        int bedSlot = mc.player.getHeldItemMainhand().getItem() == Items.BED ? mc.player.inventory.currentItem : -1;
        if (bedSlot == -1) {
            for (int l = 0; l < 9; ++l) {
                if (mc.player.inventory.getStackInSlot(l).getItem() == Items.BED) {
                    bedSlot = l;
                    break;
                }
            }
        }
        boolean offhand = false;
        if (mc.player.getHeldItemOffhand().getItem() == Items.BED) {
            offhand = true;
        }
        if (shouldPlace) {
            shouldPlace = false;
            if (bedSlot == -1 && !offhand) return;
            if (!offhand && mc.player.inventory.currentItem != bedSlot) {
                if (autoSwitch.getValue()) {
                    mc.player.inventory.currentItem = bedSlot;
                    mc.player.connection.sendPacket(new CPacketHeldItemChange(bedSlot));
                }
            }
            Vec3d vec = new Vec3d(blockPos).add(0.5, 0.5, 0.5).add(new Vec3d(EnumFacing.DOWN.getDirectionVec()).scale(0.5));
            float f = (float) (vec.x - (double) blockPos.getX());
            float f1 = (float) (vec.y - (double) blockPos.getY());
            float f2 = (float) (vec.z - (double) blockPos.getZ());
            boolean sneak = false;
            if (mc.player.isSneaking()) {
                sneak = true;
                mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SNEAKING));
            }

            if (mc.world.getBlockState(blockPos.up().offset(direction)).getBlock() == Blocks.BED) {
                mc.player.connection.sendPacket(new CPacketAnimation(offhand ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND));
                mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(blockPos.up().offset(direction), EnumFacing.UP, offhand ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND, 0, 0, 0));
            }
            mc.player.swingArm(offhand ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND);
            mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(blockPos, EnumFacing.UP, offhand ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND, f, f1, f2));
            mc.player.connection.sendPacket(new CPacketAnimation(offhand ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND));
            mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(blockPos.up(), EnumFacing.UP, offhand ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND, 0, 0, 0));
            if (sneak)
                mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_SNEAKING));
        }
    });

    public void onEnable() {
        shouldPlace = false;
        blockPos = null;
        if (mc.player == null || mc.world == null) {
            disable();
        }
    }

    public void onDisable() {
        if (mc.player != null && mc.world != null) ((Aimbot) ModuleManager.getModuleByName("Aimbot")).resetRotation();
    }

    public List<BlockPos> getSphere(final BlockPos loc, final float r, final int h, final boolean hollow, final boolean sphere, final int plus_y) {
        final List<BlockPos> circleblocks = new ArrayList<BlockPos>();
        final int cx = loc.getX();
        final int cy = loc.getY();
        final int cz = loc.getZ();
        for (int x = cx - (int) r; x <= cx + r; ++x) {
            for (int z = cz - (int) r; z <= cz + r; ++z) {
                for (int y = sphere ? (cy - (int) r) : cy; y < (sphere ? (cy + r) : ((float) (cy + h))); ++y) {
                    final double dist = (cx - x) * (cx - x) + (cz - z) * (cz - z) + (sphere ? ((cy - y) * (cy - y)) : 0);
                    if (dist < r * r && (!hollow || dist >= (r - 1.0f) * (r - 1.0f))) {
                        final BlockPos l = new BlockPos(x, y + plus_y, z);
                        circleblocks.add(l);
                    }
                }
            }
        }
        return circleblocks;
    }

    public List<BedSaver> canPlaceBed(List<EntityPlayer> entityPlayerList, List<BlockPos> blockPosList) {
        List<BedSaver> bedSaverList = new ArrayList<>();
        List<EnumFacing> list = new ArrayList<>();
        List<Double> damage = new ArrayList<>();
        for (BlockPos pos : blockPosList) {
            boolean x = false;
            for (EntityPlayer entityPlayer : entityPlayerList) {
                if (entityPlayer.getDistanceSq(pos) <= Math.pow(distance.getValue(), 2)) {
                    x = true;
                    break;
                }
            }
            if (!x) continue;
            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                BlockPos side = pos.offset(facing);
                BlockPos boost = pos.add(0, 1, 0);
                BlockPos boost2 = pos.add(0, 1, 0).offset(facing);
                Block boostBlock = mc.world.getBlockState(boost).getBlock();
                Block boostBlock2 = mc.world.getBlockState(boost2).getBlock();

                if ((boostBlock == Blocks.AIR || boostBlock == Blocks.BED) &&
                        (boostBlock2 == Blocks.AIR || boostBlock2 == Blocks.BED) &&
                        mc.world.getBlockState(side).getMaterial().isOpaque() && mc.world.getBlockState(side).isFullCube() &&
                        mc.world.getBlockState(pos).getMaterial().isOpaque() && mc.world.getBlockState(pos).isFullCube()) {
                    double selfDmg = calculateDamage(boost2.x + 0.5, boost2.y + 0.5, boost2.z + 0.5, mc.player);
                    if (selfDmg > maxSelfDmg.getValue() || selfDmg >= mc.player.getHealth() + mc.player.getAbsorptionAmount() + 2)
                        continue;
                    list.add(facing);
                    damage.add(selfDmg);
                }
            }
            if (list.size() > 0) {
                bedSaverList.add(new BedSaver(pos, list, damage));
                list.clear();
                damage.clear();
            }
        }
        return bedSaverList;
    }

    public static float calculateDamage(final double posX, final double posY, final double posZ, final Entity entity) {
        final float doubleExplosionSize = 10.0f;
        Vec3d playerBoundingBox = entity.boundingBox.getCenter();
        final double distancedsize = BlockInteractionHelper.blockDistance(posX, posY, posZ, playerBoundingBox.x, playerBoundingBox.y, playerBoundingBox.z) / doubleExplosionSize;
        final Vec3d vec3d = new Vec3d(posX, posY, posZ);
        final double blockDensity = getBlockDensity3(vec3d, entity.getEntityBoundingBox(), entity.world);
        final double v = (1.0 - distancedsize) * blockDensity;
        final float damage = (float) (int) ((v * v + v) / 2.0 * 7.0 * doubleExplosionSize + 1.0);
        double finald = 1.0;
        if (entity instanceof EntityLivingBase) {
            finald = getBlastReduction((EntityLivingBase) entity, getDamageMultiplied(damage), new Explosion(mc.world, null, posX, posY, posZ, 5.0f, true, true));
        }
        return (float) finald;
    }
    public static float getBlockDensity(Vec3d vec, AxisAlignedBB bbox) {
        Vec3d bboxDelta = new Vec3d(
                1.0 / ((bbox.maxX - bbox.minX) * 2.0 + 1),
                1.0 / ((bbox.maxY - bbox.minY) * 2.0 + 1),
                1.0 / ((bbox.maxZ - bbox.minZ) * 2.0 + 1)
        );

        double xOff = (1.0 - Math.floor(1.0 / bboxDelta.x) * bboxDelta.x) / 2.0;
        double zOff = (1.0 - Math.floor(1.0 / bboxDelta.z) * bboxDelta.z) / 2.0;

        if (bboxDelta.x >= 0.0 && bboxDelta.y >= 0.0 && bboxDelta.z >= 0.0) {
            int nonSolid = 0;
            int total = 0;

            for (double x = 0.0; x <= 1.0; x += bboxDelta.x) {
                for (double y = 0.0; y <= 1.0; y += bboxDelta.y) {
                    for (double z = 0.0; z <= 1.0; z += bboxDelta.z) {
                        Vec3d startPos = new Vec3d(
                                xOff + bbox.minX + (bbox.maxX - bbox.minX) * x,
                                bbox.minY + (bbox.maxY - bbox.minY) * y,
                                zOff + bbox.minZ + (bbox.maxZ - bbox.minZ) * z
                        );

                        if (!rayTraceSolidCheck(startPos, vec, true)) ++nonSolid;
                        ++total;
                    }
                }
            }
            return nonSolid / total;
        }
        return 0.0f;
    }

    public static float getBlockDensity2(Vec3d vec, AxisAlignedBB bb, World world) {
        double d0 = 1.0D / ((bb.maxX - bb.minX) * 2.0D + 1.0D);
        double d1 = 1.0D / ((bb.maxY - bb.minY) * 2.0D + 1.0D);
        double d2 = 1.0D / ((bb.maxZ - bb.minZ) * 2.0D + 1.0D);
        double d3 = (1.0D - Math.floor(1.0D / d0) * d0) / 2.0D;
        double d4 = (1.0D - Math.floor(1.0D / d2) * d2) / 2.0D;

        if (d0 >= 0.0D && d1 >= 0.0D && d2 >= 0.0D) {
            int j2 = 0;
            int k2 = 0;

            for (float f = 0.0F; f <= 1.0F; f = (float) ((double) f + d0)) {
                for (float f1 = 0.0F; f1 <= 1.0F; f1 = (float) ((double) f1 + d1)) {
                    for (float f2 = 0.0F; f2 <= 1.0F; f2 = (float) ((double) f2 + d2)) {
                        double d5 = bb.minX + (bb.maxX - bb.minX) * (double) f;
                        double d6 = bb.minY + (bb.maxY - bb.minY) * (double) f1;
                        double d7 = bb.minZ + (bb.maxZ - bb.minZ) * (double) f2;

                        if (world.rayTraceBlocks(new Vec3d(d5 + d3, d6, d7 + d4), vec) == null || (world.rayTraceBlocks(new Vec3d(d5 + d3, d6, d7 + d4), vec).typeOfHit == RayTraceResult.Type.BLOCK && mc.world.getBlockState(world.rayTraceBlocks(new Vec3d(d5 + d3, d6, d7 + d4), vec).getBlockPos()).getBlock() == Blocks.BED)) {
                            ++j2;
                        }

                        ++k2;
                    }
                }
            }

            return (float) j2 / (float) k2;
        } else {
            return 0.0F;
        }
    }

    public static float getBlockDensity3(Vec3d vec, AxisAlignedBB bb, World world) {
        double d0 = 1.0D / ((bb.maxX - bb.minX) * 2.0D + 1.0D);
        double d1 = 1.0D / ((bb.maxY - bb.minY) * 2.0D + 1.0D);
        double d2 = 1.0D / ((bb.maxZ - bb.minZ) * 2.0D + 1.0D);
        double d3 = (1.0D - Math.floor(1.0D / d0) * d0) / 2.0D;
        double d4 = (1.0D - Math.floor(1.0D / d2) * d2) / 2.0D;

        if (d0 >= 0.0D && d1 >= 0.0D && d2 >= 0.0D)
        {
            int j2 = 0;
            int k2 = 0;

            for (float f = 0.0F; f <= 1.0F; f = (float)((double)f + d0))
            {
                for (float f1 = 0.0F; f1 <= 1.0F; f1 = (float)((double)f1 + d1))
                {
                    for (float f2 = 0.0F; f2 <= 1.0F; f2 = (float)((double)f2 + d2))
                    {
                        double d5 = bb.minX + (bb.maxX - bb.minX) * (double)f;
                        double d6 = bb.minY + (bb.maxY - bb.minY) * (double)f1;
                        double d7 = bb.minZ + (bb.maxZ - bb.minZ) * (double)f2;

                        if (world.rayTraceBlocks(new Vec3d(d5 + d3, d6, d7 + d4), vec) == null || (world.rayTraceBlocks(new Vec3d(d5 + d3, d6, d7 + d4), vec).typeOfHit == RayTraceResult.Type.BLOCK && mc.world.getBlockState(world.rayTraceBlocks(new Vec3d(d5 + d3, d6, d7 + d4), vec).getBlockPos()).getBlock() == Blocks.BED))
                        {
                            ++j2;
                        }

                        ++k2;
                    }
                }
            }

            return (float)j2 / (float)k2;
        }
        else
        {
            return 0.0F;
        }
    }

    public static float getBlastReduction(final EntityLivingBase entity, float damage, final Explosion explosion) {
        if (entity instanceof EntityPlayer) {
            final EntityPlayer ep = (EntityPlayer) entity;
            final DamageSource ds = DamageSource.causeExplosionDamage(explosion);
            damage = CombatRules.getDamageAfterAbsorb(damage, (float) ep.getTotalArmorValue(), (float) ep.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue());
            final int k = getEnchantmentModifierDamage(ep.getArmorInventoryList(), ds);
            final float f = MathHelper.clamp((float) k, 0.0f, 20.0f);
            damage *= 1.0f - f / 25.0f;
            ResistanceDetector w = new ResistanceDetector();
            if (entity.isPotionActive(Objects.requireNonNull(Potion.getPotionById(11))) || entity.getAbsorptionAmount() >= 9 || w.resistanceList.containsKey(entity.getName())) {
                damage -= damage / 5.0f;
            }
            return damage;
        }
        damage = CombatRules.getDamageAfterAbsorb(damage, (float) entity.getTotalArmorValue(), (float) entity.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue());
        return damage;
    }

    CFontRenderer fontRenderer = new CFontRenderer(new Font("Verdana", 0, 18), true, true);

    @Override
    public void onWorldRender(RenderEvent event) {
        if (shouldPlace) {
            BlockPos render = blockPos.up().offset(direction);
            UwUGodTessellator.prepare(GL_QUADS);
            final Vec3d interp = MathUtil.interpolateEntity(mc.player, mc.getRenderPartialTicks());
            AxisAlignedBB renderPos = new AxisAlignedBB(render.x, render.y, render.z, render.x + 1, render.y + 0.5625, render.z + 1);
            try {
                UwUGodTessellator.drawBox2(renderPos.offset(-interp.x, -interp.y, -interp.z), 255, 148, 231, 128, GeometryMasks.Quad.ALL);

            } catch (Exception o) {
            }
            UwUGodTessellator.release();
            GlStateManager.pushMatrix();
            try {
                UwUGodTessellator.glBillboardDistanceScaled((float) render.getX() + 0.5f, (float) render.getY() + 0.5f, (float) render.getZ() + 0.5f, mc.player, 1);
                final float damage = calculateDamage(render.getX() + 0.5, render.getY() + 0.5, render.getZ() + 0.5, renderEnt);
                final float damage2 = calculateDamage(render.getX() + 0.5, render.getY() + 0.5, render.getZ() + 0.5, mc.player);
                final String damageText = (Math.floor(damage) == damage ? (int) damage : String.format("%.1f", damage)) + "";
                final String damageText2 = (Math.floor(damage2) == damage2 ? (int) damage2 : String.format("%.1f", damage2)) + "";
                GlStateManager.disableDepth();
                GlStateManager.translate(-(fontRenderer.getStringWidth(damageText + "/" + damageText2) / 2.0d), 0, 0);
                fontRenderer.drawStringWithShadow("\u00a7b" + damageText + "/" + damageText2, 0, 10, 0xFFAAAAAA);
                GlStateManager.enableDepth();
            } catch (Exception o) {
            }
            GlStateManager.popMatrix();
        }
    }

    public String getHudInfo() {
        return shouldPlace ? "Killing" : "Idle";
    }

    public static boolean rayTraceSolidCheck(Vec3d start, Vec3d end, boolean shouldIgnore) {
        if (!Double.isNaN(start.x) && !Double.isNaN(start.y) && !Double.isNaN(start.z)) {
            if (!Double.isNaN(end.x) && !Double.isNaN(end.y) && !Double.isNaN(end.z)) {
                int currX = MathHelper.floor(start.x);
                int currY = MathHelper.floor(start.y);
                int currZ = MathHelper.floor(start.z);

                int endX = MathHelper.floor(end.x);
                int endY = MathHelper.floor(end.y);
                int endZ = MathHelper.floor(end.z);

                BlockPos blockPos = new BlockPos(currX, currY, currZ);
                IBlockState blockState = mc.world.getBlockState(blockPos);
                net.minecraft.block.Block block = blockState.getBlock();

                if ((blockState.getCollisionBoundingBox(mc.world, blockPos) != Block.NULL_AABB) &&
                        block.canCollideCheck(blockState, false) && (getBlocks().contains(block) || !shouldIgnore)) {
                    return true;
                }

                double seDeltaX = end.x - start.x;
                double seDeltaY = end.y - start.y;
                double seDeltaZ = end.z - start.z;

                int steps = 200;

                while (steps-- >= 0) {
                    if (Double.isNaN(start.x) || Double.isNaN(start.y) || Double.isNaN(start.z)) return false;
                    if (currX == endX && currY == endY && currZ == endZ) return false;

                    boolean unboundedX = true;
                    boolean unboundedY = true;
                    boolean unboundedZ = true;

                    double stepX = 999.0;
                    double stepY = 999.0;
                    double stepZ = 999.0;
                    double deltaX = 999.0;
                    double deltaY = 999.0;
                    double deltaZ = 999.0;

                    if (endX > currX) {
                        stepX = currX + 1.0;
                    } else if (endX < currX) {
                        stepX = currX;
                    } else {
                        unboundedX = false;
                    }

                    if (endY > currY) {
                        stepY = currY + 1.0;
                    } else if (endY < currY) {
                        stepY = currY;
                    } else {
                        unboundedY = false;
                    }

                    if (endZ > currZ) {
                        stepZ = currZ + 1.0;
                    } else if (endZ < currZ) {
                        stepZ = currZ;
                    } else {
                        unboundedZ = false;
                    }

                    if (unboundedX) deltaX = (stepX - start.x) / seDeltaX;
                    if (unboundedY) deltaY = (stepY - start.y) / seDeltaY;
                    if (unboundedZ) deltaZ = (stepZ - start.z) / seDeltaZ;

                    if (deltaX == 0.0) deltaX = -1.0e-4;
                    if (deltaY == 0.0) deltaY = -1.0e-4;
                    if (deltaZ == 0.0) deltaZ = -1.0e-4;

                    EnumFacing facing;

                    if (deltaX < deltaY && deltaX < deltaZ) {
                        facing = endX > currX ? EnumFacing.WEST : EnumFacing.EAST;
                        start = new Vec3d(stepX, start.y + seDeltaY * deltaX, start.z + seDeltaZ * deltaX);
                    } else if (deltaY < deltaZ) {
                        facing = endY > currY ? EnumFacing.DOWN : EnumFacing.UP;
                        start = new Vec3d(start.x + seDeltaX * deltaY, stepY, start.z + seDeltaZ * deltaY);
                    } else {
                        facing = endZ > currZ ? EnumFacing.NORTH : EnumFacing.SOUTH;
                        start = new Vec3d(start.x + seDeltaX * deltaZ, start.y + seDeltaY * deltaZ, stepZ);
                    }

                    currX = MathHelper.floor(start.x) - (facing == EnumFacing.EAST ? 1 : 0);
                    currY = MathHelper.floor(start.y) - (facing == EnumFacing.UP ? 1 : 0);
                    currZ = MathHelper.floor(start.z) - (facing == EnumFacing.SOUTH ? 1 : 0);

                    blockPos = new BlockPos(currX, currY, currZ);
                    blockState = mc.world.getBlockState(blockPos);
                    block = blockState.getBlock();

                    if (block.canCollideCheck(blockState, false) && (getBlocks().contains(block) || !shouldIgnore)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static List<Block> getBlocks() {
        return Arrays.asList(
                Blocks.OBSIDIAN, Blocks.BEDROCK, Blocks.COMMAND_BLOCK, Blocks.BARRIER, Blocks.ENCHANTING_TABLE, Blocks.ENDER_CHEST, Blocks.END_PORTAL_FRAME, Blocks.BEACON, Blocks.ANVIL
        );
    }

    public static float getDamageMultiplied(final float damage) {
        final int diff = mc.world.getDifficulty().getId();
        return damage * ((diff == 0) ? 0.0f : ((diff == 2) ? 1.0f : ((diff == 1) ? 0.5f : 1.5f)));
    }

    public static int getEnchantmentModifierDamage(Iterable<ItemStack> stacks, DamageSource source) {
        ModifierDamage ENCHANTMENT_MODIFIER_DAMAGE = new ModifierDamage();
        ENCHANTMENT_MODIFIER_DAMAGE.damageModifier = 0;
        ENCHANTMENT_MODIFIER_DAMAGE.source = source;
        applyEnchantmentModifierArray(ENCHANTMENT_MODIFIER_DAMAGE, stacks);
        return ENCHANTMENT_MODIFIER_DAMAGE.damageModifier;
    }

    private static void applyEnchantmentModifier(AutoCrystal2.IModifier modifier, ItemStack stack) {
        if (!stack.isEmpty()) {
            NBTTagList nbttaglist = stack.getEnchantmentTagList();

            for (int i = 0; i < nbttaglist.tagCount(); ++i) {
                int j = nbttaglist.getCompoundTagAt(i).getShort("id");
                int k = nbttaglist.getCompoundTagAt(i).getShort("lvl");

                if (Enchantment.getEnchantmentByID(j) != null) {
                    modifier.calculateModifier(Enchantment.getEnchantmentByID(j), k);
                }
            }
        }
    }

    /**
     * Executes the enchantment modifier on the array of ItemStack passed.
     */
    private static void applyEnchantmentModifierArray(AutoCrystal2.IModifier modifier, Iterable<ItemStack> stacks) {
        for (ItemStack itemstack : stacks) {
            applyEnchantmentModifier(modifier, itemstack);
        }
    }

    static final class ModifierDamage implements AutoCrystal2.IModifier {
        /**
         * Used to calculate the damage modifier (extra armor) on enchantments that the player have on equipped
         * armors.
         */
        public int damageModifier;
        /**
         * Used as parameter to calculate the damage modifier (extra armor) on enchantments that the player have on
         * equipped armors.
         */
        public DamageSource source;

        private ModifierDamage() {
        }

        /**
         * Generic method use to calculate modifiers of offensive or defensive enchantment values.
         */
        public void calculateModifier(Enchantment enchantmentIn, int enchantmentLevel) {
            this.damageModifier += enchantmentIn.calcModifierDamage(enchantmentLevel, this.source);
        }
    }

    interface IModifier {
        /**
         * Generic method use to calculate modifiers of offensive or defensive enchantment values.
         */
        void calculateModifier(Enchantment enchantmentIn, int enchantmentLevel);
    }

}
