package dev.setpocket

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RulesTest {
    @Test fun allTriplesMatchDefinition() {
        var count = 0
        for (a in 0..80) for (b in a + 1..80) for (c in b + 1..80) {
            val attrs = listOf(a,b,c).map(Rules::attributes)
            val expected = (0..3).all { axis -> attrs.map { it[axis] }.distinct().size in listOf(1,3) }
            assertEquals(expected, Rules.isSet(a,b,c))
            if (expected) count++
        }
        assertEquals(1080, count)
        assertFalse(Rules.isSet(0,0,0))
    }
    @Test fun longPracticePreservesBoardInvariants() {
        val random = Random(42)
        var board = Rules.newBoard(random)
        repeat(2000) {
            assertEquals(12, board.distinct().size)
            assertTrue(board.all { it in 0..80 })
            val triple = Rules.findSet(board)!!.toSet()
            val next = Rules.replace(board, triple, random)
            board.indices.filter { it !in triple }.forEach { assertEquals(board[it], next[it]) }
            board = next
        }
    }
    @Test fun interactionAndScore() {
        val game = Game()
        game.tap(0); game.tap(0); assertTrue(game.selected.isEmpty())
        game.showHint(); assertTrue(game.hint in game.board.indices)
        game.clear(); assertEquals(-1, game.hint)
        val set = Rules.findSet(game.board)!!
        set.forEach(game::tap)
        assertEquals(1, game.score); assertEquals(1L, game.revision); assertTrue(game.selected.isEmpty())
        val invalid = (0..9).flatMap { a -> (a+1..10).flatMap { b -> (b+1..11).map { c -> listOf(a,b,c) } } }.first { !Rules.isSet(game.board[it[0]],game.board[it[1]],game.board[it[2]]) }
        val before = game.board.toList()
        invalid.forEach(game::tap)
        assertEquals(before, game.board); assertEquals(1, game.score)
        game.newPuzzle(); assertEquals(1, game.score); assertNotNull(Rules.findSet(game.board))
    }
    @Test fun completeNormalGamesConserveAll81Cards() {
        var additions = 0
        repeat(300) { seed ->
            val game = Game.fresh(Mode.NORMAL, Random(seed))
            val removed = mutableSetOf<Int>()
            var steps = 0
            while (!game.finished) {
                assertTrue(++steps < 70)
                val all = game.board + game.deck + removed
                assertEquals(81, all.size)
                assertEquals((0..80).toSet(), all.toSet())
                assertTrue(game.board.size <= 21)
                val triple = Rules.findSet(game.board)
                if (triple == null) {
                    val before = game.deck.size
                    game.addThree(); additions++
                    assertEquals(before - 3, game.deck.size)
                } else {
                    val oldSize = game.board.size
                    val oldDeck = game.deck.size
                    val discarded = triple.map { game.board[it] }
                    val original = game.board.toList()
                    game.addThree() // must refuse while a SET exists
                    assertEquals(original, game.board)
                    triple.forEach(game::tap)
                    assertTrue(discarded.all { removed.add(it) })
                    assertEquals(if (oldSize <= 12 && oldDeck >= 3) oldDeck - 3 else oldDeck, game.deck.size)
                }
            }
            assertTrue(game.deck.isEmpty())
            assertNull(Rules.findSet(game.board))
            assertEquals(81, game.board.size + removed.size)
            assertEquals(removed.size / 3, game.score)
            val finishedBoard = game.board.toList()
            game.showHint(); game.clear(); game.addThree(); game.tap(0)
            assertTrue(game.finished); assertEquals(finishedBoard, game.board)
            game.newPuzzle(Mode.NORMAL, Random(seed))
            assertEquals(0, game.score); assertEquals(69, game.deck.size)
        }
        assertTrue(additions > 0)
    }
    @Test fun modeChangesResetOnlyWhenStartingANewGame() {
        val game = Game(score = 8)
        game.newPuzzle(Mode.NORMAL, Random(1))
        assertEquals(Mode.NORMAL, game.mode); assertEquals(0, game.score)
        game.newPuzzle(Mode.ENDLESS)
        assertEquals(Mode.ENDLESS, game.mode); assertTrue(game.deck.isEmpty())
        assertNotNull(Rules.findSet(game.board))
    }
    @Test fun everyPairHasExactlyOneCompletingCard() {
        for (a in 0..80) for (b in a + 1..80) {
            val c = Rules.completingCard(a, b)
            assertTrue(c in 0..80)
            assertTrue(Rules.isSet(a, b, c))
            assertEquals(listOf(c), (0..80).filter { Rules.isSet(a, b, it) })
        }
    }
    @Test fun completionQuestionsAndAnswerTransitions() {
        repeat(500) { seed ->
            val game = Game.fresh(Mode.COMPLETION, Random(seed))
            assertEquals(2, game.pair.size)
            assertEquals(6, game.board.size)
            assertEquals(8, (game.pair + game.board).distinct().size)
            assertEquals(1, game.board.count { Rules.isSet(game.pair[0], game.pair[1], it) })
            val answer = Rules.completingCard(game.pair[0], game.pair[1])
            val original = game.board.toList()
            val pair = game.pair.toList()
            val revision = game.revision
            game.tap(game.board.indexOfFirst { it != answer })
            assertEquals(original, game.board); assertEquals(pair, game.pair)
            assertEquals(0, game.score); assertEquals(revision, game.revision)
            game.showHint(); assertEquals(answer, game.board[game.hint])
            game.tap(game.hint)
            assertEquals(1, game.score); assertEquals(revision + 1, game.revision)
            assertEquals(-1, game.hint); assertEquals(2, game.pair.size)
            game.newPuzzle(); assertEquals(1, game.score)
            game.newPuzzle(Mode.NORMAL); assertTrue(game.pair.isEmpty()); assertEquals(0, game.score)
        }
    }
    @Test fun difficultyCandidatesHaveOneAnswerAndNearMisses() {
        for (choices in listOf(6,12,18)) repeat(100) { seed ->
            val game = Game.fresh(Mode.COMPLETION, Random(seed), choices)
            assertEquals(choices, game.board.size)
            assertEquals(choices + 2, (game.board + game.pair).distinct().size)
            val answer = Rules.completingCard(game.pair[0], game.pair[1])
            assertEquals(1, game.board.count { Rules.isSet(game.pair[0],game.pair[1],it) })
            assertTrue(game.board.filter { it != answer }.count { c -> Rules.attributes(c).zip(Rules.attributes(answer)).count { (x,y) -> x != y } == 1 } >= 5)
            game.tap(game.board.indexOf(answer))
            assertEquals(1, game.score); assertEquals(choices,game.board.size)
        }
    }
    @Test fun freeAnswerRequiresAllAttributesAndKeepsWrongInput() {
        repeat(200) { seed ->
            val game = Game.fresh(Mode.COMPLETION, Random(seed), 0)
            assertTrue(game.board.isEmpty()); assertEquals(List(4) { -1 },game.answerParts)
            game.submitAnswer(); assertEquals(0,game.score)
            val pair = game.pair.toList()
            val correct = Rules.attributes(Rules.completingCard(pair[0],pair[1]))
            correct.forEachIndexed { axis, value -> game.choosePart(axis,value) }
            game.choosePart(0,(correct[0]+1)%3)
            val wrong = game.answerParts.toList()
            game.submitAnswer()
            assertEquals(0,game.score); assertEquals(pair,game.pair); assertEquals(wrong,game.answerParts)
            game.showHint(); assertEquals(correct,game.answerParts)
            game.submitAnswer()
            assertEquals(1,game.score); assertEquals(List(4) { -1 },game.answerParts)
            assertEquals(0,game.candidateCount); assertTrue(game.board.isEmpty())
            game.newPuzzle(choices=18); assertEquals(18,game.board.size)
            game.newPuzzle(choices=0); game.showHint()
            assertEquals(1,game.answerParts.count { it != -1 })
        }
    }
}
