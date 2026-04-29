package com.atlyn.subterranea.domain.persistence

import com.atlyn.subterranea.domain.model.Achievement
import com.atlyn.subterranea.domain.model.DiceResult
import com.atlyn.subterranea.domain.model.Difficulty
import com.atlyn.subterranea.domain.model.GameCharacter
import com.atlyn.subterranea.domain.model.GameState
import com.atlyn.subterranea.domain.model.HexCoordinate
import com.atlyn.subterranea.domain.model.HexTile
import com.atlyn.subterranea.domain.model.MapPreset
import com.atlyn.subterranea.domain.model.MapPresetHint
import com.atlyn.subterranea.domain.model.Player
import com.atlyn.subterranea.domain.model.Resource
import com.atlyn.subterranea.domain.model.Structure
import com.atlyn.subterranea.domain.model.StructureType
import com.atlyn.subterranea.domain.model.TerrainType
import com.atlyn.subterranea.domain.model.TurnPhase
import com.atlyn.subterranea.domain.model.Zone
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manual JSON serialization for [GameState] to support resume-on-process-death.
 *
 * Volatile UI fields ([GameState.lastExplorationEvent], [GameState.pendingInteractiveEvent],
 * [GameState.pendingEventCoord]) are intentionally NOT persisted - they are modal overlays
 * that re-trigger on the next user action and would complicate the schema (sealed-class
 * polymorphism). Restored games default these to null.
 *
 * Schema is versioned; older versions are dropped silently to fail safely on upgrade.
 */
object GameStatePersistence {
    private const val SCHEMA_VERSION = 1

    fun serialize(state: GameState): String {
        val json = JSONObject()
        json.put("v", SCHEMA_VERSION)
        json.put("difficulty", state.difficulty.name)
        json.put("selectedCharacter", state.selectedCharacter.name)
        json.put("mapPreset", state.mapPreset.name)
        json.put("currentPlayerIndex", state.currentPlayerIndex)
        json.put("turnNumber", state.turnNumber)
        json.put("turnPhase", state.turnPhase.name)
        json.put("actionsThisTurn", state.actionsThisTurn)
        json.put("maxActionsPerTurn", state.maxActionsPerTurn)
        json.put("victoryPointsToWin", state.victoryPointsToWin)
        json.put("canExploreThisTurn", state.canExploreThisTurn)
        json.put("exploresThisTurn", state.exploresThisTurn)
        json.put("gameOver", state.gameOver)
        json.put("pendingConsolation", state.pendingConsolation)
        json.put("discountTradeAvailable", state.discountTradeAvailable)

        state.lastDiceResult?.let {
            json.put("lastDice", JSONObject().put("d1", it.die1).put("d2", it.die2))
        }

        json.put("lastProduction", resourceMapToJson(state.lastProduction))

        json.put("eventLog", JSONArray().apply {
            state.eventLog.forEach { put(it) }
        })

        json.put("players", JSONArray().apply {
            state.players.forEach { put(playerToJson(it)) }
        })

        json.put("structures", JSONArray().apply {
            state.structures.forEach { put(structureToJson(it)) }
        })

        json.put("board", JSONArray().apply {
            state.board.values.forEach { put(tileToJson(it)) }
        })

        state.winner?.let { json.put("winnerId", it.id) }

        return json.toString()
    }

    fun deserialize(s: String): GameState? {
        return try {
            val json = JSONObject(s)
            if (json.optInt("v") != SCHEMA_VERSION) {
                null
            } else {
                buildState(json)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildState(json: JSONObject): GameState {
        val difficulty = Difficulty.valueOf(json.getString("difficulty"))
        val selectedCharacter = GameCharacter.valueOf(json.getString("selectedCharacter"))
        val mapPreset = MapPreset.valueOf(json.getString("mapPreset"))
        val turnPhase = TurnPhase.valueOf(json.getString("turnPhase"))

        val players = jsonArrayMap(json.getJSONArray("players")) { playerFromJson(it) }
        val structures = jsonArrayMap(json.getJSONArray("structures")) { structureFromJson(it) }
        val tiles = jsonArrayMap(json.getJSONArray("board")) { tileFromJson(it) }
        val board = tiles.associateBy { it.coordinate }

        val eventLog = mutableListOf<String>()
        val logArr = json.getJSONArray("eventLog")
        for (i in 0 until logArr.length()) eventLog.add(logArr.getString(i))

        val lastDice = json.optJSONObject("lastDice")?.let {
            DiceResult(it.getInt("d1"), it.getInt("d2"))
        }

        val lastProduction = resourceMapFromJson(json.getJSONObject("lastProduction"))

        val winnerId = if (json.has("winnerId")) json.getInt("winnerId") else null
        val winner = winnerId?.let { id -> players.find { it.id == id } }

        return GameState(
            board = board,
            players = players,
            currentPlayerIndex = json.getInt("currentPlayerIndex"),
            turnNumber = json.getInt("turnNumber"),
            turnPhase = turnPhase,
            lastDiceResult = lastDice,
            lastExplorationEvent = null,
            lastProduction = lastProduction,
            structures = structures,
            actionsThisTurn = json.getInt("actionsThisTurn"),
            maxActionsPerTurn = json.getInt("maxActionsPerTurn"),
            gameOver = json.getBoolean("gameOver"),
            winner = winner,
            victoryPointsToWin = json.getInt("victoryPointsToWin"),
            eventLog = eventLog,
            canExploreThisTurn = json.getBoolean("canExploreThisTurn"),
            difficulty = difficulty,
            exploresThisTurn = json.getInt("exploresThisTurn"),
            selectedCharacter = selectedCharacter,
            pendingInteractiveEvent = null,
            pendingEventCoord = null,
            mapPreset = mapPreset,
            pendingConsolation = json.getBoolean("pendingConsolation"),
            discountTradeAvailable = json.getBoolean("discountTradeAvailable")
        )
    }

    // ---------- Player ----------

    private fun playerToJson(p: Player): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("resources", resourceMapToJson(p.resources))
        put("victoryPoints", p.victoryPoints)
        put("explorationCount", p.explorationCount)
        put("achievements", JSONArray().apply { p.achievements.forEach { put(it.name) } })
        put("structuresBuilt", JSONArray().apply {
            p.structuresBuilt.forEach { put(structureToJson(it)) }
        })
    }

    private fun playerFromJson(o: JSONObject): Player {
        val achArr = o.getJSONArray("achievements")
        val achievements = mutableSetOf<Achievement>()
        for (i in 0 until achArr.length()) {
            runCatching { achievements.add(Achievement.valueOf(achArr.getString(i))) }
        }
        val structuresBuilt = jsonArrayMap(o.getJSONArray("structuresBuilt")) { structureFromJson(it) }
        return Player(
            id = o.getInt("id"),
            name = o.getString("name"),
            resources = resourceMapFromJson(o.getJSONObject("resources")),
            victoryPoints = o.getInt("victoryPoints"),
            structuresBuilt = structuresBuilt,
            explorationCount = o.getInt("explorationCount"),
            achievements = achievements
        )
    }

    // ---------- Structure ----------

    private fun structureToJson(s: Structure): JSONObject = JSONObject().apply {
        put("type", s.type.name)
        put("loc", coordToJson(s.location))
        put("ownerId", s.ownerId)
        put("abilityCooldown", s.abilityCooldown)
        put("tilesIlluminated", s.tilesIlluminated)
    }

    private fun structureFromJson(o: JSONObject): Structure = Structure(
        type = StructureType.valueOf(o.getString("type")),
        location = coordFromJson(o.getJSONObject("loc")),
        ownerId = o.getInt("ownerId"),
        abilityCooldown = o.getInt("abilityCooldown"),
        tilesIlluminated = o.getInt("tilesIlluminated")
    )

    // ---------- HexTile ----------

    private fun tileToJson(t: HexTile): JSONObject = JSONObject().apply {
        put("coord", coordToJson(t.coordinate))
        put("zone", t.zone.name)
        put("isRevealed", t.isRevealed)
        put("terrain", t.terrain.name)
        t.numberToken?.let { put("numberToken", it) }
        t.secondaryNumberToken?.let { put("secondaryNumberToken", it) }
        put("hasRubble", t.hasRubble)
        put("isIlluminated", t.isIlluminated)
        t.presetHint?.let { put("presetHint", it.name) }
    }

    private fun tileFromJson(o: JSONObject): HexTile = HexTile(
        coordinate = coordFromJson(o.getJSONObject("coord")),
        zone = Zone.valueOf(o.getString("zone")),
        isRevealed = o.getBoolean("isRevealed"),
        terrain = TerrainType.valueOf(o.getString("terrain")),
        numberToken = if (o.has("numberToken")) o.getInt("numberToken") else null,
        secondaryNumberToken = if (o.has("secondaryNumberToken")) o.getInt("secondaryNumberToken") else null,
        hasRubble = o.getBoolean("hasRubble"),
        isIlluminated = o.getBoolean("isIlluminated"),
        presetHint = if (o.has("presetHint")) {
            runCatching { MapPresetHint.valueOf(o.getString("presetHint")) }.getOrNull()
        } else null
    )

    // ---------- Common helpers ----------

    private fun coordToJson(c: HexCoordinate) = JSONObject().put("q", c.q).put("r", c.r)
    private fun coordFromJson(o: JSONObject) = HexCoordinate(o.getInt("q"), o.getInt("r"))

    private fun resourceMapToJson(m: Map<Resource, Int>): JSONObject = JSONObject().apply {
        m.forEach { (r, n) -> put(r.name, n) }
    }

    private fun resourceMapFromJson(o: JSONObject): Map<Resource, Int> {
        val out = mutableMapOf<Resource, Int>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            runCatching { out[Resource.valueOf(k)] = o.getInt(k) }
        }
        return out
    }

    private inline fun <T> jsonArrayMap(arr: JSONArray, block: (JSONObject) -> T): List<T> {
        val out = ArrayList<T>(arr.length())
        for (i in 0 until arr.length()) out.add(block(arr.getJSONObject(i)))
        return out
    }
}
