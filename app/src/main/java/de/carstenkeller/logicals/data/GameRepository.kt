package de.carstenkeller.logicals.data

import android.content.Context
import android.util.Log
import de.carstenkeller.logicals.core.GameState
import de.carstenkeller.logicals.core.PuzzleJson
import de.carstenkeller.logicals.core.PuzzleType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Hält pro Rätselart genau einen begonnenen Spielstand.
 *
 * Der aktuelle Stand liegt im Speicher (sofort sichtbar für alle Bildschirme) und wird
 * in einer anwendungsweiten Coroutine der Reihe nach als JSON-Datei geschrieben. So
 * gehen Speichervorgänge nicht verloren, wenn ein Bildschirm (und sein ViewModel)
 * direkt nach einer Eingabe geschlossen wird.
 */
class GameRepository private constructor(context: Context) {

    private sealed interface Op {
        data class Save(val state: GameState) : Op
        data class Delete(val type: PuzzleType) : Op
    }

    private val dir = File(context.filesDir, "saves")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ops = Channel<Op>(Channel.UNLIMITED)
    private val states = PuzzleType.entries.associateWith { MutableStateFlow<GameState?>(null) }
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            dir.mkdirs()
            for (type in PuzzleType.entries) {
                states.getValue(type).compareAndSet(null, read(type))
            }
            loaded.complete(Unit)
            for (op in ops) {
                when (op) {
                    is Op.Save -> write(op.state)
                    is Op.Delete -> fileFor(op.type).delete()
                }
            }
        }
    }

    fun observe(type: PuzzleType): Flow<GameState?> = flow {
        loaded.await()
        emitAll(states.getValue(type))
    }

    suspend fun load(type: PuzzleType): GameState? {
        loaded.await()
        return states.getValue(type).value
    }

    fun save(state: GameState) {
        states.getValue(state.puzzle.kind).value = state
        ops.trySend(Op.Save(state))
    }

    fun delete(type: PuzzleType) {
        states.getValue(type).value = null
        ops.trySend(Op.Delete(type))
    }

    private fun fileFor(type: PuzzleType) = File(dir, "${type.name.lowercase()}.json")

    private fun read(type: PuzzleType): GameState? {
        val file = fileFor(type)
        if (!file.exists()) return null
        return try {
            PuzzleJson.decodeFromString(GameState.serializer(), file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Spielstand für $type ist unlesbar und wird verworfen", e)
            file.delete()
            null
        }
    }

    private fun write(state: GameState) {
        try {
            val target = fileFor(state.puzzle.kind)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(PuzzleJson.encodeToString(GameState.serializer(), state))
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Spielstand konnte nicht gespeichert werden", e)
        }
    }

    companion object {
        private const val TAG = "GameRepository"

        @Volatile
        private var instance: GameRepository? = null

        fun get(context: Context): GameRepository =
            instance ?: synchronized(this) {
                instance ?: GameRepository(context.applicationContext).also { instance = it }
            }
    }
}
