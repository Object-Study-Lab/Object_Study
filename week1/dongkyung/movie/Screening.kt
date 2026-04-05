class Screening(
    val movie: Movie,
    val sequence: Int,
    val startTime: LocalDateTime,
) {

    val fee: Money
        get() = movie.fee

    fun isSequence(sequence: Int): Boolean {
        return this.sequence == sequence
    }

    fun reserve(customer: Customer, audienceCount: Int): Reservation {
        return Reservation(customer, this, calculateFee(audienceCount), audienceCount)
    }

    /**
     * Screening이 Movie 에게 calculateMovieFee 메시지를 전송한다
     * 메시지 (Message) - 무엇을 해라
     * 메서드 (Method) - 어떻게 할 것인가
     */
    private fun calculateFee(audienceCount: Int): Money {
        return movie.calculateMovieFee(this).times(audienceCount)
    }
}
