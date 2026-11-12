model "toolshed"

persona Neighbor
persona Shed Manager

context Tools
context Loans
context Holds
context Neighbors

type LateReturnFact { loanId: LoanId, toolName: Text, dueDate: Date, returnedOn: Date }
type LateReturn { loanId: LoanId, toolName: Text, dueDate: Date, returnedOn: Date, flaggedAt: Instant }

# Timeline order, left to right, grouped into storylines: the main journey (Borrow and return a
# tool) and the chapters around it. Human-triggered State Change slices are ui -> command -> event.
# Read models repeat right after the event that feeds them.

storyline "Stock the shed"

storyline "Join the shed"

storyline "Borrow and return a tool"

storyline "Chase an overdue loan" after "Borrow and return a tool"

storyline "Hold a tool for pickup" after "Borrow and return a tool"

storyline "Keep late returners in check" after "Borrow and return a tool"

storyline "Take a tool or a neighbor out of the shed"

storyline "Keep tools in working order" after "Take a tool or a neighbor out of the shed"
