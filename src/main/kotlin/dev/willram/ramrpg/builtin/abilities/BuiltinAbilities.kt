/** Default abilities: VeinMiner, Quickshot. */
package dev.willram.ramrpg.builtin.abilities

import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramrpg.api.abilities.Ability
import dev.willram.ramrpg.api.abilities.AbilityContext
import dev.willram.ramrpg.api.abilities.AbilityRegistry
import dev.willram.ramrpg.api.abilities.AbilityResult
import dev.willram.ramrpg.api.abilities.AbilityTrigger
import dev.willram.ramrpg.api.abilities.Cooldown
import dev.willram.ramrpg.api.abilities.ResourceCost
import dev.willram.ramrpg.api.effects.BlockMatchers
import dev.willram.ramrpg.api.identity.AbilityKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.skills.XpContext
import dev.willram.ramrpg.api.skills.XpSource
import dev.willram.ramrpg.builtin.identity.RamSkills
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

private fun ak(v: String) = AbilityKey.of("ramrpg", v)
private fun fail(msg: String) = AbilityResult.Fail(Component.text(msg))

class VeinMiner(private val skillService: SkillService) : Ability {
    override val key = ak("vein_miner")
    override val triggers: List<AbilityTrigger> = listOf(AbilityTrigger.BlockBreak(BlockMatchers.ANY))
    override val cooldown = Cooldown(40)
    override val costs: List<ResourceCost> = listOf(ResourceCost.Mana(15.0))
    override val unlockSkill: SkillKey = RamSkills.MINING
    override val unlockLevel: Int = 5

    override fun execute(ctx: AbilityContext): AbilityResult {
        val item = ctx.item ?: return fail("No tool")
        if (!Tag.ITEMS_PICKAXES.isTagged(item.type)) return fail("Need pickaxe")
        val origin = ctx.extraBlock ?: return fail("No block")
        if (!isOre(origin.type)) return fail("Not ore")
        val visited = HashSet<Block>()
        val queue = ArrayDeque<Block>()
        queue.add(origin)
        var count = 0
        // Origin block is broken by vanilla BlockBreakEvent; XP listener already
        // awards it. Skip it here so we only XP the *additional* blocks.
        visited.add(origin)
        for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
            if (dx == 0 && dy == 0 && dz == 0) continue
            queue.add(origin.getRelative(dx, dy, dz))
        }
        while (queue.isNotEmpty() && count < 16) {
            val b = queue.removeFirst()
            if (!visited.add(b)) continue
            if (b.type != origin.type) continue
            val xp = oreXp(b.type)
            Schedulers.run(b) { b.breakNaturally(item) }
            awardMiningXp(ctx.player, b.type, xp)
            count++
            for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                if (dx == 0 && dy == 0 && dz == 0) continue
                queue.add(b.getRelative(dx, dy, dz))
            }
        }
        return AbilityResult.Success
    }

    private fun awardMiningXp(player: Player, mat: Material, xp: Double) {
        val src = object : XpSource {
            override val key = XpSourceKey.of("ramrpg", "veinminer_${mat.name.lowercase()}")
            override val skill = RamSkills.MINING
            override fun xp(ctx: XpContext): Double = xp
        }
        skillService.addXp(player, src)
    }

    private fun isOre(m: Material): Boolean = m.name.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS

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

class Quickshot : Ability {
    override val key = ak("quickshot")
    override val triggers: List<AbilityTrigger> = listOf(AbilityTrigger.SneakRightClick)
    override val cooldown = Cooldown(200)
    override val costs: List<ResourceCost> = listOf(ResourceCost.Mana(40.0))
    override val unlockSkill: SkillKey = RamSkills.COMBAT
    override val unlockLevel: Int = 10

    override fun execute(ctx: AbilityContext): AbilityResult {
        val item = ctx.item ?: return fail("No item")
        if (item.type != Material.BOW && item.type != Material.CROSSBOW) return fail("Need bow")
        ctx.player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 200, 1, true, false, true))
        return AbilityResult.Success
    }
}

object BuiltinAbilities {
    fun registerAll(reg: AbilityRegistry, skillService: SkillService) {
        val owner = "ramrpg-builtin"
        reg.register(owner, VeinMiner(skillService))
        reg.register(owner, Quickshot())
    }
}

/** Broken block when ability fires from BlockBreakEvent. */
val AbilityContext.extraBlock: Block?
    get() = (this as? BlockAwareAbilityContext)?.block

class BlockAwareAbilityContext(
    override val player: Player,
    override val item: ItemStack?,
    override val target: Entity? = null,
    override val damageVictim: LivingEntity? = null,
    val block: Block? = null,
) : AbilityContext
