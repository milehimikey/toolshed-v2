storyline "Keep tools in working order"

# The shed manager sends a tool in the shed for maintenance (sharpening, cleaning, tuning, ...),
# with the tasks it needs and any notes. While it is in maintenance it is off the Tool Catalog.
# Scheduling a tool that is already in maintenance is not refused: it edits the maintenance,
# replacing the whole task list and notes (add, remove or reword a task), and records Tool
# Maintenance Tasks Changed. The same list and notes again records nothing (Amendment 7).
# Who scheduled it (scheduledBy) and who changed it (changedBy) are assigned by the handler from
# the signed-in shed manager's identity, never typed by the caller. Completing the work puts the
# tool back.
slice "Schedule Tool Maintenance" {
  ui Tool Inventory @Shed Manager
  command Schedule Tool Maintenance { toolId: ToolId, tasks: Text[], notes: Text } note "slices/schedule-tool-maintenance.md"
  oneof {
    event Tool Maintenance Scheduled @Tools { toolId: ToolId, toolName: Text assigned, tasks: Text[], notes: Text, scheduledBy: Text assigned, scheduledAt: Instant assigned }
    event Tool Maintenance Tasks Changed @Tools { toolId: ToolId, tasks: Text[], notes: Text, changedBy: Text assigned, changedAt: Instant assigned }
  }
}
slice "Tool Maintenance" {
  view Tool Maintenance from "Tool Maintenance Scheduled" updates from "Tool Maintenance Tasks Changed", "Tool Retired" { toolId: ToolId, toolName: Text, scheduledBy: Text, scheduledAt: Instant, tasks: Text[] derived from "Tool Maintenance Scheduled", "Tool Maintenance Tasks Changed", notes: Text derived from "Tool Maintenance Scheduled", "Tool Maintenance Tasks Changed", lastChangedBy: Text derived from "Tool Maintenance Tasks Changed", lastChangedAt: Instant derived from "Tool Maintenance Tasks Changed" } note "slices/tool-maintenance.md"
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
