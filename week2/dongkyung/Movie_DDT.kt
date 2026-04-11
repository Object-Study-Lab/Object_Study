class Movie(
    val title: String,
    val runningTime: Duration,
    val fee: Money,
    initialConditions: List<DiscountCondition>,

    val movieType: MovieType,
    val discountAmount: Money,
    val discountPercent: Double
) {
    private val _discountConditions: MutableList<DiscountCondition> = initialConditions.toMutableList()

    val discountConditions: List<DiscountCondition>
        get() = _discountConditions


    fun addDiscountCondition(condition: DiscountCondition) {
        _discountConditions.add(condition)
    }

    fun removeDiscountCondition(condition: DiscountCondition) {
        _discountConditions.remove(condition)
    }
}

class DiscountCondition(
    val type: DiscountConditionType,
    val sequence: Int,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime
)

class Screening(
    val movie: Movie,
    val sequence: Int,
    val whenScreened: LocalDateTime
)

class Customer(
    private val name: String,
    private val id: String,
)

class Reservation(
    private val customer: Customer,
    private val screening: Screening,
    private val fee: Money,
    private val audienceCount: Int
)

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
enum class DiscountConditionType {
    SEQUENCE,
    PERIOD
}

enum class MovieType {
    AMOUNT_DISCOUNT,
    PERCENT_DISCOUNT,
    NONE_DISCOUNT;
}

class ReservationAgency {

    fun reserve(screening: Screening, customer: Customer, audienceCount: Int): Reservation {
        val movie = screening.movie

        var discountable = false

        for (condition in movie.discountConditions) {
            if (condition.type == DiscountConditionType.PERIOD) {
                // 비교 로직
            } else {
                discountable = condition.sequence == screening.sequence
            }

            if (discountable) break
        }
        if (discountable) {
            val discountAmount = when (movie.movieType) {
                AMOUNT_DISCOUNT -> movie.discountAmount
                PERCENT_DISCOUNT -> movie.fee.times(movie.discountPercent)
                NONE_DISCOUNT -> Money.ZERO
            }
            val fee = movie.fee.minus(discountAmount)
            return Reservation(customer, screening, fee, audienceCount)
        } else {
            val fee = movie.fee
            return Reservation(customer, screening, fee, audienceCount)
        }
    }
}