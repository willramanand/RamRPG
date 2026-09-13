/**
 * Per-player runtime state: skill levels, xp totals, mana, last-active skill.
 * Backed by RamCore DataItem so FilePlayerStore can persist via JSON.
 */
package dev.willram.ramrpg.core.storage

import dev.willram.ramcore.data.DataItem
import dev.willram.ramrpg.api.identity.SkillKey
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerRpgData : DataItem() {
    val skillLevels: MutableMap<String, Int> = ConcurrentHashMap()
    val skillXp: MutableMap<String, Double> = ConcurrentHashMap()
    /** -1.0 marks uninitialized; ManaRegen seeds to current max on first tick. */
    @Volatile var currentMana: Double = -1.0
    @Volatile var maxManaCache: Double = 0.0
    /** Stored as ContentId string; null until first xp gain. */
    @Volatile var lastActiveSkillId: String? = null
    // Quest progress moved to RamCore's ObjectiveProgressStore in WP-1.2a; only the daily-reset stamp
    // stays on the profile.
    /** Epoch ms of last daily reset. */
    @Volatile var lastDailyReset: Long = 0L
    /** Ability keys (ContentId.toString) the player has toggled off. */
    val disabledAbilities: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun getLevel(key: SkillKey): Int = skillLevels.getOrDefault(key.id.toString(), 1)
    fun getXp(key: SkillKey): Double = skillXp.getOrDefault(key.id.toString(), 0.0)
    fun setLevel(key: SkillKey, lvl: Int) { skillLevels[key.id.toString()] = lvl; markDirty() }
    fun setXp(key: SkillKey, xp: Double) { skillXp[key.id.toString()] = xp; markDirty() }

    /**
     * Deep copy for [dev.willram.ramcore.playerdata.PlayerDataKey]'s snapshot hook. PlayerDataService
     * calls this on the player's own thread right before handing the value to the async writer, so the
     * writer never serialises a value that gameplay is still mutating. Every mutable collection and
     * scalar is copied; [dataVersion] carries so the store envelope keeps the right version. Extend
     * this the same way when a later dataVersion bump adds fields (v2 quests, v3 buffs, v4 perks).
     */
    fun snapshot(): PlayerRpgData {
        val copy = PlayerRpgData()
        copy.skillLevels.putAll(this.skillLevels)
        copy.skillXp.putAll(this.skillXp)
        copy.disabledAbilities.addAll(this.disabledAbilities)
        copy.currentMana = this.currentMana
        copy.maxManaCache = this.maxManaCache
        copy.lastActiveSkillId = this.lastActiveSkillId
        copy.lastDailyReset = this.lastDailyReset
        copy.dataVersion(this.dataVersion())
        return copy
    }
}

interface PlayerStore {
    fun get(id: UUID): PlayerRpgData?
    fun require(id: UUID): PlayerRpgData
    fun put(id: UUID, data: PlayerRpgData)
    fun remove(id: UUID): PlayerRpgData?
    fun all(): Map<UUID, PlayerRpgData>
}

class InMemoryPlayerStore : PlayerStore {
    private val map = ConcurrentHashMap<UUID, PlayerRpgData>()
    override fun get(id: UUID): PlayerRpgData? = map[id]
    override fun require(id: UUID): PlayerRpgData = map.computeIfAbsent(id) { PlayerRpgData() }
    override fun put(id: UUID, data: PlayerRpgData) { map[id] = data }
    override fun remove(id: UUID): PlayerRpgData? = map.remove(id)
    override fun all(): Map<UUID, PlayerRpgData> = map.toMap()
}
