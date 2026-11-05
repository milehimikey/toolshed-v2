storyline "Hold a tool for pickup"

slice "Place Hold" {
  ui Tool Catalog @Neighbor
  command Place Hold { holdId: HoldId, toolId: ToolId, neighborId: NeighborId } note "slices/place-hold.md"
  event Hold Placed { holdId: HoldId, toolId: ToolId, toolName: Text assigned, neighborId: NeighborId, neighborName: Text assigned, neighborEmail: Text assigned, placedAt: Instant assigned } @Holds
}
slice "My Holds" {
  view My Holds from "Hold Placed" { holdId: HoldId, neighborId: NeighborId, toolId: ToolId, toolName: Text, placedAt: Instant, status: Text derived, position: Int derived, collectByDate: Date derived, pickupNotice: Text derived, canCancel: Bool derived } note "slices/my-holds.md"
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

slice "Holds To Collect" {
  view Holds To Collect from "Hold Placed", "Tool Checked Out" { holdId: HoldId, toolId: ToolId, neighborId: NeighborId, loanId: LoanId derived from "Tool Checked Out" } note "slices/holds-to-collect.md"
}

# The holder taking the tool out is an ordinary checkout; the collector then closes their hold.
slice "Collect Hold" {
  processor Hold Collector from "Holds To Collect" note "slices/collect-hold.md"
  command Collect Hold { holdId: HoldId, loanId: LoanId }
  event Hold Collected { holdId: HoldId, toolId: ToolId assigned, neighborId: NeighborId assigned, loanId: LoanId, collectedAt: Instant assigned } loops-to "Holds To Collect" @Holds
}
slice "My Holds — hold collected" {
  view My Holds again from "Hold Collected"
  ui My Holds @Neighbor
}
slice "Undelivered Notices — hold collected" {
  view Undelivered Notices again from "Hold Collected"
  ui Undelivered Notices @Shed Manager
}

slice "Cancel Hold" {
  ui My Holds @Neighbor
  command Cancel Hold { neighborId: NeighborId, holdId: HoldId } note "slices/cancel-hold.md"
  event Hold Cancelled { holdId: HoldId, cancelledAt: Instant assigned } loops-to "Holds To Collect" @Holds
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
slice "Undelivered Notices — hold cancelled" {
  view Undelivered Notices again from "Hold Cancelled"
  ui Undelivered Notices @Shed Manager
}

slice "Holds To Fulfill" {
  view Holds To Fulfill from "Hold Placed", "Tool Returned" updates from "Hold Collected", "Hold Cancelled", "Tool Checked Out" { toolId: ToolId, holdId: HoldId derived, neighborId: NeighborId derived } note "slices/holds-to-fulfill.md"
}

slice "Fulfill Hold" {
  processor Hold Fulfiller from "Holds To Fulfill" note "slices/fulfill-hold.md"
  command Fulfill Hold { holdId: HoldId }
  event Hold Fulfilled { holdId: HoldId, toolId: ToolId assigned, toolName: Text assigned, neighborId: NeighborId assigned, neighborName: Text assigned, neighborEmail: Text assigned, collectByDate: Date assigned, fulfilledAt: Instant assigned } loops-to "Holds To Fulfill" @Holds
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
  view Pickup Notices To Send from "Hold Fulfilled" updates from "Hold Cancelled", "Hold Collected", "Neighbor Email Changed" { holdId: HoldId, toolId: ToolId, neighborId: NeighborId, collectByDate: Date, holderName: Text derived from "Hold Fulfilled", holderEmail: Text derived from "Hold Fulfilled", "Neighbor Email Changed", toolName: Text } note "slices/pickup-notices-to-send.md"
}

slice "Send Pickup Notice" {
  translation Pickup Notice Gateway from "Pickup Notices To Send" note "slices/send-pickup-notice.md"
  command Send Pickup Notice { holdId: HoldId, neighborId: NeighborId, sentTo: Text, holderName: Text, toolName: Text, collectByDate: Date }
  event Pickup Notice Sent { holdId: HoldId, neighborId: NeighborId, sentTo: Text, holderName: Text, toolName: Text, collectByDate: Date, sentAt: Instant assigned } loops-to "Pickup Notices To Send" @Holds
}
slice "My Holds — pickup notice sent" {
  view My Holds again from "Pickup Notice Sent"
  ui My Holds @Neighbor
}

slice "Record Pickup Notice Failure" {
  translation Pickup Email Failure Report note "slices/record-pickup-notice-failure.md" divergence "No email provider selected yet (constitution Amendment 8): no inbound transport reaches this translation and its verifier fails closed until a provider is chosen; the mapping code is in place (conformance 2026-11-01, rulings 2 and 3)"
  command Record Pickup Notice Failure { holdId: HoldId, reason: Text }
  event Pickup Notice Failed { holdId: HoldId, toolId: ToolId assigned, neighborId: NeighborId assigned, neighborName: Text assigned, neighborEmail: Text assigned, toolName: Text assigned, collectByDate: Date assigned, reason: Text, failedAt: Instant assigned } @Holds
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
  view Pickups To Expire from "Hold Fulfilled", "Hold Collected", "Hold Cancelled" { holdId: HoldId, toolId: ToolId, neighborId: NeighborId, collectByDate: Date, dueToExpire: Bool derived } note "slices/pickups-to-expire.md"
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
