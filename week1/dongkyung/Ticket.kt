class Invitation(
    private val time: Long
)

class Ticket(
    private val fee: Long
) {
    fun getFee(): Long {
        return fee
    }
}

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

class Audience(
    private val bag: Bag
) {
    fun buy(ticket: Ticket): Long {
        return bag.hold(ticket)
    }
}

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

class TicketSeller(
    private val ticketOffice: TicketOffice
) {
    fun sellTo(audience: Audience) {
        ticketOffice.sellTicketTo(audience)
    }
}

class Theater(
    private val ticketSeller: TicketSeller
) {
    fun enter(audience: Audience) {
        ticketSeller.sellTo(audience)
    }
}