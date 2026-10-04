package de.carstenkeller.logicals.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Catsweeper: Minesweeper mit Hunden statt Bomben. Jedes aufgedeckte Feld ist eine Katze,
 * die Zahl gibt an, von wie vielen Hunden sie umzingelt ist.
 *
 * Kodierung der Spielereinträge: [HIDDEN], [REVEALED], [MARKED] (Knochen-Markierung).
 */
@Serializable
@SerialName("catsweeper")
data class CatsweeperPuzzle(
    override val width: Int,
    override val height: Int,
    /** Zellindizes der Hunde, aufsteigend. */
    val dogs: List<Int>,
    val difficulty: Difficulty,
) : Puzzle() {
    override val kind: PuzzleType get() = PuzzleType.CATSWEEPER
    override val options: PuzzleOptions get() = PuzzleOptions(difficulty, width, height)

    private val dogSet: Set<Int> by lazy { dogs.toHashSet() }

    /** Anzahl benachbarter Hunde je Zelle. */
    val dogCounts: IntArray by lazy {
        IntArray(width * height) { i -> neighbors(i).count { it in dogSet } }
    }

    fun isDog(index: Int): Boolean = index in dogSet

    fun neighbors(index: Int): List<Int> {
        val r = index / width
        val c = index % width
        return buildList {
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val rr = r + dr
                val cc = c + dc
                if (rr in 0 until height && cc in 0 until width) add(rr * width + cc)
            }
        }
    }

    override fun isEditable(index: Int): Boolean = true

    override fun initialEntries(): IntArray = IntArray(width * height)

    override fun conflicts(entries: IntArray): Set<Int> = emptySet()

    override fun isSolved(entries: IntArray): Boolean =
        entries.indices.all { isDog(it) || entries[it] == REVEALED }

    override fun isLost(entries: IntArray): Boolean = dogs.any { entries[it] == REVEALED }

    /**
     * Deckt [index] auf. Felder ohne Nachbarhunde decken ihre Nachbarn automatisch auf.
     * Ein Tippen auf eine aufgedeckte Zahl, um die bereits genauso viele Knochen liegen,
     * deckt alle übrigen Nachbarn auf.
     */
    fun reveal(entries: IntArray, index: Int): IntArray {
        val result = entries.copyOf()
        val start = when (result[index]) {
            MARKED -> return result
            REVEALED -> {
                val around = neighbors(index)
                if (around.count { result[it] == MARKED } != dogCounts[index]) return result
                around.filter { result[it] == HIDDEN }
            }
            else -> listOf(index)
        }
        val stack = ArrayDeque(start)
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (result[i] != HIDDEN) continue
            result[i] = REVEALED
            if (!isDog(i) && dogCounts[i] == 0) {
                for (n in neighbors(i)) if (result[n] == HIDDEN) stack.addLast(n)
            }
        }
        return result
    }

    fun toggleMark(entries: IntArray, index: Int): IntArray {
        val result = entries.copyOf()
        result[index] = when (result[index]) {
            HIDDEN -> MARKED
            MARKED -> HIDDEN
            else -> result[index]
        }
        return result
    }

    /**
     * Sorgt dafür, dass der erste Zug sicher ist: Hunde auf [index] und (wenn genug Platz
     * ist) in seiner Nachbarschaft werden an zufällige andere Stellen versetzt.
     */
    fun withSafeStart(index: Int, random: Random): CatsweeperPuzzle {
        val zone = (neighbors(index) + index).toSet()
        val keepFree = if (width * height - zone.size >= dogs.size) zone else setOf(index)
        val moving = dogs.filter { it in keepFree }
        if (moving.isEmpty()) return this
        val free = (0 until width * height).filter { it !in keepFree && it !in dogSet }.shuffled(random)
        val newDogs = (dogs.filter { it !in keepFree } + free.take(moving.size)).sorted()
        return copy(dogs = newDogs)
    }

    companion object {
        const val HIDDEN = 0
        const val REVEALED = 1
        const val MARKED = 2
    }
}

class CatsweeperGenerator(private val random: Random = Random.Default) {

    fun generate(width: Int, height: Int, difficulty: Difficulty): CatsweeperPuzzle {
        val density = when (difficulty) {
            Difficulty.EASY -> 0.12
            Difficulty.MEDIUM -> 0.16
            Difficulty.HARD -> 0.21
        }
        val cells = width * height
        val count = (cells * density).toInt().coerceIn(1, cells - 9)
        val dogs = (0 until cells).shuffled(random).take(count).sorted()
        return CatsweeperPuzzle(width, height, dogs, difficulty)
    }
}
