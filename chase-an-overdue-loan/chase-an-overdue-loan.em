storyline "Chase an overdue loan"

slice "Overdue Loans To Notify" {
  view Overdue Loans To Notify from "Tool Checked Out" updates from "Neighbor Email Changed", "Loan Extended", "Tool Returned" { loanId: LoanId, toolId: ToolId, neighborId: NeighborId, dueDate: Date, toolName: Text, borrowerName: Text derived from "Tool Checked Out", borrowerEmail: Text derived from "Tool Checked Out", "Neighbor Email Changed", overdue: Bool derived } note "slices/overdue-loans-to-notify.md"
}

# Internally triggered translation: the gateway watches the to-do view and hands notices to the provider.
slice "Send Overdue Notice" {
  translation Overdue Notice Gateway from "Overdue Loans To Notify" note "slices/send-overdue-notice.md"
  command Send Overdue Notice { loanId: LoanId, neighborId: NeighborId, sentTo: Text, borrowerName: Text, toolName: Text, dueDate: Date }
  event Overdue Notice Sent { loanId: LoanId, neighborId: NeighborId, sentTo: Text, borrowerName: Text, toolName: Text, dueDate: Date, sentAt: Instant assigned } loops-to "Overdue Loans To Notify" @Loans
}
slice "My Loans — overdue notice sent" {
  view My Loans again from "Overdue Notice Sent"
  ui My Loans @Neighbor
}

slice "Record Overdue Notice Failure" {
  translation Email Provider Failure Report note "slices/record-overdue-notice-failure.md" divergence "No email provider selected yet (constitution Amendment 8): no inbound transport reaches this translation and its verifier fails closed until a provider is chosen; the mapping code is in place (conformance 2026-11-01, rulings 2 and 3)"
  command Record Overdue Notice Failure { loanId: LoanId, reason: Text }
  event Overdue Notice Failed { loanId: LoanId, neighborId: NeighborId assigned, neighborName: Text assigned, neighborEmail: Text assigned, toolName: Text assigned, dueDate: Date assigned, reason: Text, failedAt: Instant assigned } @Loans
}
slice "My Loans — overdue notice failed" {
  view My Loans again from "Overdue Notice Failed"
  ui My Loans @Neighbor
}
slice "Undelivered Notices" {
  view Undelivered Notices from "Overdue Notice Failed" updates from "Neighbor Email Changed", "Loan Extended", "Tool Returned" { kind: Text derived, loanId: LoanId, holdId: HoldId derived, neighborId: NeighborId, neighborName: Text, neighborEmail: Text derived from "Overdue Notice Failed", "Neighbor Email Changed", toolId: ToolId derived, toolName: Text, dueDate: Date, collectByDate: Date derived, reason: Text, failedAt: Instant } note "slices/undelivered-notices.md"
  ui Undelivered Notices @Shed Manager
}
