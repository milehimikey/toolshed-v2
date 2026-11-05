storyline "Keep late returners in check"

slice "Neighbors To Flag" {
  view Neighbors To Flag from "Tool Returned" { neighborId: NeighborId, loanId: LoanId, toolName: Text, dueDate: Date, returnedOn: Date, inWindow: Bool derived } note "slices/neighbors-to-flag.md"
}

slice "Flag Neighbor" {
  processor Late Return Flagger from "Neighbors To Flag" note "slices/flag-neighbor.md"
  command Flag Neighbor { neighborId: NeighborId, lateReturns: LateReturnFact[] }
  event Neighbor Flagged { neighborId: NeighborId, lateReturns: LateReturnFact[], flaggedAt: Instant assigned } loops-to "Neighbors To Flag" @Neighbors
}
slice "Neighbors — neighbor flagged" {
  view Neighbors again from "Neighbor Flagged"
  ui Neighbors @Shed Manager
}

slice "Suspend Neighbor" {
  ui Neighbors @Shed Manager
  command Suspend Neighbor { neighborId: NeighborId } note "slices/suspend-neighbor.md"
  event Neighbor Suspended { neighborId: NeighborId, suspendedAt: Instant assigned } loops-to "Neighbors To Flag" @Neighbors
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
  ui Neighbors @Shed Manager
  command Reinstate Neighbor { neighborId: NeighborId } note "slices/reinstate-neighbor.md"
  event Neighbor Reinstated { neighborId: NeighborId, reinstatedAt: Instant assigned } loops-to "Neighbors To Flag" @Neighbors
}
slice "Neighbors — neighbor reinstated" {
  view Neighbors again from "Neighbor Reinstated"
  ui Neighbors @Shed Manager
}
slice "My Account — neighbor reinstated" {
  view My Account again from "Neighbor Reinstated"
  ui My Account @Neighbor
}
