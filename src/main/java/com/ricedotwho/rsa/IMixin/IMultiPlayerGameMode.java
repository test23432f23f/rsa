package com.ricedotwho.rsa.IMixin;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.SequencedPacketCreator;

public interface IMultiPlayerGameMode {
   void sendPacketSequenced(ClientWorld var1, SequencedPacketCreator var2);

   void syncSlot();
}
