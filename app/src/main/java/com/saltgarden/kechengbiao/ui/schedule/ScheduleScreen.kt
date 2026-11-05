package com.saltgarden.kechengbiao.ui.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saltgarden.kechengbiao.core.time.EventTime
import com.saltgarden.kechengbiao.core.time.Timeline
import com.saltgarden.kechengbiao.core.time.WeekCalculator
import com.saltgarden.kechengbiao.core.time.nodeEndMinute
import com.saltgarden.kechengbiao.core.time.nodeStartMinute
import com.saltgarden.kechengbiao.data.CourseEntity
import com.saltgarden.kechengbiao.data.EventEntity
import com.saltgarden.kechengbiao.data.TableEntity
import com.saltgarden.kechengbiao.ui.theme.CourseColors
import com.saltgarden.kechengbiao.ui.theme.GlassChip
import com.saltgarden.kechengbiao.ui.theme.GlassIconButton
import com.saltgarden.kechengbiao.ui.theme.glassSurface
import com.saltgarden.kechengbiao.ui.theme.pressScale
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

private val HOUR_HEIGHT = 62.dp
private val TIME_COL_WIDTH = 44.dp

@Composable
fun ScheduleScreen(
    vm: ScheduleViewModel,
    onGoImport: () -> Unit,
    onGoSettings: () -> Unit,
) {
    val table by vm.table.collectAsStateWithLifecycle()
    val courses by vm.courses.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val todayWeek by vm.todayWeek.collectAsStateWithLifecycle()

    val maxWeek = table?.maxWeek ?: 25
    val pagerState = rememberPagerState(
        initialPage = (todayWeek - 1).coerceIn(0, maxWeek - 1),
        pageCount = { maxWeek }
    )
    val week = pagerState.currentPage + 1
    val scope = rememberCoroutineScope()

    val nodeTimes = remember(table?.nodeTimes) {
        WeekCalculator.parseNodeTimes(table?.nodeTimes ?: WeekCalculator.defaultNodeTimes())
    }
    val timeline = remember(nodeTimes) { Timeline.of(nodeTimes) }

    // todayWeek 是异步加载的 StateFlow（初值 1）：课表就绪后一次性跳到"本周"。
    var jumpedToToday by rememberSaveable(table?.id) { mutableStateOf(false) }
    LaunchedEffect(table?.id, todayWeek, maxWeek) {
        if (!jumpedToToday && table?.id != null) {
            pagerState.scrollToPage((todayWeek - 1).coerceIn(0, maxWeek - 1))
            jumpedToToday = true
        }
    }

    var detailCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var editCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var detailEvent by remember { mutableStateOf<EventEntity?>(null) }
    var editEvent by remember { mutableStateOf<EventEntity?>(null) }
    var editEventDateLabel by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // 单行顶栏：‹ 日期 › 导入 设置
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .glassSurface(RoundedCornerShape(26.dp), elevation = 6.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    Icons.Filled.ChevronLeft, "上一周",
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                        }
                    },
                    size = 32.dp, iconSize = 20.dp,
                )
                Text(
                    "第 $week 周 · ${weekLabel(table?.termStartDate, week)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                GlassIconButton(
                    Icons.Filled.ChevronRight, "下一周",
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                (pagerState.currentPage + 1).coerceAtMost(maxWeek - 1)
                            )
                        }
                    },
                    size = 32.dp, iconSize = 20.dp,
                )
                Spacer(Modifier.width(6.dp))
                GlassIconButton(
                    Icons.Filled.Settings, "设置", onGoSettings,
                    size = 32.dp, iconSize = 18.dp,
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            val dates = table?.let { WeekCalculator.weekDates(it.termStartDate, week) }
            val days = if (table?.showWeekend == false) (1..5) else (1..7)

            // 星期表头
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Spacer(Modifier.width(TIME_COL_WIDTH))
                days.forEach { d ->
                    val date = dates?.getOrNull(d - 1)
                    val isToday = date?.equals(LocalDate.now()) == true
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "周" + "一二三四五六日".let { it[d - 1].toString() },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                        if (isToday && date != null) {
                            // 今天的日期放进实心圆徽标，远比单纯换色醒目
                            Box(
                                Modifier
                                    .padding(top = 2.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        } else {
                            Text(
                                date?.let { WeekCalculator.formatDate(it) } ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalLine()

            Box(Modifier.weight(1f)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val w = page + 1
                    val pageDates = table?.let { WeekCalculator.weekDates(it.termStartDate, w) }
                    Row(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    ) {
                        TimeColumn(timeline)
                        days.forEach { d ->
                            val date = pageDates?.getOrNull(d - 1)
                            DayColumn(
                                day = d,
                                week = w,
                                date = date,
                                timeline = timeline,
                                nodeTimes = nodeTimes,
                                courses = courses,
                                events = events,
                                showOtherWeek = table?.showOtherWeek ?: false,
                                onCourseClick = { detailCourse = it },
                                onEventClick = { detailEvent = it },
                                onQuickAddCourse = { day, node ->
                                    editCourse = CourseEntity(
                                        tableId = 0, name = "", day = day,
                                        startNode = node, endNode = node, weeks = "1-16"
                                    )
                                },
                                onAddEvent = { dateOfEvent, startMinute, endMinute ->
                                    editEvent = EventEntity(
                                        date = dateOfEvent.toString(),
                                        startMinute = startMinute,
                                        endMinute = endMinute,
                                        title = "",
                                    )
                                    editEventDateLabel = dateLabelOf(dateOfEvent)
                                },
                            )
                        }
                    }
                }

                if (courses.isEmpty() && events.isEmpty()) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("还没有课程", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("长按空白格子添加课程，或去导入页从教务系统一键导入", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.Button(onClick = onGoImport) { Text("去导入") }
                    }
                }

                // 非本周时右下角弹出“回到本周”
                BackToTodayChip(
                    visible = week != todayWeek,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage((todayWeek - 1).coerceIn(0, maxWeek - 1))
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
                )
            }
        }
    }

    detailCourse?.let { c ->
        CourseDetailDialog(
            course = c,
            onDismiss = { detailCourse = null },
            onEdit = { detailCourse = null; editCourse = c },
            onDelete = { vm.deleteCourse(c); detailCourse = null }
        )
    }

    editCourse?.let { c ->
        CourseEditDialog(
            initial = c,
            isNew = c.id == 0L && c.tableId == 0L,
            onDismiss = { editCourse = null },
            onConfirm = { course ->
                if (course.id == 0L) vm.addCourse(course.copy(tableId = table?.id ?: 0, colorIndex = vm.nextColorIndex()))
                else vm.updateCourse(course)
                editCourse = null
            }
        )
    }

    detailEvent?.let { ev ->
        EventDetailDialog(
            event = ev,
            dateLabel = dateLabelOf(ev),
            onDismiss = { detailEvent = null },
            onEdit = {
                detailEvent = null
                editEvent = ev
                editEventDateLabel = dateLabelOf(ev)
            },
            onDelete = { vm.deleteEvent(ev); detailEvent = null }
        )
    }

    editEvent?.let { ev ->
        EventEditDialog(
            dateLabel = editEventDateLabel,
            initial = ev,
            isNew = ev.id == 0L,
            onDismiss = { editEvent = null },
            onConfirm = { event ->
                if (event.id == 0L) vm.addEvent(event) else vm.updateEvent(event)
                editEvent = null
            }
        )
    }
}

private fun dateLabelOf(date: LocalDate): String =
    "${date.monthValue}.${date.dayOfMonth} 周" + "一二三四五六日"[date.dayOfWeek.value - 1]

/** 单独成一个无作用域接收者的 Composable，避免与 ColumnScope/RowScope 的 AnimatedVisibility 冲突 */
@Composable
private fun BackToTodayChip(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        GlassChip("回到本周", onClick = onClick)
    }
}

private fun dateLabelOf(event: EventEntity): String =
    runCatching { dateLabelOf(LocalDate.parse(event.date)) }.getOrDefault(event.date)

private fun weekLabel(termStart: Long?, week: Int): String {
    if (termStart == null) return ""
    val dates = WeekCalculator.weekDates(termStart, week)
    return "${WeekCalculator.formatDate(dates.first())} - ${WeekCalculator.formatDate(dates.last())}"
}

@Composable
private fun HorizontalLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    )
}

/** 左侧时间栏：整点标签，与右侧整点分割线对齐 */
@Composable
private fun TimeColumn(timeline: Timeline) {
    val density = LocalDensity.current
    Box(Modifier.width(TIME_COL_WIDTH).height(timeline.height)) {
        timeline.hourMarks.forEach { minute ->
            val y = timeline.y(minute)
            Text(
                EventTime.format(minute),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .offset(y = y - 6.dp)
                    .padding(start = 2.dp)
            )
        }
    }
}

/** 一天的课程列：整点分割线；长按建课，长按后滑动按半小时选择日程 */
@Composable
private fun RowScope.DayColumn(
    day: Int,
    week: Int,
    date: LocalDate?,
    timeline: Timeline,
    nodeTimes: List<Pair<String, String>>,
    courses: List<CourseEntity>,
    events: List<EventEntity>,
    showOtherWeek: Boolean,
    onCourseClick: (CourseEntity) -> Unit,
    onEventClick: (EventEntity) -> Unit,
    onQuickAddCourse: (day: Int, node: Int) -> Unit,
    onAddEvent: (date: LocalDate, startMinute: Int, endMinute: Int) -> Unit,
) {
    val density = LocalDensity.current
    val hourHeightPx = with(density) { HOUR_HEIGHT.toPx() }
    val haptics = LocalHapticFeedback.current
    val inWeek = courses.filter {
        it.day == day && WeekCalculator.parseWeeks(it.weeks).contains(week)
    }
    val otherWeek = courses.filter {
        it.day == day && !WeekCalculator.parseWeeks(it.weeks).contains(week)
    }
    val dateKey = date?.toString()
    val dayEvents = remember(events, dateKey) {
        if (dateKey == null) emptyList() else events.filter { it.date == dateKey }.sortedBy { it.startMinute }
    }

    // 滑动选择（半小时吸附）
    var selStartMin by remember { mutableIntStateOf(-1) }
    var selEndMin by remember { mutableIntStateOf(-1) }
    var selNode by remember { mutableIntStateOf(1) }
    var moved by remember { mutableStateOf(false) }

    fun minuteAtSnapped(yPx: Float): Int =
        Timeline.snap30(timeline.minuteAt(yPx, hourHeightPx))

    Box(
        Modifier
            .weight(1f)
            .height(timeline.height)
            .pointerInput(day, dateKey, timeline) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val m = minuteAtSnapped(offset.y)
                        selStartMin = m
                        selEndMin = m
                        // 长按建课时用坐标反推节次（按作息逐节查找）
                        selNode = nodeAtMinutes(m, nodeTimes)
                        moved = false
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (abs(dragAmount.x) + abs(dragAmount.y) > 1f) moved = true
                        selEndMin = minuteAtSnapped(change.position.y)
                    },
                    onDragEnd = {
                        val s = selStartMin
                        val e = selEndMin
                        val didMove = moved
                        val pressedNode = selNode
                        selStartMin = -1
                        selEndMin = -1
                        moved = false
                        if (s >= 0 && e >= 0) {
                            if (!didMove) {
                                onQuickAddCourse(day, pressedNode)
                            } else if (date != null) {
                                val lo = minOf(s, e)
                                val hi = maxOf(s, e)
                                onAddEvent(date, lo, if (hi > lo) hi else lo + 30)
                            }
                        }
                    },
                    onDragCancel = {
                        selStartMin = -1
                        selEndMin = -1
                        moved = false
                    }
                )
            }
    ) {
        // 整点分割线（与左侧时间标签对齐）
        timeline.hourMarks.forEach { minute ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = timeline.y(minute))
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            )
        }

        // 非本周课程先画（淡化），本周课程后画，避免淡化卡盖住本周卡
        if (showOtherWeek) {
            otherWeek.forEach { c ->
                CourseCard(
                    course = c,
                    startMinute = nodeStartMinute(c.startNode, nodeTimes),
                    endMinute = nodeEndMinute(c.endNode, nodeTimes),
                    timeline = timeline,
                    dimmed = true,
                    onClick = onCourseClick,
                )
            }
        }
        inWeek.forEach { c ->
            CourseCard(
                course = c,
                startMinute = nodeStartMinute(c.startNode, nodeTimes),
                endMinute = nodeEndMinute(c.endNode, nodeTimes),
                timeline = timeline,
                dimmed = false,
                onClick = onCourseClick,
            )
        }
        // 一次性日程（虚线玻璃卡，仅在所属日期显示）
        dayEvents.forEach { ev -> EventCard(ev, timeline, onClick = onEventClick) }

        // 滑动选择浮层（半小时吸附）
        if (selStartMin >= 0) {
            val e = if (selEndMin >= 0) selEndMin else selStartMin
            val top = timeline.y(minOf(selStartMin, e))
            val bottom = timeline.y(maxOf(selStartMin, e))
            val h = (bottom - top).coerceAtLeast(18.dp)
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .offset(y = top)
                    .fillMaxWidth()
                    .height(h)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), shape)
            )
        }
    }
}

/** 由分钟反推所在节次（用于长按建课），找不到时按兜底网格推算 */
private fun nodeAtMinutes(minute: Int, nodeTimes: List<Pair<String, String>>): Int {
    nodeTimes.forEachIndexed { i, (s, e) ->
        val start = EventTime.parseMinutes(s) ?: return@forEachIndexed
        val end = EventTime.parseMinutes(e) ?: return@forEachIndexed
        if (minute in start..end) return i + 1
    }
    val fallback = ((minute - 8 * 60) / 45) + 1
    return fallback.coerceIn(1, maxOf(nodeTimes.size, 1))
}

/** 课程卡：按真实起止时间定位 */
@Composable
private fun BoxScope.CourseCard(
    course: CourseEntity,
    startMinute: Int,
    endMinute: Int,
    timeline: Timeline,
    dimmed: Boolean,
    onClick: (CourseEntity) -> Unit,
) {
    val palette = CourseColors.get(course.colorIndex)
    val top = timeline.y(startMinute)
    val h = (timeline.y(endMinute) - top).coerceAtLeast(26.dp)
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .offset(y = top + 1.dp)
            .fillMaxWidth()
            .padding(horizontal = 1.dp)
            .height(h - 2.dp)
            .alpha(if (dimmed) 0.5f else 1f)
            .pressScale(interaction, pressedScale = 0.95f)
            .glassSurface(
                shape = RoundedCornerShape(14.dp),
                elevation = 2.dp,
                tint = palette.container.copy(alpha = 0.92f),
            )
            .clickable(interactionSource = interaction, indication = null) { onClick(course) }
            .padding(5.dp)
    ) {
        Column {
            Text(
                course.name,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = palette.content,
                fontWeight = FontWeight.Medium
            )
            if (course.room.isNotBlank()) {
                Text(
                    "@${course.room}",
                    fontSize = 9.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = palette.content.copy(alpha = 0.8f)
                )
            }
            if (dimmed) {
                Text("(非本周)", fontSize = 8.sp, color = palette.content.copy(alpha = 0.7f))
            }
        }
    }
}

/** 一次性日程卡片：虚线描边 + 事件图标 + 起止时间 */
@Composable
private fun BoxScope.EventCard(
    event: EventEntity,
    timeline: Timeline,
    onClick: (EventEntity) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val top = timeline.y(event.startMinute)
    val h = (timeline.y(event.endMinute) - top).coerceAtLeast(24.dp)
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .offset(y = top + 1.dp)
            .fillMaxWidth()
            .padding(horizontal = 1.dp)
            .height(h - 2.dp)
            .pressScale(interaction, pressedScale = 0.95f)
            .clip(shape)
            .background(primary.copy(alpha = 0.10f))
            .drawBehind {
                drawRoundRect(
                    color = primary.copy(alpha = 0.55f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(10f, 7f)
                        )
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
                )
            }
            .clickable(interactionSource = interaction, indication = null) { onClick(event) }
            .padding(5.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Event,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    event.title,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                "${EventTime.format(event.startMinute)}-${EventTime.format(event.endMinute)}",
                fontSize = 8.sp,
                lineHeight = 10.sp,
                color = primary.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(start = 14.dp)
            )
            if (event.location.isNotBlank()) {
                Text(
                    "@${event.location}",
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = primary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 14.dp)
                )
            }
        }
    }
}
