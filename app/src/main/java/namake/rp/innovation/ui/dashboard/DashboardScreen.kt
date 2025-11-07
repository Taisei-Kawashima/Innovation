package namake.rp.innovation.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import namake.rp.innovation.ui.theme.AlertBackground
import namake.rp.innovation.ui.theme.AppAlert
import namake.rp.innovation.ui.theme.AppPrimary
import namake.rp.innovation.ui.theme.AppSafe
import namake.rp.innovation.ui.theme.QuestBlueBg
import namake.rp.innovation.ui.theme.QuestRedBg
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: HealthViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                DashboardHeader(
                    userName = state.userName,
                    isLinked = state.isHealthConnectLinked
                )
            },
            containerColor = Color.White,
            contentColor = Color.Black
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(paddingValues)
                    .widthIn(max = 500.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.showSleepAlert) {
                    item {
                        AlertCard(deficitMinutes = state.sleepDeficitMinutes)
                    }
                }

                item {
                    HealthScoreCard(
                        score = state.totalScore,
                        exerciseStatus = state.exerciseStatus,
                        sleepStatus = state.sleepStatus,
                        heartRateStatus = state.heartRateStatus,
                        todaySteps = state.todaySteps,
                        sleepHours = state.sleepHours,
                        heartRate = state.heartRate,
                        todaySleepHours = state.todaySleepHours,
                        yesterdaySleepHours = state.yesterdaySleepHours
                    )
                }

                item {
                    MiniQuestCard()
                }

                item {
                    TrendCard(dailySteps = state.dailySteps)
                }

//                item {
//                    CostAppealCard()
//                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
fun DashboardHeader(userName: String, isLinked: Boolean) {
    Surface(
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = "android.resource://namake.rp.innovation/drawable/karadalog"
                    ),
                    contentDescription = "Karada Logo",
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
                Image(
                    painter = rememberAsyncImagePainter(
                        model = "https://placehold.co/40x40/FF7043/ffffff?text=U"
                    ),
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun AlertCard(deficitMinutes: Int) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = AlertBackground,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(4.dp, AppAlert.copy(alpha = 0.6f)),
                    RoundedCornerShape(16.dp)
                )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column {
                        Text(
                            text = "【警告】睡眠負債が蓄積しています",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "過去3日間の平均睡眠時間が目標より${deficitMinutes}分不足しています。このままでは集中力低下に繋がります。",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            lineHeight = 20.sp
                        )
                    }
                }
                HorizontalDivider(
                    color = AppPrimary.copy(alpha = 0.2f),
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "今夜の目標：23:00就寝を目指しましょう",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun HealthScoreCard(
    score: Int,
    exerciseStatus: String,
    sleepStatus: String,
    heartRateStatus: String,
    todaySteps: Int = 0,
    sleepHours: Double = 0.0,
    heartRate: Int = 0,
    todaySleepHours: Double = 0.0,
    yesterdaySleepHours: Double = 0.0
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "総合健康スコア",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )

                ScoreGauge(score = score, modifier = Modifier.size(100.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 今日の歩数セクション
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFE0B2),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "今日の歩数",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "$todaySteps",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF7043)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                ScoreBreakdownRow(
                    title = "運動",
                    value = todaySteps.toString() + "歩"
                )
                ScoreBreakdownRow(
                    title = "睡眠",
                    value = String.format(Locale.US, "%.1f時間", yesterdaySleepHours)
                )
                ScoreBreakdownRow(
                    title = "心拍数",
                    value = "$heartRate bpm"
                )
            }
        }
    }
}

@Composable
fun ScoreGauge(score: Int, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        val progress = score / 100f

        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.fillMaxSize(),
            color = Color.LightGray.copy(alpha = 0.5f),
            strokeWidth = 10.dp
        )
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = AppPrimary,
            strokeWidth = 10.dp,
            strokeCap = StrokeCap.Round
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$score",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = AppPrimary
            )
            Text(
                text = "/100",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun ScoreBreakdownRow(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 14.sp, color = Color.Gray)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
    }
}

@Composable
fun MiniQuestCard() {
    AppCard {
        Text(
            text = "今日のミニクエスト",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QuestItem(
                text = "【運動】あと15分軽いウォーキングをする",
                color = Color(0xFF3B82F6),
                backgroundColor = QuestBlueBg
            )
            QuestItem(
                text = "【食事】夕食の塩分摂取量を1g減らす",
                color = Color(0xFFEF4444),
                backgroundColor = QuestRedBg
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "※これらのアクションは、過去のデータと今日の活動状況に基づいて提案されています。",
            fontSize = 12.sp,
            color = Color.Gray,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun QuestItem(text: String, color: Color, backgroundColor: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.DarkGray.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TrendCard(dailySteps: List<Int> = emptyList()) {
    AppCard {
        Text(
            text = "過去7日間の活動トレンド",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "歩数 (歩)", fontSize = 14.sp, color = Color.Gray)
            Text(text = "+15%", fontSize = 14.sp, color = AppSafe, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(12.dp))

        // グラフを表示（日別歩数データを使用）
        if (dailySteps.isNotEmpty()) {
            ActivityTrendChart(dailySteps = dailySteps)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "データを読み込み中...", fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun ActivityTrendChart(dailySteps: List<Int>) {
    val entries = dailySteps.mapIndexed { index, steps ->
        com.github.mikephil.charting.data.Entry(index.toFloat(), steps.toFloat())
    }

    val maxSteps = dailySteps.maxOrNull() ?: 10000
    val baseSteps = maxSteps.toFloat()

    // 現在の日付から7日間の日付ラベルを生成
    val dateLabels = run {
        val today = java.time.LocalDate.now()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MM/dd")
        (0..6).map { index ->
            today.minusDays((6 - index).toLong()).format(formatter)
        }.toTypedArray()
    }

    val dataSet = com.github.mikephil.charting.data.LineDataSet(entries, "歩数").apply {
        color = android.graphics.Color.rgb(70, 130, 180)
        setDrawCircles(true)
        circleRadius = 4f
        lineWidth = 2.5f
        setDrawValues(true)
        valueTextSize = 30f
        setCircleColor(android.graphics.Color.rgb(70, 130, 180))
        mode = com.github.mikephil.charting.data.LineDataSet.Mode.CUBIC_BEZIER
    }

    val lineData = com.github.mikephil.charting.data.LineData(dataSet)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                com.github.mikephil.charting.utils.Utils.init(context)

                com.github.mikephil.charting.charts.LineChart(context).apply {
                    data = lineData
                    description.isEnabled = false
                    axisRight.isEnabled = false
                    axisLeft.apply {
                        axisMinimum = 0f
                        axisMaximum = (baseSteps * 1.2f).coerceAtLeast(10000f)
                        setDrawGridLines(true)
                    }
                    xAxis.apply {
                        labelCount = 7
                        setDrawLabels(true)
                        valueFormatter = object : com.github.mikephil.charting.formatter.IndexAxisValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val index = value.toInt()
                                return if (index >= 0 && index < dateLabels.size) dateLabels[index] else ""
                            }
                        }
                        position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                    }
                    legend.apply {
                        isEnabled = true
                        form = com.github.mikephil.charting.components.Legend.LegendForm.LINE
                        textColor = android.graphics.Color.BLACK
                    }
                    setTouchEnabled(true)
                    isDragEnabled = true
                    isScaleXEnabled = false
                    isScaleYEnabled = false
                    invalidate()
                }
            },
            update = { chart ->
                chart.data = lineData
                chart.axisLeft.apply {
                    axisMinimum = 0f
                    axisMaximum = (baseSteps * 1.2f).coerceAtLeast(10000f)
                }
                chart.invalidate()
            }
        )
    }
}

@Composable
fun CostAppealCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = AppPrimary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "低コストで、あなたの健康習慣をサポート。",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "スマートウォッチは不要。他社に比べて年間で数万円の節約が可能です。",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 20.sp
                )
                Button(
                    onClick = { /* TODO */ },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = AppPrimary
                    ),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = "年間コスト比較を見る",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Surface(
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                content = content
            )
        }
    }
}