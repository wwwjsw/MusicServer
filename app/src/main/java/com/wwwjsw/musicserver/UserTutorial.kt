package com.wwwjsw.musicserver

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val PREFS_NAME = "music_server_prefs"
private const val KEY_TUTORIAL_SEEN = "tutorial_seen"

/** One step of the onboarding guide. */
private data class TutorialStep(
    val title: String,
    val body: String,
    /** Drawable icon shown in the step header, or null for the app icon. */
    val iconRes: Int? = null,
)

private val tutorialSteps = listOf(
    TutorialStep(
        title = "Bem-vindo ao Music Server",
        body = "O Music Server transforma o áudio deste dispositivo em uma " +
            "biblioteca de músicas que você pode tocar de qualquer outro aparelho " +
            "da sua rede doméstica. Este guia mostra como começar.",
    ),
    TutorialStep(
        title = "Conecte-se pelo navegador",
        body = "Com o celular e o computador na mesma rede Wi-Fi, aponte a câmera " +
            "do computador para o QR Code da tela, ou abra o endereço " +
            "http://<ip-do-celular>:8080. Também é possível usar o botão " +
            "\"Open Web Player in Browser\" no próprio celular.",
    ),
    TutorialStep(
        title = "Navegue pelos álbuns",
        body = "O cartão \"Albums\" lista as capas agrupadas por álbum, cada uma " +
            "pronta para tocar o álbum inteiro.",
        iconRes = R.drawable.baseline_theater_comedy_24,
    ),
    TutorialStep(
        title = "Ouça todas as músicas",
        body = "O cartão \"All Songs\" mostra faixas, artistas e álbuns de todas as " +
            "músicas encontradas no aparelho, em uma lista única.",
        iconRes = R.drawable.twotone_play_circle_24,
    ),
    TutorialStep(
        title = "Controle a reprodução",
        body = "Toque em uma música ou álbum para abrir o player. Os botões de 10 " +
            "segundos voltam ou avançam na faixa, e o áudio continua tocando mesmo " +
            "com o app minimizado.",
        iconRes = R.drawable.twotone_play_circle_24,
    ),
)

/** True the first time the guide has not yet been dismissed on this device. */
fun isTutorialFirstRun(context: Context): Boolean =
    !context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_TUTORIAL_SEEN, false)

/** Marks the guide as seen so that it only opens automatically on first use. */
fun markTutorialSeen(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_TUTORIAL_SEEN, true)
        .apply()
}

/**
 * Help icon intended for the navbar. Tapping it re-opens the user guide.
 */
@Composable
fun TutorialHelpButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(modifier = modifier, onClick = onClick) {
        Icon(
            painter = painterResource(id = R.drawable.ic_help_24),
            contentDescription = "Abrir tutorial",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Paginated user guide.
 *
 * @param firstRun true for the automatic first-run dialog: it offers "Pular" on
 *   the first page and reports completion with [onCompleted].
 * @param onCompleted the guide was finished or skipped, so it is not shown again
 *   automatically.
 * @param onDismiss the dialog was closed without completing it (only possible
 *   when [firstRun] is false).
 */
@Composable
fun UserTutorialDialog(
    firstRun: Boolean,
    onCompleted: () -> Unit,
    onDismiss: () -> Unit = onCompleted,
) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val lastStep = stepIndex == tutorialSteps.lastIndex
    val step = tutorialSteps[stepIndex]

    BackHandler {
        when {
            stepIndex > 0 -> stepIndex--
            firstRun -> onCompleted()
            else -> onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!firstRun) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = step.iconRes ?: R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = step.title,
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                AnimatedContent(targetState = step, label = "tutorial-step") { current ->
                    Text(text = current.body, style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(16.dp))
                StepIndicator(current = stepIndex, total = tutorialSteps.size)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (lastStep) {
                        if (firstRun) onCompleted() else onDismiss()
                    } else {
                        stepIndex++
                    }
                },
            ) {
                Text(if (lastStep) "Começar" else "Próximo")
            }
        },
        dismissButton = {
            if (stepIndex > 0) {
                TextButton(onClick = { stepIndex-- }) { Text("Voltar") }
            } else if (firstRun) {
                TextButton(onClick = onCompleted) { Text("Pular") }
            }
        },
    )
}

@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val active = index == current
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (active) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
            )
        }
        Text(
            text = "${current + 1}/$total",
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}
