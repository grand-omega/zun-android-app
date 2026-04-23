package dev.zun.flux.ui.nav

object Routes {
    const val HOME = "home"
    const val CAMERA = "camera"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val PROGRESS = "progress/{jobId}"
    const val RESULT = "result/{jobId}"

    fun progress(jobId: String) = "progress/$jobId"

    fun result(jobId: String) = "result/$jobId"
}
