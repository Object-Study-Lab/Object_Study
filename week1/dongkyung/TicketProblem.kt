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
    fun hasInvitation(): Boolean {
        return invitation != null
    }

    fun hasTicket(): Boolean {
        return ticket != null
    }

    fun setTicket(ticket: Ticket) {
        this.ticket = ticket
    }

    fun minusAmount(amount: Long) {
        this.amount -= amount
    }

    fun plusAmount(amount: Long) {
        this.amount += amount
    }
}

class Audience(
    private val bag: Bag
) {
    fun buy(ticket: Ticket): Long {
        if (bag.hasInvitation()) {
            bag.setTicket(ticket)
            return 0L
        } else {
            bag.setTicket(ticket)
            bag.minusAmount(ticket.getFee())
            return ticket.getFee()
        }
    }
}

class TicketOffice(
    private var amount: Long,
    private val tickets: MutableList<Ticket>
) {
    fun getTicket(): Ticket {
        return tickets.removeAt(0)
    }

    fun minusAmount(amount: Long) {
        this.amount -= amount
    }

    fun plusAmount(amount: Long) {
        this.amount += amount
    }
}

class TicketSeller(
    private val ticketOffice: TicketOffice
) {
    fun sellTo(audience: Audience) {
        ticketOffice.plusAmount(audience.buy(ticketOffice.getTicket()))
    }
}

class Theater(
    private val ticketSeller: TicketSeller
) {
    fun enter(audience: Audience) {
        ticketSeller.sellTo(audience)
    }
}
