package org.example

import org.example.MovieType.*
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class Screening(
    private val movie: Movie,
    val sequence: Int,
    val whenScreened: LocalDateTime
) {


    fun reserve(customer: Customer, audienceCount: Int): Reservation {
        return Reservation(customer, this, calculateFee(audienceCount), audienceCount)
    }

    private fun calculateFee(audienceCount: Int): Money {
        return movie.calculateMovieFee(this)
    }

    fun getSequence(): Int {
        return sequence
    }
}

class Movie(
    private val discountConditions: List<DiscountCondition>,
    private val title: String,
    private val runningTime: Duration,
    private val fee: Money,

    private val movieType: MovieType,
    private val discountAmount: Money,
    private val discountPercent: Double
) {
    fun calculateMovieFee(screening: Screening): Money {
        if (isDiscountable(screening)) {
            return fee.minus(calculateDiscountAmount())
        }
        return fee
    }

    private fun isDiscountable(screening: Screening): Boolean {
        return discountConditions.any { condition -> condition.isSatisfiedBy(screening) }
    }

    private fun calculateDiscountAmount(): Money {
        return when (movieType) {
            AMOUNT_DISCOUNT -> calculateAmountDiscountAmount()
            PERCENT_DISCOUNT -> calculatePercentDiscountAmount()
            NONE_DISCOUNT -> calculateNoneDiscountAmount()
        }
    }

    private fun calculateAmountDiscountAmount(): Money {
        return discountAmount
    }

    private fun calculatePercentDiscountAmount(): Money {
        return fee.times(discountPercent)
    }

    private fun calculateNoneDiscountAmount(): Money {
        return Money.ZERO
    }
}

enum class MovieType {
    AMOUNT_DISCOUNT,
    PERCENT_DISCOUNT,
    NONE_DISCOUNT
}

enum class DiscountConditionType {
    SEQUENCE,
    PERIOD
}

interface DiscountCondition {
    fun isSatisfiedBy(screening: Screening): Boolean
}

class PeriodCondition(
    private val dayOfWeek: DayOfWeek,
    private val startTime: LocalTime,
    private val endTime: LocalTime,
): DiscountCondition {
    override fun isSatisfiedBy(screening: Screening): Boolean {
        return dayOfWeek == screening.whenScreened.dayOfWeek &&
                startTime <= screening.whenScreened.toLocalTime() &&
                endTime.isAfter(screening.whenScreened.toLocalTime())
    }
}

class SequenceCondition(
    private val sequence: Int
): DiscountCondition {
    override fun isSatisfiedBy(screening: Screening): Boolean {
        return sequence == screening.sequence
    }
}

class Customer {

}

@JvmInline
value class Money(
    val amount: Double
) {
    operator fun times(amount: Double): Money {
        return Money(this.amount * amount)
    }

    operator fun minus(money: Money): Money {
        return Money(this.amount - money.amount)
    }

    companion object {
        val ZERO = Money(0.0)
    }
}

class Reservation(
    private val customer: Customer,
    private val screening: Screening,
    private val money: Money,
    private val audienceCount: Int
)