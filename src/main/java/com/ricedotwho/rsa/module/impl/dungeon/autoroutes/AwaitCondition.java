package com.ricedotwho.rsa.module.impl.dungeon.autoroutes;

import com.google.gson.JsonObject;

public abstract class AwaitCondition<T> {
   private final AwaitType type;

   public abstract boolean test(Node var1);

   protected abstract void consume(T var1);

   public abstract void onEnter();

   public abstract void reset();

   public abstract void serialize(JsonObject var1);

   public AwaitCondition(AwaitType type) {
      this.type = type;
   }

   public AwaitType getType() {
      return this.type;
   }
}
