package com.backrooms.mixin;

import com.backrooms.entity.ModDamageTypes;
import com.backrooms.sound.ModSounds;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.events.WorldEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Port dari {@code com.sp.mixin.EntityMixin}: air Poolrooms asam (senja/fajar, ditambah
 * malam Skinwalker di port ini) dan suara renang baru.
 *
 * <p>Aslinya damage air asam juga dipanggil di klien, tapi di sana {@code damage} tidak
 * berbuat apa-apa; di sini hanya di server.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;updateSwimming()V"))
    private void backrooms$acidWater(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.level() instanceof ServerLevel level && BackroomsLevel.at(level, self.getX()) == BackroomsLevel.POOLROOMS) {
            if (WorldEvents.get(level, self.getX()).isWaterAcidic() && self.isInWater()) {
                self.hurt(ModDamageTypes.of(level, ModDamageTypes.ACID_WATER), 1.0F);
            }
        }
    }

    @Inject(method = "getSwimSound", at = @At("RETURN"), cancellable = true)
    private void backrooms$newSwimSound(CallbackInfoReturnable<SoundEvent> cir) {
        cir.setReturnValue(ModSounds.event("swim"));
    }

    @ModifyArg(method = "waterSwimSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;playSwimSound(F)V"))
    private float backrooms$sfxLouder(float volume) {
        return volume + 0.1F;
    }
}
