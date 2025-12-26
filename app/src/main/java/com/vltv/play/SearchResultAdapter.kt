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
            .inflate(R.layout.item_vod, parent, false) // mesmo card de filmes/séries
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.tvName.text = item.title

        // Por enquanto usa só o ícone padrão; depois dá para passar URL em SearchResultItem
        Glide.with(holder.itemView.context)
            .load(R.mipmap.ic_launcher) // troque para item.iconUrl quando tiver
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
