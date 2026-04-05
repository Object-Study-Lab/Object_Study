class PeriodCondition(
    private val dayOfWeek: DayOfWeek,
    private val startTime: LocalTime,
    private val endTime: LocalTime
) : DiscountCondition {
    override fun isSatisfiedBy(screening: Screening): Boolean {
        val screeningStartTime = screening.startTime
        return screeningStartTime.dayOfWeek == dayOfWeek &&
                screeningStartTime.toLocalTime() in startTime..endTime
    }
}