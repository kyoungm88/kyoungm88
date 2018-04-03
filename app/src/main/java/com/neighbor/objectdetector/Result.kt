package com.neighbor.objectdetector

class Result {
    private var mNumber: Int
    private var mProbability: Float
    private var mTimeCost: Long

    constructor(result: FloatArray, timeCost: Long) {
        mNumber = argmax(result)
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

    private fun argmax(probs: FloatArray): Int {
        var maxIdx = -1
        var maxProb = 0.0f
        for (i in probs.indices) {
            if (probs[i] > maxProb) {
                maxProb = probs[i]
                maxIdx = i
            }
        }
        return maxIdx
    }
}