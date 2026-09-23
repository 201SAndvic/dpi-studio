package com.appconfig.injector.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appconfig.injector.R
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

const val SELF_WIDTH_MIN = 320
const val SELF_WIDTH_MAX = 1000

/** 当前屏幕按上报 dpi 换算出来的宽度（dp）。车机常见 160dpi，会把 1440px 当成 1440dp。 */
fun currentScreenDp(ctx: Context): Int {
    val dm = ctx.resources.displayMetrics
    return (dm.widthPixels / dm.density).roundToInt()
}

/** 推荐的本机显示宽度：屏幕 dp 宽明显偏大时（说明 dpi 上报不准），给一个正常的界面宽度。 */
fun suggestSelfMinWidth(ctx: Context): Int {
    val dpWidth = currentScreenDp(ctx)
    return when {
        dpWidth >= 1200 -> 640
        dpWidth >= 900 -> 600
        dpWidth >= 700 -> 560
        else -> dpWidth.coerceIn(SELF_WIDTH_MIN, SELF_WIDTH_MAX)
    }
}

/**
 * 按设定的 dp 宽度渲染界面。
 *
 * 原理与模块改 densityDpi 一致：density = 屏幕像素宽 ÷ 期望的 dp 宽。
 * minWidthDp <= 0 表示跟随系统，不做处理。
 */
@Composable
fun WithSelfSize(minWidthDp: Int, content: @Composable () -> Unit) {
    if (minWidthDp <= 0) {
        content()
        return
    }
    val ctx = LocalContext.current
    val base = LocalDensity.current
    val widthPx = ctx.resources.displayMetrics.widthPixels
    val density = (widthPx.toFloat() / minWidthDp).coerceIn(0.4f, 8f)
    CompositionLocalProvider(
        LocalDensity provides Density(density = density, fontScale = base.fontScale),
        content = content,
    )
}

// ---------------------------------------------------------------- 尺寸编辑控件

/** 尺寸编辑控件：滑块 + 推荐值 + 跟随系统，引导流程和设置页共用。 */
@Composable
fun SelfSizeEditor(
    value: Int,
    suggested: Int,
    onValueChange: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val dm = ctx.resources.displayMetrics
    val currentDp = currentScreenDp(ctx)
    val effective = (if (value > 0) value else suggested).coerceIn(SELF_WIDTH_MIN, SELF_WIDTH_MAX)

    // 拖动期间只改本地值：界面尺寸一旦实时重排，滑块的像素位置会跟着变，
    // 手势映射就错位了（表现为「拖不动、一次只走一格」）。松手时再真正应用。
    var dragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(effective.toFloat()) }
    LaunchedEffect(value, suggested, dragging) {
        if (!dragging) sliderValue = effective.toFloat()
    }
    val shownValue = sliderValue.roundToInt().coerceIn(SELF_WIDTH_MIN, SELF_WIDTH_MAX)

    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = ctx.getString(
                R.string.onboard_current_screen,
                dm.widthPixels,
                dm.heightPixels,
                dm.densityDpi,
                currentDp,
            ),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (value <= 0 && !dragging) {
                ctx.getString(R.string.onboard_follow_system)
            } else {
                ctx.getString(R.string.onboard_value, shownValue)
            },
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Slider(
            value = sliderValue,
            onValueChange = { v ->
                dragging = true
                sliderValue = v
            },
            onValueChangeFinished = {
                dragging = false
                onValueChange((sliderValue / 10f).roundToInt() * 10)
            },
            valueRange = SELF_WIDTH_MIN.toFloat()..SELF_WIDTH_MAX.toFloat(),
            steps = 0,
        )
        // 滑块的可视高度大于其声明高度，这里留出间隙，避免与下方按钮重叠
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val recommendSelected = value == suggested
            val followSelected = value <= 0
            TextButton(
                text = ctx.getString(R.string.onboard_recommend, suggested),
                onClick = { onValueChange(suggested) },
                modifier = Modifier.weight(1f),
                colors = if (recommendSelected) {
                    ButtonDefaults.textButtonColorsPrimary()
                } else {
                    ButtonDefaults.textButtonColors()
                },
                minHeight = 40.dp,
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            )
            TextButton(
                text = ctx.getString(R.string.onboard_follow_system),
                onClick = { onValueChange(0) },
                modifier = Modifier.weight(1f),
                colors = if (followSelected) {
                    ButtonDefaults.textButtonColorsPrimary()
                } else {
                    ButtonDefaults.textButtonColors()
                },
                minHeight = 40.dp,
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- 首次引导

/** 三步引导：欢迎 → 界面显示尺寸 → 一切就绪。 */
@Composable
fun OnboardingScreen(
    value: Int,
    suggested: Int,
    onValueChange: (Int) -> Unit,
    onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    val lastStep = 2

    // 引导里返回键 = 上一步；第一步时交给外层处理（退出应用）
    BackHandler(enabled = step > 0) { step-- }

    Scaffold(containerColor = MiuixTheme.colorScheme.surface) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            // 左上角应用名（欢迎页不显示，与参考引导页一致）
            AnimatedVisibility(
                visible = step > 0,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(140)),
            ) {
                Text(
                    text = ctx.getString(R.string.app_name),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    val dir = if (forward) 1 else -1
                    (
                        slideInHorizontally(tween(280)) { w -> dir * w / 4 } +
                            fadeIn(tween(280))
                        ) togetherWith
                        (
                            slideOutHorizontally(tween(240)) { w -> -dir * w / 4 } +
                                fadeOut(tween(180))
                            )
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "onboarding-step",
            ) { currentStep ->
                Box(Modifier.fillMaxSize()) {
                    when (currentStep) {
                        0 -> WelcomeStep(ctx)
                        1 -> SizeStep(ctx, value, suggested, onValueChange)
                        else -> DoneStep(ctx)
                    }
                }
            }

            // 底部按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { if (step > 0) step-- },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    enabled = step > 0,
                    cornerRadius = 16.dp,
                ) {
                    Text(
                        text = ctx.getString(R.string.onboard_prev),
                        style = MiuixTheme.textStyles.button,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Button(
                    onClick = { if (step < lastStep) step++ else onDone() },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    cornerRadius = 16.dp,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = if (step < lastStep) {
                            ctx.getString(R.string.onboard_next)
                        } else {
                            ctx.getString(R.string.onboard_start)
                        },
                        style = MiuixTheme.textStyles.button,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/**
 * 从「设置 → 本机显示尺寸」进入的独立页面：
 * 没有引导步骤、没有上一步/下一步，左上角是返回箭头。
 */
@Composable
fun SelfSizeScreen(
    value: Int,
    suggested: Int,
    onValueChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    Scaffold(containerColor = MiuixTheme.colorScheme.surface) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, top = 6.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Basic.ArrowRight,
                        contentDescription = ctx.getString(R.string.about_back),
                        modifier = Modifier
                            .size(width = 11.dp, height = 17.dp)
                            .rotate(180f),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                }
            }
            SizeContent(ctx, value, suggested, onValueChange)
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** 尺寸设置的内容部分，引导第二页和独立页面共用。 */
@Composable
private fun SizeContent(
    ctx: Context,
    value: Int,
    suggested: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(6.dp))
        Icon(
            imageVector = MiuixIcons.Medium.Tune,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = MiuixTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = ctx.getString(R.string.onboard_size_title),
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = ctx.getString(R.string.onboard_size_desc),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
        Spacer(Modifier.height(18.dp))
        AppCard {
            SelfSizeEditor(
                value = value,
                suggested = suggested,
                onValueChange = onValueChange,
            )
        }
        Text(
            text = ctx.getString(R.string.onboard_size_hint),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun WelcomeStep(ctx: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(size = 124.dp, corner = 30.dp)
        Spacer(Modifier.height(30.dp))
        Text(
            text = ctx.getString(R.string.app_name),
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = 36.sp,
        )
    }
}

@Composable
private fun SizeStep(
    ctx: Context,
    value: Int,
    suggested: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SizeContent(ctx, value, suggested, onValueChange)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DoneStep(ctx: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BigCheck(color = MiuixTheme.colorScheme.primary, modifier = Modifier.size(132.dp))
        Spacer(Modifier.height(30.dp))
        Text(
            text = ctx.getString(R.string.onboard_done_title),
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = ctx.getString(R.string.onboard_done_desc),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 一个大号对勾，笔画圆头、略粗，与参考引导页一致。 */
@Composable
private fun BigCheck(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.085f
        val start = Offset(w * 0.10f, h * 0.54f)
        val middle = Offset(w * 0.38f, h * 0.82f)
        val end = Offset(w * 0.90f, h * 0.16f)
        drawLine(color, start, middle, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, middle, end, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
