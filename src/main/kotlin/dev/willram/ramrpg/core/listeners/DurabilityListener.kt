/**
 * WP-2.2: cancels vanilla durability loss ONLY for recognized RPG items, so Minecraft never breaks them
 * (`docs/design/2.2-durability.md`, decision D2). RPG durability is driven entirely by DurabilityService
 * through the DamagePipeline's APPLY-stage drain (`builtin/stats/DamageStages.kt`), never by this event.
 * Non-RPG items (anything [dev.willram.ramrpg.api.items.ItemInstanceService.identify] doesn't recognize)
 * fall through this handler untouched and keep exactly their old vanilla durability behaviour.
 */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramrpg.api.items.ItemInstanceService
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerItemDamageEvent

/**
 * [items] is an optional, defaulted-null trailing constructor parameter for the same reason WP-2.1c's
 * `ItemRequirementServices` is optional on the item-based `StatProvider`s (see
 * `docs/design/2.1c-inert-items.md`): this listener's sole construction call site is
 * `RamRPG.kt` (`DurabilityListener().register(this)`), and this WP's file scope (rule 8 / B5) forbids
 * editing `RamRPG.kt` -- that file is wired centrally by the orchestrator at merge time. Making the
 * parameter optional keeps that existing call site compiling unchanged.
 *
 * **Consequence -- read this before merging:** until the orchestrator updates that call site to pass the
 * real `ItemInstanceService` (`DurabilityListener(itemInstances)`), [items] is `null` and this listener
 * falls back to cancelling EVERY `PlayerItemDamageEvent` -- i.e. exactly the blanket-cancel behaviour this
 * file shipped with before WP-2.2. That is a deliberate fail-safe default (identical to the pre-WP-2.2
 * shipped behaviour, not a regression), not the WP's actual goal: non-RPG items only get their vanilla
 * durability back once the real service is wired in. See `docs/design/2.2-durability.md`'s orchestrator
 * TODO.
 */
class DurabilityListener(private val items: ItemInstanceService? = null) {
    fun register(consumer: TerminableConsumer) {
        consumer.bind(Events.subscribe(PlayerItemDamageEvent::class.java, EventPriority.NORMAL)
            .handler { e ->
                val svc = items
                if (svc == null || svc.identify(e.item) != null) e.isCancelled = true
            })
    }
}
