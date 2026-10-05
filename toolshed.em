model "toolshed"

persona Neighbor
persona Shed Manager

context Tools
context Loans
context Holds
context Neighbors

type LateReturn { loanId: LoanId, toolName: Text, dueDate: Date, returnedOn: Date, flaggedAt: Instant }

# Timeline order, left to right. Human-triggered State Change slices are ui -> command -> event.
# Read models repeat right after the event that feeds them.

slice "Add Tool" {
  ui Tool Inventory @Shed Manager
  command Add Tool { toolId: ToolId, name: Text, description: Text, defaultCheckoutPeriodDays: Int } note "slices/add-tool.md"
  event Tool Added { toolId: ToolId, name: Text, description: Text, defaultCheckoutPeriodDays: Int, addedAt: Instant assigned } @Tools
}
slice "Tool Inventory" {
  view Tool Inventory from "Tool Added" { toolId: ToolId, name: Text, description: Text, defaultCheckoutPeriodDays: Int, addedAt: Instant, onLoan: Bool derived, loanId: LoanId derived, borrowerId: NeighborId derived, borrowerName: Text derived, dueDate: Date derived, retired: Bool derived, canRetire: Bool derived, canMarkLost: Bool derived } note "slices/tool-inventory.md"
  ui Tool Inventory @Shed Manager
}
slice "Tool Catalog" {
  view Tool Catalog from "Tool Added" { toolId: ToolId, name: Text, description: Text, defaultCheckoutPeriodDays: Int, addedAt: Instant, status: Text derived, dueDate: Date derived, holdsWaiting: Int derived, canCheckOut: Bool derived, canPlaceHold: Bool derived } note "slices/tool-catalog.md"
  ui Tool Catalog @Neighbor
}

slice "Register Neighbor" {
  ui Sign Up @Neighbor
  command Register Neighbor { neighborId: NeighborId, name: Text, email: Text } note "slices/register-neighbor.md"
  event Neighbor Registered { neighborId: NeighborId, name: Text, email: Text, registeredAt: Instant assigned } @Neighbors
}
slice "Tool Inventory — neighbor registered" {
  view Tool Inventory again from "Neighbor Registered"
  ui Tool Inventory @Shed Manager
}
slice "Neighbors" {
  view Neighbors from "Neighbor Registered" { neighborId: NeighborId, name: Text, email: Text, registeredAt: Instant, status: Text derived, canSuspend: Bool derived } note "slices/neighbors.md"
  ui Neighbors @Shed Manager
}
slice "My Account" {
  view My Account from "Neighbor Registered" { neighborId: NeighborId, name: Text, email: Text, registeredAt: Instant, status: Text derived, canChangeEmail: Bool derived, canCloseAccount: Bool derived } note "slices/my-account.md"
  ui My Account @Neighbor
}

slice "Check Out Tool" {
  ui Tool Catalog @Neighbor
  command Check Out Tool { loanId: LoanId, toolId: ToolId, neighborId: NeighborId, loanPeriodDays: Int } note "slices/check-out-tool.md"
  event Tool Checked Out { loanId: LoanId, toolId: ToolId, neighborId: NeighborId, loanPeriodDays: Int, dueDate: Date assigned, checkedOutAt: Instant assigned } @Loans
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
  view My Loans from "Tool Added", "Tool Checked Out" { loanId: LoanId, neighborId: NeighborId, toolId: ToolId, toolName: Text derived from "Tool Added", checkedOutAt: Instant, dueDate: Date, overdue: Bool derived, overdueNotice: Text derived, holdWaiting: Bool derived, toolRetired: Bool derived, canExtend: Bool derived } note "slices/my-loans.md"
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

slice "Place Hold" {
  ui Tool Catalog @Neighbor
  command Place Hold { holdId: HoldId, toolId: ToolId, neighborId: NeighborId } note "slices/place-hold.md"
  event Hold Placed { holdId: HoldId, toolId: ToolId, neighborId: NeighborId, placedAt: Instant assigned } @Holds
}
slice "My Holds" {
  view My Holds from "Hold Placed", "Tool Checked Out", "Tool Added" { holdId: HoldId, neighborId: NeighborId, toolId: ToolId, toolName: Text derived from "Tool Added", placedAt: Instant, status: Text derived, position: Int derived, collectByDate: Date derived, pickupNotice: Text derived, canCancel: Bool derived } note "slices/my-holds.md"
  ui My Holds @Neighbor
}
slice "Tool Catalog — hold placed" {
  view Tool Catalog again from "Hold Placed"
  ui Tool Catalog @Neighbor
}
slice "My Loans — hold placed" {
  view My Loans again from "Hold Placed"
  ui My Loans @Neighbor
}

slice "Cancel Hold" {
  ui My Holds @Neighbor
  command Cancel Hold { neighborId: NeighborId, holdId: HoldId } note "slices/cancel-hold.md"
  event Hold Cancelled { holdId: HoldId, cancelledAt: Instant assigned } @Holds
}
slice "My Holds — hold cancelled" {
  view My Holds again from "Hold Cancelled"
  ui My Holds @Neighbor
}
slice "Tool Catalog — hold cancelled" {
  view Tool Catalog again from "Hold Cancelled"
  ui Tool Catalog @Neighbor
}
slice "My Loans — hold cancelled" {
  view My Loans again from "Hold Cancelled"
  ui My Loans @Neighbor
}

slice "Overdue Loans To Notify" {
  view Overdue Loans To Notify from "Tool Added", "Neighbor Registered", "Tool Checked Out", "Loan Extended" { loanId: LoanId, toolId: ToolId, neighborId: NeighborId, dueDate: Date, toolName: Text derived from "Tool Added", borrowerName: Text derived from "Neighbor Registered", borrowerEmail: Text derived from "Neighbor Registered", overdue: Bool derived } note "slices/overdue-loans-to-notify.md"
}

# Internally triggered translation: the gateway watches the to-do view and hands notices to the provider.
slice "Send Overdue Notice" {
  translation Overdue Notice Gateway from "Overdue Loans To Notify" note "slices/send-overdue-notice.md"
  command Send Overdue Notice { loanId: LoanId, sentTo: Text, borrowerName: Text, toolName: Text, dueDate: Date }
  event Overdue Notice Sent { loanId: LoanId, sentTo: Text, borrowerName: Text, toolName: Text, dueDate: Date, sentAt: Instant assigned } loops-to "Overdue Loans To Notify" @Loans
}
slice "My Loans — overdue notice sent" {
  view My Loans again from "Overdue Notice Sent"
  ui My Loans @Neighbor
}

slice "Record Overdue Notice Failure" {
  translation Email Provider Failure Report note "slices/record-overdue-notice-failure.md"
  command Record Overdue Notice Failure { loanId: LoanId, reason: Text }
  event Overdue Notice Failed { loanId: LoanId, reason: Text, failedAt: Instant assigned } @Loans
}
slice "My Loans — overdue notice failed" {
  view My Loans again from "Overdue Notice Failed"
  ui My Loans @Neighbor
}
slice "Undelivered Notices" {
  view Undelivered Notices from "Overdue Notice Failed", "Tool Checked Out", "Hold Cancelled", "Neighbor Registered", "Tool Added", "Loan Extended" { kind: Text derived, loanId: LoanId, holdId: HoldId derived, neighborId: NeighborId, neighborName: Text derived from "Neighbor Registered", neighborEmail: Text derived from "Neighbor Registered", toolId: ToolId, toolName: Text derived from "Tool Added", dueDate: Date, collectByDate: Date derived, reason: Text, failedAt: Instant } note "slices/undelivered-notices.md"
  ui Undelivered Notices @Shed Manager
}

slice "Change Email" {
  ui My Account @Neighbor
  command Change Email { neighborId: NeighborId, email: Text } note "slices/change-email.md"
  event Neighbor Email Changed { neighborId: NeighborId, email: Text, changedAt: Instant assigned } loops-to "Overdue Loans To Notify" @Neighbors
}
slice "My Account — email changed" {
  view My Account again from "Neighbor Email Changed"
  ui My Account @Neighbor
}
slice "Neighbors — email changed" {
  view Neighbors again from "Neighbor Email Changed"
  ui Neighbors @Shed Manager
}
slice "Undelivered Notices — email changed" {
  view Undelivered Notices again from "Neighbor Email Changed"
  ui Undelivered Notices @Shed Manager
}

slice "Return Tool" {
  ui My Loans @Neighbor
  command Return Tool { neighborId: NeighborId, loanId: LoanId } note "slices/return-tool.md"
  event Tool Returned { loanId: LoanId, toolId: ToolId assigned, returnedAt: Instant assigned, returnedOn: Date assigned, late: Bool assigned } loops-to "Overdue Loans To Notify" @Loans
}
slice "Undelivered Notices — tool returned" {
  view Undelivered Notices again from "Tool Returned"
  ui Undelivered Notices @Shed Manager
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

slice "Holds To Fulfill" {
  view Holds To Fulfill from "Hold Placed", "Tool Returned", "Hold Cancelled", "Tool Checked Out" { toolId: ToolId, holdId: HoldId derived, neighborId: NeighborId derived } note "slices/holds-to-fulfill.md"
}

slice "Fulfill Hold" {
  processor Hold Fulfiller from "Holds To Fulfill" note "slices/fulfill-hold.md"
  command Fulfill Hold { holdId: HoldId }
  event Hold Fulfilled { holdId: HoldId, toolId: ToolId assigned, neighborId: NeighborId assigned, collectByDate: Date assigned, fulfilledAt: Instant assigned } loops-to "Holds To Fulfill" @Holds
}
slice "Undelivered Notices — hold fulfilled" {
  view Undelivered Notices again from "Hold Fulfilled"
  ui Undelivered Notices @Shed Manager
}
slice "Tool Catalog — hold fulfilled" {
  view Tool Catalog again from "Hold Fulfilled"
  ui Tool Catalog @Neighbor
}
slice "My Holds — hold fulfilled" {
  view My Holds again from "Hold Fulfilled"
  ui My Holds @Neighbor
}

slice "Pickup Notices To Send" {
  view Pickup Notices To Send from "Hold Fulfilled", "Neighbor Registered", "Tool Added", "Hold Cancelled", "Tool Checked Out", "Neighbor Email Changed" { holdId: HoldId, toolId: ToolId, neighborId: NeighborId, collectByDate: Date, holderName: Text derived from "Neighbor Registered", holderEmail: Text derived from "Neighbor Registered", "Neighbor Email Changed", toolName: Text derived from "Tool Added" } note "slices/pickup-notices-to-send.md"
}

slice "Send Pickup Notice" {
  translation Pickup Notice Gateway from "Pickup Notices To Send" note "slices/send-pickup-notice.md"
  command Send Pickup Notice { holdId: HoldId, sentTo: Text, holderName: Text, toolName: Text, collectByDate: Date }
  event Pickup Notice Sent { holdId: HoldId, sentTo: Text, holderName: Text, toolName: Text, collectByDate: Date, sentAt: Instant assigned } loops-to "Pickup Notices To Send" @Holds
}
slice "My Holds — pickup notice sent" {
  view My Holds again from "Pickup Notice Sent"
  ui My Holds @Neighbor
}

slice "Record Pickup Notice Failure" {
  translation Pickup Email Failure Report note "slices/record-pickup-notice-failure.md"
  command Record Pickup Notice Failure { holdId: HoldId, reason: Text }
  event Pickup Notice Failed { holdId: HoldId, reason: Text, failedAt: Instant assigned } @Holds
}
slice "My Holds — pickup notice failed" {
  view My Holds again from "Pickup Notice Failed"
  ui My Holds @Neighbor
}
slice "Undelivered Notices — pickup notice failed" {
  view Undelivered Notices again from "Pickup Notice Failed"
  ui Undelivered Notices @Shed Manager
}

slice "Pickups To Expire" {
  view Pickups To Expire from "Hold Fulfilled", "Tool Checked Out", "Hold Cancelled" { holdId: HoldId, toolId: ToolId, neighborId: NeighborId, collectByDate: Date, dueToExpire: Bool derived } note "slices/pickups-to-expire.md"
}

slice "Expire Hold" {
  processor Pickup Expirer from "Pickups To Expire" note "slices/expire-hold.md"
  command Expire Hold { holdId: HoldId }
  event Hold Expired { holdId: HoldId, expiredAt: Instant assigned } loops-to "Holds To Fulfill", "Pickups To Expire", "Pickup Notices To Send" @Holds
}
slice "My Loans — hold expired" {
  view My Loans again from "Hold Expired"
  ui My Loans @Neighbor
}
slice "My Holds — hold expired" {
  view My Holds again from "Hold Expired"
  ui My Holds @Neighbor
}
slice "Tool Catalog — hold expired" {
  view Tool Catalog again from "Hold Expired"
  ui Tool Catalog @Neighbor
}
slice "Undelivered Notices — hold expired" {
  view Undelivered Notices again from "Hold Expired"
  ui Undelivered Notices @Shed Manager
}

slice "Neighbors To Flag" {
  view Neighbors To Flag from "Tool Returned", "Tool Checked Out" { neighborId: NeighborId, loanId: LoanId, returnedOn: Date, inWindow: Bool derived } note "slices/neighbors-to-flag.md"
}

slice "Flag Neighbor" {
  processor Late Return Flagger from "Neighbors To Flag" note "slices/flag-neighbor.md"
  command Flag Neighbor { neighborId: NeighborId, loanIds: LoanId[] }
  event Neighbor Flagged { neighborId: NeighborId, loanIds: LoanId[], flaggedAt: Instant assigned } loops-to "Neighbors To Flag" @Neighbors
}
slice "Flagged Neighbors" {
  view Flagged Neighbors from "Neighbor Flagged", "Neighbor Registered", "Tool Added", "Tool Checked Out", "Loan Extended", "Tool Returned", "Neighbor Email Changed" { neighborId: NeighborId, name: Text, email: Text, flagCount: Int derived, latestFlaggedAt: Instant derived from "Neighbor Flagged", lateReturns: LateReturn[] derived } note "slices/flagged-neighbors.md"
  ui Flagged Neighbors @Shed Manager
}

slice "Suspend Neighbor" {
  ui Flagged Neighbors @Shed Manager
  command Suspend Neighbor { neighborId: NeighborId } note "slices/suspend-neighbor.md"
  event Neighbor Suspended { neighborId: NeighborId, suspendedAt: Instant assigned } loops-to "Neighbors To Flag" @Neighbors
}
slice "Flagged Neighbors — neighbor suspended" {
  view Flagged Neighbors again from "Neighbor Suspended"
  ui Flagged Neighbors @Shed Manager
}
slice "Suspended Neighbors" {
  view Suspended Neighbors from "Neighbor Suspended", "Neighbor Registered", "Neighbor Email Changed" { neighborId: NeighborId, name: Text, email: Text, suspendedAt: Instant } note "slices/suspended-neighbors.md"
  ui Suspended Neighbors @Shed Manager
}
slice "Neighbors — neighbor suspended" {
  view Neighbors again from "Neighbor Suspended"
  ui Neighbors @Shed Manager
}
slice "My Account — neighbor suspended" {
  view My Account again from "Neighbor Suspended"
  ui My Account @Neighbor
}

slice "Reinstate Neighbor" {
  ui Suspended Neighbors @Shed Manager
  command Reinstate Neighbor { neighborId: NeighborId } note "slices/reinstate-neighbor.md"
  event Neighbor Reinstated { neighborId: NeighborId, reinstatedAt: Instant assigned } loops-to "Neighbors To Flag" @Neighbors
}
slice "Suspended Neighbors — neighbor reinstated" {
  view Suspended Neighbors again from "Neighbor Reinstated"
  ui Suspended Neighbors @Shed Manager
}
slice "Neighbors — neighbor reinstated" {
  view Neighbors again from "Neighbor Reinstated"
  ui Neighbors @Shed Manager
}
slice "My Account — neighbor reinstated" {
  view My Account again from "Neighbor Reinstated"
  ui My Account @Neighbor
}

slice "Retire Tool" {
  ui Tool Inventory @Shed Manager
  command Retire Tool { toolId: ToolId } note "slices/retire-tool.md"
  event Tool Retired { toolId: ToolId, retiredAt: Instant assigned } loops-to "Holds To Fulfill", "Pickups To Expire", "Pickup Notices To Send" @Tools
}
slice "Tool Inventory — tool retired" {
  view Tool Inventory again from "Tool Retired"
  ui Tool Inventory @Shed Manager
}
slice "Tool Catalog — tool retired" {
  view Tool Catalog again from "Tool Retired"
  ui Tool Catalog @Neighbor
}
slice "My Holds — tool retired" {
  view My Holds again from "Tool Retired"
  ui My Holds @Neighbor
}
slice "My Loans — tool retired" {
  view My Loans again from "Tool Retired"
  ui My Loans @Neighbor
}
slice "Undelivered Notices — tool retired" {
  view Undelivered Notices again from "Tool Retired"
  ui Undelivered Notices @Shed Manager
}

slice "Record Tool Lost" {
  ui Tool Inventory @Shed Manager
  command Record Tool Lost note "slices/record-tool-lost.md"
  event Tool Lost loops-to "Overdue Loans To Notify", "Holds To Fulfill" @Tools
}
slice "Tool Inventory — tool lost" {
  view Tool Inventory again from "Tool Lost"
  ui Tool Inventory @Shed Manager
}
slice "Tool Catalog — tool lost" {
  view Tool Catalog again from "Tool Lost"
  ui Tool Catalog @Neighbor
}
slice "My Loans — tool lost" {
  view My Loans again from "Tool Lost"
  ui My Loans @Neighbor
}
slice "My Holds — tool lost" {
  view My Holds again from "Tool Lost"
  ui My Holds @Neighbor
}
slice "Undelivered Notices — tool lost" {
  view Undelivered Notices again from "Tool Lost"
  ui Undelivered Notices @Shed Manager
}

slice "Close Account" {
  ui My Account @Neighbor
  command Close Account note "slices/close-account.md"
  event Neighbor Account Closed loops-to "Holds To Fulfill", "Pickups To Expire", "Pickup Notices To Send", "Neighbors To Flag" @Neighbors
}
slice "Flagged Neighbors — account closed" {
  view Flagged Neighbors again from "Neighbor Account Closed"
  ui Flagged Neighbors @Shed Manager
}
slice "Suspended Neighbors — account closed" {
  view Suspended Neighbors again from "Neighbor Account Closed"
  ui Suspended Neighbors @Shed Manager
}
slice "My Account — account closed" {
  view My Account again from "Neighbor Account Closed"
  ui My Account @Neighbor
}
slice "Neighbors — account closed" {
  view Neighbors again from "Neighbor Account Closed"
  ui Neighbors @Shed Manager
}
slice "My Holds — account closed" {
  view My Holds again from "Neighbor Account Closed"
  ui My Holds @Neighbor
}
slice "Tool Catalog — account closed" {
  view Tool Catalog again from "Neighbor Account Closed"
  ui Tool Catalog @Neighbor
}
slice "My Loans — account closed" {
  view My Loans again from "Neighbor Account Closed"
  ui My Loans @Neighbor
}
slice "Undelivered Notices — account closed" {
  view Undelivered Notices again from "Neighbor Account Closed"
  ui Undelivered Notices @Shed Manager
}
