package com.example.game.core

import android.view.View
import com.example.podclassic.util.Values
import kotlin.random.Random

class Object(val view : View, var x: Int, var y : Int, var width : Int, var height : Int) {
    companion object {
        val MAX_VEL = if (Values.resolution < Values.RESOLUTION_LOW) 6 else 10
    }

    var hitable = true
    var limited = true
    var visibility = true
    set(value) {
        if (value) {
            view.visibility = View.VISIBLE
        } else {
            view.visibility = View.INVISIBLE
        }
        field = value
    }

    val autoMove = AutoMove()

    class AutoMove() {

        val random = Random(System.currentTimeMillis())

        var velX = 0
        fun addRandomToX() {
            var tempVel = (if (random.nextInt(MAX_VEL) == 0) -velX else velX) + random.nextInt(MAX_VEL) - (MAX_VEL / 2)
            if (tempVel > MAX_VEL) tempVel = MAX_VEL
            if (tempVel < -MAX_VEL) tempVel = -MAX_VEL
            velX = tempVel
        }

        var velY = 0
        fun moving() : Boolean {
            return velX != 0 || velY != 0
        }
    }

    interface OnHitListener {
        fun onHit(self : Object, by : Object)
    }

    interface OnLimitedListener {
        fun onLimited(width: Int, height: Int, self : Object)
    }


    var onHitListener : OnHitListener? = null
    var onLimitedListener : OnLimitedListener? = null

    override fun toString(): String {
        return "{x = " + x + "\ny = " + y + "\nwidth = " + width + "\nheight = " + height + "\nvelx = " + autoMove.velX + "\nvely = " + autoMove.velY + "\nhitable = " + hitable + "}\n"
    }

}