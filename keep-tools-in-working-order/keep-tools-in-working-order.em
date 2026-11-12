storyline "Keep tools in working order"

# The shed manager sends a tool in the shed for maintenance (sharpening, cleaning, tuning, ...).
# While it is in maintenance it is off the Tool Catalog; completing the work puts it back.
slice "Schedule Tool Maintenance" {
  ui Tool Inventory @Shed Manager
  command Schedule Tool Maintenance { toolId: ToolId, tasks: Text[] } note "slices/schedule-tool-maintenance.md"
  event Tool Maintenance Scheduled { toolId: ToolId, toolName: Text assigned, tasks: Text[], scheduledAt: Instant assigned } @Tools
}
slice "Tool Maintenance" {
  view Tool Maintenance from "Tool Maintenance Scheduled" updates from "Tool Retired" { toolId: ToolId, toolName: Text, tasks: Text[], scheduledAt: Instant } note "slices/tool-maintenance.md"
  ui Tool Maintenance @Shed Manager
}
slice "Tool Catalog — maintenance scheduled" {
  view Tool Catalog again from "Tool Maintenance Scheduled"
  ui Tool Catalog @Neighbor
}

slice "Complete Tool Maintenance" {
  ui Tool Maintenance @Shed Manager
  command Complete Tool Maintenance { toolId: ToolId } note "slices/complete-tool-maintenance.md"
  event Tool Maintenance Completed { toolId: ToolId, completedAt: Instant assigned } loops-to "Tool Maintenance" @Tools
}
slice "Tool Catalog — maintenance completed" {
  view Tool Catalog again from "Tool Maintenance Completed"
  ui Tool Catalog @Neighbor
}
