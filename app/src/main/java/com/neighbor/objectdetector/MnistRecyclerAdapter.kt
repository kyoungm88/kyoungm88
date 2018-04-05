package com.neighbor.objectdetector

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.neighbor.objectdetector.classifier.Result

class MnistRecyclerAdapter(val context: Context, val listData: ArrayList<Result>): RecyclerView.Adapter<MnistViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MnistViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_mnist, parent, false)
        return MnistViewHolder(view)
    }

    override fun getItemCount(): Int {
        return listData.size
    }

    override fun onBindViewHolder(holder: MnistViewHolder, position: Int) {
        val result = listData[position]

        holder.getPredictionView().text = result.getNumber().toString()
        holder.getCostView().text = result.getTimeCost().toString()
    }

}