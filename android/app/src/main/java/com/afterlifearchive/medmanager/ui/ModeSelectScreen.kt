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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme

@Composable
fun ModeSelectScreen(onModeSelected: (AppMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extended = MedicationTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
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
                    badge = "本人モード",
                    title = "本人として使う",
                    subtitle = "今日のお薬を確認します",
                    tint = colors.primary,
                    onClick = { onModeSelected(AppMode.PATIENT) },
                )
                RoleModeCard(
                    image = R.drawable.role_family,
                    badgeIcon = Icons.Rounded.Groups,
                    badge = "家族モード",
                    title = "家族として使う",
                    subtitle = "薬と在庫を管理します",
                    tint = extended.orange,
                    onClick = { onModeSelected(AppMode.CAREGIVER) },
                )
            }
        }
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
                text = "お薬見守り",
                color = extended.readableSecondaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "どちらで\n使いますか？",
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
                Text("このモードで始める", color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(38.dp).background(tint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
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
