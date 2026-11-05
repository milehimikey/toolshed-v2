storyline "Borrow and return a tool"

slice "Check Out Tool" {
  ui Tool Catalog @Neighbor
  command Check Out Tool { loanId: LoanId, toolId: ToolId, neighborId: NeighborId, loanPeriodDays: Int } note "slices/check-out-tool.md"
  event Tool Checked Out { loanId: LoanId, toolId: ToolId, toolName: Text assigned, neighborId: NeighborId, neighborName: Text assigned, neighborEmail: Text assigned, loanPeriodDays: Int, dueDate: Date assigned, checkedOutAt: Instant assigned } @Loans
}
slice "Tool Inventory — tool checked out" {
  view Tool Inventory again from "Tool Checked Out"
  ui Tool Inventory @Shed Manager
}
slice "Tool Catalog — checked out" {
  view Tool Catalog again from "Tool Checked Out"
  ui Tool Catalog @Neighbor
}
slice "My Loans" {
  view My Loans from "Tool Checked Out" { loanId: LoanId, neighborId: NeighborId, toolId: ToolId, toolName: Text, checkedOutAt: Instant, dueDate: Date, overdue: Bool derived, overdueNotice: Text derived, holdWaiting: Bool derived, toolRetired: Bool derived, canExtend: Bool derived } note "slices/my-loans.md"
  ui My Loans @Neighbor
}

slice "Extend Loan" {
  ui My Loans @Neighbor
  command Extend Loan { neighborId: NeighborId, loanId: LoanId, fromDueDate: Date } note "slices/extend-loan.md"
  event Loan Extended { loanId: LoanId, fromDueDate: Date, dueDate: Date assigned, extendedAt: Instant assigned } @Loans
}
slice "Tool Inventory — loan extended" {
  view Tool Inventory again from "Loan Extended"
  ui Tool Inventory @Shed Manager
}
slice "My Loans — loan extended" {
  view My Loans again from "Loan Extended"
  ui My Loans @Neighbor
}
slice "Tool Catalog — loan extended" {
  view Tool Catalog again from "Loan Extended"
  ui Tool Catalog @Neighbor
}

slice "Return Tool" {
  ui My Loans @Neighbor
  command Return Tool { neighborId: NeighborId, loanId: LoanId } note "slices/return-tool.md"
  event Tool Returned { loanId: LoanId, toolId: ToolId assigned, toolName: Text assigned, neighborId: NeighborId assigned, dueDate: Date assigned, returnedAt: Instant assigned, returnedOn: Date assigned, late: Bool assigned } @Loans
}
slice "Tool Inventory — tool returned" {
  view Tool Inventory again from "Tool Returned"
  ui Tool Inventory @Shed Manager
}
slice "Tool Catalog — tool returned" {
  view Tool Catalog again from "Tool Returned"
  ui Tool Catalog @Neighbor
}
slice "My Loans — tool returned" {
  view My Loans again from "Tool Returned"
  ui My Loans @Neighbor
}
