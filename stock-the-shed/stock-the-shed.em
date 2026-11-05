storyline "Stock the shed"

slice "Add Tool" {
  ui Tool Inventory @Shed Manager
  command Add Tool { toolId: ToolId, name: Text, description: Text, defaultCheckoutPeriodDays: Int } note "slices/add-tool.md"
  event Tool Added { toolId: ToolId, name: Text, description: Text, defaultCheckoutPeriodDays: Int, addedAt: Instant assigned } @Tools
}
slice "Tool Inventory" {
  view Tool Inventory from "Tool Added" { toolId: ToolId, name: Text, description: Text, defaultCheckoutPeriodDays: Int, addedAt: Instant, onLoan: Bool derived, loanId: LoanId derived, borrowerId: NeighborId derived, borrowerName: Text derived, dueDate: Date derived, retired: Bool derived, canRetire: Bool derived, canMarkLost: Bool derived, overdue: Bool derived } note "slices/tool-inventory.md"
  ui Tool Inventory @Shed Manager
}
slice "Tool Catalog" {
  view Tool Catalog from "Tool Added" { toolId: ToolId, name: Text, description: Text, defaultCheckoutPeriodDays: Int, addedAt: Instant, status: Text derived, dueDate: Date derived, holdsWaiting: Int derived, canCheckOut: Bool derived, canPlaceHold: Bool derived } note "slices/tool-catalog.md"
  ui Tool Catalog @Neighbor
}
