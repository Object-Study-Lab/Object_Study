class Movie(
    val title: String,
    val runningTime: Duration,
    val fee: Money,
    private val discountPolicy: DiscountPolicy
) {
    fun calculateMovieFee(screening: Screening): Money {
        return fee.minus(discountPolicy.calculateDiscountAmount(screening))
    }
}