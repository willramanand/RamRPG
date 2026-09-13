/**
 * WP-5.3: fills the seam WP-1.7b reserved for armor sets. Everything this WP needs -- the [SetRegistry],
 * the packaged `content/sets/builtin.conf` load, and [SetStatProvider]'s registration into
 * [StatService] -- happens HERE, resolved from [ServiceContext], so `RamRPG.kt` is never touched (B5).
 *
 * Mirrors [dev.willram.ramrpg.builtin.items.BuiltinItems]'s precedent exactly: [RpgContentLoader.loadPackaged]
 * reads the packaged classpath resource (a small, bounded, one-time classpath read + single-file HOCON
 * parse) SYNCHRONOUSLY at this enable()-time bootstrap point -- the same Folia rule-4 carve-out
 * `BuiltinItems` documents ("at load()/enable() bootstrap where the existing content load already
 * runs"). It does NOT depend on (or require) an operator's data folder, so this module needs no
 * `ctx as? RamPlugin` cast the way `ContentModule` does for its operator-editable `content/` pipeline.
 *
 * The three effect registries (actions/conditions/block-matchers) are rebuilt here with the SAME
 * builtin registrations [dev.willram.ramrpg.core.modules.ContentModule] uses
 * ([BuiltinEffectActions.registerAll] is pure data registration, so two independent instances agree) --
 * this WP's shipped sets only use `type = stat` effects, but building the full registry set keeps this
 * module byte-for-byte consistent with the item/enchant loading path rather than a narrower one-off.
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.core.config.RpgContentLoader
import dev.willram.ramrpg.core.config.specs.EffectSpec
import dev.willram.ramrpg.core.effects.BlockMatcherRegistry
import dev.willram.ramrpg.core.effects.BuiltinEffectActions
import dev.willram.ramrpg.core.effects.EffectActionRegistry
import dev.willram.ramrpg.core.effects.EffectConditionRegistry
import dev.willram.ramrpg.core.listeners.ItemRequirementServices
import dev.willram.ramrpg.core.platform.PlatformScheduler
import dev.willram.ramrpg.core.services.RpgServiceKeys
import dev.willram.ramrpg.core.services.SetRegistryImpl
import dev.willram.ramrpg.core.services.SetStatProvider
import java.util.logging.Logger

class SetModule(private val ctx: ServiceContext) : TerminableModule {

    override fun setup(consumer: TerminableConsumer) {
        val platform: PlatformScheduler = ctx.service(RpgServiceKeys.PLATFORM)
        val stats: StatService = ctx.service(RpgServiceKeys.STATS)
        val itemInstances: ItemInstanceService = ctx.service(RpgServiceKeys.ITEM_INSTANCES)
        val itemDefs: ItemDefinitionRegistry = ctx.service(RpgServiceKeys.ITEM_DEFINITIONS)
        val skillRegistry: SkillRegistry = ctx.service(RpgServiceKeys.SKILL_REGISTRY)
        val skillService: SkillService = ctx.service(RpgServiceKeys.SKILL_SERVICE)

        // Same three-registry shape ContentModule builds -- populated with the builtin ids (data, not
        // `when`s) so the shipped sets' `type = stat` threshold effects resolve.
        val actions = EffectActionRegistry()
        val conditions = EffectConditionRegistry()
        val matchers = BlockMatcherRegistry()
        BuiltinEffectActions.registerAll(actions, conditions, matchers, platform)
        val effectRegistries = EffectSpec.Registries(actions, conditions, matchers)

        val registry = SetRegistryImpl()
        val result = RpgContentLoader.loadPackaged(effectRegistries)
        if (result.successful()) {
            for (spec in result.sets) registry.register(OWNER, spec.toDefinition())
        } else {
            // Curated, packaged content: a load failure here is a packaging bug, not an authoring
            // mistake to silently swallow -- log it loudly, same severity ContentModule gives operator
            // content errors, but never throw (a broken set pack must not take the whole plugin down).
            Logger.getLogger("RamRPG").warning(
                "SetModule: packaged content/sets/builtin.conf failed to load: " +
                    result.errors().joinToString("; ") { it.describe() }
            )
        }

        // Constructed here (a brand-new registration site this WP owns, unlike the four WP-2.1c
        // providers whose call sites are frozen in RamRPG.kt), so a REAL ItemRequirementServices is
        // always available -- no fail-closed wiring gap for set-member requirement/inert checks.
        val requirementServices = ItemRequirementServices(skillRegistry, skillService, stats)
        stats.registerProvider(SetStatProvider(itemInstances, itemDefs, registry, requirementServices), OWNER)
    }

    companion object {
        const val OWNER = "ramrpg-set"
    }
}
