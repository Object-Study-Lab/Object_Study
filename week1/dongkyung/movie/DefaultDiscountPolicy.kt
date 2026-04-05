abstract class DefaultDiscountPolicy(
    vararg conditions: DiscountCondition
) {
    private val conditions: List<DiscountCondition> = conditions.toList()

    fun calculateDiscountAmount(screening: Screening): Money {
        for (condition in conditions) {
            if (condition.isSatisfiedBy(screening)) {
                return getDiscountAmount(screening)
            }
        }
        return Money.ZERO
    }

    // TEMPLATE METHOD 패턴: 부모 클래스에게 기본적인 알고리즘의 흐름을 구현하고 중간에 필요한 처리를 자식 클래스에게 위임
    protected abstract fun getDiscountAmount(screening: Screening): Money
}