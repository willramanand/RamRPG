/**
 * WP-1.7b: the RPG service dependency DAG and the exact order [dev.willram.ramrpg.RamRPG.load]
 * constructs and registers the 18 subsystem services in.
 *
 * Construction is topological: a service is only built after every service its constructor requires.
 * [dependencies] records those constructor edges (RPG-service edges only; RamCore collaborators such
 * as `PlayerDataService` are not RPG services and carry no edge). [constructionOrder] is the concrete
 * order `load()` uses, and [ServiceRegistrationOrderTest] asserts it is a valid topological order of
 * [dependencies] and covers exactly [RpgServiceKeys.all]. Keeping the graph here -- next to the keys
 * it references -- means the order is a single, test-guarded source of truth rather than an implicit
 * property of the `load()` method body.
 */
package dev.willram.ramrpg.core.services

import dev.willram.ramcore.service.ServiceKey

object RpgServiceGraph {

    /** Each service mapped to the set of other RPG services its constructor requires. */
    val dependencies: Map<ServiceKey<*>, Set<ServiceKey<*>>> = linkedMapOf(
        RpgServiceKeys.PLATFORM to emptySet(),
        RpgServiceKeys.PLAYER_STORE to emptySet(),
        RpgServiceKeys.STATS to emptySet(),
        RpgServiceKeys.SKILL_REGISTRY to emptySet(),
        RpgServiceKeys.SKILL_SERVICE to setOf(RpgServiceKeys.SKILL_REGISTRY, RpgServiceKeys.PLAYER_STORE),
        RpgServiceKeys.ITEM_DEFINITIONS to emptySet(),
        RpgServiceKeys.ITEM_INSTANCES to setOf(RpgServiceKeys.ITEM_DEFINITIONS),
        RpgServiceKeys.ENCHANTMENTS to emptySet(),
        RpgServiceKeys.ENTITY_PROFILES to emptySet(),
        RpgServiceKeys.ABILITIES to emptySet(),
        RpgServiceKeys.ABILITY_SERVICE to setOf(
            RpgServiceKeys.ABILITIES, RpgServiceKeys.PLAYER_STORE, RpgServiceKeys.SKILL_SERVICE,
        ),
        RpgServiceKeys.DAMAGE_PIPELINE to emptySet(),
        RpgServiceKeys.REFORGES to emptySet(),
        RpgServiceKeys.GEMS to emptySet(),
        RpgServiceKeys.RENDERER to setOf(
            RpgServiceKeys.ITEM_DEFINITIONS, RpgServiceKeys.ITEM_INSTANCES, RpgServiceKeys.STATS,
            RpgServiceKeys.ENCHANTMENTS, RpgServiceKeys.REFORGES, RpgServiceKeys.GEMS,
        ),
        RpgServiceKeys.ECONOMY to emptySet(),
        RpgServiceKeys.QUEST_REGISTRY to emptySet(),
        RpgServiceKeys.QUESTS to setOf(
            RpgServiceKeys.QUEST_REGISTRY, RpgServiceKeys.SKILL_SERVICE,
            RpgServiceKeys.ECONOMY, RpgServiceKeys.PLAYER_STORE,
        ),
    )

    /**
     * The order `load()` constructs and registers services in. Must stay a valid topological order of
     * [dependencies]; changing `load()`'s order means changing this list (and the test enforces it).
     */
    val constructionOrder: List<ServiceKey<*>> = listOf(
        RpgServiceKeys.PLAYER_STORE,
        RpgServiceKeys.PLATFORM,
        RpgServiceKeys.STATS,
        RpgServiceKeys.SKILL_REGISTRY,
        RpgServiceKeys.SKILL_SERVICE,
        RpgServiceKeys.ITEM_DEFINITIONS,
        RpgServiceKeys.ITEM_INSTANCES,
        RpgServiceKeys.ENCHANTMENTS,
        RpgServiceKeys.ENTITY_PROFILES,
        RpgServiceKeys.ABILITIES,
        RpgServiceKeys.ABILITY_SERVICE,
        RpgServiceKeys.DAMAGE_PIPELINE,
        RpgServiceKeys.REFORGES,
        RpgServiceKeys.GEMS,
        RpgServiceKeys.RENDERER,
        RpgServiceKeys.ECONOMY,
        RpgServiceKeys.QUEST_REGISTRY,
        RpgServiceKeys.QUESTS,
    )
}
