package com.afterlifearchive.medmanager.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.AnalyticsAppMode
import com.afterlifearchive.medmanager.AnalyticsScreen
import com.afterlifearchive.medmanager.AnalyticsService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme

@Composable
fun ModeSelectScreen(analyticsService: AnalyticsService? = null, onModeSelected: (AppMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extended = MedicationTheme.colors
    val analyticsState = analyticsService?.state?.collectAsStateWithLifecycle()?.value
    var showConsent by remember(analyticsState?.decided) { mutableStateOf(analyticsState?.decided == false) }
    LaunchedEffect(Unit) { analyticsService?.logScreenViewed(AnalyticsScreen.MODE_SELECT) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 22.dp,
            top = 52.dp,
            end = 22.dp,
            bottom = 34.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { ModeHeader() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RoleModeCard(
                    image = R.drawable.role_patient,
                    badgeIcon = Icons.Rounded.CheckCircle,
                    badge = stringResource(R.string.mode_select_patient_badge),
                    title = stringResource(R.string.mode_select_patient_title),
                    subtitle = stringResource(R.string.mode_select_patient_subtitle),
                    tint = colors.primary,
                    onClick = {
                        analyticsService?.logModeSelected(AnalyticsAppMode.PATIENT)
                        onModeSelected(AppMode.PATIENT)
                    },
                )
                RoleModeCard(
                    image = R.drawable.role_family,
                    badgeIcon = Icons.Rounded.Groups,
                    badge = stringResource(R.string.mode_select_caregiver_badge),
                    title = stringResource(R.string.mode_select_caregiver_title),
                    subtitle = stringResource(R.string.mode_select_caregiver_subtitle),
                    tint = extended.orange,
                    onClick = {
                        analyticsService?.logModeSelected(AnalyticsAppMode.CAREGIVER)
                        onModeSelected(AppMode.CAREGIVER)
                    },
                )
            }
        }
    }
    if (showConsent) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.analytics_consent_title)) },
            text = { Text(stringResource(R.string.analytics_consent_message)) },
            confirmButton = {
                TextButton(onClick = {
                    analyticsService?.setCollectionEnabled(true)
                    showConsent = false
                    analyticsService?.logScreenViewed(AnalyticsScreen.MODE_SELECT)
                }) { Text(stringResource(R.string.analytics_consent_allow)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    analyticsService?.setCollectionEnabled(false)
                    showConsent = false
                }) { Text(stringResource(R.string.analytics_consent_decline)) }
            },
        )
    }
}

@Composable
private fun ModeHeader() {
    val colors = MaterialTheme.colorScheme
    val extended = MedicationTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(28.dp).background(colors.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Medication,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                color = extended.readableSecondaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = stringResource(R.string.mode_select_heading),
            color = colors.onBackground,
            fontSize = 38.sp,
            lineHeight = 45.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun RoleModeCard(
    @DrawableRes image: Int,
    badgeIcon: ImageVector,
    badge: String,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val extended = MedicationTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = tint.copy(alpha = 0.14f), spotColor = tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.16f), shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick),
        color = colors.surface,
        shape = shape,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RoleIllustration(image, tint)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(badgeIcon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                        Text(badge, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    Text(title, color = colors.onSurface, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = extended.readableSecondaryText, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.mode_select_start), color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(38.dp).background(tint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun RoleIllustration(@DrawableRes image: Int, tint: Color) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .size(112.dp)
            .background(
                Brush.linearGradient(listOf(tint.copy(alpha = 0.14f), tint.copy(alpha = 0.06f))),
                shape,
            )
            .border(1.dp, tint.copy(alpha = 0.18f), shape)
            .clip(shape),
    ) {
        Image(
            painter = painterResource(image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(if (image == R.drawable.role_patient) 7.dp else 2.dp),
        )
    }
}
