/**
 * RamRPG glue for RamCore's loot system. An RPG item drop is a LootReward whose payload is an
 * [RpgItemPayload] (an ItemKey plus the ItemInstanceInit used to build the stack). [SEED] is the
 * LootFunction that stamps each generated reward with a concrete rollSeed drawn from the generation
 * random, so a dropped item's stat rolls are deterministic and reproducible (closing the old
 * seedless drop path). The actual ItemStack is built from the payload by LootListener (off-thread,
 * on the entity's context) — never here, so this layer stays unit-testable.
 */
package dev.willram.ramrpg.core.loot

import dev.willram.ramcore.loot.LootContext
import dev.willram.ramcore.loot.LootFunction
import dev.willram.ramcore.loot.LootReward
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.items.ItemInstanceInit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

/** Reward id all RPG item drops carry; LootListener resolves these into ItemStacks. */
const val RPG_ITEM_REWARD_ID: String = "ramrpg:item"

/** LootReward payload for an RPG item drop. Not persisted here — WP-1.1b owns the codec. */
data class RpgItemPayload(val item: ItemKey, val init: ItemInstanceInit)

object RpgLootFunctions {

    /** A base reward for [item] (x[count]); the seed is filled in later by [SEED]. */
    fun rpgItemReward(item: ItemKey, count: Int = 1): LootReward =
        LootReward.of(RPG_ITEM_REWARD_ID, RpgItemPayload(item, ItemInstanceInit()), count)

    /**
     * Stamps an RPG-item reward with a rollSeed from the generation random, so each drop rolls its
     * own stats and those rolls can be reproduced from the stored seed. A no-op on other rewards.
     */
    val SEED: LootFunction = LootFunction { reward, _, random ->
        when (val payload = reward.payload()) {
            is RpgItemPayload -> LootReward.of(
                reward.id(),
                payload.copy(init = payload.init.copy(rollSeed = random.nextLong())),
                reward.amount(),
            )
            else -> reward
        }
    }
}

object RpgLootContexts {
    /** Loot context for a mob kill: killer as the player, the mob as the source entity. */
    fun forKill(entity: LivingEntity, killer: Player?): LootContext {
        val b = LootContext.builder("kill").sourceEntity(entity.uniqueId)
        if (killer != null) b.player(killer.uniqueId)
        return b.build()
    }
}
