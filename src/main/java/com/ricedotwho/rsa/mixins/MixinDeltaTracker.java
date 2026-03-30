package com.ricedotwho.rsa.mixins;

import com.ricedotwho.rsa.component.impl.TickFreeze;
import net.minecraft.client.render.RenderTickCounter.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Dynamic.class)
public abstract class MixinDeltaTracker {
   @Shadow
   public abstract float getTickProgress(boolean var1);

   @Inject(method = "getTickProgress", at = @At("HEAD"), cancellable = true)
   private void isEntityFrozen(boolean bl, CallbackInfoReturnable<Float> cir) {
      if (TickFreeze.isFrozen()) {
         cir.setReturnValue(TickFreeze.getPartialTick());
      }
   }

   @Inject(method = "beginRenderTick", at = @At("HEAD"))
   public void advanceGameTime(long l, boolean tick, CallbackInfoReturnable<Integer> cir) {
      TickFreeze.setLastTickPartialTicks(this.getTickProgress(true));
   }
}
