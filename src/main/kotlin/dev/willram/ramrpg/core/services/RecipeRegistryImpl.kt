package dev.willram.ramrpg.core.services

import dev.willram.ramcore.content.ContentKey
import dev.willram.ramcore.content.ContentRegistry
import dev.willram.ramrpg.api.crafting.Recipe
import dev.willram.ramrpg.api.crafting.RecipeKey
import dev.willram.ramrpg.api.crafting.RecipeRegistry
import dev.willram.ramrpg.api.crafting.StationKey

/**
 * WP-3.1b: owner-scoped [Recipe] registry, backed by RamCore's [ContentRegistry] exactly like
 * [GemRegistryImpl] / [ItemDefinitionRegistryImpl] / [ReforgeRegistryImpl] -- so `/rpg reload` can
 * owner-unregister a content pack via [unregisterOwner] the same way every other RPG content type does.
 */
class RecipeRegistryImpl(
    private val backing: ContentRegistry<Recipe> = ContentRegistry.create(Recipe::class.java),
) : RecipeRegistry {
    override fun register(owner: String, recipe: Recipe) {
        backing.register(owner, ContentKey.of(recipe.key.id, Recipe::class.java), recipe)
    }

    override fun unregisterOwner(owner: String): Int = backing.unregisterOwner(owner)

    override fun get(key: RecipeKey): Recipe? = backing.get(key.id).orElse(null)

    override fun all(): Collection<Recipe> = backing.entries().map { it.value() }

    override fun forStation(station: StationKey): Collection<Recipe> =
        backing.entries().map { it.value() }.filter { it.station == station }
}
