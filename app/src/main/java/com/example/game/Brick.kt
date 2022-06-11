package com.example.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.FrameLayout
import com.example.game.core.GameView
import com.example.game.core.Object
import com.example.game.util.Colors
import com.example.podclassic.base.ScreenView
import kotlin.random.Random

class Brick constructor(context: Context) : FrameLayout(context), ScreenView {

    private var gameView: GameView? = null

    private var brickWidth = 0
    private var brickHeight = 0

    private var ballHeight = 0
    private var ballWidth = 0

    private var boardHeight = 0
    private var boardWidth = 0


    val bricks by lazy { Array(MAX_Y_OBJ) { arrayOfNulls<Object?>(X_OBJ) } }
    private var ball: Object? = null
    private var board: Object? = null

    companion object {

        class Ball(context: Context?) : View(context) {

            private val paint = Paint()

            override fun onDraw(canvas: Canvas?) {
                val cx = width / 2f
                val cy = height / 2f
                paint.shader = Colors.getShader(
                    width / 2f,
                    0f,
                    width / 2f,
                    height.toFloat(),
                    Colors.ball_1,
                    Colors.ball_2
                )
                canvas?.drawCircle(cx, cy, cx, paint)
            }
        }

        class Board(context: Context?) : View(context) {

            companion object {
                const val PADDING = 5f
            }

            private val paint = Paint()

            override fun onDraw(canvas: Canvas?) {
                super.onDraw(canvas)
                paint.shader = null
                canvas?.drawRoundRect(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    PADDING,
                    PADDING,
                    paint
                )
                paint.shader = Colors.getShader(
                    width / 2f,
                    0f,
                    width / 2f,
                    height.toFloat(),
                    Colors.board_1,
                    Colors.board_2
                )
                canvas?.drawRoundRect(1f, 1f, width - 1f, height - 1f, PADDING, PADDING, paint)
            }
        }

        class Brick(context: Context?, val colorIndex: Int = Random.nextInt(Colors.brick_1.size)) :
            View(context) {

            companion object {
                const val PADDING = 5f
            }

            private val paint = Paint()

            override fun onDraw(canvas: Canvas?) {
                super.onDraw(canvas)
                paint.shader = Colors.getShader(
                    width / 2f,
                    0f,
                    width / 2f,
                    height.toFloat(),
                    Colors.line,
                    Colors.line
                )
                canvas?.drawRoundRect(
                    PADDING,
                    PADDING,
                    width - PADDING,
                    height - PADDING,
                    PADDING,
                    PADDING,
                    paint
                )
                val c1 = Colors.brick_1[colorIndex]
                val c2 = Colors.brick_2[colorIndex]
                paint.shader =
                    Colors.getShader(width / 2f, 0f, width / 2f, height.toFloat(), c1, c2)
                canvas?.drawRoundRect(
                    PADDING + 1,
                    PADDING + 1,
                    width - PADDING - 1,
                    height - PADDING - 1,
                    PADDING,
                    PADDING,
                    paint
                )
            }
        }

        const val MAX_Y_OBJ = 5

        const val PADDING = 20
    }

    private var X_OBJ = 8
        set(value) {
            if (value >= 8) field = value
        }

    private var Y_OBJ = 1
        set(value) {
            if (value in 2..MAX_Y_OBJ) field = value
        }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        brickHeight = measuredHeight / X_OBJ / 2
        brickWidth = measuredWidth / X_OBJ
        ballWidth = measuredWidth / 56
        ballHeight = ballWidth
        boardWidth = measuredWidth / 5
        boardHeight = boardWidth / 6
    }


    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        endGame()
    }

    private fun createBricks() {
        for (i in 1..Y_OBJ) {
            for (j in 1..X_OBJ) {
                val o = Object(
                    Brick(context, i - 1),
                    (j - 1) * brickWidth,
                    (i - 1) * brickHeight,
                    brickWidth,
                    brickHeight
                )
                gameView?.addObject(o)
                bricks[i - 1][j - 1] = o
            }
        }
    }

    private fun createBall() {
        val o = Object(
            Ball(context),
            0 + ballWidth,
            (height - ballWidth) / 2,
            ballWidth,
            ballHeight
        )
        o.onHitListener = object : Object.OnHitListener {
            override fun onHit(self: Object, by: Object) {
                if (by.view is Brick) {
                    by.visibility = false
                }
                if (((self.x + self.width >= by.x && self.x <= by.x) || (self.x <= by.x + by.width && self.x + self.width >= by.x + by.width))) {
                    self.autoMove.velX = -self.autoMove.velX
                    self.autoMove.addRandomToX()
                } else {
                    self.autoMove.addRandomToX()
                    self.autoMove.velY = -self.autoMove.velY
                }
            }

        }
        o.onLimitedListener = object :
            Object.OnLimitedListener {
            override fun onLimited(width: Int, height: Int, self: Object) {
                if (self.x <= 0 || self.x + self.width >= width) {
                    self.autoMove.velX = -self.autoMove.velX
                }

                if (self.y <= 0) {
                    self.autoMove.velY = -self.autoMove.velY
                }

                if (self.y + self.height >= height) {
                    endGame()
                }
            }


        }
        gameView?.addObject(o)
        ball = o
    }


    private fun createBoard() {
        var height = boardHeight - PADDING
        if (height <= 0) {
            height = 5
        }
        val o = Object(
            Board(context),
            (width - boardWidth) / 2,
            this.height - boardHeight - PADDING,
            boardWidth,
            height
        )
        board = o
        gameView?.addObject(o)
    }


    var started = false
    var ended = false
    var prepared = false

    private fun startGame() {
        if (started || ended) {
            return
        }
        started = true
        prepared = false
        gameView?.start()
        val v = Object.MAX_VEL / 2

        gameView?.autoMoveObject(ball!!, v, v)
    }

    fun endGame() {
        ended = true
        prepared = false
        gameView?.stop()
    }

    init {
        gameView = GameView(context)
        addView(gameView)

        gameView?.gameChecker = object :
            GameView.GameChecker {
            override fun checkEnd() {
                for (i in 1..Y_OBJ) {
                    for (j in 1..X_OBJ) {
                        if (bricks[i - 1][j - 1]?.visibility == true) {
                            return
                        }
                    }
                }
                if (Y_OBJ < MAX_Y_OBJ) {
                    Y_OBJ++
                }
                endGame()
                reset()
            }
        }
    }

    fun reset() {
        gameView?.reset()
        createBall()
        createBoard()
        createBricks()
        started = false
        ended = false
        prepared = true
    }

    override fun enter(): Boolean {
        if (started) {
            endGame()
        }
        if (!prepared) {
            reset()
        }
        startGame()
        return true
    }

    override fun enterLongClick(): Boolean {
        return false
    }

    override fun slide(slideVal: Int): Boolean {
        val vel = if (slideVal > 0) 1 else if (slideVal < 0) -1 else 0
        gameView?.moveObject(board!!, vel * 50, 0)
        return false
    }

    override fun getTitle(): String {
        return "Brick"
    }

    override fun onViewCreate() {}

    override fun onViewDelete() {}

}