package com.metrolist.music.ui.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.ui.theme.DesignTokens
import com.metrolist.music.constants.HasCompletedOnboardingKey
import com.metrolist.music.utils.rememberPreference

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val (_, setCompleted) = rememberPreference(HasCompletedOnboardingKey, defaultValue = false)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(DesignTokens.Spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        )
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.extraLarge))
        Text(
            text = "Welcome to Metrolist",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.medium))
        Text(
            text = "Your offline-first music companion. Stream online or build your library locally.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = DesignTokens.Spacing.medium)
        )
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.huge))
        Button(
            onClick = {
                setCompleted(true)
                onComplete()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Started")
        }
    }
}
