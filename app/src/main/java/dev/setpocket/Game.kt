package dev.setpocket

import kotlin.random.Random

/** Four ternary digits: count, shape, colour, fill. Cards are unique integers 0..80. */
object Rules {
    fun completingCard(a: Int, b: Int): Int {
        require(a in 0..80 && b in 0..80 && a != b)
        var place = 1
        var card = 0
        attributes(a).zip(attributes(b)).forEach { (x, y) ->
            card += ((6 - x - y) % 3) * place
            place *= 3
        }
        return card
    }
    fun attributes(card: Int): List<Int> = listOf(card % 3, card / 3 % 3, card / 9 % 3, card / 27 % 3)
    fun isSet(a: Int, b: Int, c: Int): Boolean = a != b && b != c && a != c &&
        attributes(a).zip(attributes(b)).zip(attributes(c)).all { (ab, cValue) -> (ab.first + ab.second + cValue) % 3 == 0 }
    fun findSet(board: List<Int>): List<Int>? {
        for (i in board.indices) for (j in i + 1 until board.size) for (k in j + 1 until board.size)
            if (isSet(board[i], board[j], board[k])) return listOf(i, j, k)
        return null
    }
    fun newBoard(random: Random = Random.Default): List<Int> {
        // Guaranteed termination: inject a SET if random sampling did not find one.
        repeat(100) {
            val board = (0..80).shuffled(random).take(12)
            if (findSet(board) != null) return board
        }
        return (listOf(0, 1, 2) + (3..80).shuffled(random).take(9)).shuffled(random)
    }
    fun replace(board: List<Int>, indices: Set<Int>, random: Random = Random.Default): List<Int> {
        require(indices.size == 3 && indices.all { it in board.indices })
        val pool = (0..80).filter { it !in board }
        repeat(100) {
            val replacements = pool.shuffled(random).take(3)
            var next = 0
            val result = board.mapIndexed { index, card -> if (index in indices) replacements[next++] else card }
            if (findSet(result) != null) return result
        }
        // Extremely rare: retain the nine unselected cards and inject a guaranteed SET.
        val remaining = board.filterIndexed { index, _ -> index !in indices }
        val available = (0..80).filter { it !in remaining }
        val triple = findSet(available)!!.map { available[it] }
        var next = 0
        return board.mapIndexed { index, card -> if (index in indices) triple[next++] else card }
    }
}

enum class Mode { NORMAL, ENDLESS, COMPLETION }

data class Game(
    var board: List<Int> = Rules.newBoard(),
    val selected: MutableSet<Int> = linkedSetOf(),
    var score: Int = 0,
    var hint: Int = -1,
    var revision: Long = 0,
    var status: String = "3枚タップして SET を見つけよう",
    var mode: Mode = Mode.ENDLESS,
    var deck: List<Int> = emptyList(),
    var pair: List<Int> = emptyList(),
    var candidateCount: Int = 6,
    var answerParts: List<Int> = List(4) { -1 }
) {
    val finished: Boolean get() = mode == Mode.NORMAL && deck.isEmpty() && Rules.findSet(board) == null
    fun tap(index: Int) {
        if (finished || index !in board.indices) return
        hint = -1
        if (mode == Mode.COMPLETION) {
            if (board[index] == Rules.completingCard(pair[0], pair[1])) {
                score++
                newPuzzle(Mode.COMPLETION)
                status = "正解！ 次の2枚に合う1枚は？"
            } else status = "惜しい！ 同じ属性は同じ、違う属性は残りの1つ"
            return
        }
        if (!selected.add(index)) selected.remove(index)
        if (selected.size < 3) { status = "${selected.size} / 3 枚を選択"; return }
        val cards = selected.map { board[it] }
        if (Rules.isSet(cards[0], cards[1], cards[2])) {
            if (mode == Mode.ENDLESS) board = Rules.replace(board, selected)
            else if (board.size <= 12 && deck.size >= 3) {
                var n = 0
                board = board.mapIndexed { i, c -> if (i in selected) deck[n++] else c }
                deck = deck.drop(3)
            } else board = board.filterIndexed { i, _ -> i !in selected }
            score++; revision++
            status = if (finished) "終了！ $score SET 見つけました" else "SET！ 次の3枚を探そう"
        } else status = "惜しい！ 各属性が「全部同じ」か「全部違う」"
        selected.clear()
    }
    fun choosePart(axis: Int, value: Int) {
        if (mode != Mode.COMPLETION || candidateCount != 0 || axis !in 0..3 || value !in 0..2) return
        answerParts = answerParts.mapIndexed { i, old -> if (i == axis) value else old }
        status = if (-1 in answerParts) "4つの属性を指定して「回答」" else "選べました。「回答」で判定"
    }
    fun submitAnswer() {
        if (mode != Mode.COMPLETION || candidateCount != 0) return
        if (-1 in answerParts) { status = "4つの属性をすべて選んでください"; return }
        val answer = answerParts.zip(listOf(1, 3, 9, 27)).sumOf { (value, place) -> value * place }
        if (Rules.isSet(pair[0], pair[1], answer)) {
            score++; newPuzzle(Mode.COMPLETION)
            status = "正解！ 次の2枚に合う1枚を指定しよう"
        } else status = "惜しい！ 属性を見直してもう一度"
    }
    fun addThree() {
        if (mode != Mode.NORMAL) return
        if (finished) { status = "終了！ $score SET 見つけました"; return }
        if (Rules.findSet(board) != null) { status = "まだ SET があります。ヒントも使えます"; return }
        if (deck.isEmpty()) return
        board = board + deck.take(3); deck = deck.drop(3)
        revision++; selected.clear(); hint = -1
        status = "3枚追加しました"
    }
    fun showHint() {
        selected.clear()
        if (mode == Mode.COMPLETION) {
            if (candidateCount == 0) {
                val correct = Rules.attributes(Rules.completingCard(pair[0], pair[1]))
                val axis = (0..3).firstOrNull { answerParts[it] != correct[it] }
                if (axis == null) { status = "この組み合わせで正解です。「回答」を押そう"; return }
                choosePart(axis, correct[axis])
                status = "${listOf("個数", "形", "色", "塗り")[axis]}を正解に合わせました"
                return
            }
            hint = board.indexOf(Rules.completingCard(pair[0], pair[1]))
            status = "金色の1枚で SET が完成します"
            return
        }
        hint = Rules.findSet(board)?.first() ?: -1
        status = when {
            finished -> "終了！ $score SET 見つけました"
            hint < 0 -> "SET がありません。「＋3枚」で追加"
            else -> "金色のカードを含む SET を探そう"
        }
    }
    fun clear() {
        selected.clear(); hint = -1; answerParts = List(4) { -1 }
        status = if (finished) "終了！ $score SET 見つけました" else if (mode == Mode.COMPLETION && candidateCount == 0) "4つの属性を指定して「回答」" else if (mode == Mode.COMPLETION) "上の2枚と SET になる1枚を選ぼう" else "3枚タップして SET を見つけよう"
    }
    fun newPuzzle(newMode: Mode = mode, random: Random = Random.Default, choices: Int = candidateCount) {
        require(choices in listOf(0, 6, 12, 18))
        candidateCount = choices
        val preserveScore = mode == newMode && mode != Mode.NORMAL
        mode = newMode
        pair = emptyList()
        if (mode == Mode.COMPLETION) {
            pair = (0..80).shuffled(random).take(2)
            val answer = Rules.completingCard(pair[0], pair[1])
            val target = Rules.attributes(answer)
            // Prefer near misses; ties stay random after shuffling.
            val distractors = (0..80).filter { it !in pair && it != answer }.shuffled(random)
                .sortedBy { card -> Rules.attributes(card).zip(target).count { (a, b) -> a != b } }
                .take((candidateCount - 1).coerceAtLeast(0))
            board = if (candidateCount == 0) emptyList() else (distractors + answer).shuffled(random)
            deck = emptyList()
        } else if (mode == Mode.NORMAL) {
            val shuffled = (0..80).shuffled(random)
            board = shuffled.take(12); deck = shuffled.drop(12)
        } else { board = Rules.newBoard(random); deck = emptyList() }
        if (!preserveScore) score = 0
        revision++; clear()
    }
    companion object {
        fun fresh(mode: Mode, random: Random = Random.Default, choices: Int = 6): Game = Game().apply { newPuzzle(mode, random, choices) }
    }
}
