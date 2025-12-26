package com.vltv.play

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class SearchResultAdapter(
    private var list: List<SearchResultItem>,
    private val onClick: (SearchResultItem) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvName)
        val imgPoster: ImageView = v.findViewById(R.id.imgPoster)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vod, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.tvName.text = item.title

        Glide.with(holder.itemView.context)
            .load(item.iconUrl ?: R.mipmap.ic_launcher)
            .placeholder(R.mipmap.ic_launcher)
            .into(holder.imgPoster)

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = list.size

    fun updateData(newList: List<SearchResultItem>) {
        list = newList
        notifyDataSetChanged()
    }
}
