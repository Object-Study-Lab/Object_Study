interface DiscountPolicy {
    fun calculateDiscountAmount(screening: Screening): Money
}