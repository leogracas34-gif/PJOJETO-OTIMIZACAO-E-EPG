package com.vltv.play

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

class SearchResultAdapter(private val onClick: (SearchResultItem) -> Unit) : 
    ListAdapter<SearchResultItem, SearchResultAdapter.VH>(SearchDiffCallback()) {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvResultTitle)
        val tvType: TextView = v.findViewById(R.id.tvResultType)
        val tvExtra: TextView = v.findViewById(R.id.tvResultExtra)
        val imgPoster: ImageView = v.findViewById(R.id.imgPoster)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.tvTitle.text = item.title
        holder.tvType.text = when (item.type) {
            "live" -> "📺 Canal ao vivo"
            "movie" -> "🎬 Filme"
            "series" -> "📺 Série"
            else -> item.type
        }
        holder.tvExtra.text = item.extraInfo ?: ""
        
        Glide.with(holder.itemView)
            .load(item.icon)
            .placeholder(android.R.color.darker_gray)
            .transform(RoundedCorners(12))
            .into(holder.imgPoster)

        holder.itemView.setOnClickListener { onClick(item) }
    }

    class SearchDiffCallback : DiffUtil.ItemCallback<SearchResultItem>() {
        override fun areItemsTheSame(old: SearchResultItem, new: SearchResultItem) = old.id == new.id
        override fun areContentsTheSame(old: SearchResultItem, new: SearchResultItem) = old == new
    }
}
