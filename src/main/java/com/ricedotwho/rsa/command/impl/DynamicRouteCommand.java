package com.ricedotwho.rsa.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.pathfinding.GoalDungeonRoom;
import com.ricedotwho.rsa.component.impl.pathfinding.GoalDungeonXYZ;
import com.ricedotwho.rsa.component.impl.pathfinding.GoalXYZ;
import com.ricedotwho.rsa.module.impl.dungeon.DynamicRoutes;
import com.ricedotwho.rsa.module.impl.dungeon.autoroutes.NodeType;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.command.Command;
import com.ricedotwho.rsm.command.api.CommandInfo;
import com.ricedotwho.rsm.component.impl.map.handler.DungeonInfo;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;
import com.ricedotwho.rsm.utils.EtherUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DefaultPosArgument;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommandSource;

@CommandInfo(name = "dynamicroute", aliases = "dr", description = "Handles creating dynamic routes.")
public class DynamicRouteCommand extends Command {
   public LiteralArgumentBuilder<ClientCommandSource> build() {
      return (LiteralArgumentBuilder<ClientCommandSource>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)literal(
                                    this.name()
                                 )
                                 .then(literal("add").executes(DynamicRouteCommand::addNode)))
                              .then(literal("clear").executes(DynamicRouteCommand::clearNodes)))
                           .then(literal("stop").executes(DynamicRouteCommand::stopPathing)))
                        .then(
                           literal("path")
                              .then(argument("pos", BlockPosArgumentType.blockPos()).executes(ctx -> path(ctx, (DefaultPosArgument)ctx.getArgument("pos", DefaultPosArgument.class))))
                        ))
                     .then(
                        literal("roompath")
                           .then(
                              argument("room", StringArgumentType.greedyString())
                                 .executes(ctx -> dungeonRoomPath(ctx, (String)ctx.getArgument("room", String.class)))
                           )
                     ))
                  .then(
                     literal("insta")
                        .then(
                           argument("room1", StringArgumentType.string())
                              .then(
                                 argument("room2", StringArgumentType.string())
                                    .then(
                                       argument("room3", StringArgumentType.string())
                                          .executes(
                                             ctx -> insta(
                                                ctx,
                                                (String)ctx.getArgument("room1", String.class),
                                                (String)ctx.getArgument("room2", String.class),
                                                (String)ctx.getArgument("room3", String.class)
                                             )
                                          )
                                    )
                              )
                        )
                  ))
               .then(
                  literal("roomfind")
                     .then(argument("pos", BlockPosArgumentType.blockPos()).executes(ctx -> dungeonPath(ctx, (DefaultPosArgument)ctx.getArgument("pos", DefaultPosArgument.class))))
               ))
            .then(literal("cp").executes(DynamicRouteCommand::copyBlockPosLook)))
         .then(literal("remove").executes(DynamicRouteCommand::removeNode));
   }

   private static int stopPathing(CommandContext<ClientCommandSource> ctx) {
      boolean bl = ((DynamicRoutes)RSM.getModule(DynamicRoutes.class)).cancelPathing();
      if (bl) {
         RSA.chat("Cancelled pathing!");
         return 1;
      } else {
         RSA.chat("No pathing active!");
         return 0;
      }
   }

   private static int copyBlockPosLook(CommandContext<ClientCommandSource> ctx) {
      Vec3d pos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
      float yaw = MinecraftClient.getInstance().gameRenderer.getCamera().getYaw();
      float pitch = MinecraftClient.getInstance().gameRenderer.getCamera().getPitch();
      Vec3d vec = EtherUtils.rayTraceBlock(61, yaw, pitch, pos);
      Vec3d viewVector = vec.subtract(pos).normalize();
      Vec3d vec2 = viewVector.multiply(0.001F).add(vec);
      BlockPos ether = BlockPos.ofFloored(vec2);
      String s = ether.getX() + " " + ether.getY() + " " + ether.getZ();
      MinecraftClient.getInstance().keyboard.setClipboard(s);
      RSA.chat("Copied " + s);
      return 1;
   }

   private static int path(CommandContext<ClientCommandSource> ctx, DefaultPosArgument pos) {
      if (MinecraftClient.getInstance().player == null) {
         return 0;
      } else {
         BlockPos blockPos = BlockPos.ofFloored(pos.x().value(), pos.y().value(), pos.z().value());
         BlockPos startPos = BlockPos.ofFloored(MinecraftClient.getInstance().player.getEntityPos().subtract(0.0, 0.001F, 0.0));
         ((DynamicRoutes)RSM.getModule(DynamicRoutes.class)).executePath(startPos, new GoalXYZ(blockPos));
         return 1;
      }
   }

   private static int insta(CommandContext<ClientCommandSource> ctx, String... roomNames) {
      if (MinecraftClient.getInstance().player == null) {
         return 0;
      } else {
         BlockPos startPos = BlockPos.ofFloored(MinecraftClient.getInstance().player.getEntityPos().subtract(0.0, 0.001F, 0.0));
         List<GoalDungeonRoom> goals = new ArrayList<>();

         for (String s : roomNames) {
            UniqueRoom uniqueRoom = DungeonInfo.getRoomByName(s);
            if (uniqueRoom == null || uniqueRoom.getTiles().isEmpty()) {
               RSA.chat("Room not loaded!");
            }

            GoalDungeonRoom goal = GoalDungeonRoom.create(uniqueRoom);
            if (goal == null) {
               RSA.chat("Failed to create goal!");
               return 0;
            }

            goals.add(goal);
         }

         ((DynamicRoutes)RSM.getModule(DynamicRoutes.class)).pathGoals(startPos, goals);
         return 1;
      }
   }

   private static int dungeonRoomPath(CommandContext<ClientCommandSource> ctx, String uniqueRoomName) {
      if (MinecraftClient.getInstance().player == null) {
         return 0;
      } else {
         UniqueRoom uniqueRoom = DungeonInfo.getRoomByName(uniqueRoomName);
         if (uniqueRoom == null || uniqueRoom.getTiles().isEmpty()) {
            RSA.chat("Room not loaded!");
         }

         BlockPos startPos = BlockPos.ofFloored(MinecraftClient.getInstance().player.getEntityPos().subtract(0.0, 0.001F, 0.0));
         GoalDungeonRoom goal = GoalDungeonRoom.create(uniqueRoom);
         if (goal == null) {
            RSA.chat("Failed to create goal!");
            return 0;
         } else {
            ((DynamicRoutes)RSM.getModule(DynamicRoutes.class)).executePath(startPos, goal);
            return 1;
         }
      }
   }

   private static int dungeonPath(CommandContext<ClientCommandSource> ctx, DefaultPosArgument pos) {
      if (MinecraftClient.getInstance().player == null) {
         return 0;
      } else {
         BlockPos blockPos = BlockPos.ofFloored(pos.x().value(), pos.y().value(), pos.z().value());
         BlockPos startPos = BlockPos.ofFloored(MinecraftClient.getInstance().player.getEntityPos().subtract(0.0, 0.001F, 0.0));
         GoalDungeonXYZ goal = GoalDungeonXYZ.create(blockPos);
         if (goal == null) {
            RSA.chat("Failed to create goal!");
            return 0;
         } else {
            ((DynamicRoutes)RSM.getModule(DynamicRoutes.class)).executePath(startPos, goal);
            return 1;
         }
      }
   }

   private static int clearNodes(CommandContext<ClientCommandSource> ctx) {
      if (!((DynamicRoutes)RSM.getModule(DynamicRoutes.class)).clearNodes()) {
         RSA.chat("No nodes found!");
         return 0;
      } else {
         RSA.chat("Cleared all nodes!");
         return 1;
      }
   }

   private static int removeNode(CommandContext<ClientCommandSource> ctx) {
      if (!((DynamicRoutes)RSM.getModule(DynamicRoutes.class)).removeNearest()) {
         RSA.chat("No nodes found in this room!");
         return 0;
      } else {
         RSA.chat("Removed node!");
         return 1;
      }
   }

   private static int addNode(CommandContext<ClientCommandSource> ctx) {
      if (MinecraftClient.getInstance().player == null) {
         return 0;
      } else {
         boolean bl = ((DynamicRoutes)RSM.getModule(DynamicRoutes.class)).addNode(MinecraftClient.getInstance().player);
         if (!bl) {
            RSA.chat("Failed to raytrace etherwarp!");
            return 0;
         } else {
            RSA.chat("Added " + NodeType.ETHERWARP + " node!");
            return 1;
         }
      }
   }
}
