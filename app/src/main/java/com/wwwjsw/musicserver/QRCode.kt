package com.wwwjsw.musicserver

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Hashtable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

fun generateQrCodeBitmap(content: String, width: Int, height: Int): ImageBitmap {
    val hints = Hashtable<EncodeHintType, Any>()
    hints[EncodeHintType.CHARACTER_SET] = "UTF-8"

    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints)

    val bmp = createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bmp[x, y] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
        }
    }

    return bmp.asImageBitmap()
}

@Composable
fun QrCodeView(content: String, size: Int, modifier: Modifier = Modifier) {
    val qrCodeBitmap = generateQrCodeBitmap(content,size, size)
    Image(
        bitmap = qrCodeBitmap,
        contentDescription = content,
        modifier = modifier,
        contentScale = ContentScale.Inside
    )
}

@Preview
@Composable
fun QrCodeViewPreview() {
    QrCodeView("https://www.google.com", 200)
}