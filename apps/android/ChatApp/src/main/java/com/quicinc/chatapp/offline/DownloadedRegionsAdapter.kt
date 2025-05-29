package com.quicinc.chatapp.offline
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.quicinc.chatapp.R
import com.quicinc.chatapp.ui.LanguageManager

class DownloadedRegionsAdapter(
    private var regions: List<DownloadedRegion>,
    private val languageManager: LanguageManager,
    private val onDeleteClick: (DownloadedRegion) -> Unit
) : RecyclerView.Adapter<DownloadedRegionsAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val regionName: TextView = itemView.findViewById(R.id.regionName)
        val downloadDate: TextView = itemView.findViewById(R.id.downloadDate)
        val regionSize: TextView = itemView.findViewById(R.id.regionSize)
        val deleteButton: LinearLayout = itemView.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_downloaded_region, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val region = regions[position]

        // 지역명 설정 (너무 긴 경우 줄임)
        holder.regionName.text = region.name

        // 다운로드 날짜 설정
        holder.downloadDate.text = languageManager.getLocalizedString(
            "다운로드: ${region.downloadDate}",
            "Downloaded: ${region.downloadDate}"
        )

        // 지역 크기 설정
        holder.regionSize.text = languageManager.getLocalizedString(
            "크기: ${region.size}",
            "Size: ${region.size}"
        )

        // 삭제 버튼 텍스트 설정
        val deleteTextView = holder.deleteButton.getChildAt(1) as TextView
        deleteTextView.text = languageManager.getLocalizedString("삭제", "Delete")

        // 삭제 버튼 클릭 리스너
        holder.deleteButton.setOnClickListener {
            onDeleteClick(region)
        }

        // 지역 타입에 따른 추가 정보 표시 (선택사항)
        when {
            region.name.contains("수도권") || region.name.contains("metropolitan", true) -> {
                // 수도권 지역인 경우 특별 표시
                holder.regionName.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_home, 0, 0, 0
                )
                holder.regionName.compoundDrawablePadding = 8
            }
            region.name.contains("시") || region.name.contains("city", true) -> {
                // 도시 지역
                holder.regionName.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_location_on, 0, 0, 0
                )
                holder.regionName.compoundDrawablePadding = 8
            }
            else -> {
                // 기타 지역
                holder.regionName.setCompoundDrawablesWithIntrinsicBounds(
                    0, 0, 0, 0
                )
            }
        }

        // 접근성 개선
        holder.deleteButton.contentDescription = languageManager.getLocalizedString(
            "${region.name} 지역 삭제",
            "Delete ${region.name} region"
        )

        // 아이템 전체 접근성 설명
        holder.itemView.contentDescription = languageManager.getLocalizedString(
            "다운로드된 지역: ${region.name}, 다운로드 날짜: ${region.downloadDate}, 크기: ${region.size}",
            "Downloaded region: ${region.name}, downloaded: ${region.downloadDate}, size: ${region.size}"
        )
    }

    override fun getItemCount(): Int = regions.size

    fun updateRegions(newRegions: List<DownloadedRegion>) {
        regions = newRegions
        notifyDataSetChanged()
    }

    fun clearResults() {
        regions = emptyList()
        notifyDataSetChanged()
    }

    // 특정 지역 제거 (삭제 후 UI 업데이트용)
    fun removeRegion(regionId: String) {
        val index = regions.indexOfFirst { it.id == regionId }
        if (index != -1) {
            val mutableRegions = regions.toMutableList()
            mutableRegions.removeAt(index)
            regions = mutableRegions
            notifyItemRemoved(index)

            // 리스트가 비었으면 전체 갱신 (빈 상태 표시를 위해)
            if (regions.isEmpty()) {
                notifyDataSetChanged()
            }
        }
    }

    // 지역 추가 (새 다운로드 완료 시)
    fun addRegion(region: DownloadedRegion) {
        val mutableRegions = regions.toMutableList()
        mutableRegions.add(0, region) // 최신 항목을 맨 위에 추가
        regions = mutableRegions
        notifyItemInserted(0)
    }

    // 현재 지역 수 반환
    fun getRegionCount(): Int = regions.size

    // 특정 타입의 지역이 있는지 확인
    fun hasMetropolitanRegion(): Boolean {
        return regions.any {
            it.name.contains("수도권") || it.name.contains("metropolitan", true)
        }
    }

    // 총 다운로드 크기 계산 (표시용)
    fun getTotalSize(): String {
        var totalBytes = 0L

        regions.forEach { region ->
            // 크기 문자열에서 숫자 추출 (예: "400 MB" -> 400)
            val sizeStr = region.size
            val sizeNumber = sizeStr.filter { it.isDigit() }.toLongOrNull() ?: 0L

            when {
                sizeStr.contains("GB", true) -> totalBytes += sizeNumber * 1024 * 1024 * 1024
                sizeStr.contains("MB", true) -> totalBytes += sizeNumber * 1024 * 1024
                sizeStr.contains("KB", true) -> totalBytes += sizeNumber * 1024
                else -> totalBytes += sizeNumber
            }
        }

        return when {
            totalBytes >= 1024 * 1024 * 1024 -> "${totalBytes / (1024 * 1024 * 1024)} GB"
            totalBytes >= 1024 * 1024 -> "${totalBytes / (1024 * 1024)} MB"
            totalBytes >= 1024 -> "${totalBytes / 1024} KB"
            else -> "$totalBytes B"
        }
    }
}