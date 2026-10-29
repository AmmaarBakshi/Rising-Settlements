package com.risingsettlements.mixin;

import com.risingsettlements.mayor.Reputation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notices completed trades, which earn the trading player reputation in the villager's
 * village. Fabric API has no trade event; {@code notifyTrade} runs on the server once per
 * finished trade. Wandering traders don't belong to a village, so only villagers count.
 */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerMixin {
	@Inject(method = "notifyTrade", at = @At("HEAD"))
	private void risingSettlements$onTrade(MerchantOffer offer, CallbackInfo ci) {
		AbstractVillager self = (AbstractVillager) (Object) this;
		if (self instanceof Villager villager && self.getTradingPlayer() instanceof ServerPlayer player) {
			Reputation.onTrade(player, villager);
		}
	}
}
