/** Awards xp for mining / woodcutting / farming / fishing / enchanting / breeding. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.skills.XpContext
import dev.willram.ramrpg.api.skills.XpSource
import dev.willram.ramrpg.builtin.identity.RamSkills
import dev.willram.ramrpg.utils.BlockUtils
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.entity.EntityBreedEvent
import org.bukkit.event.entity.EntityDamageEvent
import com.destroystokyo.paper.event.player.PlayerJumpEvent
import org.bukkit.event.player.PlayerFishEvent

class NonCombatXpListener(private val skillService: SkillService) {

    fun register() {
        Events.subscribe(BlockPlaceEvent::class.java).handler { e ->
            BlockUtils.setPlayerPlaced(e.block)
        }
        Events.subscribe(BlockBreakEvent::class.java).handler { e ->
            if (BlockUtils.isPlayerPlaced(e.block)) return@handler
            val (skill, xp) = classifyBlock(e.block.type) ?: return@handler
            fire(e.player, skill, "block_${e.block.type.name.lowercase()}", xp)
        }
        Events.subscribe(PlayerFishEvent::class.java).handler { e ->
            if (e.state != PlayerFishEvent.State.CAUGHT_FISH) return@handler
            fire(e.player, RamSkills.FISHING, "fish_caught", 5.0)
        }
        Events.subscribe(EnchantItemEvent::class.java).handler { e ->
            fire(e.enchanter, RamSkills.ENCHANTING, "enchant_apply", e.expLevelCost.toDouble())
        }
        Events.subscribe(EntityBreedEvent::class.java).handler { e ->
            val p = e.breeder as? org.bukkit.entity.Player ?: return@handler
            fire(p, RamSkills.FARMING, "entity_breed", 4.0)
        }
        Events.subscribe(EntityDamageEvent::class.java).handler { e ->
            val p = e.entity as? org.bukkit.entity.Player ?: return@handler
            if (e.finalDamage <= 0.0) return@handler
            fire(p, RamSkills.DEFENSE, "damage_taken", e.finalDamage * 0.5)
        }
        Events.subscribe(PlayerJumpEvent::class.java).handler { e ->
            fire(e.player, RamSkills.AGILITY, "jump", 0.2)
        }
        // sprint distance: every ~16 blocks sprinted grants 1 xp
        Events.subscribe(org.bukkit.event.player.PlayerMoveEvent::class.java).handler { e ->
            if (!e.player.isSprinting) return@handler
            val from = e.from
            val to = e.to ?: return@handler
            if (from.world != to.world) return@handler
            val dist = from.distanceSquared(to)
            if (dist < 0.04) return@handler
            sprintAcc.merge(e.player.uniqueId, kotlin.math.sqrt(dist)) { a, b -> a + b }
            val acc = sprintAcc[e.player.uniqueId] ?: 0.0
            if (acc >= 16.0) {
                sprintAcc[e.player.uniqueId] = 0.0
                fire(e.player, RamSkills.AGILITY, "sprint", 1.0)
            }
        }
    }

    private val sprintAcc = java.util.concurrent.ConcurrentHashMap<java.util.UUID, Double>()

    private fun fire(player: org.bukkit.entity.Player, skill: SkillKey, sourceId: String, amount: Double) {
        val src = object : XpSource {
            override val key = XpSourceKey.of("ramrpg", sourceId)
            override val skill = skill
            override fun xp(ctx: XpContext): Double = amount
        }
        skillService.addXp(player, src)
    }

    private fun classifyBlock(m: Material): Pair<SkillKey, Double>? {
        if (Tag.LOGS.isTagged(m)) return RamSkills.WOODCUTTING to 4.0
        if (Tag.LEAVES.isTagged(m)) return RamSkills.FORAGING to 0.5
        if (m.name.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS) return RamSkills.MINING to oreXp(m)
        if (Tag.MINEABLE_PICKAXE.isTagged(m) && (m == Material.STONE || m == Material.COBBLESTONE || m == Material.DEEPSLATE)) return RamSkills.MINING to 0.5
        if (m == Material.SAND || m == Material.GRAVEL || m == Material.DIRT || m == Material.GRASS_BLOCK || m == Material.PODZOL || m == Material.MYCELIUM || m == Material.SOUL_SAND || m == Material.SOUL_SOIL) return RamSkills.EXCAVATION to 1.0
        if (Tag.CROPS.isTagged(m) || m == Material.PUMPKIN || m == Material.MELON || m == Material.SUGAR_CANE || m == Material.NETHER_WART) return RamSkills.FARMING to 2.0
        return null
    }

    private fun oreXp(m: Material): Double = when (m) {
        Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE -> 2.0
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE -> 4.0
        Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE -> 3.0
        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE -> 6.0
        Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE -> 5.0
        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE -> 5.0
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE -> 12.0
        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE -> 12.0
        Material.NETHER_QUARTZ_ORE -> 4.0
        Material.ANCIENT_DEBRIS -> 30.0
        else -> 1.0
    }
}
