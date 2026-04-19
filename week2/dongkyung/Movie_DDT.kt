class Movie(
    private val title: String,
    private val runningTime: Duration,
    private val fee: Money,
    private val discountConditions: List<DiscountCondition>,

    val movieType: MovieType,
    private val discountAmount: Money,
    private val discountPercent: Double
) {

    fun isDiscountable(whenScreened: LocalDateTime, sequence: Int): Boolean {
        for (condition in discountConditions) {
            if (condition.type == DiscountConditionType.PERIOD) {
                if (condition.isDiscountable(whenScreened.dayOfWeek, whenScreened.toLocalTime())) {
                    return true
                }
            } else {
                if (condition.isDiscountable(sequence)) {
                    return true
                }
            }
        }
        return false
    }

    fun calculateAmountDiscountFee(): Money {
        if (movieType != MovieType.AMOUNT_DISCOUNT) {
            throw IllegalArgumentException()
        }
        return fee.minus(discountAmount)
    }

    fun calculatePercentDiscountFee(): Money {
        if (movieType != MovieType.PERCENT_DISCOUNT) {
            throw IllegalArgumentException()
        }
        return fee.minus(fee.times(discountPercent))
    }

    fun calculateNoneDiscountFee(): Money {
        if (movieType != MovieType.NONE_DISCOUNT) {
            throw IllegalArgumentException()
        }
        return fee
    }
}

class DiscountCondition(
    val type: DiscountConditionType,
    val sequence: Int,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime
) {
    fun isDiscountable(dayOfWeek: DayOfWeek, time: LocalTime): Boolean {
        if (type != DiscountConditionType.PERIOD) {
            throw IllegalArgumentException()
        }
        return this.dayOfWeek == dayOfWeek && this.startTime <= time && this.endTime >= time
    }

    fun isDiscountable(sequence: Int): Boolean {
        if (type != DiscountConditionType.SEQUENCE) {
            throw IllegalArgumentException()
        }
        return this.sequence == sequence
    }
}

class Screening(
    val movie: Movie,
    val sequence: Int,
    val whenScreened: LocalDateTime
) {
    fun calculateFee(audienceCount: Int): Money {
        return when (movie.movieType) {
            AMOUNT_DISCOUNT -> {
                movie.calculateAmountDiscountFee().times(audienceCount)
            }
            PERCENT_DISCOUNT -> {
                movie.calculatePercentDiscountFee().times(audienceCount)
            }
            NONE_DISCOUNT -> movie.calculateNoneDiscountFee().times(audienceCount)
        }
    }
}

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
        val fee = screening.calculateFee(audienceCount)
        return Reservation(customer, screening, fee, audienceCount)
    }
}