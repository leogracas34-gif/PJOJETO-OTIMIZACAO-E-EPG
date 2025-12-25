package com.vltv.play

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchResultAdapter(
    private var list: List<SearchResultItem>,
    private val onClick: (SearchResultItem) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvResultTitle)
        val tvType: TextView = v.findViewById(R.id.tvResultType)
        val tvExtra: TextView = v.findViewById(R.id.tvResultExtra)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.tvTitle.text = item.title
        holder.tvType.text = when (item.type) {
            "live" -> "Canal ao vivo"
            "movie" -> "Filme"
            "series" -> "Série"
            else -> item.type
        }
        holder.tvExtra.text = item.extraInfo ?: ""
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = list.size

    fun updateData(newList: List<SearchResultItem>) {
        list = newList
        notifyDataSetChanged()
    }
}
