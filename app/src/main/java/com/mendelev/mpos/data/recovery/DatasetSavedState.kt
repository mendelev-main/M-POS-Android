package com.mendelev.mpos.data.recovery

import android.os.Bundle

/** Restored Activity/SavedStateHandle input must never cross a dataset replacement. */
object DatasetSavedState {
    const val KEY="mpos.dataset.generation"
    fun filter(saved: Bundle?,active: String?): Bundle? {
        if(saved==null || active==null) return null
        val prior=saved.getString(KEY)
        return saved.takeIf {prior==active || prior==null && active=="legacy"}
    }
}
