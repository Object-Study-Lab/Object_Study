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
    private val title: String,
    private val runningTime: Duration,
    private val fee: Money,
    private val movieType: MovieType,
    private val discountPolicy: DiscountPolicy
) {
    fun calculateMovieFee(screening: Screening): Money {
        return fee.minus(discountPolicy.calculateDiscountAmount(screening))
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

class AmountDiscountPolicy(
    private val discountAmount: Money,
    vararg conditions: DiscountCondition
) : DefaultDiscountPolicy(*conditions) {
    override fun getDiscountAmount(screening: Screening): Money {
        return discountAmount
    }
}

class PeriodCondition(
    private val dayOfWeek: DayOfWeek,
    private val startTime: LocalTime,
    private val endTime: LocalTime,
) : DiscountCondition {
    override fun isSatisfiedBy(screening: Screening): Boolean {
        return dayOfWeek == screening.whenScreened.dayOfWeek &&
                startTime <= screening.whenScreened.toLocalTime() &&
                endTime.isAfter(screening.whenScreened.toLocalTime())
    }
}

interface DiscountPolicy {
    fun calculateDiscountAmount(screening: Screening): Money
}

class NoneDiscountPolicy : DiscountPolicy {
    override fun calculateDiscountAmount(screening: Screening): Money {
        return Money.ZERO
    }
}

class SequenceCondition(
    private val sequence: Int
) : DiscountCondition {
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