storyline "Take a tool or a neighbor out of the shed"

slice "Retire Tool" {
  ui Tool Inventory @Shed Manager
  command Retire Tool { toolId: ToolId } note "slices/retire-tool.md"
  event Tool Retired { toolId: ToolId, retiredAt: Instant assigned } @Tools
  event Hold Cancelled @Holds
}
slice "My Holds — tool retired" {
  view My Holds again from "Hold Cancelled"
  ui My Holds @Neighbor
}
slice "Tool Inventory — tool retired" {
  view Tool Inventory again from "Tool Retired"
  ui Tool Inventory @Shed Manager
}
slice "Tool Catalog — tool retired" {
  view Tool Catalog again from "Tool Retired"
  ui Tool Catalog @Neighbor
}
slice "My Loans — tool retired" {
  view My Loans again from "Tool Retired"
  ui My Loans @Neighbor
}

slice "Record Tool Lost" {
  ui Tool Inventory @Shed Manager
  command Record Tool Lost { loanId: LoanId, toolId: ToolId } note "slices/record-tool-lost.md"
  event Tool Lost { loanId: LoanId, toolId: ToolId assigned, lostAt: Instant assigned } loops-to "Overdue Loans To Notify" @Tools
  event Hold Cancelled @Holds
}
slice "My Holds — tool lost" {
  view My Holds again from "Hold Cancelled"
  ui My Holds @Neighbor
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
slice "Undelivered Notices — tool lost" {
  view Undelivered Notices again from "Tool Lost"
  ui Undelivered Notices @Shed Manager
}

slice "Close Account" {
  ui My Account @Neighbor
  command Close Account { neighborId: NeighborId } note "slices/close-account.md"
  event Neighbor Account Closed { neighborId: NeighborId, whileSuspended: Bool assigned, closedAt: Instant assigned } loops-to "Neighbors To Flag" @Neighbors
  event Hold Cancelled @Holds
}
slice "My Holds — account closed" {
  view My Holds again from "Hold Cancelled"
  ui My Holds @Neighbor
}
slice "My Account — account closed" {
  view My Account again from "Neighbor Account Closed"
  ui My Account @Neighbor
}
slice "Neighbors — account closed" {
  view Neighbors again from "Neighbor Account Closed"
  ui Neighbors @Shed Manager
}
