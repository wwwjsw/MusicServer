import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import fi.iki.elonen.NanoHTTPD
import java.io.FileInputStream

class MediaServer(port: Int, private val context: Context) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
        val params = session.parameters
        val audioIdString = params["audio_id"]?.get(0)

        return if (!audioIdString.isNullOrEmpty()) {
            val audioId = audioIdString.toLongOrNull()
            if (audioId != null) {
                val audioUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId)
                val parcelFileDescriptor = context.contentResolver.openFileDescriptor(audioUri, "r")

                if (parcelFileDescriptor != null) {
                    val fileInputStream = FileInputStream(parcelFileDescriptor.fileDescriptor)
                    val fileSize = parcelFileDescriptor.statSize

                    newFixedLengthResponse(
                        Response.Status.OK,
                        "audio/mpeg",
                        fileInputStream,
                        fileSize
                    )
                } else {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Audio file not found")
                }
            } else {
                newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid audio ID")
            }
        } else {
            newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing audio ID")
        }
    }
}
