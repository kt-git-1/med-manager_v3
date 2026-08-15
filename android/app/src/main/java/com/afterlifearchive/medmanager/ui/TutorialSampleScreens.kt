package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dedicated, non-interactive tutorial fixtures matching the published iOS 1.0.6 sample views. */
@Composable
internal fun PatientTutorialSampleScreen(tab: PatientTab) {
    val title = when (tab) {
        PatientTab.TODAY -> "今日のお薬"
        PatientTab.HISTORY -> "服薬履歴"
        PatientTab.SETTINGS -> "設定"
    }
    val subtitle = when (tab) {
        PatientTab.TODAY -> LocalDate.now(ZoneId.of("Asia/Tokyo"))
            .format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE))
        PatientTab.HISTORY -> "最近の服薬記録を確認できます"
        PatientTab.SETTINGS -> "通知や連携の設定を変更できます"
    }
    val icon = when (tab) {
        PatientTab.TODAY -> Icons.Rounded.CalendarMonth
        PatientTab.HISTORY -> Icons.Rounded.History
        PatientTab.SETTINGS -> Icons.Rounded.Settings
    }
    TutorialSampleFrame(horizontalPadding = 20, tag = "patient-tutorial-sample-${tab.name.lowercase()}") {
        TutorialHeader(title, subtitle, icon, MaterialTheme.colorScheme.primary)
        when (tab) {
            PatientTab.TODAY -> PatientTutorialToday()
            PatientTab.HISTORY -> PatientTutorialHistory()
            PatientTab.SETTINGS -> PatientTutorialSettings()
        }
        Spacer(Modifier.height(240.dp))
    }
}

@Composable
private fun PatientTutorialToday() {
    val colors = MedicationTheme.colors
    TutorialCard(accent = MaterialTheme.colorScheme.primary) {
        Text("今日のお薬", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TutorialProgressStep("朝", "8:07", Icons.Rounded.Check, MaterialTheme.colorScheme.primary, true, Modifier.weight(1f))
            TutorialProgressStep("昼", "12:30", Icons.Rounded.Warning, colors.orange, false, Modifier.weight(1f))
            TutorialProgressStep("夜", "19:00", Icons.Rounded.AccessTime, colors.caregiverBlue, false, Modifier.weight(1f))
            TutorialProgressStep("眠前", "23:00", Icons.Rounded.AccessTime, Color.Gray, false, Modifier.weight(1f))
        }
    }
    TutorialCard(accent = MaterialTheme.colorScheme.primary) {
        Text("次のお薬", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TutorialCircleIcon(Icons.Rounded.AccessTime, MaterialTheme.colorScheme.primary, 66)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("夜のお薬", color = colors.primaryTealText, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("予定 19:00", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
        Text("合計2錠（2種類）", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        TutorialMedicationLine("夕食後の薬 10 mg", "1回1錠", MaterialTheme.colorScheme.primary)
        TutorialMedicationLine("胃薬", "1回1錠", MaterialTheme.colorScheme.primary)
        TutorialPrimaryButton("今飲んだ（13:47）", MaterialTheme.colorScheme.primary)
    }
    Text("今日の記録", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    TutorialCard(accent = colors.orange) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TutorialCircleIcon(Icons.Rounded.Warning, colors.orange, 54, solid = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("飲み遅れのお薬", color = colors.orange, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("昼 12:30・1時間17分遅れ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
        }
        Text("今飲んだ場合は、ここから記録してください", fontWeight = FontWeight.SemiBold)
        TutorialMedicationLine("血圧の薬 5 mg", "1回1錠", colors.orange, warning = true)
        TutorialMedicationLine("胃薬", "1回1錠", colors.orange, warning = true)
        TutorialPrimaryButton("今飲んだ（13:47）", colors.orange)
    }
    TutorialCard(accent = MaterialTheme.colorScheme.primary) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TutorialCircleIcon(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primary, 52)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("服用済み", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("朝 8:07に服用", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PatientTutorialHistory() {
    val colors = MedicationTheme.colors
    TutorialCard(accent = colors.orange) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TutorialRing("1/3", colors.orange)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("今日の進捗", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("1/3回分 記録済み", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("ここまで順調です。残りも飲めたら記録しましょう。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TutorialPill("記録済み 1回分", MaterialTheme.colorScheme.primary)
                    TutorialPill("残り 2回分", colors.orange)
                }
            }
        }
    }
    TutorialCard(accent = colors.orange) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TutorialCircleIcon(Icons.Rounded.CalendarMonth, MaterialTheme.colorScheme.primary, 56, solid = true)
            Text("連続記録", modifier = Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("5日", color = colors.primaryTealText, fontSize = 46.sp, fontWeight = FontWeight.Bold)
        }
        TutorialBenefit("5日間、記録できています。すばらしいです。", Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primary)
        Text(
            "今日もすべて記録すると、6日になります",
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            color = colors.primaryTealText,
            fontWeight = FontWeight.SemiBold,
        )
    }
    TutorialCard(accent = MaterialTheme.colorScheme.primary) {
        Text("今週", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("3/7日", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary, fontSize = 46.sp, fontWeight = FontWeight.Bold)
        Text("記録済み", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TutorialWeekDay("月", "6/8", MaterialTheme.colorScheme.primary, true, Modifier.weight(1f))
            TutorialWeekDay("火", "6/9", MaterialTheme.colorScheme.primary, true, Modifier.weight(1f))
            TutorialWeekDay("水", "6/10", colors.orange, false, Modifier.weight(1f))
            TutorialWeekDay("木", "6/11", colors.caregiverBlue, false, Modifier.weight(1f))
            TutorialWeekDay("金", "6/12", Color.Gray, false, Modifier.weight(1f))
        }
        Text("記録できた日があります。この調子で、できる日に少しずつ続けましょう。", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
    }
    Text("最近の記録", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    TutorialHistoryRow("今日 6月11日（木）", "朝・昼・夜のお薬", "まだ", colors.orange, Icons.Rounded.CalendarMonth)
    TutorialHistoryRow("昨日 6月10日（水）", "朝・昼のお薬", "済み", MaterialTheme.colorScheme.primary, Icons.Rounded.CheckCircle)
}

@Composable
private fun PatientTutorialSettings() {
    TutorialCard {
        TutorialSectionHeader("お薬の通知", Icons.Rounded.Notifications, MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TutorialCircleIcon(Icons.Rounded.Notifications, MaterialTheme.colorScheme.primary, 44)
            Column(Modifier.weight(1f)) {
                Text("通知を有効にする", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("飲む時間にこの端末へ通知します", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TutorialToggle()
        }
    }
    TutorialCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TutorialCircleIcon(Icons.Rounded.Person, MaterialTheme.colorScheme.primary, 44)
            Column {
                Text("連携中", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("家族と服薬記録を共有しています", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    TutorialPrimaryButton("ログアウト", MedicationTheme.colors.patientRed)
}

@Composable
internal fun CaregiverTutorialSampleScreen(step: Int) {
    val spec = caregiverTutorialSample(step)
    TutorialSampleFrame(
        horizontalPadding = if (spec.kind == CaregiverTutorialKind.MEDICATIONS || spec.kind == CaregiverTutorialKind.INVENTORY) 16 else 20,
        tag = "caregiver-tutorial-sample-${spec.tag}",
    ) {
        if (spec.kind == CaregiverTutorialKind.TODAY) CaregiverTutorialTodayHeader() else TutorialHeader(spec.title, spec.subtitle, spec.icon, MaterialTheme.colorScheme.primary, patientName = "田中 花子")
        when (spec.kind) {
            CaregiverTutorialKind.TODAY -> CaregiverTutorialToday()
            CaregiverTutorialKind.MEDICATIONS -> CaregiverTutorialMedications()
            CaregiverTutorialKind.INVENTORY -> CaregiverTutorialInventory()
            CaregiverTutorialKind.HISTORY -> CaregiverTutorialHistory()
            CaregiverTutorialKind.SETTINGS -> CaregiverTutorialSettings()
            CaregiverTutorialKind.TIME_PRESET -> { CaregiverTutorialSelectionCard(); CaregiverTutorialTimeCard() }
            CaregiverTutorialKind.REGISTER -> CaregiverTutorialRegistration()
            CaregiverTutorialKind.ISSUE_CODE -> { CaregiverTutorialSelectionCard(); CaregiverTutorialPatientCard(highlight = true) }
            CaregiverTutorialKind.SHARE_CODE -> CaregiverTutorialLinkCode()
            CaregiverTutorialKind.NOTIFICATION -> CaregiverTutorialNotification()
        }
        Spacer(Modifier.height(240.dp))
    }
}

private enum class CaregiverTutorialKind { TODAY, MEDICATIONS, INVENTORY, HISTORY, SETTINGS, TIME_PRESET, REGISTER, ISSUE_CODE, SHARE_CODE, NOTIFICATION }
private data class CaregiverTutorialSpec(val kind: CaregiverTutorialKind, val tag: String, val title: String, val subtitle: String, val icon: ImageVector)
private fun caregiverTutorialSample(step: Int) = when (step.coerceIn(0, 9)) {
    0 -> CaregiverTutorialSpec(CaregiverTutorialKind.TODAY, "today", "今日の予定", "このように今日飲む予定がまとまります", Icons.Rounded.Home)
    1 -> CaregiverTutorialSpec(CaregiverTutorialKind.MEDICATIONS, "medications", "薬を管理", "登録した薬が一覧で表示されます", Icons.Rounded.Medication)
    2 -> CaregiverTutorialSpec(CaregiverTutorialKind.INVENTORY, "inventory", "在庫を確認", "残数と補充目安を確認できます", Icons.Rounded.Inventory2)
    3 -> CaregiverTutorialSpec(CaregiverTutorialKind.HISTORY, "history", "服薬履歴", "記録状況を日付ごとに確認できます", Icons.Rounded.History)
    4 -> CaregiverTutorialSpec(CaregiverTutorialKind.SETTINGS, "settings", "連携・設定", "見守る方と連携状態を管理できます", Icons.Rounded.Settings)
    5 -> CaregiverTutorialSpec(CaregiverTutorialKind.TIME_PRESET, "time-preset", "服用時間を調整", "朝・昼・夜・眠前の時刻を変更できます", Icons.Rounded.AccessTime)
    6 -> CaregiverTutorialSpec(CaregiverTutorialKind.REGISTER, "register", "見守る方を登録", "最初に本人の名前を登録します", Icons.Rounded.PersonAdd)
    7 -> CaregiverTutorialSpec(CaregiverTutorialKind.ISSUE_CODE, "issue-code", "連携コードを発行", "登録後に本人用のコードを作ります", Icons.Rounded.Link)
    8 -> CaregiverTutorialSpec(CaregiverTutorialKind.SHARE_CODE, "share-code", "本人へコードを共有", "コピーまたは共有で本人へ渡します", Icons.Rounded.Share)
    else -> CaregiverTutorialSpec(CaregiverTutorialKind.NOTIFICATION, "notification", "通知を受け取る", "服薬記録や飲み忘れを通知します", Icons.Rounded.Notifications)
}

@Composable
private fun CaregiverTutorialTodayHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(58.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Text("田", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        Column {
            Text("田中 花子さん", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("今日の予定", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CaregiverTutorialToday() {
    val colors = MedicationTheme.colors
    TutorialCard(accent = colors.orange) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TutorialCircleIcon(Icons.Rounded.Warning, colors.orange, 48, solid = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("飲み遅れが1回ありました", color = colors.orange, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("朝 8:00予定 → 13:21に本人が記録", fontWeight = FontWeight.Bold)
                Text("5時間21分遅れ", color = colors.orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    TutorialCard {
        Text("今日の服薬状況", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TutorialTodayStatus("朝", "13:21", Icons.Rounded.Warning, colors.orange, Modifier.weight(1f))
            TutorialTodayStatus("昼", "12:30", Icons.Rounded.Remove, Color.Gray, Modifier.weight(1f))
            TutorialTodayStatus("夜", "19:05", Icons.Rounded.Check, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            TutorialTodayStatus("眠前", "23:00", Icons.Rounded.Remove, Color.Gray, Modifier.weight(1f))
        }
    }
    Text("今日の予定", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    TutorialScheduleCard("朝", "8:00", "13:21", "血圧の薬 5 mg", true)
    TutorialScheduleCard("昼", "12:30", null, "整腸剤 50 mg", false)
}

@Composable
private fun CaregiverTutorialMedications() {
    val colors = MedicationTheme.colors
    TutorialMetricGrid(listOf(Triple("3", "登録中", MaterialTheme.colorScheme.primary), Triple("2", "定時", colors.caregiverBlue), Triple("1", "頓服", colors.orange), Triple("0", "終了", Color.Gray)))
    TutorialChips(listOf("すべて", "定時", "頓服", "終了"), selected = 0)
    TutorialSectionLabel("定時")
    TutorialMedicationCard("血圧の薬 5 mg", "定時", "毎日 朝・昼", "1回1錠", "残り18錠", colors.caregiverBlue)
    TutorialMedicationCard("整腸剤 50 mg", "定時", "毎日 夜", "1回1錠", "残り10錠", MaterialTheme.colorScheme.primary)
    TutorialSectionLabel("頓服")
    TutorialMedicationCard("頭痛薬", "頓服", "必要な時", "1回1錠", null, colors.orange)
}

@Composable
private fun CaregiverTutorialInventory() {
    val colors = MedicationTheme.colors
    TutorialMetricGrid(listOf(Triple("1", "要確認", colors.orange), Triple("2", "管理中", colors.caregiverBlue)))
    TutorialCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TutorialCircleIcon(Icons.Rounded.Notifications, colors.orange, 38)
            Column(Modifier.weight(1f)) {
                Text("まず補充が必要な薬があります", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("血圧の薬 5 mg が残り少なくなっています。補充したら在庫数を更新してください。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    TutorialChips(listOf("すべて", "低在庫のみ", "在庫なし"), selected = 0)
    TutorialSectionLabel("在庫一覧")
    TutorialInventoryCard("血圧の薬 5 mg", "4", "あと2日分", "残り日数が少ないため、早めの補充が必要です。", colors.orange, true)
    TutorialInventoryCard("整腸剤 50 mg", "10", "あと5日分", "服薬記録に合わせて自動で減ります。", MaterialTheme.colorScheme.primary, false)
}

@Composable
private fun CaregiverTutorialHistory() {
    val colors = MedicationTheme.colors
    TutorialCard {
        Text("選択した日", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("6月10日（水）", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("4回中2回を記録", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("未記録のお薬があります", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TutorialPill("服用済 2", MaterialTheme.colorScheme.primary)
            TutorialPill("未記録 2", Color.Gray)
        }
    }
    TutorialHistorySlotCard("朝", "8:00", "13:21", "5時間21分遅れ", colors.orange)
    TutorialHistorySlotCard("昼", "12:30", null, "未記録", colors.caregiverBlue)
    TutorialCalendarCard()
}

@Composable
private fun CaregiverTutorialSettings() {
    CaregiverTutorialSelectionCard()
    CaregiverTutorialPatientCard()
    CaregiverTutorialTimeCard()
    TutorialCard {
        TutorialSectionHeader("通知", Icons.Rounded.Notifications, MaterialTheme.colorScheme.primary)
        Text("服薬記録の通知を受け取ります", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("通知を受け取る", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            TutorialToggle()
        }
    }
}

@Composable
private fun CaregiverTutorialSelectionCard() {
    TutorialCard {
        TutorialSectionHeader("見守る方", Icons.Rounded.Person, MaterialTheme.colorScheme.primary)
        Text("設定する方を選択してください", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("田中 花子", modifier = Modifier.weight(1f), color = MedicationTheme.colors.primaryTealText, fontWeight = FontWeight.Bold)
            Text("⌄", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CaregiverTutorialPatientCard(highlight: Boolean = false) {
    TutorialCard(accent = MaterialTheme.colorScheme.primary) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("田中 花子", modifier = Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            TutorialPill("選択中", MaterialTheme.colorScheme.primary)
        }
        TutorialOutlineButton("連携コードを発行", Icons.Rounded.Link, MaterialTheme.colorScheme.primary, highlight)
        TutorialOutlineButton("見守りを解除", Icons.Rounded.Delete, MedicationTheme.colors.caregiverRed)
    }
}

@Composable
private fun CaregiverTutorialTimeCard() {
    val colors = MedicationTheme.colors
    TutorialCard {
        TutorialSectionHeader("詳細設定", Icons.Rounded.Tune, MaterialTheme.colorScheme.primary)
        Text("服用時間などを変更できます", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TutorialCircleIcon(Icons.Rounded.AccessTime, colors.caregiverBlue, 34)
            Column(Modifier.weight(1f)) {
                Text("服用時間", fontWeight = FontWeight.Bold)
                Text("朝・昼・夜・眠前の時刻", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CaregiverTutorialRegistration() {
    val colors = MedicationTheme.colors
    TutorialCard(accent = colors.orange) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TutorialCircleIcon(Icons.Rounded.PersonAdd, colors.orange, 48)
            Column {
                Text("見守る方を登録", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("本人の名前を入力して保存します。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
        }
        Text("表示名", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().background(MedicationTheme.colors.elevatedBackground, RoundedCornerShape(14.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("田中 花子", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        TutorialPrimaryButton("保存", colors.orange)
    }
}

@Composable
private fun CaregiverTutorialLinkCode() {
    val colors = MedicationTheme.colors
    TutorialCard(accent = MaterialTheme.colorScheme.primary) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Text("連携コード", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("本人の端末で入力してください", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)) {
            "482913".forEach { digit ->
                Box(Modifier.width(38.dp).height(50.dp).background(MedicationTheme.colors.elevatedBackground, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Text(digit.toString(), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { TutorialOutlineButton("コピー", Icons.Rounded.ContentCopy, MaterialTheme.colorScheme.primary) }
            Box(Modifier.weight(1f)) { TutorialOutlineButton("共有", Icons.Rounded.Share, colors.orange, true) }
        }
        Text("有効期限: 今日 18:00", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CaregiverTutorialNotification() {
    val colors = MedicationTheme.colors
    TutorialCard(accent = colors.orange) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TutorialCircleIcon(Icons.Rounded.Notifications, colors.orange, 52)
            Column(Modifier.weight(1f)) {
                Text("通知を受け取る", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("本人が記録したときにこの端末へ知らせます。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
        }
        TutorialBenefit("服薬記録をすぐ確認できます", Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primary)
        TutorialBenefit("飲み忘れに気づきやすくなります", Icons.Rounded.Warning, colors.caregiverRed)
    }
}

@Composable
private fun TutorialSampleFrame(horizontalPadding: Int, tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding.dp, vertical = 16.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun TutorialHeader(title: String, subtitle: String, icon: ImageVector, tint: Color, patientName: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (patientName == null) PatientHeaderIcon(icon) else CaregiverPatientAvatar(patientName)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            patientName?.let { Text("$it さん", color = tint, fontWeight = FontWeight.Bold) }
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TutorialCard(accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = MedicationTheme.colors.caregiverCardShadow, spotColor = MedicationTheme.colors.caregiverCardShadow)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .then(if (accent != null) Modifier.border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(18.dp)) else Modifier),
    ) {
        accent?.let { Box(Modifier.width(6.dp).height(54.dp).align(Alignment.CenterStart).background(it, RoundedCornerShape(3.dp))) }
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun TutorialCircleIcon(icon: ImageVector, tint: Color, size: Int, solid: Boolean = false) {
    Box(Modifier.size(size.dp).background(if (solid) tint else tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = if (solid) Color.White else tint, modifier = Modifier.size((size * 0.52f).dp))
    }
}

@Composable
private fun TutorialProgressStep(title: String, detail: String, icon: ImageVector, tint: Color, filled: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(42.dp).background(if (filled) tint else tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (filled) Color.White else tint, modifier = Modifier.size(18.dp))
        }
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun TutorialMedicationLine(name: String, detail: String, tint: Color, warning: Boolean = false) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MedicationPillsGlyph(tint, Modifier.size(30.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = if (warning) MedicationTheme.colors.patientRed else MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(if (warning) Icons.Rounded.Warning else Icons.Rounded.AccessTime, contentDescription = null, tint = tint)
    }
}

@Composable
private fun TutorialPrimaryButton(title: String, tint: Color) {
    Row(Modifier.fillMaxWidth().height(58.dp).background(tint, RoundedCornerShape(18.dp)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TutorialPill(text: String, tint: Color) {
    Text(text, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(tint.copy(alpha = 0.14f), CircleShape).padding(horizontal = 10.dp, vertical = 6.dp))
}

@Composable
private fun TutorialRing(value: String, tint: Color) {
    Box(Modifier.size(86.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { 1f / 3f },
            modifier = Modifier.fillMaxSize(),
            color = tint,
            trackColor = tint.copy(alpha = 0.16f),
            strokeWidth = 10.dp,
        )
        Text(value, color = tint, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TutorialWeekDay(day: String, date: String, tint: Color, filled: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(day, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.size(34.dp).background(if (filled) tint else tint.copy(alpha = 0.13f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (filled) Icons.Rounded.Check else Icons.Rounded.AccessTime, contentDescription = null, tint = if (filled) Color.White else tint, modifier = Modifier.size(16.dp))
        }
        Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun TutorialHistoryRow(title: String, subtitle: String, status: String, tint: Color, icon: ImageVector) {
    TutorialCard(accent = tint) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TutorialCircleIcon(icon, tint, 46)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TutorialPill(status, tint)
        }
    }
}

@Composable
private fun TutorialSectionHeader(title: String, icon: ImageVector, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TutorialToggle() {
    Box(Modifier.width(52.dp).height(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) {
        Box(Modifier.size(28.dp).align(Alignment.CenterEnd).padding(2.dp).background(Color.White, CircleShape))
    }
}

@Composable
private fun TutorialTodayStatus(title: String, time: String, icon: ImageVector, tint: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.size(42.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        Text(time, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun TutorialScheduleCard(slot: String, scheduled: String, actual: String?, medication: String, recorded: Boolean) {
    val tint = if (recorded) MaterialTheme.colorScheme.primary else MedicationTheme.colors.orange
    TutorialCard(accent = tint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$slot $scheduled", modifier = Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TutorialPill(if (recorded) "飲み遅れ" else "未記録", MedicationTheme.colors.orange)
        }
        actual?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("実際 $it", color = MedicationTheme.colors.orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("本人が記録", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        TutorialMedicationLine(medication, "1回1錠", tint)
        if (!recorded) TutorialPrimaryButton("代理で記録", MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TutorialMetricGrid(items: List<Triple<String, String, Color>>) {
    items.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (value, label, tint) ->
                Column(Modifier.weight(1f).background(tint.copy(alpha = 0.10f), RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Text(value, color = tint, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TutorialChips(labels: List<String>, selected: Int) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            val tint = if (index == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Text(label, color = tint, fontWeight = FontWeight.Bold, modifier = Modifier.background(tint.copy(alpha = if (index == selected) 0.16f else 0.08f), CircleShape).padding(horizontal = 13.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun TutorialSectionLabel(text: String) = Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)

@Composable
private fun TutorialMedicationCard(name: String, badge: String, detail: String, dose: String, inventory: String?, tint: Color) {
    TutorialCard(accent = tint) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MedicationPillsGlyph(tint, Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
            TutorialPill(badge, tint)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(dose, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            inventory?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TutorialInventoryCard(name: String, quantity: String, days: String, help: String, tint: Color, attention: Boolean) {
    TutorialCard(accent = tint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("$quantity 錠", color = tint, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
            TutorialPill(days, tint)
        }
        Text(help, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (attention) TutorialOutlineButton("在庫を更新", Icons.Rounded.Add, tint)
    }
}

@Composable
private fun TutorialHistorySlotCard(slot: String, scheduled: String, actual: String?, detail: String, tint: Color) {
    TutorialCard(accent = tint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$slot 予定 $scheduled", modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            TutorialPill(if (actual == null) "未記録" else "飲み遅れ", if (actual == null) Color.Gray else MedicationTheme.colors.orange)
        }
        if (actual == null) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("実際 $actual", color = MedicationTheme.colors.orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = MedicationTheme.colors.orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("本人が記録", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TutorialCalendarCard() {
    val colors = MedicationTheme.colors
    TutorialCard {
        Text("カレンダー", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("日付ごとの記録状況を確認できます", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth()) { listOf("月", "火", "水", "木", "金", "土", "日").forEach { Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        (1..14).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val selected = day == 10
                    val markers = when (day) {
                        5 -> listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
                        6 -> listOf(MaterialTheme.colorScheme.primary, Color.Gray)
                        8 -> listOf(MaterialTheme.colorScheme.primary, colors.caregiverRed)
                        9 -> listOf(MaterialTheme.colorScheme.primary, colors.indigo)
                        10 -> listOf(MaterialTheme.colorScheme.primary, Color.Gray, colors.caregiverRed)
                        11 -> listOf(Color.Gray)
                        else -> emptyList()
                    }
                    Column(Modifier.weight(1f).height(48.dp).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(9.dp)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(day.toString(), color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { markers.forEach { Box(Modifier.size(5.dp).background(it, CircleShape)) } }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TutorialLegend("服用済", MaterialTheme.colorScheme.primary)
            TutorialLegend("未服用", colors.caregiverRed)
            TutorialLegend("未記録", Color.Gray)
        }
    }
}

@Composable
private fun TutorialLegend(text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(7.dp).background(tint, CircleShape))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TutorialOutlineButton(title: String, icon: ImageVector, tint: Color, highlighted: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(tint.copy(alpha = 0.13f), RoundedCornerShape(12.dp))
            .then(if (highlighted) Modifier.border(3.dp, tint, RoundedCornerShape(12.dp)) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(title, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TutorialBenefit(text: String, icon: ImageVector, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TutorialCircleIcon(icon, tint, 30)
        Text(text, fontWeight = FontWeight.Bold)
    }
}
