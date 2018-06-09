package com.pechi.poker.game

import com.pechi.poker.deck.PokerCard
import com.pechi.poker.deck.PokerDeck
import com.pechi.poker.deck.PokerHand

data class Game(val deck: PokerDeck, val players: List<Player>, var state: State, private var moves: List<Move>) {

    fun drawCard(): Unit {
        val card = deck.mdeck.pop()
        val move = Move(card)
        addMove(move)
        applyMove(move)
    }

    fun dropCard(): PokerCard {
        val card = deck.mdeck.pop()
        addMove(Move(card, MoveType.BOTAR))
        return card
    }

    fun addMove(move: Move): Unit {
        this.moves += move
    }

    fun applyMove(move: Move): Unit {
        state = this.state.apply(move)
    }

    fun init(): Unit {
        deck.init()
        (1..2).forEach {
            players.forEach {
                it.cards += deck.mdeck.pop()
            }
        }
    }
}


enum class MoveType {
    BOTAR, JUGAR, APOSTAR
}

data class Bet(val coin100: Int = 0, val coin50: Int = 0, val coin25: Int = 0, val coin10: Int = 0) {
    fun totalMoney(): Int {
        return coin100 + coin50 + coin25 + coin10
    }
}

data class Move(val card: PokerCard, val type: MoveType = MoveType.JUGAR)


data class Player(val name: String, var cards: List<PokerCard>, var hand: PokerHand, var folded: Boolean = false, val coin100: Int = 10, val coin50: Int = 10, val coin25: Int = 10, val coin10: Int = 10) {

    fun totalMoney(): Int {
        return coin100 + coin50 + coin25 + coin10
    }

    fun placeBet(amount: Int): Bet {
        wants = listOf(
                Chip("100", 100, coin100),
                Chip("50", 50, coin50),
                Chip("25", 25, coin25),
                Chip("10", 10, coin10)
        )
        val (coins, _, _) = dist(wants.size - 1, amount)

        val groupedCoin = coins.groupBy(Chip::name)
        return Bet(groupedCoin.getOrElse("100") { emptyList() }.size,
                groupedCoin.getOrElse("50") { emptyList() }.size,
                groupedCoin.getOrElse("25") { emptyList() }.size,
                groupedCoin.getOrElse("10") { emptyList() }.size)
    }

    data class Chip(val name: String, val weight: Int, val value: Int)

    var wants = listOf(
            Chip("100", 100, 100),
            Chip("50", 50, 50),
            Chip("25", 25, 25),
            Chip("10", 10, 10)
    )


    fun dist(i: Int, w: Int): Triple<MutableList<Chip>, Int, Int> {
        val chosen = mutableListOf<Chip>()
        if (i < 0 || w == 0) return Triple(chosen, 0, 0)
        else if (wants[i].weight > w) return dist(i - 1, w)
        val (l0, w0, v0) = dist(i - 1, w)
        var (l1, w1, v1) = dist(i, w - wants[i].weight)
        v1 += wants[i].value
        if (v1 > v0) {
            l1.add(wants[i])
            return Triple(l1, w1 + wants[i].weight, v1)
        }
        return Triple(l0, w0, v0)
    }
}


data class State(val deck: PokerDeck, val tableCards: List<PokerCard>) {

    fun apply(move: Move): State {
        this.deck.removeCard(move.card)

        return State(this.deck, tableCards + move.card)
    }

    companion object {
        fun newState(deck: PokerDeck) = State(deck, emptyList())
    }
}


data class GameMatch(var players: List<Player>) {
    enum class GAME_STAGE {
        START_BETS, WAIT_FOR_BETS, DEALING, BLINDS, CALL_RAISE_FOLD, HIGHS, LOWS
    }

    val MAX_CARDS_TABLE = 5
    val START_CARDS_TABLE = 3
    var minBetAmount = 20
    val mDeck = PokerDeck()
    val mGame: Game = Game(mDeck, players, State.newState(mDeck), emptyList())
    var droppedCard: List<PokerCard> = emptyList()
    var high: Player = players[0]
    var low: Player = players[1]
    var game_stage = GAME_STAGE.WAIT_FOR_BETS
    var bets: MutableMap<Player, MutableList<Bet>> = HashMap()
    var lastBet: Bet? = null
    var turnPlayer: Int = 0

    fun start() {
        mGame.init()
        wairForStartBets()
    }

    fun blinds(): PokerCard {
        game_stage = GAME_STAGE.BLINDS
        val dropCard = mGame.dropCard()
        droppedCard += dropCard
        mGame.drawCard()
        mGame.drawCard()
        mGame.drawCard()
        return dropCard
    }

    private fun calculateNextPlayerTurn(): Unit {
        if (turnPlayer + 1 >= players.size)
            turnPlayer = 0
        else
            turnPlayer += 1

        if (players[turnPlayer].folded)
            calculateNextPlayerTurn()
    }

    fun wairForBets(): Unit {
        game_stage = GAME_STAGE.WAIT_FOR_BETS
    }

    fun wairForStartBets(): Unit {
        game_stage = GAME_STAGE.START_BETS
    }

    fun call(player: Player): Unit {
        if (game_stage == GAME_STAGE.CALL_RAISE_FOLD ||
                game_stage == GAME_STAGE.WAIT_FOR_BETS) {
            game_stage = GAME_STAGE.CALL_RAISE_FOLD
            calculateNextPlayerTurn()
            placeBet(player, lastBet!!)
        }
    }

    fun fold(player: Player): Unit {
        if (game_stage == GAME_STAGE.CALL_RAISE_FOLD ||
                game_stage == GAME_STAGE.WAIT_FOR_BETS) {
            player.folded = true
            calculateNextPlayerTurn()
        }
    }

    fun raise(player: Player, amount: Int): Unit {
        if (game_stage == GAME_STAGE.CALL_RAISE_FOLD ||
                game_stage == GAME_STAGE.WAIT_FOR_BETS) {
            val bet = player.placeBet(amount + lastBet!!.totalMoney())
            lastBet = bet
            placeBet(player, bet)
            calculateNextPlayerTurn()
        }
    }

    fun high(): Unit {
        if (game_stage == GAME_STAGE.START_BETS) {
            val bet = high.placeBet(minBetAmount)
            lastBet = bet
            placeBet(high, bet)
            game_stage = GAME_STAGE.HIGHS
            turnPlayer = 1
        }
    }

    fun low(): Unit {
        if (game_stage == GAME_STAGE.HIGHS) {
            val bet = low.placeBet(minBetAmount / 2)
            placeBet(low, bet)
            game_stage = GAME_STAGE.LOWS
            turnPlayer = 2
        }
    }

    private fun placeBet(player: Player, bet: Bet): Unit {
        if (bets.containsKey(player)) {
            bets[player]?.add(bet)
        } else {
            bets.put(player, arrayListOf(bet))
        }
        when (game_stage) {
            GAME_STAGE.WAIT_FOR_BETS -> GAME_STAGE.CALL_RAISE_FOLD
        }
        val allBets = mGame.players.filter { !it.folded }
                .map {
                    sumBets(bets[it])
                }
                .reduceRight { i: Int, acc: Int ->
                    i - acc
                }

        if (allBets == 0) {
            game_stage = GAME_STAGE.DEALING
        }
    }

    private fun sumBets(bets: MutableList<Bet>?) = bets?.sumBy {
        it.totalMoney()
    } ?: 0


    fun deal(): PokerCard {
        game_stage = GAME_STAGE.DEALING
        val dropCard = mGame.dropCard()
        droppedCard += dropCard
        mGame.drawCard()
        game_stage = GAME_STAGE.WAIT_FOR_BETS
        return dropCard
    }
}

