storyline "Join the shed"

slice "Register Neighbor" {
  ui Sign Up @Neighbor
  command Register Neighbor { neighborId: NeighborId, name: Text, email: Text } note "slices/register-neighbor.md"
  event Neighbor Registered { neighborId: NeighborId, name: Text, email: Text, registeredAt: Instant assigned } @Neighbors
}
slice "Neighbors" {
  view Neighbors from "Neighbor Registered" { neighborId: NeighborId, name: Text, email: Text, registeredAt: Instant, status: Text derived, suspendedAt: Instant derived, flagCount: Int derived, latestFlaggedAt: Instant derived, lateReturns: LateReturn[] derived, canSuspend: Bool derived, canReinstate: Bool derived } note "slices/neighbors.md"
  ui Neighbors @Shed Manager
}
slice "My Account" {
  view My Account from "Neighbor Registered" { neighborId: NeighborId, name: Text, email: Text, status: Text derived, canChangeEmail: Bool derived, canCloseAccount: Bool derived } note "slices/my-account.md"
  ui My Account @Neighbor
}

slice "Change Email" {
  ui My Account @Neighbor
  command Change Email { neighborId: NeighborId, email: Text } note "slices/change-email.md"
  event Neighbor Email Changed { neighborId: NeighborId, email: Text, changedAt: Instant assigned } @Neighbors
}
slice "My Account — email changed" {
  view My Account again from "Neighbor Email Changed"
  ui My Account @Neighbor
}
slice "Neighbors — email changed" {
  view Neighbors again from "Neighbor Email Changed"
  ui Neighbors @Shed Manager
}
