package dev.zun.flux.data.api

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer

class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val bufferedSink = CountingSink(sink).buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }

    inner class CountingSink(delegate: okio.Sink) : okio.ForwardingSink(delegate) {
        private var bytesWritten = 0L

        override fun write(source: okio.Buffer, byteCount: Long) {
            super.write(source, byteCount)
            bytesWritten += byteCount
            val totalLength = contentLength()
            if (totalLength > 0) {
                onProgress(bytesWritten.toFloat() / totalLength)
            }
        }
    }
}
