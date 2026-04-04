class TicketOffice(
    private var amount: Long,
    private val tickets: MutableList<Ticket>
) {
    fun sellTicketTo(audience: Audience) {
        plusAmount(audience.buy(getTicket()))
    }

    private fun getTicket(): Ticket {
        return tickets.removeAt(0)
    }

    private fun plusAmount(amount: Long) {
        this.amount += amount
    }
}