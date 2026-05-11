/**
 * Identity keys for content registries. Each key wraps a RamCore [ContentId]
 * (namespace + value) and acts as a typed handle so registries cannot be
 * cross-wired (e.g. a `StatKey` cannot be passed where an `ItemKey` is expected).
 */
package dev.willram.ramrpg.api.identity

import dev.willram.ramcore.content.ContentId

@JvmInline value class StatKey(val id: ContentId) {
    override fun toString(): String = id.toString()
    companion object { fun of(ns: String, v: String) = StatKey(ContentId.of(ns, v)) }
}

@JvmInline value class SkillKey(val id: ContentId) {
    override fun toString(): String = id.toString()
    companion object { fun of(ns: String, v: String) = SkillKey(ContentId.of(ns, v)) }
}

@JvmInline value class ItemKey(val id: ContentId) {
    override fun toString(): String = id.toString()
    companion object { fun of(ns: String, v: String) = ItemKey(ContentId.of(ns, v)) }
}

@JvmInline value class EnchantmentKey(val id: ContentId) {
    override fun toString(): String = id.toString()
    companion object { fun of(ns: String, v: String) = EnchantmentKey(ContentId.of(ns, v)) }
}

@JvmInline value class AbilityKey(val id: ContentId) {
    override fun toString(): String = id.toString()
    companion object { fun of(ns: String, v: String) = AbilityKey(ContentId.of(ns, v)) }
}

@JvmInline value class EffectKey(val id: ContentId) {
    override fun toString(): String = id.toString()
    companion object { fun of(ns: String, v: String) = EffectKey(ContentId.of(ns, v)) }
}

@JvmInline value class EntityProfileKey(val id: ContentId) {
    override fun toString(): String = id.toString()
    companion object { fun of(ns: String, v: String) = EntityProfileKey(ContentId.of(ns, v)) }
}

@JvmInline value class DamageTypeKey(val id: ContentId) {
    override fun toString(): String = id.toString()
    companion object { fun of(ns: String, v: String) = DamageTypeKey(ContentId.of(ns, v)) }
}

@JvmInline value class XpSourceKey(val id: ContentId) {
    override fun toString(): String = id.toString()
    companion object { fun of(ns: String, v: String) = XpSourceKey(ContentId.of(ns, v)) }
}

object RamRpgNamespace { const val NS = "ramrpg" }
