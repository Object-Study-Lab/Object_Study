/**
 * amount, ticket 생성값 필수
 */
class Bag(
    private var amount: Long,
    private var ticket: Ticket?,
    private var invitation: Invitation? = null,
) {

    fun hold(ticket: Ticket): Long {
        if (hasInvitation()) {
            setTicket(ticket)
            return 0L
        } else {
            setTicket(ticket)
            minusAmount(ticket.getFee())
            return ticket.getFee()
        }
    }

    private fun hasInvitation(): Boolean {
        return invitation != null
    }

    private fun setTicket(ticket: Ticket) {
        this.ticket = ticket
    }

    private fun minusAmount(amount: Long) {
        this.amount -= amount
    }
}