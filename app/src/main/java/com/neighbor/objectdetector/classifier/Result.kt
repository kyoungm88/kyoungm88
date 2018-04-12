package com.neighbor.objectdetector.classifier

import com.neighbor.objectdetector.util.Utils

class Result(result: FloatArray, timeCost: Long) {
    private val mNumber: Int
    private val mProbability: Float
    private val mTimeCost: Long

    init {
        mNumber = Utils.argmax(result)
        mProbability = result[mNumber]
        mTimeCost = timeCost
    }

    fun getNumber(): Int {
        return mNumber
    }

    fun getProbability(): Float {
        return mProbability
    }

    fun getTimeCost(): Long {
        return mTimeCost
    }

}