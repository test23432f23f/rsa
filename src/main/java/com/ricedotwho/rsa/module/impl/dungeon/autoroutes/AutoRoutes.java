package com.ricedotwho.rsa.module.impl.dungeon.autoroutes;

import com.google.common.reflect.TypeToken;
import com.google.gson.GsonBuilder;
import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.pathfinding.Goal;
import com.ricedotwho.rsa.component.impl.pathfinding.GoalDungeonXYZ;
import com.ricedotwho.rsa.module.impl.dungeon.DynamicRoutes;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.awaits.AwaitClick;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.awaits.AwaitSecrets;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.BatNode;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.nodes.BreakNode;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.map.Map;
import com.ricedotwho.rsm.component.impl.map.handler.Dungeon;
import com.ricedotwho.rsm.component.impl.map.map.Room;
import com.ricedotwho.rsm.component.impl.map.map.RoomData;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.Keybind;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.client.InputPollEvent;
import com.ricedotwho.rsm.event.impl.client.PacketEvent.Receive;
import com.ricedotwho.rsm.event.impl.client.PacketEvent.Send;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent.Start;
import com.ricedotwho.rsm.event.impl.game.DungeonEvent.ChangeRoom;
import com.ricedotwho.rsm.event.impl.render.Render3DEvent.Extract;
import com.ricedotwho.rsm.event.impl.world.WorldEvent.Load;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.group.DefaultGroupSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.ColourSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.KeybindSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.SaveSetting;
import com.ricedotwho.rsm.utils.Accessor;
import com.ricedotwho.rsm.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.Formatting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.jetbrains.annotations.NotNull;

@ModuleInfo(aliases = "Auto Routes", id = "Autoroutes", category = Category.DUNGEONS)
public class AutoRoutes extends Module implements Accessor {
   private final HashMap<RoomData, List<Node>> activeNodes = new HashMap<>();
   private final HashMap<String, List<Node>> redoMap = new HashMap<>();
   private static final BooleanSetting centerOnly = new BooleanSetting("Center Only", false);
   private static final BooleanSetting zeroTickBreak = new BooleanSetting("0t Break", false);
   private static final BooleanSetting use1_8Height = new BooleanSetting("Use 1.8 height for placing node", false);
   private final BooleanSetting editMode = new BooleanSetting("Edit Mode", false);
   private final KeybindSetting triggerBind = new KeybindSetting("Trigger Bind", new Keybind(0, true, this::onTrigger));
   private final KeybindSetting addBlockBind = new KeybindSetting("Add Block Bind", new Keybind(59, false, this::addBlockToInNode));
   private final KeybindSetting routeStartBind = new KeybindSetting("Route to start Bind", new Keybind(257, false, this::routeToStart));
   private final DefaultGroupSetting render = new DefaultGroupSetting("Render", this);
   private static final BooleanSetting startDepth = new BooleanSetting("Start Depth", false);
   private static final BooleanSetting nodeDepth = new BooleanSetting("Node Depth", true);
   private static final ColourSetting startColour = new ColourSetting("Start", Colour.GREEN.copy());
   private static final ColourSetting etherwarpColour = new ColourSetting("Etherwarp", Colour.CYAN.copy());
   private static final ColourSetting breakColour = new ColourSetting("Break", Colour.YELLOW.copy());
   private static final ColourSetting boomColour = new ColourSetting("Boom", Colour.RED.copy());
   private static final ColourSetting batColour = new ColourSetting("Bat", Colour.BLUE.copy());
   private static final ColourSetting aotvColour = new ColourSetting("Aotv", Colour.MAGENTA.copy());
   private static final ColourSetting useColour = new ColourSetting("Use", Colour.WHITE.copy());
   private final SaveSetting<HashMap<String, List<Node>>> data = new SaveSetting(
      "Nodes",
      "routes",
      "routes.json",
      HashMap::new,
      (new TypeToken<HashMap<String, List<Node>>>() {}).getType(),
      new GsonBuilder().registerTypeHierarchyAdapter(Node.class, new NodeAdapter()).setPrettyPrinting().create(),
      true,
      this::reload,
      null
   );
   private int tickTime = 0;
   private boolean forceNextNotSneak = false;
   private Node inNode;
   private boolean isRouting = false;
   private byte crouchDataShiftRegister = 0;
   public int lastBlockC08 = 0;
   private Class<? extends Node> lastType = null;
   private static final Set<String> SECRET_NAMES = Set.of(
      "Health Potion VIII Splash Potion",
      "Healing Potion 8 Splash Potion",
      "Healing Potion VIII Splash Potion",
      "Healing VIII Splash Potion",
      "Healing 8 Splash Potion",
      "Decoy",
      "Inflatable Jerry",
      "Spirit Leap",
      "Trap",
      "Training Weights",
      "Defuse Kit",
      "Dungeon Chest Key",
      "Treasure Talisman",
      "Revive Stone",
      "Architect's First Draft",
      "Secret Dye",
      "Candycomb"
   );

   public AutoRoutes() {
      this.registerProperty(
         new Setting[]{
            this.editMode, centerOnly, zeroTickBreak, use1_8Height, this.triggerBind, this.addBlockBind, this.routeStartBind, this.data, this.render
         }
      );
      this.render.add(new Setting[]{startDepth, nodeDepth, startColour, etherwarpColour, breakColour, boomColour, batColour, aotvColour});
      this.inNode = null;
      this.createBackup();
   }

   @SubscribeEvent
   public void onWorldLoad(Load event) {
      this.inNode = null;
      this.activeNodes.clear();
      this.crouchDataShiftRegister = 0;
      this.lastBlockC08 = 0;
   }

   @SubscribeEvent
   public void onRoomEnter(ChangeRoom event) {
      if (event.unique != null && event.room != null && event.oldRoom != null) {
         Room room = event.getRoom();
         this.inNode = null;
         if (!this.activeNodes.containsKey(room.getData())) {
            this.cacheRoomNodes(room);
         }
      }
   }

   @SubscribeEvent
   public void onRender(Extract event) {
      if (Location.getArea().is(Island.Dungeon) && Map.getCurrentRoom() != null) {
         Room currentRoom = Map.getCurrentRoom();
         List<Node> nodes = this.activeNodes.get(currentRoom.getData());
         if (nodes != null && !nodes.isEmpty()) {
            nodes.forEach(n -> n.render((Boolean)nodeDepth.getValue() && (!n.isStart() || (Boolean)startDepth.getValue())));
         }
      }
   }

   @SubscribeEvent
   public void onClientTickStart(Start event) {
      this.lastBlockC08--;
      this.isRouting = false;
      if (Location.getArea().is(Island.Dungeon)) {
         this.tickTime++;
         if (!this.hasGuiOpen()) {
            if (!(Boolean)this.editMode.getValue() && Map.getCurrentRoom() != null && MinecraftClient.getInstance().player != null) {
               Room currentRoom = Map.getCurrentRoom();
               List<Node> nodes = this.activeNodes.get(currentRoom.getData());
               if (nodes != null && !nodes.isEmpty()) {
                  Pos playerPos = new Pos(MinecraftClient.getInstance().player.getEntityPos());
                  nodes.forEach(n -> n.updateNodeState(playerPos, this.tickTime));
                  this.lastType = null;

                  while (this.handleQueue(playerPos, nodes)) {
                  }
               } else {
                  this.inNode = null;
               }
            }
         }
      }
   }

   public boolean willBeCrouchingForEtherwarpEvaluation() {
      return (this.crouchDataShiftRegister >> 1 & 1) == 1;
   }

   @SubscribeEvent
   public void onPollInputs(InputPollEvent event) {
      if (this.isRouting() && Location.getArea().is(Island.Dungeon) && !this.hasGuiOpen()) {
         PlayerInput oldInputs = event.getClientInput();
         PlayerInput newInputs = new PlayerInput(
            oldInputs.forward(),
            oldInputs.backward(),
            oldInputs.left(),
            oldInputs.right(),
            oldInputs.jump(),
            !this.forceNextNotSneak,
            oldInputs.sprint()
         );
         this.forceNextNotSneak = false;
         event.getInput().apply(newInputs);
      }
   }

   private void cacheRoomNodes(Room room) {
      List<Node> nodes = (List<Node>)((HashMap)this.data.getValue()).get(room.getData().name());
      if (nodes != null && !nodes.isEmpty()) {
         UniqueRoom uniqueRoom = room.getUniqueRoom();
         nodes.forEach(n -> n.calculate(uniqueRoom));
         this.activeNodes.put(room.getData(), nodes);
      }
   }

   public void load() {
      this.data.load();
   }

   private void reload() {
      this.activeNodes.clear();
      this.inNode = null;
      if (Location.getArea().is(Island.Dungeon)) {
         Room room = Map.getCurrentRoom();
         if (room != null && room.getUniqueRoom() != null) {
            this.cacheRoomNodes(room);
         }
      }
   }

   private boolean hasGuiOpen() {
      return MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().currentScreen instanceof HandledScreen;
   }

   public boolean clearNodes(UniqueRoom uniqueRoom) {
      if (MinecraftClient.getInstance().player != null && this.activeNodes.containsKey(uniqueRoom.getMainRoom().getData())) {
         List<Node> nodes = this.activeNodes.get(uniqueRoom.getMainRoom().getData());
         if (nodes.isEmpty()) {
            return false;
         } else {
            nodes.clear();
            this.save();
            return true;
         }
      } else {
         return false;
      }
   }

   public boolean removeNearest(UniqueRoom uniqueRoom) {
      if (MinecraftClient.getInstance().player != null && this.activeNodes.containsKey(uniqueRoom.getMainRoom().getData())) {
         List<Node> nodes = this.activeNodes.get(uniqueRoom.getMainRoom().getData());
         if (nodes.isEmpty()) {
            return false;
         } else {
            int bestIndex = -1;
            double bestDistance = Double.MAX_VALUE;
            Vec3d playerPos = MinecraftClient.getInstance().player.getEntityPos();

            for (int i = 0; i < nodes.size(); i++) {
               double d = nodes.get(i).getRealPos().squaredDistanceTo(playerPos);
               if (!(d >= bestDistance)) {
                  bestIndex = i;
                  bestDistance = d;
               }
            }

            if (bestIndex < 0) {
               return false;
            } else {
               nodes.remove(bestIndex);
               this.save();
               return true;
            }
         }
      } else {
         return false;
      }
   }

   public boolean undoNode(UniqueRoom uniqueRoom) {
      if (MinecraftClient.getInstance().player != null && this.activeNodes.containsKey(uniqueRoom.getMainRoom().getData())) {
         List<Node> nodes = this.activeNodes.get(uniqueRoom.getMainRoom().getData());
         if (nodes.isEmpty()) {
            return false;
         } else {
            if (!this.redoMap.containsKey(uniqueRoom.getName())) {
               this.redoMap.put(uniqueRoom.getName(), new ArrayList<>());
            }

            Node node = nodes.removeLast();
            this.redoMap.get(uniqueRoom.getName()).add(node);
            this.save();
            RSA.chat("Undid %s at %s", node.getName(), node.getRealPos().toChatString());
            return true;
         }
      } else {
         return false;
      }
   }

   public boolean redoNode(UniqueRoom uniqueRoom) {
      if (MinecraftClient.getInstance().player != null && this.activeNodes.containsKey(uniqueRoom.getMainRoom().getData())) {
         List<Node> nodes = this.activeNodes.get(uniqueRoom.getMainRoom().getData());
         if (!this.redoMap.containsKey(uniqueRoom.getName())) {
            return false;
         } else {
            List<Node> redo = this.redoMap.get(uniqueRoom.getName());
            if (redo.isEmpty()) {
               return false;
            } else {
               Node node = redo.removeLast();
               nodes.add(node);
               this.save();
               RSA.chat("Redid %s at %s", node.getName(), node.getRealPos().toChatString());
               return true;
            }
         }
      } else {
         return false;
      }
   }

   public void addNode(Node node, UniqueRoom uniqueRoom) {
      ((HashMap)this.data.getValue()).putIfAbsent(uniqueRoom.getName(), new ArrayList());
      List<Node> nodes = (List<Node>)((HashMap)this.data.getValue()).get(uniqueRoom.getName());
      node.calculate(uniqueRoom);
      nodes.add(node);
      if (!this.activeNodes.containsKey(uniqueRoom.getMainRoom().getData())) {
         this.activeNodes.put(uniqueRoom.getMainRoom().getData(), nodes);
      }

      this.save();
   }

   public void setForceSneak(boolean bl) {
      this.forceNextNotSneak = bl;
   }

   public void onTrigger() {
      if (this.isEnabled() && Location.getArea().is(Island.Dungeon) && Map.getCurrentRoom() != null) {
         if (this.inNode instanceof BatNode) {
            this.inNode.setTriggered(true);
         }

         if (this.inNode != null && this.inNode.hasAwaits()) {
            this.inNode.getAwaitManager().consume(AwaitClick.class, true);
            this.inNode.getAwaitManager().consume(AwaitSecrets.class, 100);
         }
      }
   }

   @SubscribeEvent
   public void onSendPacket(Send event) {
      if (Location.getArea().is(Island.Dungeon) && MinecraftClient.getInstance().world != null) {
         if (event.getPacket() instanceof PlayerInputC2SPacket packet) {
            PlayerInput input = packet.input();
            this.crouchDataShiftRegister = (byte)(this.crouchDataShiftRegister << 1);
            this.crouchDataShiftRegister = (byte)(this.crouchDataShiftRegister | (byte)(input.sneak() ? 1 : 0));
         } else if (this.inNode != null && Map.getCurrentRoom() != null) {
            if (this.inNode.hasAwaits() && this.inNode.getAwaitManager().hasAwait(AwaitType.SECRETS)) {
               if (event.getPacket() instanceof PlayerInteractBlockC2SPacket useItemOnPacket) {
                  Block block = MinecraftClient.getInstance().world.getBlockState(useItemOnPacket.getBlockHitResult().getBlockPos()).getBlock();
                  if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.PLAYER_HEAD || block == Blocks.LEVER) {
                     this.inNode.getAwaitManager().consume(AwaitSecrets.class, 1);
                     this.lastBlockC08 = 2;
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public void onReceivePacket(Receive event) {
      if (Location.getArea().is(Island.Dungeon)
         && Map.getCurrentRoom() != null
         && this.inNode != null
         && mc.world != null
         && this.inNode.hasAwaits()
         && this.inNode.getAwaitManager().hasAwait(AwaitType.SECRETS)) {
         if (event.getPacket() instanceof ItemPickupAnimationS2CPacket packet) {
            if (MinecraftClient.getInstance().world == null) {
               return;
            }

            if (!(MinecraftClient.getInstance().world.getEntityById(packet.getEntityId()) instanceof ItemEntity itemEntity)) {
               return;
            }

            String name = Formatting.strip(itemEntity.getStack().getName().getString());
            if (!SECRET_NAMES.contains(name)) {
               return;
            }

            this.inNode.getAwaitManager().consume(AwaitSecrets.class, 1);
         } else if (event.getPacket() instanceof EntitiesDestroyS2CPacket packet) {
            packet.getEntityIds()
               .forEach(
                  id -> {
                     Entity entity = mc.world.getEntityById(id);
                     if (entity instanceof ItemEntity itemEntityx
                        && entity.squaredDistanceTo(mc.player) < 64.0
                        && SECRET_NAMES.contains(Formatting.strip(itemEntityx.getStack().getName().getString()))) {
                        this.inNode.getAwaitManager().consume(AwaitSecrets.class, 1);
                     }
                  }
               );
         }
      }
   }

   private void trySetInNode(Node node) {
      if (this.inNode != node) {
         this.inNode = node;
         if (node.hasAwaits()) {
            node.getAwaitManager().onEnterNode();
         }
      }
   }

   public boolean handleQueue(Pos playerPos, List<Node> nodes) {
      List<Node> activeNodes = new ArrayList<>();

      for (Node node : nodes) {
         if (node.isInNode(playerPos)) {
            this.isRouting = true;
            if (!node.isTriggered() && !node.hasRanThisTick(this.tickTime)) {
               activeNodes.add(node);
            }
         }
      }

      if (activeNodes.isEmpty()) {
         this.inNode = null;
         return false;
      } else {
         activeNodes.sort(Comparator.<Node>comparingInt(n -> n.getPriority()).reversed());
         Node nodex = activeNodes.getFirst();
         this.trySetInNode(nodex);
         if (!nodex.shouldAwait() && this.lastBlockC08 <= 0 && (this.lastType == null || this.lastType == nodex.getClass())) {
            nodex.preTrigger(this.tickTime);
            boolean bl = nodex.run(playerPos);
            if (bl) {
               this.lastType = (Class<? extends Node>)nodex.getClass();
            }

            return bl;
         } else {
            return false;
         }
      }
   }

   private void addBlockToInNode() {
      Room currentRoom = Map.getCurrentRoom();
      if (Location.getArea().is(Island.Dungeon)
         && !Dungeon.isInBoss()
         && currentRoom != null
         && !this.activeNodes.isEmpty()
         && mc.player != null
         && this.activeNodes.containsKey(currentRoom.getData())) {
         Pos playerPos = new Pos(mc.player.getEntityPos());
         Optional<BreakNode> opt = this.activeNodes
            .get(currentRoom.getData())
            .stream()
            .filter(n -> n.isInNode(playerPos) && n instanceof BreakNode)
            .map(n -> (BreakNode)n)
            .findFirst();
         if (opt.isEmpty()) {
            RSA.chat("Not in break node");
         } else {
            opt.get().addOrRemoveBlock();
         }
      }
   }

   private void routeToStart() {
      if (mc.player != null && !this.hasGuiOpen()) {
         if (Location.getArea().is(Island.Dungeon) && !Dungeon.isInBoss() && !this.activeNodes.isEmpty()) {
            Room currentRoom = Map.getCurrentRoom();
            if (currentRoom != null && this.activeNodes.containsKey(currentRoom.getData())) {
               BlockPos startPos = mc.player.getBlockPos().down();
               Node closestStart = this.activeNodes
                  .get(currentRoom.getData())
                  .stream()
                  .filter(Node::isStart)
                  .min(Comparator.comparingDouble(n -> n.getRealPos().squaredDistanceTo(startPos.toCenterPos())))
                  .orElse(null);
               if (closestStart == null) {
                  RSA.chat("Couldn't find a start node.");
               } else {
                  Pos goalPos = closestStart.getRealPos();
                  Goal goal = GoalDungeonXYZ.create(goalPos.asBlockPos().down(goalPos.y % 1.0 == 0.0 ? 1 : 0));
                  DynamicRoutes dynamicRoutes = (DynamicRoutes)RSM.getModule(DynamicRoutes.class);
                  if (!dynamicRoutes.isEnabled()) {
                     RSA.chat("Couldn't use dynamic routes (disabled).");
                  } else {
                     dynamicRoutes.cancelPathing();
                     dynamicRoutes.executePath(startPos, goal);
                  }
               }
            }
         }
      }
   }

   public void save() {
      this.data.save();
   }

   public void createBackup() {
      File backUpDir = FileUtils.getCategoryFolder(this.data.getPath() + "/backups");
      List<Long> timeStamps = getTimeStamps(backUpDir);
      pruneBackups(backUpDir, timeStamps, 9);
      File newBackup = new File(backUpDir, System.currentTimeMillis() + ".json");

      try {
         Files.copy(this.data.getFile().toPath(), newBackup.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException var5) {
         RSA.getLogger().error("Failed to create autoroute backup!", var5);
         return;
      }

      RSA.getLogger().info("Created autoroute backup");
   }

   @NotNull
   private static List<Long> getTimeStamps(File backUpDir) {
      List<Long> timeStamps = new ArrayList<>();

      for (File file : Objects.requireNonNull(backUpDir.listFiles())) {
         String name = file.getName();
         if (name.endsWith(".json")) {
            String timeString = name.substring(0, name.length() - 5);
            if (!timeString.isEmpty()) {
               try {
                  timeStamps.add(Long.parseLong(timeString));
               } catch (NumberFormatException var9) {
               }
            }
         }
      }

      return timeStamps;
   }

   private static void pruneBackups(File backUpDir, List<Long> timeStamps, int maxSize) {
      if (timeStamps.size() > maxSize) {
         timeStamps.sort(Long::compareTo);

         for (int i = 0; i < timeStamps.size() - maxSize; i++) {
            Long ts = timeStamps.get(i);
            File file = new File(backUpDir, ts + ".json.bak");
            if (file.exists() && !file.delete()) {
               RSA.getLogger().error("Failed to delete old backup: {}", file.getName());
            }
         }
      }
   }

   public HashMap<RoomData, List<Node>> getActiveNodes() {
      return this.activeNodes;
   }

   public HashMap<String, List<Node>> getRedoMap() {
      return this.redoMap;
   }

   public BooleanSetting getEditMode() {
      return this.editMode;
   }

   public KeybindSetting getTriggerBind() {
      return this.triggerBind;
   }

   public KeybindSetting getAddBlockBind() {
      return this.addBlockBind;
   }

   public KeybindSetting getRouteStartBind() {
      return this.routeStartBind;
   }

   public DefaultGroupSetting getRender() {
      return this.render;
   }

   public SaveSetting<HashMap<String, List<Node>>> getData() {
      return this.data;
   }

   public int getTickTime() {
      return this.tickTime;
   }

   public boolean isForceNextNotSneak() {
      return this.forceNextNotSneak;
   }

   public Node getInNode() {
      return this.inNode;
   }

   public byte getCrouchDataShiftRegister() {
      return this.crouchDataShiftRegister;
   }

   public int getLastBlockC08() {
      return this.lastBlockC08;
   }

   public Class<? extends Node> getLastType() {
      return this.lastType;
   }

   public static BooleanSetting getCenterOnly() {
      return centerOnly;
   }

   public static BooleanSetting getZeroTickBreak() {
      return zeroTickBreak;
   }

   public static BooleanSetting getUse1_8Height() {
      return use1_8Height;
   }

   public static BooleanSetting getStartDepth() {
      return startDepth;
   }

   public static BooleanSetting getNodeDepth() {
      return nodeDepth;
   }

   public static ColourSetting getStartColour() {
      return startColour;
   }

   public static ColourSetting getEtherwarpColour() {
      return etherwarpColour;
   }

   public static ColourSetting getBreakColour() {
      return breakColour;
   }

   public static ColourSetting getBoomColour() {
      return boomColour;
   }

   public static ColourSetting getBatColour() {
      return batColour;
   }

   public static ColourSetting getAotvColour() {
      return aotvColour;
   }

   public static ColourSetting getUseColour() {
      return useColour;
   }

   public boolean isRouting() {
      return this.isRouting;
   }
}
