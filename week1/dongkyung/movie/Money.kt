/**
 * 변수가 하나인데 굳이 클래스를 만들어야 하나? 생각이 들었음
 * Long, Double Type 만으로 이게 돈을 의미하는지 모호함
 * Money 라는 좀 더 명시적이고 직관적인 클래스 생성
 * -> 전체적인 설계의 명확성고 유연성을 높이는 첫걸음
 */
class Money private constructor(
    private val amount: BigDecimal
) {
    companion object {
        val ZERO = wons(0L)

        fun wons(amount: Long): Money {
            return Money(BigDecimal.valueOf(amount))
        }

        fun wons(amount: Double): Money {
            return Money(BigDecimal.valueOf(amount))
        }
    }

    operator fun plus(other: Money): Money {
        return Money(amount + other.amount)
    }

    operator fun minus(other: Money): Money {
        return Money(amount - other.amount)
    }

    operator fun times(percent: Double): Money {
        return Money(amount * BigDecimal.valueOf(percent))
    }

    operator fun times(count: Int): Money {
        return Money(amount * BigDecimal.valueOf(count.toLong()))
    }

    fun isLessThan(other: Money): Boolean {
        return amount < other.amount
    }

    fun isGreaterThanOrEqual(other: Money): Boolean {
        return amount >= other.amount
    }
}