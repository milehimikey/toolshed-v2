storyline "Keep tools in working order"

# The shed manager sends a tool in the shed for maintenance (sharpening, cleaning, tuning, ...),
# with the tasks it needs and any notes. Who sent it (scheduledBy) and who added later tasks
# (addedBy) are assigned by the handler from the signed-in shed manager's identity, never typed
# by the caller. While it is in maintenance it is off the Tool Catalog. More tasks found while it
# is in maintenance are added to it; completing the work puts it back.
slice "Schedule Tool Maintenance" {
  ui Tool Inventory @Shed Manager
  command Schedule Tool Maintenance { toolId: ToolId, tasks: Text[], notes: Text } note "slices/schedule-tool-maintenance.md"
  event Tool Maintenance Scheduled { toolId: ToolId, toolName: Text assigned, tasks: Text[], notes: Text, scheduledBy: Text assigned, scheduledAt: Instant assigned } @Tools
}
slice "Tool Maintenance" {
  view Tool Maintenance from "Tool Maintenance Scheduled" updates from "Tool Retired" { toolId: ToolId, toolName: Text, scheduledBy: Text, scheduledAt: Instant, tasks: Text[] derived, notes: Text[] derived, taskCount: Int derived, lastAddedAt: Instant derived } note "slices/tool-maintenance.md"
  ui Tool Maintenance @Shed Manager
}
slice "Tool Catalog — maintenance scheduled" {
  view Tool Catalog again from "Tool Maintenance Scheduled"
  ui Tool Catalog @Neighbor
}

slice "Add Tool Maintenance Tasks" {
  ui Tool Maintenance @Shed Manager
  command Add Tool Maintenance Tasks { toolId: ToolId, fromTaskCount: Int, tasks: Text[], notes: Text } note "slices/add-tool-maintenance-tasks.md"
  event Tool Maintenance Tasks Added { toolId: ToolId, fromTaskCount: Int, tasks: Text[], notes: Text, addedBy: Text assigned, addedAt: Instant assigned } loops-to "Tool Maintenance" @Tools
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
