
package com.quicinc.chatapp.search;
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mapbox.search.result.SearchResult

class CombinedSearchResultsAdapter(
    private val onItemClick: (SearchManager.CombinedSearchResult) -> Unit
) : RecyclerView.Adapter<CombinedSearchResultsAdapter.CombinedSearchResultViewHolder>() {

    private var searchResults: List<SearchManager.CombinedSearchResult> = emptyList()

    class CombinedSearchResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // ✅ 수정: android.R.id.icon 제거, simple_list_item_2 사용
        private val nameTextView: TextView = itemView.findViewById(android.R.id.text1)
        private val addressTextView: TextView = itemView.findViewById(android.R.id.text2)

        fun bind(
            combinedResult: SearchManager.CombinedSearchResult,
            onItemClick: (SearchManager.CombinedSearchResult) -> Unit
        ) {
            val searchResult = combinedResult.result

            // 결과 타입에 따른 이름 설정 (아이콘 대신 텍스트로 구분)
            val nameWithType = when (combinedResult.type) {
                SearchManager.SearchResultType.POI -> {
                    "🏢 ${searchResult.name}" // 랜드마크 이모지
                }
                SearchManager.SearchResultType.ADDRESS -> {
                    "📍 ${searchResult.name}" // 주소 이모지
                }
            }
            nameTextView.text = nameWithType

            // 주소 설정
            val addressText = searchResult.address?.formattedAddress() ?: ""
            addressTextView.text = addressText

            // 결과 타입에 따른 스타일 설정
            when (combinedResult.type) {
                SearchManager.SearchResultType.POI -> {
                    nameTextView.setTypeface(null, Typeface.BOLD) // 굵게 표시
                    nameTextView.setTextColor(itemView.context.getColor(android.R.color.holo_blue_dark))
                }
                SearchManager.SearchResultType.ADDRESS -> {
                    nameTextView.setTypeface(null, Typeface.NORMAL)
                    nameTextView.setTextColor(itemView.context.getColor(android.R.color.black))
                }
            }

            // 클릭 리스너 설정
            itemView.setOnClickListener {
                onItemClick(combinedResult)
            }

            // 접근성을 위한 content description
            itemView.contentDescription = when (combinedResult.type) {
                SearchManager.SearchResultType.POI -> "랜드마크: ${searchResult.name}"
                SearchManager.SearchResultType.ADDRESS -> "주소: ${searchResult.name}"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CombinedSearchResultViewHolder {
        // ✅ 수정: simple_list_item_2 사용 (아이콘 없는 버전)
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return CombinedSearchResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: CombinedSearchResultViewHolder, position: Int) {
        holder.bind(searchResults[position], onItemClick)
    }

    override fun getItemCount(): Int = searchResults.size

    // 누락된 메서드들 추가
    fun setResults(results: List<SearchManager.CombinedSearchResult>) {
        searchResults = results
        notifyDataSetChanged()
    }

    fun clearResults() {
        searchResults = emptyList()
        notifyDataSetChanged()
    }

    // 결과 타입별로 개수 확인하는 유틸리티 메서드
    fun getPoiCount(): Int = searchResults.count { it.type == SearchManager.SearchResultType.POI }
    fun getAddressCount(): Int = searchResults.count { it.type == SearchManager.SearchResultType.ADDRESS }
}