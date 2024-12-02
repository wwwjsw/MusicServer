package com.wwwjsw.musicserver

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

/**
 * Composable para carregar uma imagem de uma URL usando Glide.
 * @param imageUrl URL da imagem que será carregada.
 * @param modifier Modificador para personalizar o estilo do composable.
 * @param contentDescription Descrição da imagem para acessibilidade.
 */
@Composable
fun GlideImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "Imagem carregada com Glide"
) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP // Pode ser alterado conforme a necessidade
            }
        },
        modifier = modifier
    ) { imageView ->
        // Usando Glide para carregar a imagem da URL
        Glide.with(imageView.context)
            .load(imageUrl)
            .apply(RequestOptions().centerCrop()) // Você pode adicionar outras opções como .placeholder, .error etc.
            .into(imageView)

        // Caso queira adicionar alguma opção de fallback, como imagem de erro:
        // Glide.with(imageView.context)
        //     .load(imageUrl)
        //     .placeholder(R.drawable.placeholder)
        //     .error(R.drawable.error_image)
        //     .into(imageView)
    }
}
