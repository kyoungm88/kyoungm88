package com.neighbor.objectdetector

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.TextView

class MnistViewHolder internal constructor(view: View): RecyclerView.ViewHolder(view) {

    private val tvPredictionResult: TextView = view.findViewById(R.id.tvPredictionResult)
    private val tvCostResult: TextView = view.findViewById(R.id.tvCostResult)

    fun getPredictionView(): TextView {
        return tvPredictionResult
    }

    fun getCostView(): TextView {
        return tvCostResult
    }
}