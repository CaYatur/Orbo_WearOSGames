package com.cayadev.orbo.data

import com.cayadev.orbo.games.BlockBlastGame
import com.cayadev.orbo.games.BrickBreakerGame
import com.cayadev.orbo.games.BubblePopGame
import com.cayadev.orbo.games.BullseyeGame
import com.cayadev.orbo.games.CatchGame
import com.cayadev.orbo.games.ColorMatchGame
import com.cayadev.orbo.games.CrossDashGame
import com.cayadev.orbo.games.FruitSlashGame
import com.cayadev.orbo.games.LightsOutGame
import com.cayadev.orbo.games.MazeRunnerGame
import com.cayadev.orbo.games.DodgeGame
import com.cayadev.orbo.games.GemSwapGame
import com.cayadev.orbo.games.geoleap.GeoLeapHub
import com.cayadev.orbo.games.LightCycleGame
import com.cayadev.orbo.games.MemoryGame
import com.cayadev.orbo.games.MinesweeperGame
import com.cayadev.orbo.games.MolePopGame
import com.cayadev.orbo.games.NebulaSurvivorGame
import com.cayadev.orbo.games.NumberOrderGame
import com.cayadev.orbo.games.OddOneGame
import com.cayadev.orbo.games.OrbitDashGame
import com.cayadev.orbo.games.PairsGame
import com.cayadev.orbo.games.PongGame
import com.cayadev.orbo.games.QuickMathGame
import com.cayadev.orbo.games.ReactionGame
import com.cayadev.orbo.games.RhythmTapGame
import com.cayadev.orbo.games.SkyHopGame
import com.cayadev.orbo.games.SlidePuzzleGame
import com.cayadev.orbo.games.SnakeNeoGame
import com.cayadev.orbo.games.SpiralDropGame
import com.cayadev.orbo.games.StackGame
import com.cayadev.orbo.games.TapReflexGame
import com.cayadev.orbo.games.TicTacToeGame
import com.cayadev.orbo.games.TwosGame
import com.cayadev.orbo.games.WingItGame
// --- New games (60-content expansion) ---
import com.cayadev.orbo.games.KeyTilesGame
import com.cayadev.orbo.games.ColorRushGame
import com.cayadev.orbo.games.OrbitTapGame
import com.cayadev.orbo.games.QuickStopGame
import com.cayadev.orbo.games.SwipeFrenzyGame
import com.cayadev.orbo.games.SharpShooterGame
import com.cayadev.orbo.games.StarDefenderGame
import com.cayadev.orbo.games.JetDashGame
import com.cayadev.orbo.games.MissileGuardGame
import com.cayadev.orbo.games.AirHockeyGame
import com.cayadev.orbo.games.CaveFlyerGame
import com.cayadev.orbo.games.LaneRunnerGame
import com.cayadev.orbo.games.MeteorShieldGame
import com.cayadev.orbo.games.PlinkoGame
import com.cayadev.orbo.games.BubbleShooterGame
import com.cayadev.orbo.games.PinballGame
import com.cayadev.orbo.games.TunnelDashGame
import com.cayadev.orbo.games.ColorSwitchGame
import com.cayadev.orbo.games.HexDashGame
import com.cayadev.orbo.games.SkyDiveGame
import com.cayadev.orbo.games.PinWheelGame
import com.cayadev.orbo.games.TimberGame
import com.cayadev.orbo.games.PushItGame
import com.cayadev.orbo.games.FlowConnectGame
import com.cayadev.orbo.games.PipesGame
import com.cayadev.orbo.games.SlideOutGame
import com.cayadev.orbo.games.HanoiGame
import com.cayadev.orbo.games.NonogramGame
import com.cayadev.orbo.games.SudokuGame
import com.cayadev.orbo.games.PegSolitaireGame
import com.cayadev.orbo.games.CascadeGame
import com.cayadev.orbo.games.WaterSortGame
import com.cayadev.orbo.games.ShellGameGame
import com.cayadev.orbo.games.MastermindGame
import com.cayadev.orbo.games.DigitSpanGame
import com.cayadev.orbo.games.NumberHuntGame
import com.cayadev.orbo.games.MemoryMatrixGame
import com.cayadev.orbo.games.AnagramGame
import com.cayadev.orbo.games.SumTenGame
import com.cayadev.orbo.games.FlashCountGame
import com.cayadev.orbo.games.ReversiGame
import com.cayadev.orbo.games.ConnectFourGame
import com.cayadev.orbo.games.DotsBoxesGame
import com.cayadev.orbo.games.GomokuGame
import com.cayadev.orbo.games.NimGame
import com.cayadev.orbo.games.BlackjackGame
import com.cayadev.orbo.games.HigherLowerGame
import com.cayadev.orbo.games.PigGame
import com.cayadev.orbo.games.RockPaperScissorsGame
import com.cayadev.orbo.games.DicePokerGame
import com.cayadev.orbo.tools.BeatPadTool
import com.cayadev.orbo.tools.SketchTool
import com.cayadev.orbo.tools.TimerLabTool
import com.cayadev.orbo.tools.DiceRollerTool
import com.cayadev.orbo.tools.CoinFlipTool
import com.cayadev.orbo.tools.SpinnerTool
import com.cayadev.orbo.tools.TallyTool
import com.cayadev.orbo.tools.MetronomeTool
import com.cayadev.orbo.tools.BreatheTool
import com.cayadev.orbo.tools.LevelTool
import com.cayadev.orbo.tools.CompassTool
import com.cayadev.orbo.tools.FlashlightTool
import com.cayadev.orbo.tools.WorldClockTool

/**
 * The single place that wires every built game/tool into the [Catalog].
 * New titles are appended here as they're completed.
 */
object GameRegistry {
    fun registerAll() {
        // --- Reflex ---
        Catalog.register(TapReflexGame.meta())
        Catalog.register(ReactionGame.meta())
        Catalog.register(MolePopGame.meta())
        Catalog.register(RhythmTapGame.meta())
        Catalog.register(OrbitDashGame.meta())
        Catalog.register(BullseyeGame.meta())
        Catalog.register(KeyTilesGame.meta())
        Catalog.register(ColorRushGame.meta())
        Catalog.register(OrbitTapGame.meta())
        Catalog.register(QuickStopGame.meta())
        Catalog.register(SwipeFrenzyGame.meta())
        Catalog.register(SharpShooterGame.meta())
        // --- Brain ---
        Catalog.register(ColorMatchGame.meta())
        Catalog.register(MemoryGame.meta())
        Catalog.register(NumberOrderGame.meta())
        Catalog.register(QuickMathGame.meta())
        Catalog.register(PairsGame.meta())
        Catalog.register(OddOneGame.meta())
        Catalog.register(ShellGameGame.meta())     // BIG · featured
        Catalog.register(MastermindGame.meta())
        Catalog.register(DigitSpanGame.meta())
        Catalog.register(NumberHuntGame.meta())
        Catalog.register(MemoryMatrixGame.meta())
        Catalog.register(AnagramGame.meta())
        Catalog.register(SumTenGame.meta())
        Catalog.register(FlashCountGame.meta())
        // --- Action ---
        Catalog.register(FruitSlashGame.meta())    // BIG · featured
        Catalog.register(CrossDashGame.meta())
        Catalog.register(StarDefenderGame.meta())
        Catalog.register(JetDashGame.meta())
        Catalog.register(MissileGuardGame.meta())
        Catalog.register(AirHockeyGame.meta())
        Catalog.register(CaveFlyerGame.meta())
        Catalog.register(LaneRunnerGame.meta())
        Catalog.register(MeteorShieldGame.meta())
        // --- Strategy ---
        Catalog.register(LightsOutGame.meta())
        Catalog.register(ReversiGame.meta())       // BIG · featured
        Catalog.register(ConnectFourGame.meta())
        Catalog.register(DotsBoxesGame.meta())
        Catalog.register(GomokuGame.meta())
        Catalog.register(NimGame.meta())
        // --- Puzzle ---
        Catalog.register(MinesweeperGame.meta())
        Catalog.register(GemSwapGame.meta())
        Catalog.register(SlidePuzzleGame.meta())
        Catalog.register(TwosGame.meta())          // BIG · featured
        Catalog.register(BlockBlastGame.meta())    // BIG · featured
        Catalog.register(MazeRunnerGame.meta())    // BIG · featured
        Catalog.register(PushItGame.meta())
        Catalog.register(FlowConnectGame.meta())
        Catalog.register(PipesGame.meta())
        Catalog.register(SlideOutGame.meta())
        Catalog.register(HanoiGame.meta())
        Catalog.register(NonogramGame.meta())
        Catalog.register(SudokuGame.meta())        // BIG
        Catalog.register(PegSolitaireGame.meta())
        Catalog.register(CascadeGame.meta())       // BIG · featured
        Catalog.register(WaterSortGame.meta())     // BIG · featured
        // --- Arcade ---
        Catalog.register(WingItGame.meta())
        Catalog.register(DodgeGame.meta())
        Catalog.register(BubblePopGame.meta())
        Catalog.register(StackGame.meta())
        Catalog.register(CatchGame.meta())
        Catalog.register(SkyHopGame.meta())
        Catalog.register(SpiralDropGame.meta())
        Catalog.register(BrickBreakerGame.meta())  // BIG · featured
        Catalog.register(GeoLeapHub.meta())        // FLAGSHIP · featured
        Catalog.register(NebulaSurvivorGame.meta())// FLAGSHIP · featured
        Catalog.register(PlinkoGame.meta())
        Catalog.register(BubbleShooterGame.meta()) // BIG · featured
        Catalog.register(PinballGame.meta())
        Catalog.register(TunnelDashGame.meta())
        Catalog.register(ColorSwitchGame.meta())
        Catalog.register(HexDashGame.meta())       // BIG · featured
        Catalog.register(SkyDiveGame.meta())
        Catalog.register(PinWheelGame.meta())
        Catalog.register(TimberGame.meta())
        // --- Classic ---
        Catalog.register(TicTacToeGame.meta())
        Catalog.register(PongGame.meta())
        Catalog.register(LightCycleGame.meta())
        Catalog.register(SnakeNeoGame.meta())      // BIG · featured
        Catalog.register(BlackjackGame.meta())
        Catalog.register(HigherLowerGame.meta())
        Catalog.register(PigGame.meta())
        Catalog.register(RockPaperScissorsGame.meta())
        Catalog.register(DicePokerGame.meta())
        // --- Tools ---
        Catalog.register(SketchTool.meta())
        Catalog.register(TimerLabTool.meta())
        Catalog.register(BeatPadTool.meta())
        Catalog.register(DiceRollerTool.meta())
        Catalog.register(CoinFlipTool.meta())
        Catalog.register(SpinnerTool.meta())
        Catalog.register(TallyTool.meta())
        Catalog.register(MetronomeTool.meta())
        Catalog.register(BreatheTool.meta())
        Catalog.register(LevelTool.meta())
        Catalog.register(CompassTool.meta())
        Catalog.register(FlashlightTool.meta())
        Catalog.register(WorldClockTool.meta())
    }
}
