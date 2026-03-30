package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.subactions;

import com.google.gson.JsonObject;
import com.ricedotwho.rsm.utils.Accessor;

public abstract class SubAction implements Accessor {
   private final SubActionType type;

   public abstract boolean execute();

   public abstract void serialize(JsonObject var1);

   public SubActionType getType() {
      return this.type;
   }

   public SubAction(SubActionType type) {
      this.type = type;
   }
}
