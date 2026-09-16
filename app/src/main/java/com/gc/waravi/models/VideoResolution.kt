package com.gc.waravi.models

data class VideoResolution(val width : Int, val height: Int){
    companion object{
        val VIDEO_VGA = VideoResolution(640, 480)
        val VIDEO_HD = VideoResolution(1280, 720)
        val VIDEO_FHD = VideoResolution(1920, 1080)
    }

    val value : Int
        inline get() = when(this){
            VIDEO_VGA -> 1
            VIDEO_HD -> 2
            VIDEO_FHD -> 3
            else -> 1
        }
}