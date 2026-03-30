package com.ricedotwho.rsa.module.impl.dungeon;

import com.mojang.datafixers.util.Pair;
import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsm.component.impl.location.Floor;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.map.Map;
import com.ricedotwho.rsm.component.impl.map.handler.Dungeon;
import com.ricedotwho.rsm.data.Phase7;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.client.PacketEvent.Receive;
import com.ricedotwho.rsm.event.impl.client.PacketEvent.Send;
import com.ricedotwho.rsm.event.impl.game.ChatEvent.Chat;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent.Start;
import com.ricedotwho.rsm.event.impl.world.BlockChangeEvent;
import com.ricedotwho.rsm.event.impl.world.WorldEvent.Load;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.ModeSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.utils.DungeonUtils;
import com.ricedotwho.rsm.utils.RotationUtils;
import com.ricedotwho.rsm.utils.Utils;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.Formatting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.block.LeverBlock;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.entity.EntityPose;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.component.DataComponentTypes;

@ModuleInfo(aliases = "Secrets", id = "Secrets", category = Category.DUNGEONS, hasKeybind = true)
public class SecretAura extends Module {
   private static final double CHEST_RANGE = 5.745;
   private static final double SKULL_RANGE = 4.5;
   private static final double CHEST_RANGE_SQ = 33.005025;
   private static final double SKULL_RANGE_SQ = 20.25;
   private static final String REDSTONE_KEY_ID = "fed95410-aba1-39df-9b95-1d4f361eb66e";
   private static final String WITHER_ESSENCE_ID = "e0f3e929-869e-3dca-9504-54c666ee6f23";
   private static final Text CHEST_KEY = Text.translatable("container.chest");
   private static final Text LARGE_CHEST_KEY = Text.translatable("container.chestDouble");
   private final HashSet<Integer> BOSS_LEVERS = new HashSet<>();
   private final HashSet<Integer> LIGHTS_DEV = new HashSet<>();
   private final int jewLeverHash = new BlockPos(61, 134, 142).hashCode();
   private final ModeSetting type = new ModeSetting("Type", "Aura", List.of("Aura", "Triggerbot", "None"));
   private final NumberSetting delay = new NumberSetting("Click Delay", 100.0, 4000.0, 150.0, 50.0);
   private final NumberSetting reclick = new NumberSetting("Re-Click Delay", 200.0, 10000.0, 500.0, 50.0);
   private final NumberSetting swapSlot = new NumberSetting("Swap Slot Index", 0.0, 7.0, 0.0, 1.0);
   private final BooleanSetting invWalk = new BooleanSetting("In inventory", true);
   private final BooleanSetting allowReclick = new BooleanSetting("Allow Re-click", true);
   private final BooleanSetting allowBossReclick = new BooleanSetting("Allow Boss Re-click", true);
   private final BooleanSetting inBoss = new BooleanSetting("In Boss", true);
   private final BooleanSetting autoClose = new BooleanSetting("Auto Close GUI", false);
   private final BooleanSetting forceSkyblock = new BooleanSetting("Force Skyblock", false);
   private boolean hasRedstoneKey = false;
   private final Int2LongOpenHashMap clickedBlocks = new Int2LongOpenHashMap(5);
   private final IntOpenHashSet blocksDone = new IntOpenHashSet();
   private int clickBlockCooldown = 20;
   private int lastSlot = -1;

   public SecretAura() {
      this.registerProperty(
         new Setting[]{
            this.type,
            this.delay,
            this.reclick,
            this.swapSlot,
            this.invWalk,
            this.allowReclick,
            this.allowBossReclick,
            this.inBoss,
            this.autoClose,
            this.forceSkyblock
         }
      );
      this.BOSS_LEVERS.add(new BlockPos(106, 124, 113).hashCode());
      this.BOSS_LEVERS.add(new BlockPos(94, 124, 113).hashCode());
      this.BOSS_LEVERS.add(new BlockPos(23, 132, 138).hashCode());
      this.BOSS_LEVERS.add(new BlockPos(27, 124, 127).hashCode());
      this.BOSS_LEVERS.add(new BlockPos(2, 122, 55).hashCode());
      this.BOSS_LEVERS.add(new BlockPos(14, 122, 55).hashCode());
      this.BOSS_LEVERS.add(new BlockPos(84, 121, 34).hashCode());
      this.BOSS_LEVERS.add(new BlockPos(86, 128, 46).hashCode());
      this.LIGHTS_DEV.add(new BlockPos(58, 133, 142).hashCode());
      this.LIGHTS_DEV.add(new BlockPos(58, 136, 142).hashCode());
      this.LIGHTS_DEV.add(new BlockPos(62, 136, 142).hashCode());
      this.LIGHTS_DEV.add(new BlockPos(62, 133, 142).hashCode());
      this.LIGHTS_DEV.add(new BlockPos(60, 135, 142).hashCode());
      this.LIGHTS_DEV.add(new BlockPos(60, 134, 142).hashCode());
   }

   @SubscribeEvent
   public void onWorldLoad(Load event) {
      this.clear();
      this.clickBlockCooldown = 20;
   }

   @SubscribeEvent
   public void onTickEnd(Start event) {
      this.clickBlockCooldown--;
   }

   @SubscribeEvent
   public void onSendPacket(Send event) {
      if (event.getPacket() instanceof PlayerInteractBlockC2SPacket) {
         this.clickBlockCooldown = 1;
      }
   }

   @SubscribeEvent
   public void onReceivePacket(Receive event) {
      if ((Boolean)this.autoClose.getValue() && Location.getArea().is(Island.Dungeon)) {
         if (event.getPacket() instanceof OpenScreenS2CPacket openScreenPacket && MinecraftClient.getInstance().getNetworkHandler() != null) {
            RSA.getLogger().info("Container title: {}", openScreenPacket.getName());
            String content = Formatting.strip(openScreenPacket.getName().getString());
            if (Utils.equalsOneOf(openScreenPacket.getName(), new Object[]{CHEST_KEY, LARGE_CHEST_KEY})
               || Utils.equalsOneOf(content, new Object[]{"Chest", "Large Chest"})) {
               int windowId = openScreenPacket.getSyncId();
               MinecraftClient.getInstance().getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(windowId));
               event.setCancelled(true);
            }
         }
      }
   }

   @SubscribeEvent
   public void onTickStart(Start event) {
      if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().world != null && !this.type.is("None")) {
         if ((Boolean)this.forceSkyblock.getValue() || Location.getArea().is(Island.Dungeon) && !this.isRoomDisabled()) {
            if ((Boolean)this.forceSkyblock.getValue() || !Dungeon.isInBoss() || (Boolean)this.inBoss.getValue()) {
               if ((Boolean)this.invWalk.getValue() || !(MinecraftClient.getInstance().currentScreen instanceof HandledScreen)) {
                  ClientWorld level = MinecraftClient.getInstance().world;
                  boolean sneaking = MinecraftClient.getInstance().player.getLastPlayerInput().sneak();
                  Vec3d eyePos = MinecraftClient.getInstance()
                     .player
                     .getEntityPos()
                     .add(0.0, sneaking ? 1.54F : MinecraftClient.getInstance().player.getEyeHeight(EntityPose.STANDING), 0.0);
                  Vec3d flooredEyePos = eyePos.subtract(0.5, 0.0, 0.5);
                  Iterable<BlockPos> positions;
                  if (this.type.is("Aura")) {
                     Box box = new Box(eyePos, eyePos).expand(5.745, 5.745, 5.745);
                     positions = BlockPos.iterate(box);
                  } else {
                     if (!(MinecraftClient.getInstance().crosshairTarget instanceof BlockHitResult blockHitResult)) {
                        return;
                     }

                     positions = Collections.singleton(blockHitResult.getBlockPos());
                  }

                  boolean bl = (Boolean)this.forceSkyblock.getValue()
                     || Location.getArea().is(Island.Dungeon)
                        && (Location.getFloor() == Floor.F7 || Location.getFloor() == Floor.M7)
                        && DungeonUtils.isPhase(Phase7.P3);
                  double bestDistance = Double.MAX_VALUE;
                  BlockPos bestCandidate = null;
                  boolean bl2 = !(Boolean)this.allowReclick.getValue();

                  for (BlockPos blockPos : positions) {
                     int hash = getBlockPosHash(blockPos);
                     BlockState blockState = level.getBlockState(blockPos);
                     Block block = blockState.getBlock();
                     long delay = ((BigDecimal)this.delay.getValue()).longValue();
                     if (bl) {
                        if (Dungeon.isInBoss() && block != Blocks.LEVER) {
                           continue;
                        }

                        if (block == Blocks.LEVER) {
                           if (this.checkF7BossBlock(blockPos, blockState)) {
                              if (!(Boolean)this.inBoss.getValue()) {
                                 continue;
                              }

                              if ((Boolean)this.allowBossReclick.getValue()) {
                                 bl2 = false;
                              }

                              delay = 0L;
                           } else if (this.checkLightsDev(blockPos)) {
                              continue;
                           }
                        }
                     }

                     if ((!bl2 || !this.blocksDone.contains(hash))
                        && (this.isValidBlock(block) || block == Blocks.PLAYER_HEAD && isValidSkull(blockPos, level))
                        && (!Dungeon.isInBoss() || block != Blocks.PLAYER_HEAD)) {
                        if (getSkullType(blockPos, level).equals(SecretAura.SkullType.KEY)) {
                           this.hasRedstoneKey = false;
                        }

                        if (!this.clickedBlocks.containsKey(hash)) {
                           if (delay > 0L) {
                              this.clickedBlocks.put(hash, System.currentTimeMillis() + delay);
                              continue;
                           }

                           this.clickedBlocks.put(hash, System.currentTimeMillis());
                        }

                        long nextClickTime = this.clickedBlocks.get(hash);
                        if (nextClickTime <= System.currentTimeMillis()) {
                           double d = flooredEyePos.squaredDistanceTo(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                           if ((block != Blocks.PLAYER_HEAD || !(d > 20.25)) && !(d > 33.005025) && d < bestDistance) {
                              bestDistance = d;
                              bestCandidate = new BlockPos(blockPos);
                           }
                        }
                     }
                  }

                  if (bestCandidate != null) {
                     BlockState blockStatex = level.getBlockState(bestCandidate);
                     Block blockx = blockStatex.getBlock();
                     if (blockx != Blocks.PLAYER_HEAD && MinecraftClient.getInstance().player.getInventory().getSelectedSlot() != 8
                        || SwapManager.swapSlot(((BigDecimal)this.swapSlot.getValue()).intValue())) {
                        this.clickedBlocks.put(getBlockPosHash(bestCandidate), System.currentTimeMillis() + ((BigDecimal)this.reclick.getValue()).longValue());
                        Box blockAABB = blockStatex.getOutlineShape(level, bestCandidate).getBoundingBox();
                        Vec3d center = new Vec3d(
                           (blockAABB.minX + blockAABB.maxX) * 0.5 + bestCandidate.getX(),
                           (blockAABB.minY + blockAABB.maxY) * 0.5 + bestCandidate.getY(),
                           (blockAABB.minZ + blockAABB.maxZ) * 0.5 + bestCandidate.getZ()
                        );
                        BlockHitResult result = RotationUtils.collisionRayTrace(bestCandidate, blockAABB, eyePos, center);
                        if (result != null) {
                           PacketOrderManager.register(
                              PacketOrderManager.STATE.ITEM_USE,
                              () -> SwapManager.sendBlockC08(result.getPos(), result.getSide(), blockx != Blocks.PLAYER_HEAD, true)
                           );
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean checkLightsDev(BlockPos pos) {
      return pos.getZ() == 142 && pos.getY() <= 136 && pos.getY() >= 133 && pos.getX() >= 58 && pos.getX() <= 62;
   }

   private boolean checkF7BossBlock(BlockPos pos, BlockState block) {
      int hash = pos.hashCode();
      return this.BOSS_LEVERS.contains(hash)
         || this.checkLightsDev(pos) && (this.LIGHTS_DEV.contains(hash) && !(Boolean)block.get(LeverBlock.POWERED) || hash == this.jewLeverHash);
   }

   private boolean isValidBlock(Block block) {
      return block == Blocks.AIR
         ? false
         : block == Blocks.LEVER
            || block == Blocks.CHEST
            || block == Blocks.TRAPPED_CHEST
            || block == Blocks.REDSTONE_BLOCK && this.hasRedstoneKey;
   }

   public static boolean isValidSkull(BlockPos blockPos, ClientWorld level) {
      return isValidSkull(blockPos, level, false);
   }

   public static boolean isValidSkull(BlockPos blockPos, ClientWorld level, boolean keyOnly) {
      return level.getBlockEntity(blockPos) instanceof SkullBlockEntity skullBlockEntity ? isValidProfile(skullBlockEntity.getOwner(), keyOnly) : false;
   }

   public static boolean isValidProfile(ProfileComponent gameProfile, boolean keyOnly) {
      if (gameProfile == null) {
         return false;
      } else {
         String uuid = gameProfile.getGameProfile().id().toString();
         if (keyOnly) {
            return uuid.equals("fed95410-aba1-39df-9b95-1d4f361eb66e");
         } else {
            return switch (uuid) {
               case "e0f3e929-869e-3dca-9504-54c666ee6f23", "fed95410-aba1-39df-9b95-1d4f361eb66e" -> true;
               default -> false;
            };
         }
      }
   }

   public static SecretAura.SkullType getSkullType(BlockPos blockPos, ClientWorld level) {
      return level.getBlockEntity(blockPos) instanceof SkullBlockEntity skullBlockEntity ? getSkullType(skullBlockEntity.getOwner()) : SecretAura.SkullType.NONE;
   }

   public static SecretAura.SkullType getSkullType(ProfileComponent gameProfile) {
      if (gameProfile == null) {
         return SecretAura.SkullType.NONE;
      } else {
         String uuid = gameProfile.getGameProfile().id().toString();

         return switch (uuid) {
            case "e0f3e929-869e-3dca-9504-54c666ee6f23" -> SecretAura.SkullType.ESSENCE;
            case "fed95410-aba1-39df-9b95-1d4f361eb66e" -> SecretAura.SkullType.KEY;
            default -> SecretAura.SkullType.NONE;
         };
      }
   }

   private boolean isRoomDisabled() {
      if (Location.getArea().is(Island.Dungeon) && !Dungeon.isInBoss()) {
         if (Location.getArea().is(Island.Dungeon) && Map.getCurrentRoom() == null) {
            return true;
         } else {
            String var1 = Map.getCurrentRoom().getData().name();

            return switch (var1) {
               case "Water Board", "Three Weirdos" -> true;
               default -> false;
            };
         }
      } else {
         return false;
      }
   }

   private static int getBlockPosHash(BlockPos blockPos) {
      return blockPos.getY() & 0xFF | (blockPos.getX() + 2048 & 4095) << 8 | (blockPos.getZ() + 2048 & 4095) << 20;
   }

   @SubscribeEvent
   public void onPacket(Receive event) {
      if (mc.player != null && mc.world != null && !this.type.is("None")) {
         if (event.getPacket() instanceof BlockEventS2CPacket packet) {
            if (packet.getBlock().equals(Blocks.CHERRY_LOG)) {
               this.blocksDone.add(getBlockPosHash(packet.getPos()));
            }
         } else if (event.getPacket() instanceof EntityEquipmentUpdateS2CPacket packetx) {
            Entity entity = mc.world.getEntityById(packetx.getEntityId());
            if (!(entity instanceof ArmorStandEntity) || packetx.getEquipmentList().size() < 4) {
               return;
            }

            ItemStack stack = (ItemStack)((Pair)packetx.getEquipmentList().get(4)).getSecond();
            if (!stack.isOf(Items.PLAYER_HEAD)) {
               return;
            }

            Optional<? extends ProfileComponent> profile = stack.getComponentChanges().get(DataComponentTypes.PROFILE);
            if (profile == null || profile.isEmpty() || !isValidProfile(profile.get(), true)) {
               return;
            }

            this.blocksDone.add(getBlockPosHash(new BlockPos(entity.getBlockX(), entity.getBlockY() + 2, entity.getBlockZ())));
         }
      }
   }

   @SubscribeEvent
   public void onChat(Chat event) {
      if (mc.player != null && mc.world != null && !this.type.is("None") && (Boolean)this.inBoss.getValue()) {
         String content = Formatting.strip(event.getMessage().getString());
         if ("[BOSS] Goldor: Who dares trespass into my domain?".equals(content)) {
            this.clear();
            RSA.chat("Blocks cleared!");
         }
      }
   }

   @SubscribeEvent
   public void onBlockChange(BlockChangeEvent event) {
      if (mc.player != null && mc.world != null && !this.type.is("None") && !(mc.player.squaredDistanceTo(event.getPos().asVec3()) > 40.0)) {
         if (event.getOldState().isOf(Blocks.LEVER)) {
            this.blocksDone.add(getBlockPosHash(event.getBlockPos()));
         } else if (event.getOldState().isOf(Blocks.PLAYER_HEAD)) {
            if (!event.getNewState().isOf(Blocks.AIR)) {
               return;
            }

            if (isValidSkull(event.getBlockPos(), mc.world, true)) {
               this.hasRedstoneKey = true;
            }
         } else if (event.getOldState().isOf(Blocks.REDSTONE_BLOCK)) {
            this.blocksDone.add(getBlockPosHash(event.getBlockPos()));
         }
      }
   }

   public void clear() {
      this.blocksDone.clear();
      this.hasRedstoneKey = false;
   }

   public void onEnable() {
      this.clear();
   }

   public void onDisable() {
   }

   public HashSet<Integer> getBOSS_LEVERS() {
      return this.BOSS_LEVERS;
   }

   public HashSet<Integer> getLIGHTS_DEV() {
      return this.LIGHTS_DEV;
   }

   public int getJewLeverHash() {
      return this.jewLeverHash;
   }

   public ModeSetting getType() {
      return this.type;
   }

   public NumberSetting getDelay() {
      return this.delay;
   }

   public NumberSetting getReclick() {
      return this.reclick;
   }

   public NumberSetting getSwapSlot() {
      return this.swapSlot;
   }

   public BooleanSetting getInvWalk() {
      return this.invWalk;
   }

   public BooleanSetting getAllowReclick() {
      return this.allowReclick;
   }

   public BooleanSetting getAllowBossReclick() {
      return this.allowBossReclick;
   }

   public BooleanSetting getInBoss() {
      return this.inBoss;
   }

   public BooleanSetting getAutoClose() {
      return this.autoClose;
   }

   public BooleanSetting getForceSkyblock() {
      return this.forceSkyblock;
   }

   public boolean isHasRedstoneKey() {
      return this.hasRedstoneKey;
   }

   public Int2LongOpenHashMap getClickedBlocks() {
      return this.clickedBlocks;
   }

   public IntOpenHashSet getBlocksDone() {
      return this.blocksDone;
   }

   public int getClickBlockCooldown() {
      return this.clickBlockCooldown;
   }

   public int getLastSlot() {
      return this.lastSlot;
   }

   public static enum SkullType {
      ESSENCE,
      KEY,
      NONE;
   }
}
